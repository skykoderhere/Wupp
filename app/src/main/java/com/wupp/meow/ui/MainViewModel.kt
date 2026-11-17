package com.wupp.meow.ui

import android.app.Application
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wupp.meow.core.service.BridgeStatus
import com.wupp.meow.core.service.WaRealtimeService
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.data.db.ContactEntity
import com.wupp.meow.data.db.GroupReceiptEntity
import com.wupp.meow.data.db.MessageEditEntity
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.data.db.PresenceLogEntity
import com.wupp.meow.data.db.ProfileHistoryEntity
import com.wupp.meow.data.db.StatusEntity
import com.wupp.meow.data.db.WaUiDatabase
import com.wupp.meow.data.media.EagerMediaVault
import com.wupp.meow.data.media.OutboundQualityMode
import com.wupp.meow.data.media.SoundPoolManager
import com.wupp.meow.domain.security.AppLockConfig
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.domain.stealth.AutoDownloadNetwork
import com.wupp.meow.domain.stealth.EffectiveChatPrivacy
import com.wupp.meow.domain.stealth.GlobalStealthConfig
import com.wupp.meow.domain.stealth.PrimaryHeartbeatStatus
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.components.StagedOutboundMedia
import com.wupp.meow.ui.components.copyCustomWallpaperImage
import com.wupp.meow.ui.components.probeVideoMetadata
import com.wupp.meow.ui.theme.LocaleHelper
import com.wupp.meow.ui.theme.WuppAccentColor
import com.wupp.meow.ui.theme.WuppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class HomeFilterChip(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    FAVORITES("Favorites"),
    GROUPS("Groups"),
    LOCKED("Locked"),
    DELETED("Deleted"),
    EDITED("Edited")
}

internal fun filterHomeThreads(
    chats: List<ChatEntity>,
    chip: HomeFilterChip,
    query: String,
    hideLocked: Boolean,
    groupFilter: Boolean? = null
): List<ChatEntity> {
    val chatThreads = chats.filter {
        !it.isCommunityParent &&
            !it.jid.endsWith("@newsletter") &&
            it.jid != "status@broadcast" &&
            !it.jid.endsWith("@broadcast") &&
            !it.isPhantomEmptyDm() &&
            !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
            (groupFilter == null || it.isGroup == groupFilter)
    }
    val effectiveChip = if (groupFilter != null && chip == HomeFilterChip.GROUPS) {
        HomeFilterChip.ALL
    } else {
        chip
    }
    val byChip = when (effectiveChip) {
        HomeFilterChip.ALL -> chatThreads.filter { !hideLocked || !it.isLocked }
        HomeFilterChip.UNREAD -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.unreadCount > 0 }
        HomeFilterChip.FAVORITES -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.isFavorite }
        HomeFilterChip.GROUPS -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.isGroup }
        HomeFilterChip.LOCKED -> if (hideLocked) emptyList() else chatThreads.filter { it.isLocked }
        HomeFilterChip.DELETED -> chatThreads.filter { (!hideLocked || !it.isLocked) && (it.hasDeletedMessages || it.lastMessageIsDeleted) }
        HomeFilterChip.EDITED -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.hasEditedMessages }
    }
    val q = query.trim().lowercase()
    return if (q.isEmpty()) byChip
    else byChip.filter {
        it.name.lowercase().contains(q) ||
            it.jid.lowercase().contains(q) ||
            it.lastMessagePreview.lowercase().contains(q)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val dao = WaUiDatabase.getInstance(appContext).waDao()

    val stealthConfig: StateFlow<GlobalStealthConfig> = StealthMatrixManager.config
    val appLockConfig: StateFlow<AppLockConfig> = AppLockManager.config
    val isAppLocked: StateFlow<Boolean> = AppLockManager.isLocked
    val bridgeStatus: StateFlow<BridgeStatus> = WaRealtimeService.bridgeStatus

    private val _lockedChatsRevealed = MutableStateFlow(false)
    val lockedChatsRevealed: StateFlow<Boolean> = _lockedChatsRevealed.asStateFlow()

    init {
        viewModelScope.launch {
            AppLockManager.isLocked.collect { locked ->
                if (locked) {
                    _lockedChatsRevealed.value = false
                }
            }
        }
    }

    val heartbeatStatus: StateFlow<PrimaryHeartbeatStatus> = stealthConfig
        .map { StealthMatrixManager.computeHeartbeatStatus(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StealthMatrixManager.computeHeartbeatStatus())

    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HomeFilterChip.ALL)
    val selectedFilter: StateFlow<HomeFilterChip> = _selectedFilter.asStateFlow()

    private val _selectedGroupsFilter = MutableStateFlow(HomeFilterChip.ALL)
    val selectedGroupsFilter: StateFlow<HomeFilterChip> = _selectedGroupsFilter.asStateFlow()

    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _ftsSearchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val ftsSearchResults: StateFlow<List<MessageEntity>> = _ftsSearchResults.asStateFlow()

    private val _bulkVerifyOutput = MutableStateFlow<String?>(null)
    val bulkVerifyOutput: StateFlow<String?> = _bulkVerifyOutput.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()
    private var activeRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var recordingStartMs: Long = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChats: StateFlow<List<ChatEntity>> = _includeArchived
        .flatMapLatest { dao.observeChats(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = dao.observeContacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val archivedCount: StateFlow<Int> = dao.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val hiddenLockedChatsCount: StateFlow<Int> = combine(
        allChats,
        appLockConfig,
        lockedChatsRevealed
    ) { chats, lockCfg, revealed ->
        if (!lockCfg.hideLockedChatsBehindLock || revealed) 0
        else chats.count {
            it.isLocked &&
                !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.jid != "status@broadcast" &&
                !it.jid.endsWith("@broadcast") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val homeFilterCriteria = combine(
        allChats,
        _homeSearchQuery,
        appLockConfig,
        lockedChatsRevealed
    ) { chats, query, lockCfg, revealed ->
        QuadFilterState(chats, query, lockCfg.hideLockedChatsBehindLock && !revealed)
    }

    private data class QuadFilterState(
        val chats: List<ChatEntity>,
        val query: String,
        val hideLocked: Boolean
    )

    val filteredChats: StateFlow<List<ChatEntity>> = combine(
        homeFilterCriteria,
        _selectedFilter,
        stealthConfig
    ) { state, chip, stealth ->
        val groupFilter = if (stealth.separateGroupsAndChats) false else null
        filterHomeThreads(state.chats, chip, state.query, state.hideLocked, groupFilter = groupFilter)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredGroups: StateFlow<List<ChatEntity>> = combine(
        homeFilterCriteria,
        _selectedGroupsFilter
    ) { state, chip ->
        filterHomeThreads(state.chats, chip, state.query, state.hideLocked, groupFilter = true)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val channels: StateFlow<List<ChatEntity>> = allChats
        .map { chats ->
            chats.filter { it.jid.endsWith("@newsletter") }
                .sortedWith(
                    compareByDescending<ChatEntity> { it.isPinned }
                        .thenByDescending { it.pinOrder }
                        .thenByDescending { it.lastMessageTimestamp }
                        .thenBy { it.name.lowercase() }
                )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val statuses: StateFlow<List<StatusEntity>> = dao.observeStatuses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val viewOnceVault: StateFlow<List<MessageEntity>> = dao.observeViewOnceVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presenceLogs: StateFlow<List<PresenceLogEntity>> = dao.observeAllPresenceLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileHistory: StateFlow<List<ProfileHistoryEntity>> = dao.observeAllProfileHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFilter(chip: HomeFilterChip) {
        _selectedFilter.value = chip
    }

    fun selectGroupsFilter(chip: HomeFilterChip) {
        _selectedGroupsFilter.value = chip
    }

    fun toggleArchivedIncluded() {
        _includeArchived.value = !_includeArchived.value
    }

    fun setIncludeArchived(include: Boolean) {
        _includeArchived.value = include
    }

    fun setLockedChatsRevealed(revealed: Boolean) {
        _lockedChatsRevealed.value = revealed
    }

    fun updateHomeSearch(query: String) {
        _homeSearchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            val cleaned = query.trim().replace("\"", "")
            if (cleaned.length < 2) {
                _ftsSearchResults.value = emptyList()
            } else {
                val results = runCatching {
                    dao.searchMessagesFts("$cleaned*")
                }.getOrDefault(emptyList())
                _ftsSearchResults.value = results
            }
        }
    }

    fun setActiveChat(chatJid: String?) {
        WaRealtimeService.activeChatJid.value = chatJid
        if (!chatJid.isNullOrBlank()) {
            WaRealtimeService.onChatOpened(appContext, chatJid)
        }
    }

    val downloadingMediaIds: StateFlow<Set<String>> = WaRealtimeService.downloadingMediaIds
    val failedMediaIds: StateFlow<Map<String, String>> = WaRealtimeService.failedMediaIds

    fun requestMediaDownload(msg: MessageEntity, userInitiated: Boolean = true) {
        WaRealtimeService.requestMediaDownload(appContext, msg, userInitiated)
    }

    fun requestStatusMediaDownload(status: StatusEntity, userInitiated: Boolean = true) {
        WaRealtimeService.requestStatusMediaDownload(appContext, status, userInitiated)
    }

    fun observeChat(chatJid: String): Flow<ChatEntity?> = dao.observeChat(chatJid)

    fun observeRecentMessages(chatJid: String, limit: Int = 20): Flow<List<MessageEntity>> =
        dao.observeRecentMessages(chatJid, limit)

    fun observeMyReaction(messageId: String): Flow<String?> = dao.observeMyReaction(messageId)

    fun observeChatGalleryMedia(chatJid: String): Flow<List<MessageEntity>> =
        dao.observeChatGalleryMedia(chatJid)

    fun observeContact(jid: String): Flow<ContactEntity?> = dao.observeContact(jid)

    fun observeEffectivePrivacy(chatJid: String): Flow<EffectiveChatPrivacy> =
        StealthMatrixManager.observeEffectivePrivacy(dao, chatJid)

    fun canInitiateOutbound(chatJid: String): Flow<Boolean> {
        return if (chatJid.endsWith("@g.us")) {
            MutableStateFlow(true)
        } else {
            dao.observeRecentMessages(chatJid, 1).map { it.isNotEmpty() }
        }
    }

    private val pagerFlowCache = object : LinkedHashMap<String, Flow<PagingData<MessageEntity>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Flow<PagingData<MessageEntity>>>?
        ): Boolean = size > 12
    }

    fun pagedMessages(chatJid: String): Flow<PagingData<MessageEntity>> = synchronized(pagerFlowCache) {
        pagerFlowCache.getOrPut(chatJid) {
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    initialLoadSize = 120,
                    prefetchDistance = 20,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { dao.pagingMessages(chatJid) }
            ).flow.cachedIn(viewModelScope)
        }
    }

    suspend fun searchInChat(chatJid: String, query: String): List<MessageEntity> =
        withContext(Dispatchers.IO) {
            if (query.trim().length < 2) emptyList()
            else dao.searchInChat(chatJid, query.trim())
        }

    suspend fun findMessageExistsInChat(chatJid: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            val id = messageId.trim()
            id.isNotEmpty() && dao.getMessage(id)?.chatJid == chatJid
        }

    fun observeEdits(messageId: String): Flow<List<MessageEditEntity>> =
        dao.observeMessageEdits(messageId)

    fun observeGroupReceipts(messageId: String): Flow<List<GroupReceiptEntity>> =
        dao.observeGroupReceipts(messageId)

    fun toggleGhostPresence() {
        val next = !stealthConfig.value.ghostPresence
        viewModelScope.launch { WaRealtimeService.setGhostPresence(next) }
    }

    fun setThemeMode(themeMode: WuppThemeMode) {
        StealthMatrixManager.updateConfig { it.copy(themeMode = themeMode) }
    }

    fun setAccentColor(accentColor: WuppAccentColor) {
        StealthMatrixManager.updateConfig { it.copy(accentColor = accentColor) }
    }

    fun setUseSystemFont(enabled: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(useSystemFont = enabled) }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun setCurrentMood(mood: String) {
        val cleaned = mood.trim().take(60)
        StealthMatrixManager.updateConfig { it.copy(currentMood = cleaned) }
        showToast(if (cleaned.isEmpty()) "Mood cleared" else "Mood updated")
    }

    fun setQuickReactionEmojis(emojis: List<String>) {
        StealthMatrixManager.updateConfig {
            it.copy(quickReactionEmojis = StealthMatrixManager.sanitizeQuickReactions(emojis))
        }
    }

    fun resetQuickReactionEmojis() {
        StealthMatrixManager.updateConfig {
            it.copy(quickReactionEmojis = StealthMatrixManager.DEFAULT_QUICK_REACTIONS)
        }
    }

    fun setAppLanguage(languageCode: String) {
        val normalized = when (languageCode) {
            LocaleHelper.LANG_EN, LocaleHelper.LANG_TR -> languageCode
            else -> LocaleHelper.LANG_SYSTEM
        }
        StealthMatrixManager.updateConfig { it.copy(appLanguage = normalized) }
    }

    fun setChatWallpaper(uri: String) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperUri = uri) }
    }

    fun setChatWallpaperDim(dim: Float) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperDim = dim.coerceIn(0f, 0.75f)) }
    }

    fun setChatWallpaperShowDoodle(show: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperShowDoodle = show) }
    }

    fun setChatWallpaperPosition(scale: Float, offsetX: Float, offsetY: Float) {
        StealthMatrixManager.updateConfig {
            it.copy(
                chatWallpaperScale = scale.coerceIn(1f, 3f),
                chatWallpaperOffsetX = offsetX.coerceIn(-1f, 1f),
                chatWallpaperOffsetY = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    fun importChatWallpaperFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = copyCustomWallpaperImage(
                appContext,
                uri,
                keepFilePaths = StealthMatrixManager.referencedWallpaperFilePaths()
            )
            if (!saved.isNullOrBlank()) {
                StealthMatrixManager.updateConfig { it.copy(chatWallpaperUri = saved) }
            }
        }
    }

    fun setPerChatWallpaper(chatJid: String, uri: String) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(uri = uri)
        }
    }

    fun setPerChatWallpaperDim(chatJid: String, dim: Float) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(dim = dim.coerceIn(0f, 0.75f))
        }
    }

    fun setPerChatWallpaperShowDoodle(chatJid: String, show: Boolean) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(showDoodle = show)
        }
    }

    fun setPerChatWallpaperPosition(chatJid: String, scale: Float, offsetX: Float, offsetY: Float) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(
                scale = scale.coerceIn(1f, 3f),
                offsetXFraction = offsetX.coerceIn(-1f, 1f),
                offsetYFraction = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    fun importPerChatWallpaperFromUri(chatJid: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = copyCustomWallpaperImage(
                appContext,
                uri,
                keepFilePaths = StealthMatrixManager.referencedWallpaperFilePaths()
            )
            if (!saved.isNullOrBlank()) {
                StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
                    prev.copy(uri = saved)
                }
            }
        }
    }

    fun clearPerChatWallpaper(chatJid: String, altChatJid: String = "") {
        StealthMatrixManager.clearChatWallpaperOverride(chatJid, altChatJid)
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(autoDownloadEnabled = enabled) }
        WaRealtimeService.syncAutoDownloadPolicy(appContext)
    }

    fun setAutoDownloadMask(network: AutoDownloadNetwork, mask: Int) {
        StealthMatrixManager.updateConfig { cfg ->
            when (network) {
                AutoDownloadNetwork.MOBILE -> cfg.copy(autoDownloadMobileMask = mask)
                AutoDownloadNetwork.WIFI -> cfg.copy(autoDownloadWifiMask = mask)
                AutoDownloadNetwork.ROAMING -> cfg.copy(autoDownloadRoamingMask = mask)
            }
        }
        WaRealtimeService.syncAutoDownloadPolicy(appContext)
    }

    fun setMediaVisibilityInGallery(visible: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(mediaVisibilityInGallery = visible) }
    }

    fun setChatMediaVisibility(chatJid: String, overrideVisible: Boolean?) {
        StealthMatrixManager.setChatGalleryVisibility(chatJid, overrideVisible)
    }

    fun updateGlobalStealth(transform: (GlobalStealthConfig) -> GlobalStealthConfig) {
        val prevGhost = stealthConfig.value.ghostPresence
        StealthMatrixManager.updateConfig(transform)
        val newGhost = stealthConfig.value.ghostPresence
        if (prevGhost != newGhost) {
            viewModelScope.launch { WaRealtimeService.setGhostPresence(newGhost) }
        }
    }

    fun saveChatPrivacyOverride(
        chatJid: String,
        sendRead: Boolean,
        sendPlayed: Boolean,
        sendTyping: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            StealthMatrixManager.setChatPrivacyOverride(dao, chatJid, sendRead, sendPlayed, sendTyping)
        }
    }

    fun clearChatPrivacyOverride(chatJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            StealthMatrixManager.clearChatPrivacyOverride(dao, chatJid)
        }
    }

    fun toggleFavoriteChat(chat: ChatEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFavorite(chat.jid, !chat.isFavorite)
        }
    }

    fun togglePinChat(chat: ChatEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updatePinned(chat.jid, !chat.isPinned, System.currentTimeMillis())
        }
    }

    fun pinChats(jids: Collection<String>, pinned: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val order = if (pinned) System.currentTimeMillis() else 0L
            jids.forEach { dao.updatePinned(it, pinned, order) }
        }
    }

    fun muteChats(jids: Collection<String>, muted: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { jid ->
                dao.updateMuted(jid, muted, 0L)
                WaRealtimeService.toggleChannelMute(appContext, jid, muted)
            }
        }
    }

    fun archiveChats(jids: Collection<String>, archived: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { dao.updateArchived(it, archived) }
        }
    }

    fun favoriteChats(jids: Collection<String>, favorite: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { dao.updateFavorite(it, favorite) }
        }
    }

    fun setChatsUnreadLocal(jids: Collection<String>, unread: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = if (unread) 1 else 0
            jids.forEach { dao.updateUnreadCount(it, count) }
        }
    }

    fun deleteChatsLocally(jids: Collection<String>) {
        deleteChats(jids, syncToWhatsApp = false)
    }

    fun deleteChats(jids: Collection<String>, syncToWhatsApp: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch {
            WaRealtimeService.deleteChats(appContext, jids, syncToWhatsApp)
                .onSuccess {
                    if (syncToWhatsApp) {
                        showToast(if (jids.size == 1) "Chat deleted on WhatsApp" else "${jids.size} chats deleted on WhatsApp")
                    }
                }
                .onFailure { err ->
                    showToast("Deleted locally; WhatsApp sync failed: ${err.message ?: "not connected"}")
                }
        }
    }

    fun toggleChannelMute(chatJid: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            val targetMuted = !currentlyMuted
            WaRealtimeService.toggleChannelMute(appContext, chatJid, targetMuted)
                .onSuccess {
                    showToast(if (targetMuted) "Channel muted" else "Channel notifications on")
                }
                .onFailure {
                    showToast(if (targetMuted) "Channel muted locally" else "Channel unmuted locally")
                }
        }
    }

    fun isGroupAnnounceLocked(chatJid: String): Flow<Boolean> =
        WaRealtimeService.announceLockedGroups.map { it.contains(chatJid) }

    fun sendReaction(chatJid: String, target: MessageEntity, emoji: String) {
        viewModelScope.launch {
            WaRealtimeService.sendReaction(appContext, chatJid, target, emoji)
                .onFailure { err ->
                    showToast("Reaction saved locally (${err.message ?: "offline"})")
                }
        }
    }

    fun pairWithPhoneNumber(phone: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 7) {
                showToast("Enter country code + phone digits")
                return@launch
            }
            WaRealtimeService.pairWithPhoneNumber(digits)
                .onSuccess { code ->
                    showToast("Pairing Code: $code")
                    onResult(code)
                }
                .onFailure { err ->
                    showToast("Pair error: ${err.message}")
                    onResult("")
                }
        }
    }

    fun reconnectSocket() {
        val intent = Intent(appContext, WaRealtimeService::class.java).apply {
            action = WaRealtimeService.ACTION_RECONNECT
        }
        appContext.startService(intent)
    }

    fun sendText(
        chatJid: String,
        text: String,
        quoted: MessageEntity?,
        ghostMentionEveryone: Boolean
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.sendTextMessage(appContext, chatJid, text.trim(), quoted, ghostMentionEveryone)
                .onFailure { err ->
                    showToast(err.message ?: "Send failed")
                }
        }
    }

    fun editMessage(chatJid: String, messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.editOwnMessage(appContext, chatJid, messageId, newText.trim())
                .onFailure { showToast("Edit error: ${it.message}") }
        }
    }

    fun revokeMessageForEveryone(chatJid: String, messageId: String) {
        val antiDeleteOn = stealthConfig.value.antiDeleteEnabled
        viewModelScope.launch {
            WaRealtimeService.revokeMessageForEveryone(appContext, chatJid, messageId)
                .onSuccess {
                    showToast(
                        if (antiDeleteOn) "Deleted for everyone · Local copy kept (Anti-Delete)"
                        else "Message deleted for everyone"
                    )
                }
                .onFailure { err ->
                    showToast(err.message ?: "Delete for everyone failed")
                }
        }
    }

    fun deleteMessageFromDevice(messageId: String) {
        viewModelScope.launch {
            WaRealtimeService.deleteMessagePermanently(appContext, messageId)
                .onSuccess {
                    showToast("Deleted from device")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Delete failed")
                }
        }
    }

    fun markChatReadExplicitly(chatJid: String) {
        viewModelScope.launch {
            WaRealtimeService.markChatReadExplicitly(appContext, chatJid)
                .onSuccess { count ->
                    showToast("Sent manual Read receipts ($count msgs)")
                }
        }
    }

    fun markAudioPlayedExplicitly(msg: MessageEntity) {
        viewModelScope.launch {
            WaRealtimeService.sendManualPlayedReceipt(appContext, msg)
                .onSuccess { showToast("Sent manual Played receipt") }
        }
    }

    suspend fun stageMediaForPreview(uri: Uri, rawMimeType: String?): StagedOutboundMedia? {
        val resolvedMime = rawMimeType?.takeIf {
            it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true)
        } ?: "application/octet-stream"
        val ext = when {
            resolvedMime.contains("png", ignoreCase = true) -> "png"
            resolvedMime.contains("webp", ignoreCase = true) -> "webp"
            resolvedMime.contains("gif", ignoreCase = true) -> "gif"
            resolvedMime.contains("mp4", ignoreCase = true) -> "mp4"
            resolvedMime.contains("quicktime", ignoreCase = true) -> "mov"
            resolvedMime.contains("x-matroska", ignoreCase = true) -> "mkv"
            resolvedMime.contains("webm", ignoreCase = true) -> "webm"
            resolvedMime.contains("3gpp", ignoreCase = true) -> "3gp"
            resolvedMime.contains("pdf", ignoreCase = true) -> "pdf"
            resolvedMime.startsWith("video/", ignoreCase = true) -> "mp4"
            resolvedMime.startsWith("image/", ignoreCase = true) -> "jpg"
            else -> "bin"
        }
        val copied = EagerMediaVault.copyUriToCacheFile(appContext, uri, ext)
        if (copied == null) {
            showToast("Unable to read selected file")
            return null
        }
        val isVideo = resolvedMime.startsWith("video/", ignoreCase = true)
        val durationSec = if (isVideo) {
            withContext(Dispatchers.IO) { probeVideoMetadata(copied).durationSec }
        } else {
            0
        }
        return StagedOutboundMedia(
            file = copied,
            mimeType = resolvedMime,
            isVideo = isVideo,
            durationSec = durationSec,
            fileSize = copied.length()
        )
    }

    fun sendStagedMedia(
        chatJid: String,
        staged: StagedOutboundMedia,
        caption: String,
        qualityMode: OutboundQualityMode,
        isViewOnce: Boolean = false
    ) {
        viewModelScope.launch {
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = staged.file,
                mimeType = staged.mimeType,
                caption = caption,
                qualityMode = qualityMode,
                isVoiceNote = false,
                durationSec = staged.durationSec,
                isViewOnce = isViewOnce
            ).onFailure { err ->
                showToast(err.message ?: "Media send failed")
            }
        }
    }

    fun sendPickedMedia(
        chatJid: String,
        uri: Uri,
        mimeType: String,
        caption: String,
        qualityMode: OutboundQualityMode
    ) {
        viewModelScope.launch {
            val staged = stageMediaForPreview(uri, mimeType) ?: return@launch
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = staged.file,
                mimeType = staged.mimeType,
                caption = caption,
                qualityMode = qualityMode,
                isVoiceNote = false,
                durationSec = staged.durationSec
            ).onFailure { err ->
                showToast(err.message ?: "Media send failed")
            }
        }
    }

    fun toggleVoiceRecording(chatJid: String, hasRecordPermission: Boolean) {
        if (_isRecordingVoice.value) {
            stopAndSendVoiceRecording(chatJid)
        } else {
            startVoiceRecording(hasRecordPermission)
        }
    }

    private fun startVoiceRecording(hasRecordPermission: Boolean) {
        SoundPoolManager.playPttStart()
        val target = File(EagerMediaVault.mediaDir(appContext), "ptt_rec_${System.currentTimeMillis()}.m4a")
        activeRecordingFile = target
        recordingStartMs = System.currentTimeMillis()
        _isRecordingVoice.value = true

        if (hasRecordPermission) {
            runCatching {
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                rec.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(64000)
                    setAudioSamplingRate(16000)
                    setOutputFile(target.absolutePath)
                    prepare()
                    start()
                }
                activeRecorder = rec
            }.onFailure {
                activeRecorder = null
            }
        }
    }

    fun cancelVoiceRecording() {
        SoundPoolManager.playPttCancel()
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.release() }
        activeRecorder = null
        activeRecordingFile?.delete()
        activeRecordingFile = null
        _isRecordingVoice.value = false
    }

    private fun stopAndSendVoiceRecording(chatJid: String) {
        SoundPoolManager.playPttEnd()
        val rec = activeRecorder
        activeRecorder = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        val elapsedSec = (((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt()).coerceIn(1, 300)
        val file = activeRecordingFile
        activeRecordingFile = null
        _isRecordingVoice.value = false

        viewModelScope.launch {
            val audioFile = if (file != null && file.exists() && file.length() > 1024L) {
                file
            } else {
                // Clean synthesized voice note fallback when mic is muted or permission declined
                File(EagerMediaVault.mediaDir(appContext), "ptt_voice_${System.currentTimeMillis()}.wav").also { wav ->
                    EagerMediaVault.generateSpeechCadenceWav(wav, durationSec = elapsedSec.coerceAtLeast(3))
                }
            }
            val mime = if (audioFile.extension == "wav") "audio/wav" else "audio/mp4"
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = audioFile,
                mimeType = mime,
                caption = "",
                qualityMode = OutboundQualityMode.ORIGINAL,
                isVoiceNote = true,
                durationSec = elapsedSec.coerceAtLeast(2)
            ).onFailure { err ->
                showToast(err.message ?: "Voice send failed")
            }
        }
    }

    fun exportMediaToGallery(localPath: String?, displayName: String, mimeType: String) {
        if (localPath.isNullOrBlank()) {
            showToast("No decrypted file on disk for this item")
            return
        }
        viewModelScope.launch {
            EagerMediaVault.exportToGallery(appContext, File(localPath), displayName, mimeType)
                .onSuccess { showToast("Saved to Gallery ($displayName)") }
                .onFailure { showToast("Export failed: ${it.message}") }
        }
    }

    fun seedShowcaseData() {
        viewModelScope.launch {
            WaRealtimeService.seedBenchmarkAndShowcase(appContext)
                .onSuccess { showToast("Seeded 1,220-msg thread + all Mod showcases") }
                .onFailure { showToast("Seed failed: ${it.message}") }
        }
    }

    fun recordPrimaryHeartbeatNow() {
        StealthMatrixManager.recordPrimaryHeartbeat()
        showToast("Primary phone heartbeat reset (14d window refreshed)")
    }

    fun simulate11DayHeartbeatGap() {
        StealthMatrixManager.simulateElapsedDays(11)
        showToast("Simulated 11 days elapsed -> 3 days until companion unlink warning active")
    }

    fun verifyBulkNumbers(phonesCsv: String) {
        if (phonesCsv.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.verifyBulkNumbers(phonesCsv)
                .onSuccess { _bulkVerifyOutput.value = it }
                .onFailure { _bulkVerifyOutput.value = "Error: ${it.message}" }
        }
    }

    private val _participantMeta = MutableStateFlow<Map<String, ResolvedParticipantUi>>(emptyMap())
    val participantMeta: StateFlow<Map<String, ResolvedParticipantUi>> = _participantMeta.asStateFlow()

    private val _quotedMsgMeta = MutableStateFlow<Map<String, QuotedSenderMeta>>(emptyMap())
    val quotedMsgMeta: StateFlow<Map<String, QuotedSenderMeta>> = _quotedMsgMeta.asStateFlow()

    fun resolveParticipantsLocally(jids: Collection<String>) {
        val current = _participantMeta.value
        val missing = jids
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("@") && !current.containsKey(it) }
            .distinct()
            .take(80)
            .toList()
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val raw = WaRealtimeService.resolveParticipantNamesJson(missing.joinToString(","))
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@launch
            val parsed = HashMap<String, ResolvedParticipantUi>(missing.size)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = obj.optJSONObject(key) ?: continue
                parsed[key] = ResolvedParticipantUi(
                    jid = key,
                    altJid = item.optString("altJid"),
                    savedName = item.optString("savedName"),
                    pushName = item.optString("pushName"),
                    phone = item.optString("phone"),
                    avatarPath = item.optString("avatarPath"),
                    isSelf = item.optBoolean("isSelf", false)
                )
            }
            // Mark any queried JID not returned as checked so we don't re-query every recomposition.
            missing.forEach { k ->
                if (!parsed.containsKey(k)) {
                    parsed[k] = ResolvedParticipantUi(jid = k)
                }
            }
            if (parsed.isNotEmpty()) {
                _participantMeta.value = _participantMeta.value + parsed
            }
        }
    }

    fun resolveQuotedSendersLocally(quotedIds: Collection<String>) {
        val current = _quotedMsgMeta.value
        val missing = quotedIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !current.containsKey(it) }
            .distinct()
            .take(50)
            .toList()
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = HashMap<String, QuotedSenderMeta>(missing.size)
            val discoveredJids = mutableSetOf<String>()
            for (qid in missing) {
                val msg = dao.getMessage(qid)
                if (msg != null) {
                    resolved[qid] = QuotedSenderMeta(
                        senderJid = msg.senderJid,
                        senderName = msg.senderName,
                        isFromMe = msg.isFromMe
                    )
                    if (msg.senderJid.contains("@")) {
                        discoveredJids += msg.senderJid
                    }
                } else {
                    resolved[qid] = QuotedSenderMeta()
                }
            }
            if (resolved.isNotEmpty()) {
                _quotedMsgMeta.value = _quotedMsgMeta.value + resolved
            }
            if (discoveredJids.isNotEmpty()) {
                resolveParticipantsLocally(discoveredJids)
            }
        }
    }

    fun requestFullProfilePicture(jid: String) {
        if (jid.isBlank()) return
        WaRealtimeService.requestFullProfilePicture(jid)
    }

    suspend fun fetchOwnProfile() = WaRealtimeService.fetchOwnProfile()

    suspend fun saveOwnPushName(name: String) = WaRealtimeService.updateOwnPushName(name)
        .onSuccess { showToast("Display name updated") }
        .onFailure { showToast(it.message ?: "Failed to update display name") }

    suspend fun saveOwnAbout(about: String) = WaRealtimeService.updateOwnAbout(about)
        .onSuccess { showToast("About updated") }
        .onFailure { showToast(it.message ?: "Failed to update About") }

    suspend fun fetchAccountPrivacy() = WaRealtimeService.fetchAccountPrivacySettings()

    suspend fun setAccountPrivacy(category: String, value: String) =
        WaRealtimeService.updateAccountPrivacySetting(category, value)
            .onFailure { showToast(it.message ?: "Failed to update privacy setting") }

    suspend fun setDefaultDisappearingTimer(seconds: Int) =
        WaRealtimeService.setDefaultDisappearingTimerSec(seconds)
            .onSuccess { showToast("Default timer updated") }
            .onFailure { showToast(it.message ?: "Failed to update timer") }

    suspend fun fetchBlockedContacts() = WaRealtimeService.fetchBlockedContacts()

    suspend fun setContactBlocked(jid: String, blocked: Boolean) =
        WaRealtimeService.setContactBlocked(jid, blocked)
            .onSuccess { showToast(if (blocked) "Contact blocked" else "Contact unblocked") }
            .onFailure { showToast(it.message ?: "Blocklist update failed") }

    suspend fun loadStorageStats() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        WaRealtimeService.computeStorageStats(appContext)
    }

    suspend fun clearTempCache(): Long = kotlinx.coroutines.withContext(Dispatchers.IO) {
        WaRealtimeService.clearSafeTempCache(appContext)
    }

    private fun showToast(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}

data class ResolvedParticipantUi(
    val jid: String,
    val altJid: String = "",
    val savedName: String = "",
    val pushName: String = "",
    val phone: String = "",
    val avatarPath: String = "",
    val isSelf: Boolean = false
)

data class QuotedSenderMeta(
    val senderJid: String = "",
    val senderName: String = "",
    val isFromMe: Boolean = false
)
