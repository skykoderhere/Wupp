package com.wupp.meow.core.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wupp.meow.MainActivity
import com.wupp.meow.R
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.data.db.ContactEntity
import com.wupp.meow.data.db.GroupReceiptEntity
import com.wupp.meow.data.db.MessageEditEntity
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.data.db.MessageReactionEntity
import com.wupp.meow.data.db.PresenceLogEntity
import com.wupp.meow.data.db.ProfileHistoryEntity
import com.wupp.meow.data.db.StatusEntity
import com.wupp.meow.data.db.WaDao
import com.wupp.meow.data.db.WaUiDatabase
import com.wupp.meow.data.media.EagerMediaVault
import com.wupp.meow.data.media.OutboundQualityMode
import com.wupp.meow.data.media.SoundPoolManager
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wa.bridge.whatsbridge.NativeBridgeCallback
import com.wa.bridge.whatsbridge.Whatsbridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BridgeStatus(
    val engineReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val connectionState: String = "INITIALIZING",
    val ownJid: String = "",
    val latestQrCode: String? = null,
    val latestPairCode: String? = null,
    val syncProgressPct: Int = 0,
    val isSyncing: Boolean = false,
    val lastError: String? = null
)

/**
 * Resolves the UI message type and bridge upload wire mode for outbound attachments.
 * Videos remain native VIDEO messages even when [OutboundQualityMode.ORIGINAL] is active so
 * they render with preview thumbnails and playback controls instead of raw document filenames.
 */
internal fun resolveOutboundMediaDispatch(
    mimeType: String,
    qualityMode: OutboundQualityMode,
    isVoiceNote: Boolean
): Pair<String, String> = when {
    isVoiceNote -> "PTT" to qualityMode.wireValue
    mimeType.startsWith("video/", ignoreCase = true) -> {
        val wireMode = if (qualityMode == OutboundQualityMode.ORIGINAL) {
            OutboundQualityMode.HD.wireValue
        } else {
            qualityMode.wireValue
        }
        "VIDEO" to wireMode
    }
    qualityMode == OutboundQualityMode.ORIGINAL -> "DOCUMENT" to OutboundQualityMode.ORIGINAL.wireValue
    mimeType.startsWith("image/", ignoreCase = true) -> "IMAGE" to qualityMode.wireValue
    mimeType.startsWith("audio/", ignoreCase = true) -> "AUDIO" to qualityMode.wireValue
    else -> "DOCUMENT" to qualityMode.wireValue
}

/**
 * Persistent remoteMessaging foreground service hosting the Go whatsmeow bridge,
 * ordered event Channel consumer, sync WakeLock, and network switch socket resetter.
 */
class WaRealtimeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeChannel = Channel<Pair<String, String>>(capacity = Channel.UNLIMITED)
    private val syncChannel = Channel<Pair<String, String>>(capacity = Channel.UNLIMITED)
    private lateinit var dao: WaDao
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasSeenInitialNetwork = false
    private val pushnameCache = ConcurrentHashMap<String, String>()
    private val requestedAvatarJids = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var lastWatchdogReconnectMs: Long = 0L
    @Volatile
    private var watchdogConsecutiveFailures: Int = 0
    private val typingExpiryJobs = ConcurrentHashMap<String, Job>()
    private val pendingReactions = ConcurrentHashMap<String, MutableList<JSONObject>>()
    private val chatAliasToCanonical = ConcurrentHashMap<String, String>()

    private suspend fun canonicalizeAndMergeAlias(rawJid: String, rawAltJid: String): String {
        val jid = rawJid.trim()
        val alt = rawAltJid.trim()
        if (jid.isBlank()) return jid
        if (jid.endsWith("@g.us") || jid.endsWith("@newsletter") || jid == "status@broadcast") {
            return jid
        }
        val mapped = when {
            jid.endsWith("@lid") && alt.endsWith("@s.whatsapp.net") -> {
                chatAliasToCanonical[jid] = alt
                dao.mergeDuplicateChatAlias(canonicalJid = alt, duplicateJid = jid)
                pushnameCache[jid]?.let { n -> pushnameCache.putIfAbsent(alt, n) }
                if (activeChatJid.value == jid) activeChatJid.value = alt
                alt
            }
            jid.endsWith("@s.whatsapp.net") && alt.endsWith("@lid") -> {
                chatAliasToCanonical[alt] = jid
                dao.mergeDuplicateChatAlias(canonicalJid = jid, duplicateJid = alt)
                pushnameCache[alt]?.let { n -> pushnameCache.putIfAbsent(jid, n) }
                if (activeChatJid.value == alt) activeChatJid.value = jid
                jid
            }
            else -> chatAliasToCanonical[jid] ?: jid
        }
        return mapped
    }

    private fun stripJidDevice(rawJid: String): String {
        val trimmed = rawJid.trim()
        val atIdx = trimmed.indexOf('@')
        if (atIdx <= 0) return trimmed
        val user = trimmed.substring(0, atIdx).substringBefore(':')
        val server = trimmed.substring(atIdx + 1)
        return if (user.isNotBlank() && server.isNotBlank()) "$user@$server" else trimmed
    }

    private fun isNumericOrPhoneString(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        var hasDigit = false
        for (ch in trimmed) {
            when {
                ch.isDigit() -> hasDigit = true
                ch == '+' || ch == '-' || ch == ' ' || ch == '(' || ch == ')' -> Unit
                else -> return false
            }
        }
        return hasDigit
    }

    private suspend fun propagateStatusAndSenderName(
        rawJid: String,
        name: String,
        overwriteStatus: Boolean
    ) {
        val jid = stripJidDevice(rawJid)
        if (jid.isBlank() || name.isBlank()) return
        val user = jid.substringBefore("@")
        dao.updateSenderNameForJid(jid, user, name)
        dao.updateStatusSenderNameForJid(jid, user, name, overwrite = overwriteStatus)
    }

    private suspend fun drainPendingReactions(messageId: String) {
        val queued = pendingReactions.remove(messageId) ?: return
        for (rx in queued) {
            handleReaction(rx)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        dao = WaUiDatabase.getInstance(applicationContext).waDao()
        EagerMediaVault.ensureDirectories(applicationContext)
        StealthMatrixManager.init(applicationContext)
        SoundPoolManager.init(applicationContext)

        serviceScope.launch {
            reloadGroupLocks(applicationContext)
            runCatching {
                dao.clearAllChatPresence()
                dao.clearFakeTranscripts()
                dao.deleteShowcaseChats()
                dao.deleteShowcaseMessages()
                dao.deleteShowcaseStatuses()
                dao.deleteEmptyUnknownMessages()
                dao.deleteOrphanReactions()
                dao.normalizeViewOncePreviews()
                val healPrefs = getSharedPreferences("wupp_migrations", Context.MODE_PRIVATE)
                if (!healPrefs.getBoolean("newsletter_unread_healed_v1", false)) {
                    dao.resetInflatedNewsletterUnread()
                    healPrefs.edit().putBoolean("newsletter_unread_healed_v1", true).apply()
                }
                dao.cleanupEmptyProtocolChats()
                dao.getChatsWithBlankPreview().forEach { chat ->
                    val latest = dao.getLatestMessageForChat(chat.jid)
                    if (latest != null) {
                        dao.upsertChat(
                            chat.copy(
                                lastMessageId = latest.id,
                                lastMessagePreview = WaDao.previewTextFor(latest),
                                lastMessageType = if (latest.isViewOnce) "VIEW_ONCE_${latest.msgType}" else latest.msgType,
                                lastMessageFromMe = latest.isFromMe,
                                lastMessageStatus = latest.deliveryStatus,
                                lastMessageIsDeleted = latest.isDeletedBySender,
                                lastMessageTimestamp = maxOf(chat.lastMessageTimestamp, latest.timestamp)
                            )
                        )
                    }
                }
                dao.getAllChatsOnce().forEach { chat ->
                    val jidUser = chat.jid.substringBefore("@")
                    if (chat.name.isNotBlank() && chat.name != jidUser && chat.name != chat.jid && !chat.name.startsWith("+")) {
                        pushnameCache[chat.jid] = chat.name
                    } else if (!chat.isGroup) {
                        val fallbackName = dao.getLatestIncomingSenderName(chat.jid)
                        if (!fallbackName.isNullOrBlank()) {
                            pushnameCache[chat.jid] = fallbackName
                            dao.updateChatName(chat.jid, fallbackName, overwrite = false)
                        }
                    }
                }
            }
        }

        promoteForeground("Real-time sync starting…")
        startEventConsumer()
        registerNetworkWatcher()
        initializeGoBridge()
        startSocketWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECONNECT -> serviceScope.launch {
                if (hasValidatedInternet() && runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    runCatching { Whatsbridge.connectClient() }
                }
            }
            ACTION_DISCONNECT -> serviceScope.launch {
                runCatching { Whatsbridge.disconnectClient() }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val setupDone = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        if (setupDone && runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
            SyncWatchdogJobService.scheduleTaskRemovedBounce(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterNetworkWatcher()
        releaseSyncWakeLock()
        runCatching { Whatsbridge.disconnectClient() }
        realtimeChannel.close()
        syncChannel.close()
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun promoteForeground(statusLine: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Wupp Realtime Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps WhatsApp syncing in real time. Turning off 'Show notifications' here hides the constant banner without stopping sync or chat alerts."
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
            ensureMessageNotificationChannel(mgr)
        }
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wupp")
            .setContentText(statusLine)
            .setSmallIcon(R.drawable.ic_stat_wupp)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to elevate foreground service in current process state: ${err.message}")
            SyncWatchdogJobService.scheduleOneShotRetry(applicationContext, delayMs = 15_000L)
            stopSelf()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun startSocketWatchdog() {
        serviceScope.launch {
            delay(20_000L)
            while (true) {
                delay(45_000L)
                nudgeReconnectIfNeeded()
            }
        }
    }

    fun nudgeReconnectIfNeeded() {
        serviceScope.launch {
            val loggedIn = runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)
            if (!loggedIn) return@launch
            val connected = runCatching { Whatsbridge.isConnected() }.getOrDefault(false)
            if (connected) {
                watchdogConsecutiveFailures = 0
                return@launch
            }
            if (!hasValidatedInternet()) return@launch
            val cooldownMs = when (watchdogConsecutiveFailures) {
                0 -> 60_000L
                1 -> 120_000L
                else -> 300_000L
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastWatchdogReconnectMs < cooldownMs) return@launch
            lastWatchdogReconnectMs = now
            watchdogConsecutiveFailures = (watchdogConsecutiveFailures + 1).coerceAtMost(5)
            Log.i(TAG, "Self-healing socket reconnect (attempt=$watchdogConsecutiveFailures)")
            runCatching { Whatsbridge.connectClient() }
        }
    }

    private fun acquireSyncWakeLock() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaBeast:SyncWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(60_000L)
            }
        }
    }

    private fun releaseSyncWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun registerNetworkWatcher() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                syncAutoDownloadPolicy(applicationContext)
                if (!hasSeenInitialNetwork) {
                    hasSeenInitialNetwork = true
                    return
                }
                Log.d(TAG, "Network changed/available -> resetting Go socket <500ms")
                serviceScope.launch {
                    if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        runCatching { Whatsbridge.resetSocket() }
                    }
                }
            }

            override fun onLost(network: Network) {
                _bridgeStatus.value = _bridgeStatus.value.copy(connectionState = "RECONNECTING")
            }
        }
        networkCallback = cb
        runCatching { cm.registerNetworkCallback(req, cb) }
    }

    private fun unregisterNetworkWatcher() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun initializeGoBridge() {
        serviceScope.launch {
            runCatching {
                val callback = NativeBridgeCallback { eventType, payloadJson ->
                    if (eventType == "BRIDGE_LOG") {
                        runCatching {
                            val json = JSONObject(payloadJson)
                            val lvl = json.optString("level", "INFO")
                            val mod = json.optString("module", "Go")
                            val msg = json.optString("msg")
                            if (lvl == "ERROR" || lvl == "WARN") {
                                Log.w("WaBridgeGo", "[$lvl/$mod] $msg")
                            } else {
                                Log.i("WaBridgeGo", "[$mod] $msg")
                            }
                        }
                        return@NativeBridgeCallback
                    }
                    val target = if (eventType == "HISTORY_SYNC_BATCH" || eventType == "CONTACTS_SYNC_BATCH") {
                        syncChannel
                    } else {
                        realtimeChannel
                    }
                    target.trySend(eventType to payloadJson)
                }
                Whatsbridge.initClient(applicationContext.filesDir.absolutePath, callback)
                Whatsbridge.setGhostPresence(StealthMatrixManager.config.value.ghostPresence)
                syncAutoDownloadPolicy(applicationContext)
                // Always connect cleanly so paired sessions sync immediately and fresh installs stream QR right away.
                Whatsbridge.connectClient()
            }.onFailure { err ->
                Log.e(TAG, "Go bridge init failed", err)
                _bridgeStatus.value = _bridgeStatus.value.copy(
                    engineReady = true,
                    connectionState = "OFFLINE_READY",
                    lastError = err.message
                )
            }
        }
    }

    private fun startEventConsumer() {
        serviceScope.launch {
            for ((eventType, payloadJson) in realtimeChannel) {
                runCatching {
                    dispatchEvent(eventType, JSONObject(payloadJson))
                }.onFailure { err ->
                    Log.e(TAG, "Error dispatching $eventType: ${err.message}", err)
                }
            }
        }
        serviceScope.launch {
            for ((eventType, payloadJson) in syncChannel) {
                runCatching {
                    dispatchEvent(eventType, JSONObject(payloadJson))
                }.onFailure { err ->
                    Log.e(TAG, "Error dispatching $eventType: ${err.message}", err)
                }
            }
        }
    }

    private suspend fun dispatchEvent(eventType: String, json: JSONObject) {
        when (eventType) {
            "ENGINE_READY" -> {
                val own = json.optString("ownJid", "")
                val loggedIn = json.optBoolean("isLoggedIn", false) || own.isNotBlank()
                _bridgeStatus.value = _bridgeStatus.value.copy(
                    engineReady = true,
                    isLoggedIn = loggedIn,
                    ownJid = own,
                    connectionState = if (loggedIn) "CONNECTING" else "AWAITING_PAIR"
                )
            }
            "QR" -> {
                _bridgeStatus.value = _bridgeStatus.value.copy(
                    latestQrCode = json.optString("code"),
                    isLoggedIn = false,
                    connectionState = "QR_READY"
                )
            }
            "PAIR_CODE" -> {
                _bridgeStatus.value = _bridgeStatus.value.copy(
                    latestPairCode = json.optString("code"),
                    connectionState = "PAIR_CODE_READY"
                )
            }
            "CONNECTION_STATE" -> {
                val st = json.optString("state", "DISCONNECTED")
                val own = json.optString("ownJid", _bridgeStatus.value.ownJid)
                val nextLoggedIn = when (st) {
                    "LOGGED_OUT" -> false
                    "PAIRED", "CONNECTED" -> true
                    else -> _bridgeStatus.value.isLoggedIn || runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)
                }
                _bridgeStatus.value = _bridgeStatus.value.copy(
                    engineReady = true,
                    isLoggedIn = nextLoggedIn,
                    connectionState = st,
                    ownJid = if (st == "LOGGED_OUT") "" else own,
                    latestQrCode = if (nextLoggedIn) null else _bridgeStatus.value.latestQrCode,
                    latestPairCode = if (nextLoggedIn) null else _bridgeStatus.value.latestPairCode
                )
                val statusLine = when {
                    !nextLoggedIn -> "Waiting to link WhatsApp"
                    st == "CONNECTED" -> "Real-time sync active"
                    st == "RECONNECTING" -> "Reconnecting real-time sync…"
                    else -> "Real-time sync ($st)"
                }
                promoteForeground(statusLine)
                if (st == "CONNECTED") {
                    watchdogConsecutiveFailures = 0
                    SyncWatchdogJobService.schedulePeriodic(applicationContext)
                    runCatching {
                        Whatsbridge.setGhostPresence(StealthMatrixManager.config.value.ghostPresence)
                    }
                    serviceScope.launch {
                        val jidsCsv = runCatching {
                            dao.getAllChatsOnce().joinToString(",") { it.jid }
                        }.getOrDefault("")
                        triggerContactsAndAvatarsSync(jidsCsv)
                    }
                }
            }
            "HISTORY_SYNC_BATCH" -> handleHistorySyncBatch(json)
            "CONTACTS_SYNC_BATCH" -> handleContactsSyncBatch(json)
            "GROUPS_SYNC_BATCH" -> handleGroupsSyncBatch(json)
            "NEWSLETTERS_SYNC_BATCH" -> handleNewslettersSyncBatch(json)
            "MESSAGE_UPSERT" -> handleMessageUpsert(json)
            "STATUS_UPSERT" -> handleStatusUpsert(json)
            "MESSAGE_REVOKE" -> handleMessageRevoke(json)
            "MESSAGE_EDIT" -> handleMessageEdit(json)
            "MESSAGE_REACTION" -> handleReaction(json)
            "VIEW_ONCE_STRIPPED" -> handleViewOnceStripped(json)
            "VIEW_ONCE_EXPIRED" -> handleViewOnceExpired(json)
            "UNDECRYPTABLE_STUB" -> handleUndecryptableStub(json)
            "MEDIA_DOWNLOADED" -> handleMediaDownloaded(json)
            "MEDIA_DOWNLOAD_STATUS" -> handleMediaDownloadStatus(json)
            "RECEIPT" -> handleReceipt(json)
            "PRESENCE" -> handlePresence(json)
            "CHAT_PRESENCE" -> handleChatPresence(json)
            "CHAT_META_UPDATE" -> handleChatMetaUpdate(json)
            "PROFILE_UPDATE" -> handleProfileUpdate(json)
            "BRIDGE_LOG" -> {
                val lvl = json.optString("level", "INFO")
                val mod = json.optString("module", "Go")
                val msg = json.optString("msg")
                if (lvl == "ERROR" || lvl == "WARN") {
                    Log.w("WaBridgeGo", "[$lvl/$mod] $msg")
                } else {
                    Log.i("WaBridgeGo", "[$mod] $msg")
                }
            }
        }
    }

    private suspend fun handleHistorySyncBatch(json: JSONObject) {
        acquireSyncWakeLock()
        val progress = json.optInt("progress", 0)
        val isFinal = json.optBoolean("isFinal", false)
        _bridgeStatus.value = _bridgeStatus.value.copy(
            isSyncing = !isFinal,
            syncProgressPct = progress
        )
        try {
            val syncedContacts = ArrayList<ContactEntity>(128)
            val pushArr = json.optJSONArray("pushnames")
            if (pushArr != null) {
                for (i in 0 until pushArr.length()) {
                    val item = pushArr.optJSONObject(i) ?: continue
                    val jid = item.optString("jid")
                    val altJid = item.optString("altJid")
                    val name = item.optString("name").trim()
                    val canonicalJid = if (altJid.isNotBlank()) canonicalizeAndMergeAlias(jid, altJid) else (chatAliasToCanonical[jid] ?: jid)
                    if (canonicalJid.isNotBlank() && name.isNotBlank() && name != canonicalJid && name != canonicalJid.substringBefore("@")) {
                        pushnameCache[canonicalJid] = name
                        dao.updateChatName(canonicalJid, name, overwrite = false)
                        propagateStatusAndSenderName(canonicalJid, name, overwriteStatus = false)
                        syncedContacts.add(
                            ContactEntity(
                                jid = canonicalJid,
                                name = name,
                                isSaved = false,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (jid != canonicalJid && jid.isNotBlank() && name.isNotBlank() && name != jid && name != jid.substringBefore("@")) {
                        pushnameCache[jid] = name
                        propagateStatusAndSenderName(jid, name, overwriteStatus = false)
                    }
                    if (altJid.isNotBlank() && name.isNotBlank() && name != altJid && name != altJid.substringBefore("@")) {
                        pushnameCache[altJid] = name
                        dao.updateChatName(altJid, name, overwrite = false)
                        propagateStatusAndSenderName(altJid, name, overwriteStatus = false)
                    }
                }
            }

            val chatsMap = LinkedHashMap<String, ChatEntity>()
            val unreadByChat = mutableMapOf<String, Int>()
            val chatsArr = json.optJSONArray("chats")
            if (chatsArr != null) {
                for (i in 0 until chatsArr.length()) {
                    val c = chatsArr.optJSONObject(i) ?: continue
                    val rawJid = c.optString("jid")
                    if (rawJid.isBlank() || rawJid == "status@broadcast" || rawJid.endsWith("@broadcast")) continue
                    val altJid = c.optString("altJid")
                    val jid = canonicalizeAndMergeAlias(rawJid, altJid)
                    if (jid == "status@broadcast" || jid.endsWith("@broadcast")) continue
                    val unread = c.optInt("unreadCount", 0)
                    unreadByChat[jid] = maxOf(unreadByChat[jid] ?: 0, unread)
                    val name = c.optString("name").ifBlank {
                        pushnameCache[jid] ?: altJid.takeIf { it.isNotBlank() }?.let { pushnameCache[it] } ?: jid.substringBefore("@")
                    }
                    val incomingIsReal = name.isNotBlank() &&
                        name != jid &&
                        name != jid.substringBefore("@") &&
                        !name.startsWith("+")
                    val resolvedChatName = if (incomingIsReal) {
                        pushnameCache[jid] = name
                        if (altJid.isNotBlank()) pushnameCache[altJid] = name
                        name
                    } else {
                        pushnameCache[jid]
                            ?: altJid.takeIf { it.isNotBlank() }?.let { pushnameCache[it] }
                            ?: name
                    }
                    val resolvedIsReal = resolvedChatName.isNotBlank() &&
                        resolvedChatName != jid &&
                        resolvedChatName != jid.substringBefore("@") &&
                        !resolvedChatName.startsWith("+") &&
                        (altJid.isBlank() || resolvedChatName != altJid.substringBefore("@"))
                    if (altJid.isNotBlank() && resolvedIsReal) {
                        dao.updateChatName(altJid, resolvedChatName, overwrite = false)
                    }
                    chatsMap[jid] = ChatEntity(
                        jid = jid,
                        name = resolvedChatName,
                        isGroup = c.optBoolean("isGroup", jid.endsWith("@g.us")),
                        unreadCount = unreadByChat[jid] ?: unread,
                        isArchived = c.optBoolean("archived", false),
                        isPinned = c.optLong("pinned", 0L) > 0L,
                        pinOrder = c.optLong("pinned", 0L),
                        isMuted = c.optLong("muteEndTime", 0L) > 0L,
                        muteEndTime = c.optLong("muteEndTime", 0L),
                        lastMessageTimestamp = c.optLong("lastTimestampMs", 0L),
                        lastMessageType = "",
                        ephemeralDuration = c.optInt("ephemeralSec", 0),
                        isCommunityParent = c.optBoolean("isCommunityParent", false),
                        parentGroupJid = c.optString("parentGroupJid", ""),
                        isDefaultSubgroup = c.optBoolean("isDefaultSubgroup", false),
                        isLocked = c.optBoolean("locked", false)
                    )
                }
            }
            val chatsList = chatsMap.values.toList()
            for (ch in chatsList) {
                if (!ch.isGroup && !ch.isCommunityParent && !ch.jid.endsWith("@newsletter")) {
                    val bare = ch.jid.substringBefore("@")
                    val savedName = ch.name.isNotBlank() && ch.name != bare && ch.name != ch.jid && !ch.name.startsWith("+")
                    syncedContacts.add(
                        ContactEntity(
                            jid = ch.jid,
                            name = ch.name.ifBlank { bare },
                            aboutText = ch.aboutText,
                            avatarPath = ch.avatarPath,
                            isSaved = savedName,
                            updatedAt = maxOf(ch.lastMessageTimestamp, System.currentTimeMillis())
                        )
                    )
                }
            }
            if (syncedContacts.isNotEmpty()) {
                dao.upsertContacts(syncedContacts)
            }

            val msgList = mutableListOf<MessageEntity>()
            val msgsArr = json.optJSONArray("messages")
            if (msgsArr != null) {
                for (i in 0 until msgsArr.length()) {
                    val m = msgsArr.optJSONObject(i) ?: continue
                    var entity = parseNormalizedMessage(m)
                    val canonMsgChat = canonicalizeAndMergeAlias(entity.chatJid, m.optString("altChatJid"))
                    if (canonMsgChat != entity.chatJid) {
                        entity = entity.copy(chatJid = canonMsgChat)
                    }
                    if (!entity.isFromMe && (unreadByChat[entity.chatJid] ?: 0) == 0 && entity.deliveryStatus != "PLAYED") {
                        entity = entity.copy(deliveryStatus = "READ")
                    }
                    msgList.add(entity)
                }
            }

            dao.insertHistorySyncBatch(chatsList, msgList)
            dao.deleteEmptyUnknownMessages()
            dao.cleanupEmptyProtocolChats()
            val rxArr = json.optJSONArray("reactions")
            if (rxArr != null) {
                for (i in 0 until rxArr.length()) {
                    val rxObj = rxArr.optJSONObject(i) ?: continue
                    handleReaction(rxObj)
                }
            }
            for (entity in msgList) {
                if (pendingReactions.containsKey(entity.id)) {
                    drainPendingReactions(entity.id)
                }
            }
        } finally {
            if (isFinal) {
                releaseSyncWakeLock()
            }
        }
    }

    private fun triggerContactsAndAvatarsSync(jidsCsv: String) {
        runCatching {
            Whatsbridge::class.java
                .getMethod("syncContactsAndAvatars", String::class.java)
                .invoke(null, jidsCsv)
        }
    }

    private fun requestSingleAvatarIfNeeded(jid: String) {
        val clean = jid.trim()
        if (clean.isBlank() || clean == "status@broadcast" || !requestedAvatarJids.add(clean)) {
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            triggerContactsAndAvatarsSync(clean)
        }
    }

    private suspend fun handleMessageUpsert(json: JSONObject) {
        var msg = parseNormalizedMessage(json)
        if (msg.msgType == "UNKNOWN" && msg.body.isBlank() && !msg.isViewOnce) {
            return
        }
        val canonicalChat = canonicalizeAndMergeAlias(msg.chatJid, json.optString("altChatJid"))
        if (canonicalChat != msg.chatJid) {
            msg = msg.copy(chatJid = canonicalChat)
        }
        val isSync = json.optBoolean("isSync", false)
        val isGrp = json.optBoolean("isGroup", msg.chatJid.endsWith("@g.us"))
        val isActiveChat = activeChatJid.value == msg.chatJid
        val shouldAutoRead = !isSync && isActiveChat && !msg.isFromMe &&
            StealthMatrixManager.getEffectivePrivacy(dao, msg.chatJid).sendReadReceipts
        if (shouldAutoRead) {
            msg = msg.copy(deliveryStatus = "READ")
        }
        val nameHint = if (!isGrp && !msg.isFromMe) {
            msg.senderName.ifBlank { pushnameCache[msg.chatJid] ?: "" }
        } else {
            pushnameCache[msg.chatJid] ?: ""
        }
        val insertedNew = dao.upsertIncomingMessageAndRefreshChat(
            message = msg,
            chatNameHint = nameHint,
            isGroup = isGrp,
            incrementUnread = !isSync && !isActiveChat
        )
        if (!isGrp && !msg.isFromMe && !msg.chatJid.endsWith("@newsletter") && nameHint.isNotBlank()) {
            dao.upsertContacts(
                listOf(
                    ContactEntity(
                        jid = msg.chatJid,
                        name = nameHint,
                        isSaved = false,
                        updatedAt = msg.timestamp
                    )
                )
            )
        }
        drainPendingReactions(msg.id)
        if (dao.getChat(msg.chatJid)?.avatarPath.isNullOrBlank()) {
            requestSingleAvatarIfNeeded(msg.chatJid)
        }
        if (isActiveChat || (msg.isFromMe && !isSync)) {
            dao.clearLocalUnread(msg.chatJid)
            if (msg.isFromMe && !isSync) {
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                runCatching { mgr?.cancel(notificationIdForChat(msg.chatJid)) }
            }
            if (isActiveChat && shouldAutoRead && runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                val senderParam = if (isGrp) msg.senderJid else ""
                runCatching { Whatsbridge.markReadManual(msg.chatJid, senderParam, msg.id) }
                    .onFailure { Log.w(TAG, "Auto markRead failed for ${msg.id}: ${it.message}") }
            }
        }
        if (insertedNew && !isSync && !msg.isFromMe && !isActiveChat && !msg.chatJid.endsWith("@newsletter")) {
            if (isUiForeground) {
                SoundPoolManager.playIncoming()
            }
            postIncomingMessageNotification(msg, isGrp)
        }
    }

    private fun ensureMessageNotificationChannel(mgr: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val msgCh = NotificationChannel(
                MESSAGES_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming WhatsApp chat and group message alerts"
                enableVibration(true)
                setShowBadge(true)
            }
            mgr.createNotificationChannel(msgCh)
        }
    }

    private suspend fun postIncomingMessageNotification(msg: MessageEntity, isGroup: Boolean) {
        if (msg.chatJid == "status@broadcast" || msg.chatJid.endsWith("@newsletter")) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val chat = dao.getChat(msg.chatJid)
        if (chat?.isMuted == true || chat?.isArchived == true) return
        val stealthCfg = StealthMatrixManager.config.value
        if (isGroup && !stealthCfg.notifyGroupEnabled) return
        if (!isGroup && !stealthCfg.notifyDmEnabled) return

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureMessageNotificationChannel(mgr)

        val chatTitle = chat?.name?.takeIf { it.isNotBlank() }
            ?: msg.senderName.takeIf { it.isNotBlank() && !isGroup }
            ?: msg.chatJid.substringBefore("@")

        val bodyPreview = when {
            msg.isViewOnce -> when (msg.msgType.uppercase()) {
                "VIDEO" -> "View-once video"
                "PTT", "AUDIO" -> "View-once voice message"
                else -> "View-once photo"
            }
            msg.body.isNotBlank() -> msg.body
            msg.msgType == "IMAGE" -> "Photo"
            msg.msgType == "VIDEO" -> "Video"
            msg.msgType == "PTT" || msg.msgType == "AUDIO" -> "Voice message"
            msg.msgType == "DOCUMENT" -> "Document"
            msg.msgType == "STICKER" -> "Sticker"
            else -> "New message"
        }

        val contentText = if (!stealthCfg.notifyShowPreview) {
            "New message"
        } else if (isGroup) {
            val senderLabel = msg.senderName.takeIf { it.isNotBlank() }
                ?: msg.senderJid.substringBefore("@").substringBefore(":")
            if (senderLabel.isNotBlank()) "$senderLabel: $bodyPreview" else bodyPreview
        } else {
            bodyPreview
        }

        val notifId = notificationIdForChat(msg.chatJid)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat_jid", msg.chatJid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wupp)
            .setContentTitle(chatTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSilent(isUiForeground)
            .setWhen(msg.timestamp)
            .setShowWhen(true)
            .setGroup("wupp_messages")
            .setContentIntent(contentPendingIntent)
        if (!stealthCfg.notifyVibrateEnabled) {
            builder.setVibrate(longArrayOf(0L))
        }
        val notification = builder.build()

        runCatching { mgr.notify(notifId, notification) }
            .onFailure { Log.w(TAG, "Failed to post message notification: ${it.message}") }
    }

    private suspend fun handleStatusUpsert(json: JSONObject) {
        val id = json.optString("id")
        if (id.isBlank()) return
        val rawSenderJid = stripJidDevice(json.optString("senderJid"))
        val rawAltSenderJid = stripJidDevice(json.optString("altSenderJid"))
        val senderJid = canonicalizeAndMergeAlias(rawSenderJid, rawAltSenderJid).ifBlank { rawSenderJid }
        val candidateJids = listOf(senderJid, rawSenderJid, rawAltSenderJid)
            .filter { it.isNotBlank() }
            .distinct()

        val rawName = json.optString("senderName").trim()
        val rawIsUseful = rawName.isNotBlank() &&
            !isNumericOrPhoneString(rawName) &&
            rawName != senderJid &&
            rawName != senderJid.substringBefore("@")
        if (rawIsUseful && senderJid.isNotBlank()) {
            pushnameCache.putIfAbsent(senderJid, rawName)
        }

        val chatBetterName = candidateJids.firstNotNullOfOrNull { j ->
            dao.getChat(j)?.name?.trim()?.takeIf {
                it.isNotBlank() && !isNumericOrPhoneString(it) && it != j && it != j.substringBefore("@")
            }
        }
        val cacheBetterName = candidateJids.firstNotNullOfOrNull { j ->
            pushnameCache[j]?.trim()?.takeIf {
                it.isNotBlank() && !isNumericOrPhoneString(it) && it != j && it != j.substringBefore("@")
            }
        }
        val existingStatus = dao.getStatusById(id)
        val existingBetterName = existingStatus?.senderName?.trim()?.takeIf {
            it.isNotBlank() && it != "Status" && !isNumericOrPhoneString(it) &&
                it != senderJid && it != senderJid.substringBefore("@")
        }

        val resolvedName = when {
            rawIsUseful -> rawName
            chatBetterName != null -> chatBetterName
            cacheBetterName != null -> cacheBetterName
            existingBetterName != null -> existingBetterName
            rawName.isNotBlank() -> rawName
            else -> candidateJids.firstNotNullOfOrNull { j ->
                dao.getChat(j)?.name?.takeIf { it.isNotBlank() } ?: pushnameCache[j]
            } ?: senderJid.substringBefore("@").ifBlank { "Status" }
        }
        val existingPath = existingStatus?.localMediaPath?.takeIf {
            it.isNotBlank() && File(it).exists()
        }
        val status = StatusEntity(
            id = id,
            senderJid = senderJid,
            senderName = resolvedName,
            caption = json.optString("body"),
            msgType = json.optString("msgType", "IMAGE"),
            mimeType = json.optString("mimeType"),
            localMediaPath = existingPath,
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
        dao.upsertStatus(status)
        if (senderJid.isNotBlank() && dao.getChat(senderJid)?.avatarPath.isNullOrBlank()) {
            requestSingleAvatarIfNeeded(senderJid)
        }
    }

    private suspend fun handleMessageRevoke(json: JSONObject) {
        val targetId = json.optString("targetMessageId")
        val revokedAt = json.optLong("revokedAt", System.currentTimeMillis())
        val isStatus = json.optBoolean("isStatus", false)
        if (targetId.isBlank()) return
        if (isStatus) {
            dao.markStatusRevoked(targetId, revokedAt)
        } else if (StealthMatrixManager.config.value.antiDeleteEnabled) {
            dao.markMessageRevoked(targetId, revokedAt)
        }
    }

    private suspend fun handleMessageEdit(json: JSONObject) {
        val targetId = json.optString("targetMessageId")
        val newBody = json.optString("newBody")
        val editedAt = json.optLong("editedAt", System.currentTimeMillis())
        if (targetId.isNotBlank()) {
            dao.applyMessageEdit(targetId, newBody, editedAt)
        }
    }

    private suspend fun handleReaction(json: JSONObject) {
        val targetId = json.optString("targetMessageId")
        if (targetId.isBlank()) return
        val summary = json.optString("reactionSummary").trim()
        val emoji = json.optString("emoji")
        val senderJid = json.optString("senderJid")
        val senderNameHint = json.optString("senderName").trim()
        val isFromMe = json.optBoolean("isFromMe", false)
        val ts = json.optLong("timestamp", System.currentTimeMillis())
        val rawChat = json.optString("chatJid")
        val rawAlt = json.optString("altChatJid")
        if (rawChat.isNotBlank() && rawAlt.isNotBlank()) {
            canonicalizeAndMergeAlias(rawChat, rawAlt)
        }
        val existing = dao.getMessage(targetId)
        if (existing == null) {
            if (pendingReactions.size > 500) {
                pendingReactions.clear()
            }
            pendingReactions.compute(targetId) { _, list ->
                (list ?: mutableListOf()).apply {
                    if (size < 16) add(json)
                }
            }
            return
        }
        if (summary.isNotBlank() || existing.chatJid.endsWith("@newsletter")) {
            // Channel reactions arrive as aggregate counts, not per-participant entries.
            val updated = when {
                summary.isNotBlank() -> summary
                emoji.isBlank() -> ""
                existing.reactionSummary.isBlank() -> emoji
                existing.reactionSummary.contains(emoji) -> existing.reactionSummary
                else -> "${existing.reactionSummary} $emoji"
            }
            dao.updateReactionSummary(targetId, updated)
        } else {
            val actor = if (isFromMe) {
                MessageReactionEntity.ME_ACTOR
            } else {
                senderJid.ifBlank { "unknown" }
            }
            dao.applyReaction(targetId, actor, emoji, ts)
        }
        if (summary.isBlank() && emoji.isNotBlank() && !existing.chatJid.endsWith("@newsletter")) {
            val chat = dao.getChat(existing.chatJid)
            if (chat != null && ts > chat.lastMessageTimestamp) {
                val actor = if (isFromMe) {
                    "You"
                } else {
                    senderNameHint.ifBlank {
                        pushnameCache[senderJid] ?: dao.getChat(senderJid)?.name ?: senderJid.substringBefore("@")
                    }
                }
                val snippet = existing.body.ifBlank { WaDao.previewTextFor(existing) }.take(28)
                val reactionPreview = if (snippet.isNotBlank()) "$actor reacted $emoji to \"$snippet\"" else "$actor reacted $emoji"
                dao.upsertChat(
                    chat.copy(
                        lastMessagePreview = reactionPreview,
                        lastMessageFromMe = isFromMe,
                        lastMessageTimestamp = ts
                    )
                )
            }
        }
    }

    private suspend fun handleViewOnceStripped(json: JSONObject) {
        val id = json.optString("id")
        if (id.isBlank()) return
        _downloadingMediaIds.value = _downloadingMediaIds.value - id
        _failedMediaIds.value = _failedMediaIds.value - id
        val existing = dao.getMessage(id)
        val existingHasFile = !existing?.localMediaPath.isNullOrBlank() &&
            File(existing!!.localMediaPath!!).let { it.exists() && it.length() > 0L }
        if (existingHasFile) return
        val mediaTypeHint = json.optString("mediaType")
        val resolvedType = when {
            existing?.msgType in setOf("IMAGE", "VIDEO", "PTT", "AUDIO") -> existing!!.msgType
            mediaTypeHint in setOf("IMAGE", "VIDEO", "PTT", "AUDIO") -> mediaTypeHint
            else -> "VIEW_ONCE_STRIPPED"
        }
        val msg = MessageEntity(
            id = id,
            chatJid = json.optString("chatJid").ifBlank { existing?.chatJid.orEmpty() },
            senderJid = json.optString("senderJid").ifBlank { existing?.senderJid.orEmpty() },
            senderName = json.optString("pushName").ifBlank { existing?.senderName.orEmpty() },
            isFromMe = json.optBoolean("isFromMe", existing?.isFromMe ?: false),
            timestamp = json.optLong("timestamp", existing?.timestamp ?: System.currentTimeMillis()),
            body = json.optString("reason", existing?.body.orEmpty()),
            msgType = resolvedType,
            mimeType = existing?.mimeType?.ifBlank { "application/x-view-once-stub" } ?: "application/x-view-once-stub",
            durationSec = existing?.durationSec ?: 0,
            fileSize = existing?.fileSize ?: 0L,
            isViewOnce = true,
            viewOnceState = "STRIPPED",
            deliveryStatus = existing?.deliveryStatus?.ifBlank { "DELIVERED" } ?: "DELIVERED"
        )
        dao.upsertIncomingMessageAndRefreshChat(
            message = msg,
            chatNameHint = msg.senderName,
            isGroup = msg.chatJid.endsWith("@g.us"),
            incrementUnread = existing == null && activeChatJid.value != msg.chatJid
        )
    }

    private suspend fun handleViewOnceExpired(json: JSONObject) {
        val messageId = json.optString("messageId")
        val mediaTypeHint = json.optString("mediaType")
        if (messageId.isBlank()) return
        _downloadingMediaIds.value = _downloadingMediaIds.value - messageId
        _failedMediaIds.value = _failedMediaIds.value - messageId
        dao.markViewOnceStrippedIfUndownloaded(messageId, mediaTypeHint)
    }

    private suspend fun handleUndecryptableStub(json: JSONObject) {
        val retries = json.optInt("retries", 2)
        val msg = MessageEntity(
            id = json.optString("id"),
            chatJid = json.optString("chatJid"),
            senderJid = json.optString("senderJid"),
            senderName = json.optString("pushName"),
            isFromMe = false,
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            body = "Decryption retry cap ($retries/2) reached to avoid Bad MAC ban trigger",
            msgType = "TEXT",
            deliveryStatus = "DELIVERED"
        )
        dao.upsertIncomingMessageAndRefreshChat(
            message = msg,
            chatNameHint = msg.senderName,
            isGroup = msg.chatJid.endsWith("@g.us"),
            incrementUnread = false
        )
    }

    private fun handleMediaDownloadStatus(json: JSONObject) {
        val messageId = json.optString("messageId")
        val status = json.optString("status")
        val reason = json.optString("reason")
        if (messageId.isBlank()) return
        when (status) {
            "DOWNLOADING" -> {
                _downloadingMediaIds.value = _downloadingMediaIds.value + messageId
                _failedMediaIds.value = _failedMediaIds.value - messageId
            }
            "FAILED" -> {
                _downloadingMediaIds.value = _downloadingMediaIds.value - messageId
                _failedMediaIds.value = _failedMediaIds.value + (messageId to reason.ifBlank { "Tap to retry" })
            }
        }
    }

    private suspend fun handleMediaDownloaded(json: JSONObject) {
        val messageId = json.optString("messageId")
        val localPath = json.optString("localPath")
        val sha256 = json.optString("sha256")
        val fileSize = json.optLong("fileSize", 0L)
        val waveformCsv = json.optString("waveformCsv")
        val isStatus = json.optBoolean("isStatus", false)
        val isHistorical = json.optBoolean("isHistorical", false)
        val isFromMe = json.optBoolean("isFromMe", false)
        val mediaType = json.optString("mediaType")
        val mimeType = json.optString("mimeType")

        if (messageId.isNotBlank()) {
            _downloadingMediaIds.value = _downloadingMediaIds.value - messageId
            _failedMediaIds.value = _failedMediaIds.value - messageId
        }

        if (isStatus) {
            dao.updateStatusMedia(messageId, localPath)
            return
        }
        dao.updateMessageMedia(messageId, localPath, sha256, fileSize, waveformCsv)
        val msg = dao.getMessage(messageId)
        if (msg != null && (msg.msgType == "PTT" || msg.msgType == "AUDIO")) {
            EagerMediaVault.transcribeAndExtractWaveformAsync(
                context = applicationContext,
                dao = dao,
                messageId = messageId,
                localPath = localPath,
                durationSec = msg.durationSec,
                existingWaveformCsv = waveformCsv.ifBlank { msg.waveformCsv }
            )
        } else if (
            msg != null &&
            !isHistorical &&
            !isFromMe &&
            !msg.isViewOnce &&
            !msg.chatJid.endsWith("@newsletter") &&
            (mediaType == "IMAGE" || mediaType == "VIDEO" || msg.msgType == "IMAGE" || msg.msgType == "VIDEO") &&
            StealthMatrixManager.shouldShowChatMediaInGallery(msg.chatJid, msg.senderJid)
        ) {
            val vaultFile = File(localPath)
            if (vaultFile.exists() && vaultFile.length() > 0L) {
                val galleryPrefs = applicationContext.getSharedPreferences("wupp_gallery_exported", Context.MODE_PRIVATE)
                val exportKey = "exp_$messageId"
                if (!galleryPrefs.getBoolean(exportKey, false)) {
                    val resolvedMime = mimeType.ifBlank { msg.mimeType }.ifBlank {
                        if (msg.msgType == "VIDEO" || mediaType == "VIDEO") "video/mp4" else "image/jpeg"
                    }
                    val ext = if (resolvedMime.startsWith("video/", ignoreCase = true)) "mp4" else "jpg"
                    val displayName = "Wupp_${messageId.take(12)}.$ext"
                    EagerMediaVault.exportToGallery(applicationContext, vaultFile, displayName, resolvedMime)
                        .onSuccess {
                            galleryPrefs.edit().putBoolean(exportKey, true).apply()
                        }
                }
            }
        }
    }

    private suspend fun handleReceipt(json: JSONObject) {
        val chatJid = json.optString("chatJid")
        val altChatJid = json.optString("altChatJid")
        val senderJid = json.optString("senderJid")
        val receiptType = json.optString("receiptType", "DELIVERED")
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
        val isPrimaryActive = json.optBoolean("isPrimaryActive", false)
        if (isPrimaryActive) {
            StealthMatrixManager.recordPrimaryHeartbeat(timestamp)
        }
        val ids = json.optJSONArray("messageIds") ?: return
        var maxMatchedTs = 0L
        for (i in 0 until ids.length()) {
            val id = ids.optString(i)
            if (id.isBlank()) continue
            dao.updateDeliveryStatus(id, receiptType)
            val msgRow = dao.getMessage(id)
            if (msgRow != null && msgRow.timestamp > maxMatchedTs) {
                maxMatchedTs = msgRow.timestamp
            }
            if (chatJid.endsWith("@g.us") && senderJid.isNotBlank()) {
                dao.upsertGroupReceipt(
                    GroupReceiptEntity(
                        messageId = id,
                        participantJid = senderJid,
                        participantName = pushnameCache[senderJid] ?: senderJid.substringBefore("@"),
                        status = receiptType,
                        timestamp = timestamp
                    )
                )
            }
        }
        // On 1:1 DMs, a READ/DELIVERED receipt advances prior outgoing ticks up to that message timestamp
        if (maxMatchedTs > 0L && chatJid.isNotBlank() && !chatJid.endsWith("@g.us") && !chatJid.endsWith("@newsletter")) {
            val cascadeStatus = if (receiptType == "PLAYED") "READ" else receiptType
            val cascadeRank = WaDao.deliveryRank(cascadeStatus)
            if (cascadeRank >= 2) {
                dao.markDmOutgoingStatusUpTo(chatJid, maxMatchedTs, cascadeStatus, cascadeRank)
                dao.markDmChatLastStatusIfHigher(chatJid, maxMatchedTs, cascadeStatus, cascadeRank)
                if (altChatJid.isNotBlank() && altChatJid != chatJid) {
                    dao.markDmOutgoingStatusUpTo(altChatJid, maxMatchedTs, cascadeStatus, cascadeRank)
                    dao.markDmChatLastStatusIfHigher(altChatJid, maxMatchedTs, cascadeStatus, cascadeRank)
                }
            }
        }
    }

    private suspend fun handlePresence(json: JSONObject) {
        val rawJid = json.optString("jid")
        val altJid = json.optString("altJid")
        val jid = if (altJid.isNotBlank()) canonicalizeAndMergeAlias(rawJid, altJid) else (chatAliasToCanonical[rawJid] ?: rawJid)
        val isOnline = json.optBoolean("isOnline", false)
        val lastSeen = json.optLong("lastSeen", 0L)
        val ts = json.optLong("timestamp", System.currentTimeMillis())
        if (jid.isBlank()) return
        dao.updateChatPresenceOnline(jid, isOnline, lastSeen)
        val name = dao.getChat(jid)?.name ?: pushnameCache[jid] ?: jid.substringBefore("@")
        dao.insertPresenceLog(
            PresenceLogEntity(
                jid = jid,
                contactName = name,
                state = if (isOnline) "ONLINE" else "OFFLINE",
                timestamp = ts
            )
        )
    }

    private suspend fun handleChatPresence(json: JSONObject) {
        val rawChat = json.optString("chatJid")
        val altChat = json.optString("altChatJid")
        val chatJid = if (altChat.isNotBlank()) canonicalizeAndMergeAlias(rawChat, altChat) else (chatAliasToCanonical[rawChat] ?: rawChat)
        val state = json.optString("state")
        val media = json.optString("media")
        val text = when {
            state == "composing" && media == "audio" -> "recording audio..."
            state == "composing" -> "typing..."
            else -> ""
        }
        typingExpiryJobs.remove(chatJid)?.cancel()
        dao.updateChatTypingState(chatJid, text)
        if (text.isNotBlank()) {
            typingExpiryJobs[chatJid] = serviceScope.launch {
                delay(6_000L)
                dao.updateChatTypingState(chatJid, "")
                typingExpiryJobs.remove(chatJid)
            }
        }
    }

    private suspend fun handleContactsSyncBatch(json: JSONObject) {
        val arr = json.optJSONArray("contacts") ?: return
        val contactEntities = ArrayList<ContactEntity>(arr.length())
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rawJid = item.optString("jid")
            val altJid = item.optString("altJid")
            val name = item.optString("name").trim()
            val isCustom = item.optBoolean("isCustom", false)
            val canonicalJid = if (altJid.isNotBlank()) canonicalizeAndMergeAlias(rawJid, altJid) else (chatAliasToCanonical[rawJid] ?: rawJid)
            if (name.isBlank()) continue
            if (canonicalJid.isNotBlank() && name != canonicalJid && name != canonicalJid.substringBefore("@")) {
                pushnameCache[canonicalJid] = name
                dao.updateChatName(canonicalJid, name, overwrite = isCustom)
                propagateStatusAndSenderName(canonicalJid, name, overwriteStatus = isCustom)
                contactEntities.add(
                    ContactEntity(
                        jid = canonicalJid,
                        name = name,
                        isSaved = isCustom,
                        updatedAt = now
                    )
                )
            }
            if (altJid.isNotBlank() && name != altJid && name != altJid.substringBefore("@")) {
                pushnameCache[altJid] = name
                dao.updateChatName(altJid, name, overwrite = isCustom)
                propagateStatusAndSenderName(altJid, name, overwriteStatus = isCustom)
            }
        }
        if (contactEntities.isNotEmpty()) {
            dao.upsertContacts(contactEntities)
        }
    }

    private suspend fun handleGroupsSyncBatch(json: JSONObject) {
        val arr = json.optJSONArray("groups") ?: return
        val entities = ArrayList<ChatEntity>(arr.length())
        val lockUpdates = HashMap<String, Boolean>(arr.length())
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val jid = g.optString("jid")
            if (jid.isBlank()) continue
            val name = g.optString("name").ifBlank { jid.substringBefore("@") }
            val topic = g.optString("topic")
            val isCommunityParent = g.optBoolean("isCommunityParent", false)
            val parentGroupJid = g.optString("parentGroupJid")
            val isDefaultSubgroup = g.optBoolean("isDefaultSubgroup", false)
            if (g.has("onlyAdminsPost")) {
                lockUpdates[jid] = g.optBoolean("onlyAdminsPost", false)
            }
            if (name.isNotBlank() && name != jid.substringBefore("@")) {
                pushnameCache[jid] = name
            }
            entities.add(
                ChatEntity(
                    jid = jid,
                    name = name,
                    aboutText = topic,
                    isGroup = true,
                    isCommunityParent = isCommunityParent,
                    parentGroupJid = parentGroupJid,
                    isDefaultSubgroup = isDefaultSubgroup
                )
            )
        }
        if (lockUpdates.isNotEmpty()) {
            updateGroupAnnounceLocks(applicationContext, lockUpdates)
        }
        if (entities.isNotEmpty()) {
            dao.upsertSyncedGroups(entities)
        }
    }

    private suspend fun handleNewslettersSyncBatch(json: JSONObject) {
        val arr = json.optJSONArray("channels") ?: return
        val entities = ArrayList<ChatEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val jid = c.optString("jid")
            if (jid.isBlank()) continue
            val name = c.optString("name").ifBlank { jid.substringBefore("@") }
            val desc = c.optString("description")
            val isMuted = c.optBoolean("isMuted", false)
            if (name.isNotBlank() && name != jid.substringBefore("@")) {
                pushnameCache[jid] = name
            }
            entities.add(
                ChatEntity(
                    jid = jid,
                    name = name,
                    aboutText = desc,
                    isGroup = false,
                    isMuted = isMuted,
                    lastMessagePreview = desc
                )
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertSyncedNewsletters(entities)
        }
    }

    private suspend fun handleChatMetaUpdate(json: JSONObject) {
        val kind = json.optString("kind")
        val rawJid = json.optString("jid")
        val altJid = json.optString("altJid")
        if (rawJid.isBlank()) return
        val jid = if (altJid.isNotBlank()) canonicalizeAndMergeAlias(rawJid, altJid) else (chatAliasToCanonical[rawJid] ?: rawJid)
        when (kind) {
            "CONTACT" -> {
                val name = json.optString("name").trim()
                val ts = json.optLong("timestamp", System.currentTimeMillis())
                if (name.isNotBlank()) {
                    pushnameCache[jid] = name
                    dao.updateChatName(jid, name, overwrite = true)
                    propagateStatusAndSenderName(jid, name, overwriteStatus = true)
                    dao.upsertContacts(
                        listOf(ContactEntity(jid = jid, name = name, isSaved = true, updatedAt = ts))
                    )
                    if (altJid.isNotBlank()) {
                        pushnameCache[altJid] = name
                        dao.updateChatName(altJid, name, overwrite = true)
                        propagateStatusAndSenderName(altJid, name, overwriteStatus = true)
                    }
                }
            }
            "PUSHNAME" -> {
                val name = json.optString("name").trim()
                val ts = json.optLong("timestamp", System.currentTimeMillis())
                if (name.isNotBlank()) {
                    pushnameCache[jid] = name
                    dao.updateChatName(jid, name, overwrite = false)
                    propagateStatusAndSenderName(jid, name, overwriteStatus = false)
                    dao.upsertContacts(
                        listOf(ContactEntity(jid = jid, name = name, isSaved = false, updatedAt = ts))
                    )
                    if (altJid.isNotBlank()) {
                        pushnameCache[altJid] = name
                        dao.updateChatName(altJid, name, overwrite = false)
                        propagateStatusAndSenderName(altJid, name, overwriteStatus = false)
                    }
                }
            }
            "MUTE" -> {
                val muted = json.optBoolean("muted")
                val muteEnd = json.optLong("muteEnd")
                dao.updateMuted(jid, muted, muteEnd)
                if (altJid.isNotBlank() && altJid != jid) {
                    dao.updateMuted(altJid, muted, muteEnd)
                }
            }
            "PIN" -> {
                val pinned = json.optBoolean("pinned")
                val ts = json.optLong("timestamp")
                dao.updatePinned(jid, pinned, ts)
                if (altJid.isNotBlank() && altJid != jid) {
                    dao.updatePinned(altJid, pinned, ts)
                }
            }
            "ARCHIVE" -> {
                val archived = json.optBoolean("archived")
                dao.updateArchivedFromSync(jid, archived)
                if (altJid.isNotBlank() && altJid != jid) {
                    dao.updateArchivedFromSync(altJid, archived)
                }
            }
            "GROUP_ANNOUNCE" -> setGroupAnnounceLocked(applicationContext, jid, json.optBoolean("locked", false))
            "MARK_READ" -> {
                StealthMatrixManager.recordPrimaryHeartbeat(json.optLong("timestamp", System.currentTimeMillis()))
                val targets = listOfNotNull(
                    jid.takeIf { it.isNotBlank() },
                    altJid.takeIf { it.isNotBlank() && it != jid }
                )
                if (json.optBoolean("read")) {
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    targets.forEach { t ->
                        dao.clearLocalUnread(t)
                        runCatching { mgr?.cancel(notificationIdForChat(t)) }
                    }
                } else {
                    targets.forEach { t ->
                        val c = dao.getChat(t)
                        if (c != null && c.unreadCount <= 0) {
                            dao.updateUnreadCount(t, 1)
                        }
                    }
                }
            }
            "DELETE_CHAT", "CLEAR_CHAT" -> {
                val lastMsgTsMs = json.optLong("lastMessageTimestampMs", 0L)
                val targets = listOfNotNull(
                    jid.takeIf { it.isNotBlank() },
                    altJid.takeIf { it.isNotBlank() && it != jid }
                )
                val latestTs = targets.mapNotNull { dao.getLatestMessageForChat(it)?.timestamp }.maxOrNull() ?: 0L
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                targets.forEach { t -> runCatching { mgr?.cancel(notificationIdForChat(t)) } }

                if (kind == "DELETE_CHAT" && !(lastMsgTsMs > 0L && latestTs > lastMsgTsMs + 1000L)) {
                    targets.forEach { t ->
                        dao.deleteMessagesForChat(t)
                        dao.deleteChatRow(t)
                    }
                } else {
                    targets.forEach { t ->
                        if (lastMsgTsMs > 0L) {
                            dao.deleteMessagesForChatUpTo(t, lastMsgTsMs)
                        } else {
                            dao.deleteMessagesForChat(t)
                        }
                        val remainingLatest = dao.getLatestMessageForChat(t)
                        if (remainingLatest != null) {
                            dao.updateChatPreviewAfterClear(
                                chatJid = t,
                                preview = remainingLatest.body.ifBlank { remainingLatest.msgType },
                                type = remainingLatest.msgType,
                                timestamp = remainingLatest.timestamp,
                                unreadCount = 0
                            )
                        } else {
                            dao.updateChatPreviewAfterClear(
                                chatJid = t,
                                preview = "",
                                type = "UNKNOWN",
                                timestamp = if (kind == "CLEAR_CHAT") latestTs else 0L,
                                unreadCount = 0
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleProfileUpdate(json: JSONObject) {
        val rawJid = json.optString("jid")
        val altJid = json.optString("altJid")
        if (rawJid.isBlank()) return
        val jid = if (altJid.isNotBlank()) canonicalizeAndMergeAlias(rawJid, altJid) else (chatAliasToCanonical[rawJid] ?: rawJid)
        val avatarPath = json.optString("avatarPath")
        val aboutText = json.optString("aboutText")
        val ts = json.optLong("timestamp", System.currentTimeMillis())
        if (avatarPath.isNotBlank()) {
            dao.updateChatAvatar(jid, avatarPath)
            dao.updateContactAvatar(jid, avatarPath)
            if (altJid.isNotBlank()) dao.updateChatAvatar(altJid, avatarPath)
        }
        if (aboutText.isNotBlank()) {
            dao.updateChatAbout(jid, aboutText)
            dao.updateContactAbout(jid, aboutText)
            if (altJid.isNotBlank()) dao.updateChatAbout(altJid, aboutText)
        }
        val name = dao.getChat(jid)?.name ?: pushnameCache[jid] ?: jid.substringBefore("@")
        if (avatarPath.isNotBlank() || aboutText.isNotBlank()) {
            dao.insertProfileHistory(
                ProfileHistoryEntity(
                    jid = jid,
                    contactName = name,
                    avatarPath = avatarPath.ifBlank { null },
                    aboutText = aboutText,
                    capturedAt = ts
                )
            )
        }
    }

    private fun parseNormalizedMessage(json: JSONObject): MessageEntity {
        val body = json.optString("body")
        val ephSec = json.optInt("ephemeralDuration", 0)
        val isFromMe = json.optBoolean("isFromMe", false)
        val defaultStatus = if (isFromMe) "SENT" else "DELIVERED"
        return MessageEntity(
            id = json.optString("id"),
            chatJid = json.optString("chatJid"),
            senderJid = json.optString("senderJid"),
            senderName = json.optString("senderName"),
            isFromMe = isFromMe,
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            body = body,
            msgType = json.optString("msgType", "TEXT"),
            mimeType = json.optString("mimeType"),
            durationSec = json.optInt("durationSec", 0),
            fileSize = json.optLong("fileSize", 0L),
            deliveryStatus = json.optString("deliveryStatus").ifBlank { defaultStatus },
            isViewOnce = json.optBoolean("isViewOnce", false),
            viewOnceState = json.optString("viewOnceState", "NONE"),
            isEphemeral = json.optBoolean("isEphemeral", false) || ephSec > 0,
            ephemeralDuration = ephSec,
            quotedId = json.optString("quotedId").ifBlank { null },
            quotedSender = json.optString("quotedSender").ifBlank { null },
            quotedBody = json.optString("quotedBody").ifBlank { null },
            mentionsCsv = json.optString("mentionsCsv"),
            reactionSummary = json.optString("reactionSummary")
        )
    }

    companion object {
        private const val TAG = "WaRealtimeService"
        const val CHANNEL_ID = "wa_realtime_channel"
        const val MESSAGES_CHANNEL_ID = "wa_messages_channel"
        private const val NOTIF_ID = 1042
        const val ACTION_RECONNECT = "com.wupp.app.action.RECONNECT"
        const val ACTION_DISCONNECT = "com.wupp.app.action.DISCONNECT"

        fun isRealtimeStatusNotificationHidden(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val ch = mgr.getNotificationChannel(CHANNEL_ID) ?: return false
            return ch.importance == NotificationManager.IMPORTANCE_NONE
        }

        private tailrec fun findActivity(ctx: Context): android.app.Activity? = when (ctx) {
            is android.app.Activity -> ctx
            is android.content.ContextWrapper -> findActivity(ctx.baseContext)
            else -> null
        }

        private fun launchSettingsIntent(context: Context, intent: Intent): Boolean =
            runCatching {
                val activity = findActivity(context)
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                true
            }.getOrDefault(false)

        fun openNotificationChannelSettings(context: Context, channelId: String) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                }
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
            if (!launchSettingsIntent(context, intent)) {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                launchSettingsIntent(context, fallback)
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun openBatteryOptimizationSettings(context: Context) {
            val pkgUri = android.net.Uri.fromParts("package", context.packageName, null)
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = pkgUri
            }
            if (!isIgnoringBatteryOptimizations(context)) {
                val requestPrompt = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = pkgUri
                }
                if (launchSettingsIntent(context, requestPrompt)) return
            }
            if (launchSettingsIntent(context, appDetails)) return
            launchSettingsIntent(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        @Volatile
        var isUiForeground: Boolean = false

        private fun notificationIdForChat(chatJid: String): Int =
            20_000 + (chatJid.hashCode() and 0x0FFFFFFF)

        @Volatile
        var instance: WaRealtimeService? = null
            private set

        private val companionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _bridgeStatus = MutableStateFlow(BridgeStatus())
        val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

        val activeChatJid = MutableStateFlow<String?>(null)
        private val _announceLockedGroups = MutableStateFlow<Set<String>>(emptySet())
        val announceLockedGroups: StateFlow<Set<String>> = _announceLockedGroups.asStateFlow()

        private val _downloadingMediaIds = MutableStateFlow<Set<String>>(emptySet())
        val downloadingMediaIds: StateFlow<Set<String>> = _downloadingMediaIds.asStateFlow()

        private val _failedMediaIds = MutableStateFlow<Map<String, String>>(emptyMap())
        val failedMediaIds: StateFlow<Map<String, String>> = _failedMediaIds.asStateFlow()

        private fun reloadGroupLocks(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences("wupp_group_locks", Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("locked_groups", emptySet()) ?: emptySet()
            _announceLockedGroups.value = saved.toSet()
        }

        private fun updateGroupAnnounceLocks(context: Context, updates: Map<String, Boolean>) {
            val next = _announceLockedGroups.value.toMutableSet()
            updates.forEach { (jid, locked) ->
                if (locked) next.add(jid) else next.remove(jid)
            }
            _announceLockedGroups.value = next
            context.applicationContext.getSharedPreferences("wupp_group_locks", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("locked_groups", next)
                .apply()
        }

        private fun setGroupAnnounceLocked(context: Context, jid: String, locked: Boolean) {
            updateGroupAnnounceLocks(context, mapOf(jid to locked))
        }

        fun ensureStarted(context: Context) {
            runCatching {
                val intent = Intent(context, WaRealtimeService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun onChatOpened(context: Context, chatJid: String) {
            val appCtx = context.applicationContext
            runCatching {
                val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.cancel(notificationIdForChat(chatJid))
            }
            companionScope.launch {
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                val privacy = StealthMatrixManager.getEffectivePrivacy(dao, chatJid)
                if (privacy.sendReadReceipts) {
                    markChatReadExplicitly(appCtx, chatJid)
                } else {
                    dao.clearLocalUnread(chatJid)
                }
                if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    if (chatJid.endsWith("@newsletter")) {
                        runCatching {
                            Whatsbridge::class.java
                                .getMethod("fetchNewsletterMessages", String::class.java)
                                .invoke(null, chatJid)
                        }
                    } else if (chatJid.endsWith("@g.us")) {
                        runCatching {
                            Whatsbridge::class.java
                                .getMethod("fetchGroupLockState", String::class.java)
                                .invoke(null, chatJid)
                        }
                    }
                    if (StealthMatrixManager.isAutoDownloadAllowed(appCtx, "AUDIO")) {
                        val missingAudio = dao.getUndownloadedAudioMessages(chatJid)
                        for (msg in missingAudio) {
                            runCatching {
                                triggerMediaDownloadWithAnchor(dao, msg)
                            }.onFailure {
                                Log.w(TAG, "requestMediaDownload(onChatOpened) failed for ${msg.id}: ${it.message}")
                            }
                            delay(120L)
                        }
                    }
                }
            }
        }

        fun resolveParticipantNamesJson(jidsCsv: String): String {
            if (jidsCsv.isBlank()) return "{}"
            return runCatching { Whatsbridge.resolveParticipantNamesJson(jidsCsv) }
                .getOrDefault("{}")
        }

        fun requestFullProfilePicture(jid: String) {
            if (jid.isBlank()) return
            companionScope.launch {
                if (!runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) return@launch
                runCatching {
                    Whatsbridge::class.java
                        .getMethod("fetchFullProfilePicture", String::class.java)
                        .invoke(null, jid)
                }.onFailure {
                    Log.w(TAG, "requestFullProfilePicture failed for $jid: ${it.message}")
                }
            }
        }

        fun syncAutoDownloadPolicy(context: Context) {
            val appCtx = context.applicationContext
            val mask = StealthMatrixManager.activeAutoDownloadMask(appCtx)
            val allowPhoto = (mask and StealthMatrixManager.MEDIA_PHOTO) != 0
            val allowAudio = (mask and StealthMatrixManager.MEDIA_AUDIO) != 0
            val allowVideo = (mask and StealthMatrixManager.MEDIA_VIDEO) != 0
            val allowDoc = (mask and StealthMatrixManager.MEDIA_DOCUMENT) != 0
            runCatching {
                Whatsbridge.setAutoDownloadPolicy(allowPhoto, allowAudio, allowVideo, allowDoc)
            }.onFailure {
                Log.w(TAG, "syncAutoDownloadPolicy failed: ${it.message}")
            }
        }

        fun requestMediaDownload(context: Context, msg: MessageEntity, userInitiated: Boolean = true) {
            if (msg.id.isBlank()) return
            if (msg.isViewOnce || msg.msgType == "VIEW_ONCE_STRIPPED") return
            if (_downloadingMediaIds.value.contains(msg.id)) return
            if (!userInitiated && _failedMediaIds.value.containsKey(msg.id)) return
            if (!userInitiated && !StealthMatrixManager.isAutoDownloadAllowed(context, msg.msgType, msg.mimeType)) return

            val appCtx = context.applicationContext
            _downloadingMediaIds.value = _downloadingMediaIds.value + msg.id
            if (userInitiated) {
                _failedMediaIds.value = _failedMediaIds.value - msg.id
            }
            companionScope.launch {
                if (!runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    _downloadingMediaIds.value = _downloadingMediaIds.value - msg.id
                    return@launch
                }
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                runCatching {
                    val hasCached = runCatching { Whatsbridge.hasCachedMediaMeta(msg.id) }.getOrDefault(false)
                    if (hasCached) {
                        Whatsbridge.requestMediaDownload(msg.chatJid, msg.senderJid, msg.id, msg.timestamp)
                    } else {
                        triggerMediaDownloadWithAnchor(dao, msg)
                        delay(30_000L)
                        val refreshed = dao.getMessage(msg.id)
                        if (refreshed?.localMediaPath.isNullOrBlank() && _downloadingMediaIds.value.contains(msg.id)) {
                            _downloadingMediaIds.value = _downloadingMediaIds.value - msg.id
                            if (refreshed?.viewOnceState != "STRIPPED") {
                                _failedMediaIds.value = _failedMediaIds.value + (msg.id to "Tap to retry")
                            }
                        }
                    }
                }.onFailure {
                    _downloadingMediaIds.value = _downloadingMediaIds.value - msg.id
                    _failedMediaIds.value = _failedMediaIds.value + (msg.id to (it.message ?: "Download failed"))
                    Log.w(TAG, "requestMediaDownload failed for ${msg.id}: ${it.message}")
                }
            }
        }

        fun requestStatusMediaDownload(context: Context, status: StatusEntity, userInitiated: Boolean = true) {
            if (status.id.isBlank()) return
            val upperType = status.msgType.uppercase()
            val isMedia = upperType in setOf("IMAGE", "VIDEO", "AUDIO", "PTT") ||
                status.mimeType.startsWith("image/", ignoreCase = true) ||
                status.mimeType.startsWith("video/", ignoreCase = true) ||
                status.mimeType.startsWith("audio/", ignoreCase = true)
            if (!isMedia) return
            if (_downloadingMediaIds.value.contains(status.id)) return
            if (!userInitiated && _failedMediaIds.value.containsKey(status.id)) return

            val appCtx = context.applicationContext
            _downloadingMediaIds.value = _downloadingMediaIds.value + status.id
            if (userInitiated) {
                _failedMediaIds.value = _failedMediaIds.value - status.id
            }
            companionScope.launch {
                if (!runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    _downloadingMediaIds.value = _downloadingMediaIds.value - status.id
                    return@launch
                }
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                runCatching {
                    val hasCached = runCatching { Whatsbridge.hasCachedMediaMeta(status.id) }.getOrDefault(false)
                    Whatsbridge.requestMediaDownload("status@broadcast", status.senderJid, status.id, status.timestamp)
                    delay(if (hasCached) 15_000L else 25_000L)
                    val refreshed = dao.getStatusById(status.id)
                    if (refreshed?.localMediaPath.isNullOrBlank() && _downloadingMediaIds.value.contains(status.id)) {
                        _downloadingMediaIds.value = _downloadingMediaIds.value - status.id
                        _failedMediaIds.value = _failedMediaIds.value + (status.id to "Tap to retry")
                    }
                }.onFailure {
                    _downloadingMediaIds.value = _downloadingMediaIds.value - status.id
                    _failedMediaIds.value = _failedMediaIds.value + (status.id to (it.message ?: "Download failed"))
                    Log.w(TAG, "requestStatusMediaDownload failed for ${status.id}: ${it.message}")
                }
            }
        }

        private suspend fun triggerMediaDownloadWithAnchor(dao: WaDao, msg: MessageEntity) {
            Whatsbridge.requestMediaDownload(msg.chatJid, msg.senderJid, msg.id, msg.timestamp)
            val newer = dao.getNextNewerMessage(msg.chatJid, msg.timestamp)
            if (newer != null) {
                Whatsbridge.requestMediaDownload(newer.chatJid, newer.senderJid, newer.id, newer.timestamp)
            } else {
                Whatsbridge.requestMediaDownload(msg.chatJid, msg.senderJid, msg.id, msg.timestamp + 2000L)
            }
        }

        suspend fun pairWithPhoneNumber(phoneDigits: String): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
                val code = Whatsbridge.pairWithPhoneNumber(phoneDigits)
                _bridgeStatus.value = _bridgeStatus.value.copy(latestPairCode = code, connectionState = "PAIR_CODE_READY")
                code
            }
        }

        suspend fun setGhostPresence(enabled: Boolean) = withContext(Dispatchers.IO) {
            StealthMatrixManager.updateConfig { it.copy(ghostPresence = enabled) }
            runCatching { Whatsbridge.setGhostPresence(enabled) }
        }

        suspend fun sendTextMessage(
            context: Context,
            chatJid: String,
            text: String,
            quoted: MessageEntity?,
            ghostMentionEveryone: Boolean
        ): Result<MessageEntity> = withContext(Dispatchers.IO) {
            runCatching {
                val dao = WaUiDatabase.getInstance(context).waDao()
                if (!StealthMatrixManager.canInitiateOutboundMessage(dao, chatJid)) {
                    error("ERROR_463_GUARD: First message to unsaved contact blocked to prevent companion Error 463 ban. Start chat on primary phone first.")
                }

                val privacy = StealthMatrixManager.getEffectivePrivacy(dao, chatJid)
                var mentionsCsv = ""
                if (ghostMentionEveryone && chatJid.endsWith("@g.us")) {
                    val ownUser = _bridgeStatus.value.ownJid.substringBefore("@").substringBefore(":")
                    mentionsCsv = runCatching {
                        val raw = Whatsbridge.getGroupParticipantsJson(chatJid)
                        val arr = JSONArray(raw)
                        buildList {
                            for (i in 0 until arr.length()) {
                                val participant = arr.optString(i).trim()
                                val participantUser = participant.substringBefore("@").substringBefore(":")
                                if (participant.isNotBlank() && (ownUser.isBlank() || participantUser != ownUser)) {
                                    add(participant)
                                }
                            }
                        }.distinct().joinToString(",")
                    }.getOrElse { emptyList<String>().joinToString(",") }
                }

                val ownJid = _bridgeStatus.value.ownJid.ifBlank { "me@s.whatsapp.net" }
                val stanzaId = if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    Whatsbridge.sendTextMessage(
                        chatJid,
                        text,
                        quoted?.id ?: "",
                        quoted?.senderJid ?: "",
                        quoted?.body ?: "",
                        mentionsCsv,
                        !privacy.sendChatPresence
                    )
                } else {
                    "LOCAL_${UUID.randomUUID().toString().take(12).uppercase()}"
                }

                val entity = MessageEntity(
                    id = stanzaId,
                    chatJid = chatJid,
                    senderJid = ownJid,
                    senderName = "You",
                    isFromMe = true,
                    timestamp = System.currentTimeMillis(),
                    body = text,
                    msgType = "TEXT",
                    deliveryStatus = if (runCatching { Whatsbridge.isConnected() }.getOrDefault(false)) "SENT" else "LOCAL_SENT",
                    quotedId = quoted?.id,
                    quotedSender = quoted?.senderName ?: quoted?.senderJid,
                    quotedBody = quoted?.body?.ifBlank { WaDao.previewTextFor(quoted) },
                    mentionsCsv = mentionsCsv
                )
                dao.upsertIncomingMessageAndRefreshChat(
                    message = entity,
                    chatNameHint = "",
                    isGroup = chatJid.endsWith("@g.us"),
                    incrementUnread = false
                )
                SoundPoolManager.playSend()
                entity
            }
        }

        suspend fun sendMediaMessage(
            context: Context,
            chatJid: String,
            sourceFile: File,
            mimeType: String,
            caption: String,
            qualityMode: OutboundQualityMode,
            isVoiceNote: Boolean,
            durationSec: Int,
            isViewOnce: Boolean = false
        ): Result<MessageEntity> = withContext(Dispatchers.IO) {
            runCatching {
                val dao = WaUiDatabase.getInstance(context).waDao()
                if (!StealthMatrixManager.canInitiateOutboundMessage(dao, chatJid)) {
                    error("ERROR_463_GUARD: First message to unsaved contact blocked to protect account reputation.")
                }

                val effectiveQuality = if (isViewOnce && qualityMode == OutboundQualityMode.ORIGINAL) {
                    OutboundQualityMode.HD
                } else {
                    qualityMode
                }
                val prepared = EagerMediaVault.prepareOutboundMedia(context, sourceFile, mimeType, effectiveQuality)
                val (msgType, wireQualityMode) = resolveOutboundMediaDispatch(
                    mimeType = prepared.mimeType,
                    qualityMode = effectiveQuality,
                    isVoiceNote = isVoiceNote
                )
                val effectiveDurationSec = if (msgType == "VIDEO" && durationSec <= 0) {
                    probeOutboundVideoDurationSec(prepared.file)
                } else {
                    durationSec
                }
                val waveformCsv = if (isVoiceNote || mimeType.startsWith("audio/")) {
                    EagerMediaVault.extract40BarWaveform(prepared.file).joinToString(",")
                } else ""
                val wireModeWithVo = if (isViewOnce && (msgType == "IMAGE" || msgType == "VIDEO")) {
                    "${wireQualityMode}:viewonce"
                } else {
                    wireQualityMode
                }

                val stanzaId = if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    Whatsbridge.sendMediaMessage(
                        chatJid,
                        prepared.file.absolutePath,
                        prepared.mimeType,
                        caption,
                        wireModeWithVo,
                        isVoiceNote,
                        effectiveDurationSec.toLong(),
                        waveformCsv
                    )
                } else {
                    "MEDIA_${UUID.randomUUID().toString().take(12).uppercase()}"
                }

                val voActive = isViewOnce && (msgType == "IMAGE" || msgType == "VIDEO")
                val entity = MessageEntity(
                    id = stanzaId,
                    chatJid = chatJid,
                    senderJid = _bridgeStatus.value.ownJid.ifBlank { "me@s.whatsapp.net" },
                    senderName = "You",
                    isFromMe = true,
                    timestamp = System.currentTimeMillis(),
                    body = caption.ifBlank { if (msgType == "DOCUMENT") prepared.file.name else "" },
                    msgType = msgType,
                    mimeType = prepared.mimeType,
                    localMediaPath = prepared.file.absolutePath,
                    mediaSha256 = prepared.sha256,
                    fileSize = prepared.fileSize,
                    durationSec = effectiveDurationSec,
                    waveformCsv = waveformCsv,
                    deliveryStatus = if (runCatching { Whatsbridge.isConnected() }.getOrDefault(false)) "SENT" else "LOCAL_SENT",
                    isViewOnce = voActive,
                    viewOnceState = if (voActive) "AVAILABLE" else "NONE"
                )
                dao.upsertIncomingMessageAndRefreshChat(
                    message = entity,
                    chatNameHint = "",
                    isGroup = chatJid.endsWith("@g.us"),
                    incrementUnread = false
                )
                if (isVoiceNote) {
                    SoundPoolManager.playPttSent()
                    EagerMediaVault.transcribeAndExtractWaveformAsync(
                        context = context,
                        dao = dao,
                        messageId = entity.id,
                        localPath = prepared.file.absolutePath,
                        durationSec = durationSec,
                        existingWaveformCsv = waveformCsv
                    )
                } else {
                    SoundPoolManager.playSend()
                }
                entity
            }
        }

        private fun probeOutboundVideoDurationSec(file: File): Int {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (ms > 0L) ((ms + 500L) / 1000L).toInt().coerceAtLeast(1) else 0
            } catch (_: Throwable) {
                0
            } finally {
                runCatching { retriever.release() }
            }
        }

        suspend fun editOwnMessage(context: Context, chatJid: String, messageId: String, newText: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = WaUiDatabase.getInstance(context).waDao()
                    dao.applyMessageEdit(messageId, newText, System.currentTimeMillis())
                    if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        Whatsbridge.editMessage(chatJid, messageId, newText)
                    }
                    Unit
                }
            }

        suspend fun revokeMessageForEveryone(
            context: Context,
            chatJid: String,
            messageId: String
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val appCtx = context.applicationContext
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                    Whatsbridge.revokeMessage(chatJid, messageId)
                }
                if (StealthMatrixManager.config.value.antiDeleteEnabled) {
                    dao.markMessageRevoked(messageId, System.currentTimeMillis())
                } else {
                    deleteMessagePermanentlyInternal(appCtx, dao, messageId)
                }
                Unit
            }
        }

        suspend fun deleteMessagePermanently(
            context: Context,
            messageId: String
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val appCtx = context.applicationContext
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                deleteMessagePermanentlyInternal(appCtx, dao, messageId)
            }
        }

        private suspend fun deleteMessagePermanentlyInternal(
            appCtx: Context,
            dao: WaDao,
            messageId: String
        ) {
            val orphanMediaPath = dao.deleteMessagePermanentlyFromDb(messageId)
            if (!orphanMediaPath.isNullOrBlank()) {
                runCatching {
                    val file = File(orphanMediaPath)
                    val canonicalPath = file.canonicalPath
                    val safeRoots = listOfNotNull(
                        appCtx.filesDir?.canonicalPath,
                        appCtx.cacheDir?.canonicalPath,
                        appCtx.getExternalFilesDir(null)?.canonicalPath
                    )
                    if (file.exists() && safeRoots.any { canonicalPath.startsWith(it) }) {
                        file.delete()
                    }
                }
            }
        }

        suspend fun markChatReadExplicitly(context: Context, chatJid: String): Result<Int> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = WaUiDatabase.getInstance(context).waDao()
                    val chat = dao.getChat(chatJid)
                    val recentLimit = maxOf(chat?.unreadCount ?: 0, 10).coerceIn(5, 25)
                    val unreadTuples = dao.getUnreadIncomingTuples(chatJid)
                    val recentTuples = dao.getRecentIncomingTuplesForRead(chatJid, recentLimit)
                    val combinedTuples = (recentTuples + unreadTuples).distinctBy { it.id }.take(25)
                    dao.markIncomingLocallyRead(chatJid)
                    dao.clearLocalUnread(chatJid)
                    if (combinedTuples.isNotEmpty() && runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        val isGroup = chatJid.endsWith("@g.us")
                        combinedTuples.groupBy { if (isGroup) it.senderJid else "" }.forEach { (sender, list) ->
                            val idsCsv = list.joinToString(",") { it.id }
                            Log.i(TAG, "markChatReadExplicitly($chatJid): sending MarkRead for ${list.size} msgs (sender=$sender)")
                            runCatching { Whatsbridge.markReadManual(chatJid, sender, idsCsv) }
                                .onFailure { Log.w(TAG, "markReadManual failed for $chatJid (sender=$sender): ${it.message}") }
                        }
                    } else {
                        Log.i(TAG, "markChatReadExplicitly($chatJid): no outgoing MarkRead sent (count=${combinedTuples.size})")
                    }
                    combinedTuples.size
                }
            }

        suspend fun sendManualPlayedReceipt(context: Context, msg: MessageEntity): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = WaUiDatabase.getInstance(context).waDao()
                    dao.updateDeliveryStatus(msg.id, "PLAYED")
                    if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        val senderParam = if (msg.chatJid.endsWith("@g.us")) msg.senderJid else ""
                        Whatsbridge.sendPlayedReceiptManual(msg.chatJid, senderParam, msg.id)
                    }
                    Unit
                }
            }

        suspend fun toggleChannelMute(context: Context, chatJid: String, muted: Boolean): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = WaUiDatabase.getInstance(context).waDao()
                    dao.updateMuted(chatJid, muted, 0L)
                    if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        Whatsbridge::class.java
                            .getMethod("toggleNewsletterMute", String::class.java, Boolean::class.javaPrimitiveType)
                            .invoke(null, chatJid, muted)
                    }
                    Unit
                }
            }

        suspend fun deleteChats(
            context: Context,
            chatJids: Collection<String>,
            syncToWhatsApp: Boolean
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val appCtx = context.applicationContext
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val remoteTargets = mutableListOf<Pair<String, com.wupp.meow.data.db.MessageEntity?>>()

                chatJids.distinct().forEach { jid ->
                    val latest = dao.getLatestMessageForChat(jid)

                    runCatching { mgr?.cancel(notificationIdForChat(jid)) }
                    dao.deleteMessagesForChat(jid)
                    dao.deleteChatRow(jid)

                    if (syncToWhatsApp &&
                        !jid.endsWith("@newsletter") &&
                        !jid.endsWith("@broadcast")
                    ) {
                        remoteTargets.add(jid to latest)
                    }
                }

                if (syncToWhatsApp && remoteTargets.isNotEmpty()) {
                    if (!runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)) {
                        throw IllegalStateException("Deleted locally, but WhatsApp isn't connected")
                    }
                    var firstRemoteErr: Throwable? = null
                    remoteTargets.forEachIndexed { idx, (jid, latest) ->
                        if (idx > 0) delay(150L)
                        runCatching {
                            Whatsbridge.deleteChatRemote(
                                jid,
                                latest?.id ?: "",
                                latest?.isFromMe ?: false,
                                latest?.senderJid ?: "",
                                latest?.timestamp ?: 0L,
                                false
                            )
                        }.onFailure { err ->
                            if (firstRemoteErr == null) firstRemoteErr = err
                        }
                    }
                    firstRemoteErr?.let { throw it }
                }
                Unit
            }
        }

        suspend fun sendReaction(
            context: Context,
            chatJid: String,
            targetMsg: MessageEntity,
            emoji: String
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val dao = WaUiDatabase.getInstance(context).waDao()
                val isChannel = chatJid.endsWith("@newsletter")
                val finalEmoji: String
                if (isChannel) {
                    val existingSummary = targetMsg.reactionSummary.trim()
                    finalEmoji = if (emoji.isBlank() || existingSummary == emoji) "" else emoji
                    val updatedSummary = when {
                        finalEmoji.isBlank() -> ""
                        existingSummary.isBlank() -> finalEmoji
                        existingSummary.contains(finalEmoji) -> existingSummary
                        else -> "$existingSummary $finalEmoji"
                    }
                    dao.updateReactionSummary(targetMsg.id, updatedSummary)
                } else {
                    // WhatsApp keeps a single reaction per person: tapping the same emoji clears it,
                    // tapping another one replaces it, and other participants' reactions stay intact.
                    val currentMine = dao.getMyReaction(targetMsg.id)
                    finalEmoji = if (emoji.isBlank() || currentMine == emoji) "" else emoji
                    dao.applyReaction(
                        messageId = targetMsg.id,
                        actor = MessageReactionEntity.ME_ACTOR,
                        emoji = finalEmoji,
                        timestamp = System.currentTimeMillis()
                    )
                }
                if (runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false) && !isChannel) {
                    Whatsbridge::class.java
                        .getMethod(
                            "sendReaction",
                            String::class.java,
                            String::class.java,
                            String::class.java,
                            Boolean::class.javaPrimitiveType,
                            String::class.java
                        )
                        .invoke(null, chatJid, targetMsg.id, targetMsg.senderJid, targetMsg.isFromMe, finalEmoji)
                }
                Unit
            }
        }

        suspend fun verifyBulkNumbers(phonesCsv: String): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
                if (runCatching { Whatsbridge.isConnected() }.getOrDefault(false)) {
                    Whatsbridge.verifyNumbersOnWhatsApp(phonesCsv)
                } else {
                    // Structured response explaining offline vs companion mode
                    val out = JSONArray()
                    phonesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { q ->
                        val normalized = q.filter { it.isDigit() }
                        out.put(JSONObject().apply {
                            put("query", q)
                            put("jid", if (normalized.length >= 10) "$normalized@s.whatsapp.net" else "")
                            put("isIn", normalized.length >= 10 && _bridgeStatus.value.connectionState == "CONNECTED")
                            put("note", "Link active companion session to execute live server IsOnWhatsApp check")
                        })
                    }
                    out.toString(2)
                }
            }
        }

        /**
         * Seeds a complete, realistic showcase dataset including 1,220 messages for Paging 3 scroll
         * benchmarking plus live Anti-Delete, Edit History, View Once Vault & Key=0B Stripped stub,
         * playable Voice Note + offline transcript, Status Vault, Presence Logs, and Avatar History.
         */
        suspend fun seedBenchmarkAndShowcase(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val dao = WaUiDatabase.getInstance(context).waDao()
                EagerMediaVault.ensureDirectories(context)
                val now = System.currentTimeMillis()
                val elenaJid = "15550192834@s.whatsapp.net"
                val marcusJid = "15550198811@s.whatsapp.net"
                val groupJid = "120363044556677889@g.us"
                val unsavedLeadJid = "15550000463@s.whatsapp.net"

                // Create real View Once PNG, Status PNG, Avatar PNG, and Voice Note WAV files on disk
                val voImageFile = EagerMediaVault.generateVaultImageAsset(
                    File(EagerMediaVault.viewOnceVaultDir(context), "vo_elena_blueprint.png"),
                    "Elena · Decrypted Prior to Expiry",
                    0xFF005C4B.toInt()
                )
                val statusImageFile = EagerMediaVault.generateVaultImageAsset(
                    File(EagerMediaVault.statusVaultDir(context), "status_marcus_revoked.png"),
                    "Marcus Status · Preserved After Delete",
                    0xFF1F2C34.toInt()
                )
                val elenaAvatarFile = EagerMediaVault.generateVaultImageAsset(
                    File(EagerMediaVault.avatarVaultDir(context), "elena_avatar_v2.png"),
                    "Elena Profile Snapshot",
                    0xFF00A884.toInt()
                )
                val pttFile = File(EagerMediaVault.mediaDir(context), "ptt_elena_demo.wav")
                val pttWaveform = EagerMediaVault.generateSpeechCadenceWav(pttFile, durationSec = 5).joinToString(",")
                File("${pttFile.absolutePath}.txt").writeText(
                    "Hey! Pushing the dual-database Go bridge build now—check the View Once vault and anti-delete logs on device."
                )

                val chats = listOf(
                    ChatEntity(
                        jid = elenaJid,
                        name = "Elena Vance",
                        avatarPath = elenaAvatarFile.absolutePath,
                        aboutText = "Systems & Protocol R&D · Dark Mode only",
                        isFavorite = true,
                        isPinned = true,
                        pinOrder = 100L,
                        isOnline = true,
                        lastSeen = now - 5_000L,
                        lastMessageId = "msg_elena_vo_stripped",
                        lastMessagePreview = "View once media (Restricted on Companion)",
                        lastMessageType = "VIEW_ONCE_STRIPPED",
                        lastMessageTimestamp = now - 15_000L,
                        hasDeletedMessages = true,
                        hasEditedMessages = true,
                        ephemeralDuration = 86400
                    ),
                    ChatEntity(
                        jid = marcusJid,
                        name = "Marcus Chen",
                        aboutText = "At the lab",
                        isFavorite = true,
                        unreadCount = 2,
                        lastSeen = now - 320_000L,
                        lastMessageId = "msg_marcus_unread_2",
                        lastMessagePreview = "Did you test manual blue-tick dispatch yet?",
                        lastMessageType = "TEXT",
                        lastMessageTimestamp = now - 90_000L,
                        hasEditedMessages = true
                    ),
                    ChatEntity(
                        jid = groupJid,
                        name = "Arch Engineering Core (1.2k)",
                        isGroup = true,
                        unreadCount = 1,
                        lastMessageId = "msg_grp_1220",
                        lastMessagePreview = "Group benchmark message #1220 · 120fps Paging verified",
                        lastMessageType = "TEXT",
                        lastMessageTimestamp = now - 120_000L,
                        hasDeletedMessages = true
                    ),
                    ChatEntity(
                        jid = unsavedLeadJid,
                        name = "+1 555-000-0463 (Unsaved)",
                        unreadCount = 0,
                        lastMessagePreview = "No prior history (Error 463 Ban Guard Demo)",
                        lastMessageTimestamp = now - 3_600_000L
                    )
                )

                val msgs = mutableListOf<MessageEntity>()

                // Elena thread showcasing every mod card
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_1",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 700_000L,
                        body = "Welcome to Wupp local companion! Every Signal session stays isolated in wa_protocol.db.",
                        deliveryStatus = "DELIVERED",
                        isEphemeral = true,
                        ephemeralDuration = 86400,
                        ephemeralStart = now - 700_000L
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_deleted",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 520_000L,
                        body = "Original unreleased staging key backup: 9942-DELTA-881 (she revoked this 12s later)",
                        deliveryStatus = "DELIVERED",
                        isDeletedBySender = true,
                        deletedAt = now - 508_000L
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_edited",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 410_000L,
                        body = "Meet at 9:30 PM — bring the Pixel test rig.",
                        deliveryStatus = "DELIVERED",
                        isEdited = true,
                        editedAt = now - 380_000L,
                        reactionSummary = "🔥 💯"
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_ptt",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 250_000L,
                        body = "",
                        msgType = "PTT",
                        mimeType = "audio/wav",
                        localMediaPath = pttFile.absolutePath,
                        mediaSha256 = EagerMediaVault.computeSha256(pttFile),
                        fileSize = pttFile.length(),
                        durationSec = 5,
                        waveformCsv = pttWaveform,
                        audioTranscript = "Hey! Pushing the dual-database Go bridge build now—check the View Once vault and anti-delete logs on device.",
                        deliveryStatus = "DELIVERED"
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_vo_ok",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 160_000L,
                        body = "View once blueprint captured cleanly from HistorySync bundle",
                        msgType = "IMAGE",
                        mimeType = "image/png",
                        localMediaPath = voImageFile.absolutePath,
                        mediaSha256 = EagerMediaVault.computeSha256(voImageFile),
                        fileSize = voImageFile.length(),
                        isViewOnce = true,
                        viewOnceState = "AVAILABLE",
                        deliveryStatus = "DELIVERED"
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_elena_vo_stripped",
                        chatJid = elenaJid,
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        isFromMe = false,
                        timestamp = now - 15_000L,
                        body = "Key=0B stripped by Meta server on companion session",
                        msgType = "VIEW_ONCE_STRIPPED",
                        mimeType = "application/x-view-once-stub",
                        isViewOnce = true,
                        viewOnceState = "STRIPPED",
                        deliveryStatus = "DELIVERED"
                    )
                )

                // Marcus unread messages for Manual Blue-Tick verification
                msgs.add(
                    MessageEntity(
                        id = "msg_marcus_unread_1",
                        chatJid = marcusJid,
                        senderJid = marcusJid,
                        senderName = "Marcus Chen",
                        isFromMe = false,
                        timestamp = now - 180_000L,
                        body = "Just sent a message while Ghost Read is active — double-ticks won't send automatically.",
                        deliveryStatus = "DELIVERED",
                        isEdited = true,
                        editedAt = now - 160_000L
                    )
                )
                msgs.add(
                    MessageEntity(
                        id = "msg_marcus_unread_2",
                        chatJid = marcusJid,
                        senderJid = marcusJid,
                        senderName = "Marcus Chen",
                        isFromMe = false,
                        timestamp = now - 90_000L,
                        body = "Did you test manual blue-tick dispatch yet?",
                        quotedId = "msg_marcus_unread_1",
                        quotedSender = "Marcus Chen",
                        quotedBody = "Just sent a message while Ghost Read is active — double-ticks won't send automatically.",
                        deliveryStatus = "DELIVERED"
                    )
                )

                // 1,220 group messages for 120fps Paging 3 scroll benchmark
                val baseGrpTs = now - 1_220 * 15_000L
                val participants = listOf(
                    "15550192834@s.whatsapp.net" to "Elena Vance",
                    "15550198811@s.whatsapp.net" to "Marcus Chen",
                    "15550194422@s.whatsapp.net" to "Devon Brooks"
                )
                for (idx in 1..1220) {
                    val isMe = idx % 4 == 0
                    val p = participants[idx % participants.size]
                    msgs.add(
                        MessageEntity(
                            id = "msg_grp_$idx",
                            chatJid = groupJid,
                            senderJid = if (isMe) "me@s.whatsapp.net" else p.first,
                            senderName = if (isMe) "You" else p.second,
                            isFromMe = isMe,
                            timestamp = baseGrpTs + idx * 14_000L,
                            body = when (idx) {
                                1218 -> "Anti-Delete test entry in group timeline (retained locally)."
                                1220 -> "Group benchmark message #1220 · 120fps Paging verified"
                                else -> "Benchmark log #$idx: Signal pre-key rotation & WAL batch sync nominal."
                            },
                            deliveryStatus = if (isMe) "READ" else "DELIVERED",
                            isDeletedBySender = (idx == 1218),
                            deletedAt = if (idx == 1218) now - 150_000L else 0L
                        )
                    )
                }

                dao.insertHistorySyncBatch(chats, msgs)

                // Multi-version edit history entries for Elena & Marcus
                if (dao.getMessageEditsOnce("msg_elena_edited").isEmpty()) {
                    dao.insertMessageEdit(
                        MessageEditEntity(
                            messageId = "msg_elena_edited",
                            oldText = "Meet at 8:00 PM at the coffee shop.",
                            editedAt = now - 400_000L
                        )
                    )
                    dao.insertMessageEdit(
                        MessageEditEntity(
                            messageId = "msg_elena_edited",
                            oldText = "Actually 9:00 PM — forgot my laptop charger.",
                            editedAt = now - 390_000L
                        )
                    )
                }
                if (dao.getMessageEditsOnce("msg_marcus_unread_1").isEmpty()) {
                    dao.insertMessageEdit(
                        MessageEditEntity(
                            messageId = "msg_marcus_unread_1",
                            oldText = "Testing message before ghost edit.",
                            editedAt = now - 170_000L
                        )
                    )
                }

                // Group receipts for latest group message
                dao.upsertGroupReceipt(
                    GroupReceiptEntity("msg_grp_1220", elenaJid, "Elena Vance", "READ", now - 110_000L)
                )
                dao.upsertGroupReceipt(
                    GroupReceiptEntity("msg_grp_1220", marcusJid, "Marcus Chen", "DELIVERED", now - 108_000L)
                )

                // Status Vault with a revoked status preserved locally
                dao.upsertStatus(
                    StatusEntity(
                        id = "st_marcus_1",
                        senderJid = marcusJid,
                        senderName = "Marcus Chen",
                        caption = "Late night SQLite WAL profiler run [deleted by contact 4m later]",
                        msgType = "IMAGE",
                        mimeType = "image/png",
                        localMediaPath = statusImageFile.absolutePath,
                        timestamp = now - 900_000L,
                        isRevoked = true,
                        revokedAt = now - 660_000L
                    )
                )
                dao.upsertStatus(
                    StatusEntity(
                        id = "st_elena_1",
                        senderJid = elenaJid,
                        senderName = "Elena Vance",
                        caption = "Zero-loss View Once capture confirmed",
                        msgType = "IMAGE",
                        mimeType = "image/png",
                        localMediaPath = voImageFile.absolutePath,
                        timestamp = now - 300_000L,
                        isRevoked = false
                    )
                )

                // Presence History timeline
                dao.insertPresenceLog(PresenceLogEntity(jid = elenaJid, contactName = "Elena Vance", state = "OFFLINE", timestamp = now - 1_800_000L))
                dao.insertPresenceLog(PresenceLogEntity(jid = elenaJid, contactName = "Elena Vance", state = "ONLINE", timestamp = now - 600_000L))
                dao.insertPresenceLog(PresenceLogEntity(jid = marcusJid, contactName = "Marcus Chen", state = "ONLINE", timestamp = now - 400_000L))
                dao.insertPresenceLog(PresenceLogEntity(jid = marcusJid, contactName = "Marcus Chen", state = "OFFLINE", timestamp = now - 320_000L))

                // Avatar & Bio Archiver snapshots
                dao.insertProfileHistory(
                    ProfileHistoryEntity(
                        jid = elenaJid,
                        contactName = "Elena Vance",
                        avatarPath = elenaAvatarFile.absolutePath,
                        aboutText = "Systems & Protocol R&D · Dark Mode only",
                        capturedAt = now - 1_200_000L
                    )
                )
                dao.insertProfileHistory(
                    ProfileHistoryEntity(
                        jid = marcusJid,
                        contactName = "Marcus Chen",
                        avatarPath = statusImageFile.absolutePath,
                        aboutText = "Building companion bridges",
                        capturedAt = now - 2_400_000L
                    )
                )
                Unit
            }
        }

        private fun unwrapReflectFailure(err: Throwable): Throwable =
            if (err is java.lang.reflect.InvocationTargetException && err.targetException != null) {
                err.targetException
            } else {
                err
            }

        private fun hasBridgeMethod(name: String, vararg paramTypes: Class<*>): Boolean =
            runCatching {
                Whatsbridge::class.java.getMethod(name, *paramTypes)
                true
            }.getOrDefault(false)

        suspend fun fetchOwnProfile(): Result<OwnProfileUi> = withContext(Dispatchers.IO) {
            runCatching {
                val ownJid = _bridgeStatus.value.ownJid
                val phone = ownJid.substringBefore("@").substringBefore(":").takeIf { it.isNotBlank() }?.let { "+$it" }.orEmpty()
                if (!hasBridgeMethod("getOwnProfileJson")) {
                    return@runCatching OwnProfileUi(
                        ownJid = ownJid,
                        phone = phone,
                        pushName = "",
                        about = "",
                        syncSupported = false
                    )
                }
                val raw = try {
                    Whatsbridge::class.java.getMethod("getOwnProfileJson").invoke(null) as? String
                } catch (t: Throwable) {
                    throw unwrapReflectFailure(t)
                }
                val obj = JSONObject(raw.orEmpty().ifBlank { "{}" })
                OwnProfileUi(
                    ownJid = obj.optString("ownJid", ownJid),
                    phone = obj.optString("phone", phone),
                    pushName = obj.optString("pushName", ""),
                    about = obj.optString("about", ""),
                    syncSupported = true
                )
            }
        }

        suspend fun updateOwnPushName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                try {
                    Whatsbridge::class.java
                        .getMethod("setOwnPushName", String::class.java)
                        .invoke(null, name.trim())
                } catch (t: Throwable) {
                    throw unwrapReflectFailure(t)
                }
                Unit
            }
        }

        suspend fun updateOwnAbout(about: String): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                try {
                    Whatsbridge::class.java
                        .getMethod("setOwnAbout", String::class.java)
                        .invoke(null, about.trim())
                } catch (t: Throwable) {
                    throw unwrapReflectFailure(t)
                }
                Unit
            }
        }

        suspend fun fetchAccountPrivacySettings(): Result<AccountPrivacyUi> = withContext(Dispatchers.IO) {
            runCatching {
                val timerSec = StealthMatrixManager.config.value.defaultDisappearingSec
                if (!hasBridgeMethod("getAccountPrivacySettingsJson")) {
                    return@runCatching AccountPrivacyUi(
                        defaultDisappearingSec = timerSec,
                        syncSupported = false
                    )
                }
                val raw = try {
                    Whatsbridge::class.java.getMethod("getAccountPrivacySettingsJson").invoke(null) as? String
                } catch (t: Throwable) {
                    throw unwrapReflectFailure(t)
                }
                parseAccountPrivacyJson(raw.orEmpty(), timerSec, syncSupported = true)
            }
        }

        suspend fun updateAccountPrivacySetting(category: String, value: String): Result<AccountPrivacyUi> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val raw = try {
                        Whatsbridge::class.java
                            .getMethod("setAccountPrivacySetting", String::class.java, String::class.java)
                            .invoke(null, category, value) as? String
                    } catch (t: Throwable) {
                        throw unwrapReflectFailure(t)
                    }
                    parseAccountPrivacyJson(
                        raw.orEmpty(),
                        StealthMatrixManager.config.value.defaultDisappearingSec,
                        syncSupported = true
                    )
                }
            }

        suspend fun setDefaultDisappearingTimerSec(seconds: Int): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val clamped = seconds.coerceAtLeast(0)
                if (hasBridgeMethod("setDefaultDisappearingTimerSec", Long::class.javaPrimitiveType!!)) {
                    try {
                        Whatsbridge::class.java
                            .getMethod("setDefaultDisappearingTimerSec", Long::class.javaPrimitiveType!!)
                            .invoke(null, clamped.toLong())
                    } catch (t: Throwable) {
                        throw unwrapReflectFailure(t)
                    }
                }
                StealthMatrixManager.updateConfig { it.copy(defaultDisappearingSec = clamped) }
            }
        }

        suspend fun fetchBlockedContacts(): Result<Pair<List<BlockedContactUi>, Boolean>> =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!hasBridgeMethod("getBlocklistJson")) {
                        return@runCatching emptyList<BlockedContactUi>() to false
                    }
                    val raw = try {
                        Whatsbridge::class.java.getMethod("getBlocklistJson").invoke(null) as? String
                    } catch (t: Throwable) {
                        throw unwrapReflectFailure(t)
                    }
                    val arr = JSONArray(raw.orEmpty().ifBlank { "[]" })
                    val items = ArrayList<BlockedContactUi>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val jid = o.optString("jid")
                        if (jid.isBlank()) continue
                        items += BlockedContactUi(
                            jid = jid,
                            name = o.optString("name", jid.substringBefore("@")),
                            phone = o.optString("phone", "")
                        )
                    }
                    items to true
                }
            }

        suspend fun setContactBlocked(jid: String, blocked: Boolean): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    try {
                        Whatsbridge::class.java
                            .getMethod("setContactBlocked", String::class.java, Boolean::class.javaPrimitiveType!!)
                            .invoke(null, jid, blocked)
                    } catch (t: Throwable) {
                        throw unwrapReflectFailure(t)
                    }
                    Unit
                }
            }

        fun computeStorageStats(context: Context): WuppStorageStats {
            val appCtx = context.applicationContext
            val dbBytes = (appCtx.databaseList() ?: emptyArray())
                .sumOf { dbName -> appCtx.getDatabasePath(dbName)?.length() ?: 0L }
            val mediaRoot = File(appCtx.filesDir, "wa_vault")
            val altFilesSize = folderByteSize(appCtx.filesDir)
            val vaultBytes = if (mediaRoot.exists()) folderByteSize(mediaRoot) else altFilesSize
            val cacheBytes = folderByteSize(appCtx.cacheDir)
            return WuppStorageStats(
                dbBytes = dbBytes,
                vaultBytes = vaultBytes,
                tempCacheBytes = cacheBytes
            )
        }

        fun clearSafeTempCache(context: Context): Long {
            val appCtx = context.applicationContext
            val before = folderByteSize(appCtx.cacheDir)
            appCtx.cacheDir?.listFiles()?.forEach { child ->
                runCatching {
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
            return before
        }

        private fun folderByteSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            if (dir.isFile) return dir.length()
            var total = 0L
            dir.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
            return total
        }

        private fun parseAccountPrivacyJson(
            raw: String,
            timerSec: Int,
            syncSupported: Boolean
        ): AccountPrivacyUi {
            val obj = JSONObject(raw.ifBlank { "{}" })
            return AccountPrivacyUi(
                lastSeen = obj.optString("lastSeen", "all").ifBlank { "all" },
                online = obj.optString("online", "all").ifBlank { "all" },
                profile = obj.optString("profile", "all").ifBlank { "all" },
                status = obj.optString("status", "contacts").ifBlank { "contacts" },
                groupAdd = obj.optString("groupAdd", "all").ifBlank { "all" },
                readReceipts = obj.optString("readReceipts", "all").ifBlank { "all" },
                callAdd = obj.optString("callAdd", "all").ifBlank { "all" },
                defaultDisappearingSec = timerSec,
                syncSupported = syncSupported
            )
        }
    }
}

data class OwnProfileUi(
    val ownJid: String = "",
    val phone: String = "",
    val pushName: String = "",
    val about: String = "",
    val syncSupported: Boolean = false
)

data class AccountPrivacyUi(
    val lastSeen: String = "all",
    val online: String = "all",
    val profile: String = "all",
    val status: String = "contacts",
    val groupAdd: String = "all",
    val readReceipts: String = "all",
    val callAdd: String = "all",
    val defaultDisappearingSec: Int = 0,
    val syncSupported: Boolean = false
)

data class BlockedContactUi(
    val jid: String,
    val name: String,
    val phone: String
)

data class WuppStorageStats(
    val dbBytes: Long = 0L,
    val vaultBytes: Long = 0L,
    val tempCacheBytes: Long = 0L
)
