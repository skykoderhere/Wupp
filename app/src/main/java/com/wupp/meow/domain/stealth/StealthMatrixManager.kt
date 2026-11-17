package com.wupp.meow.domain.stealth

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wupp.meow.data.db.PrivacyRuleEntity
import com.wupp.meow.data.db.WaDao
import com.wupp.meow.ui.theme.LocaleHelper
import com.wupp.meow.ui.theme.WuppAccentColor
import com.wupp.meow.ui.theme.WuppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.text.BreakIterator
import java.util.concurrent.TimeUnit
import kotlin.math.max

enum class AutoDownloadNetwork(val label: String) {
    MOBILE("When using mobile data"),
    WIFI("When connected on Wi-Fi"),
    ROAMING("When roaming")
}

data class ChatWallpaperOverride(
    val uri: String = "",
    val dim: Float = 0.28f,
    val showDoodle: Boolean = true,
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f
)

data class GlobalStealthConfig(
    val ghostPresence: Boolean = true,
    val hideReadReceipts: Boolean = false,
    val hidePlayedReceipts: Boolean = true,
    val hideTypingRecording: Boolean = true,
    val antiDeleteEnabled: Boolean = true,
    val viewOnceVaultEnabled: Boolean = true,
    val disappearingImmunity: Boolean = true,
    val themeMode: WuppThemeMode = WuppThemeMode.DARK,
    val accentColor: WuppAccentColor = WuppAccentColor.DEFAULT,
    val useSystemFont: Boolean = false,
    val quickReactionEmojis: List<String> = StealthMatrixManager.DEFAULT_QUICK_REACTIONS,
    val classicWhatsAppLook: Boolean = false,
    val statusVerticalLayout: Boolean = false,
    val alwaysShowContactBio: Boolean = true,
    val compactChatRows: Boolean = false,
    val showSecondsInTimestamps: Boolean = false,
    val hideHomeCameraShortcut: Boolean = false,
    val hideStealthStatusBadges: Boolean = false,
    val separateGroupsAndChats: Boolean = false,
    val hideChannels: Boolean = false,
    val enterIsSend: Boolean = false,
    val chatFontScale: Float = 1.0f,
    val notifyDmEnabled: Boolean = true,
    val notifyGroupEnabled: Boolean = true,
    val notifyShowPreview: Boolean = true,
    val notifyVibrateEnabled: Boolean = true,
    val defaultDisappearingSec: Int = 0,
    val appLanguage: String = LocaleHelper.LANG_SYSTEM,
    val chatWallpaperUri: String = "",
    val chatWallpaperDim: Float = 0.28f,
    val chatWallpaperShowDoodle: Boolean = true,
    val chatWallpaperScale: Float = 1f,
    val chatWallpaperOffsetX: Float = 0f,
    val chatWallpaperOffsetY: Float = 0f,
    val perChatWallpapers: Map<String, ChatWallpaperOverride> = emptyMap(),
    val autoDownloadEnabled: Boolean = true,
    val autoDownloadMobileMask: Int = StealthMatrixManager.MEDIA_ALL,
    val autoDownloadWifiMask: Int = StealthMatrixManager.MEDIA_ALL,
    val autoDownloadRoamingMask: Int = StealthMatrixManager.MEDIA_NONE,
    val mediaVisibilityInGallery: Boolean = true,
    val hiddenGalleryJids: Set<String> = emptySet(),
    val visibleGalleryJids: Set<String> = emptySet(),
    val primaryLastSeenEpochMs: Long = System.currentTimeMillis(),
    val hapticFeedbackEnabled: Boolean = true,
    val currentMood: String = ""
)

data class EffectiveChatPrivacy(
    val sendReadReceipts: Boolean,
    val sendPlayedReceipts: Boolean,
    val sendChatPresence: Boolean,
    val hasCustomOverride: Boolean
)

data class PrimaryHeartbeatStatus(
    val lastPrimarySeenMs: Long,
    val elapsedDays: Int,
    val remainingDays: Int,
    val remainingHours: Long,
    val isWarningActive: Boolean,
    val isExpiredRisk: Boolean
)

/**
 * Manages Global & per-chat Ghost/Stealth rules, auto-download & gallery visibility rules,
 * Error 463 unsaved-number outbound guard, and Meta's 14-day primary phone companion heartbeat reminder.
 */
object StealthMatrixManager {
    const val MEDIA_NONE = 0
    const val MEDIA_PHOTO = 1
    const val MEDIA_AUDIO = 2
    const val MEDIA_VIDEO = 4
    const val MEDIA_DOCUMENT = 8
    const val MEDIA_ALL = MEDIA_PHOTO or MEDIA_AUDIO or MEDIA_VIDEO or MEDIA_DOCUMENT

    private const val PREFS_NAME = "wa_stealth_prefs"
    private const val KEY_GHOST_PRESENCE = "ghost_presence"
    private const val KEY_HIDE_READ = "hide_read_receipts"
    private const val KEY_HIDE_PLAYED = "hide_played_receipts"
    private const val KEY_HIDE_TYPING = "hide_typing_recording"
    private const val KEY_ANTI_DELETE = "anti_delete_enabled"
    private const val KEY_VIEW_ONCE_VAULT = "view_once_vault_enabled"
    private const val KEY_DISAPPEARING_IMMUNITY = "disappearing_immunity"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_ACCENT_COLOR = "app_accent_color"
    private const val KEY_CHAT_WALLPAPER_URI = "chat_wallpaper_uri"
    private const val KEY_CHAT_WALLPAPER_DIM = "chat_wallpaper_dim"
    private const val KEY_CHAT_WALLPAPER_DOODLE = "chat_wallpaper_doodle"
    private const val KEY_CHAT_WALLPAPER_SCALE = "chat_wallpaper_scale"
    private const val KEY_CHAT_WALLPAPER_OFFSET_X = "chat_wallpaper_offset_x"
    private const val KEY_CHAT_WALLPAPER_OFFSET_Y = "chat_wallpaper_offset_y"
    private const val KEY_CHAT_WALLPAPER_OVERRIDES = "chat_wallpaper_overrides_json"
    private const val KEY_USE_SYSTEM_FONT = "use_system_font"
    private const val KEY_QUICK_REACTIONS = "quick_reaction_emojis_json"
    private const val KEY_CLASSIC_LOOK = "classic_whatsapp_look"
    private const val KEY_STATUS_VERTICAL = "status_vertical_layout"
    private const val KEY_ALWAYS_SHOW_BIO = "always_show_contact_bio"
    private const val KEY_COMPACT_CHAT_ROWS = "compact_chat_rows"
    private const val KEY_SHOW_SECONDS_TIME = "show_seconds_timestamps"
    private const val KEY_HIDE_HOME_CAMERA = "hide_home_camera_shortcut"
    private const val KEY_HIDE_STEALTH_BADGES = "hide_stealth_status_badges"
    private const val KEY_SEPARATE_GROUPS_CHATS = "separate_groups_chats"
    private const val KEY_HIDE_CHANNELS = "hide_channels"
    private const val KEY_ENTER_IS_SEND = "enter_is_send"
    private const val KEY_CHAT_FONT_SCALE = "chat_font_scale"
    private const val KEY_NOTIFY_DM = "notify_dm_enabled"
    private const val KEY_NOTIFY_GROUP = "notify_group_enabled"
    private const val KEY_NOTIFY_PREVIEW = "notify_show_preview"
    private const val KEY_NOTIFY_VIBRATE = "notify_vibrate_enabled"
    private const val KEY_DEFAULT_DISAPPEARING_SEC = "default_disappearing_sec"
    private const val KEY_APP_LANGUAGE = "app_language"

    val DEFAULT_QUICK_REACTIONS: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "😊")
    private const val KEY_AUTO_DL_ENABLED = "auto_download_enabled"
    private const val KEY_AUTO_DL_MOBILE = "auto_download_mobile_mask"
    private const val KEY_AUTO_DL_WIFI = "auto_download_wifi_mask"
    private const val KEY_AUTO_DL_ROAMING = "auto_download_roaming_mask"
    private const val KEY_MEDIA_VISIBILITY = "media_visibility_gallery"
    private const val KEY_HIDDEN_GALLERY_JIDS = "hidden_gallery_jids"
    private const val KEY_VISIBLE_GALLERY_JIDS = "visible_gallery_jids"
    private const val KEY_PRIMARY_HEARTBEAT_MS = "primary_last_seen_ms"
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
    private const val KEY_CURRENT_MOOD = "profile_current_mood"

    private const val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
    private const val WARNING_THRESHOLD_DAYS = 10

    private lateinit var prefs: SharedPreferences
    private val _config = MutableStateFlow(GlobalStealthConfig())
    val config: StateFlow<GlobalStealthConfig> = _config.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!prefs.contains(KEY_PRIMARY_HEARTBEAT_MS)) {
                prefs.edit().putLong(KEY_PRIMARY_HEARTBEAT_MS, now).apply()
            }
            _config.value = GlobalStealthConfig(
                ghostPresence = prefs.getBoolean(KEY_GHOST_PRESENCE, true),
                hideReadReceipts = prefs.getBoolean(KEY_HIDE_READ, false),
                hidePlayedReceipts = prefs.getBoolean(KEY_HIDE_PLAYED, true),
                hideTypingRecording = prefs.getBoolean(KEY_HIDE_TYPING, true),
                antiDeleteEnabled = prefs.getBoolean(KEY_ANTI_DELETE, true),
                viewOnceVaultEnabled = prefs.getBoolean(KEY_VIEW_ONCE_VAULT, true),
                disappearingImmunity = prefs.getBoolean(KEY_DISAPPEARING_IMMUNITY, true),
                themeMode = WuppThemeMode.fromStorageKey(
                    prefs.getString(KEY_THEME_MODE, null),
                    prefs.getBoolean(KEY_DARK_THEME, true)
                ),
                accentColor = WuppAccentColor.fromStorageKey(prefs.getString(KEY_ACCENT_COLOR, null)),
                useSystemFont = prefs.getBoolean(KEY_USE_SYSTEM_FONT, false),
                quickReactionEmojis = parseQuickReactions(prefs.getString(KEY_QUICK_REACTIONS, null)),
                classicWhatsAppLook = prefs.getBoolean(KEY_CLASSIC_LOOK, false),
                statusVerticalLayout = prefs.getBoolean(KEY_STATUS_VERTICAL, false),
                alwaysShowContactBio = prefs.getBoolean(KEY_ALWAYS_SHOW_BIO, true),
                compactChatRows = prefs.getBoolean(KEY_COMPACT_CHAT_ROWS, false),
                showSecondsInTimestamps = prefs.getBoolean(KEY_SHOW_SECONDS_TIME, false),
                hideHomeCameraShortcut = prefs.getBoolean(KEY_HIDE_HOME_CAMERA, false),
                hideStealthStatusBadges = prefs.getBoolean(KEY_HIDE_STEALTH_BADGES, false),
                separateGroupsAndChats = prefs.getBoolean(KEY_SEPARATE_GROUPS_CHATS, false),
                hideChannels = prefs.getBoolean(KEY_HIDE_CHANNELS, false),
                enterIsSend = prefs.getBoolean(KEY_ENTER_IS_SEND, false),
                chatFontScale = prefs.getFloat(KEY_CHAT_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.25f),
                notifyDmEnabled = prefs.getBoolean(KEY_NOTIFY_DM, true),
                notifyGroupEnabled = prefs.getBoolean(KEY_NOTIFY_GROUP, true),
                notifyShowPreview = prefs.getBoolean(KEY_NOTIFY_PREVIEW, true),
                notifyVibrateEnabled = prefs.getBoolean(KEY_NOTIFY_VIBRATE, true),
                defaultDisappearingSec = prefs.getInt(KEY_DEFAULT_DISAPPEARING_SEC, 0).coerceAtLeast(0),
                appLanguage = prefs.getString(KEY_APP_LANGUAGE, LocaleHelper.LANG_SYSTEM) ?: LocaleHelper.LANG_SYSTEM,
                chatWallpaperUri = prefs.getString(KEY_CHAT_WALLPAPER_URI, "") ?: "",
                chatWallpaperDim = prefs.getFloat(KEY_CHAT_WALLPAPER_DIM, 0.28f),
                chatWallpaperShowDoodle = prefs.getBoolean(KEY_CHAT_WALLPAPER_DOODLE, true),
                chatWallpaperScale = prefs.getFloat(KEY_CHAT_WALLPAPER_SCALE, 1f).coerceIn(1f, 3f),
                chatWallpaperOffsetX = prefs.getFloat(KEY_CHAT_WALLPAPER_OFFSET_X, 0f).coerceIn(-1f, 1f),
                chatWallpaperOffsetY = prefs.getFloat(KEY_CHAT_WALLPAPER_OFFSET_Y, 0f).coerceIn(-1f, 1f),
                perChatWallpapers = parsePerChatWallpapers(prefs.getString(KEY_CHAT_WALLPAPER_OVERRIDES, null)),
                autoDownloadEnabled = prefs.getBoolean(KEY_AUTO_DL_ENABLED, true),
                autoDownloadMobileMask = prefs.getInt(KEY_AUTO_DL_MOBILE, MEDIA_ALL),
                autoDownloadWifiMask = prefs.getInt(KEY_AUTO_DL_WIFI, MEDIA_ALL),
                autoDownloadRoamingMask = prefs.getInt(KEY_AUTO_DL_ROAMING, MEDIA_NONE),
                mediaVisibilityInGallery = prefs.getBoolean(KEY_MEDIA_VISIBILITY, true),
                hiddenGalleryJids = prefs.getStringSet(KEY_HIDDEN_GALLERY_JIDS, emptySet())?.toSet() ?: emptySet(),
                visibleGalleryJids = prefs.getStringSet(KEY_VISIBLE_GALLERY_JIDS, emptySet())?.toSet() ?: emptySet(),
                primaryLastSeenEpochMs = prefs.getLong(KEY_PRIMARY_HEARTBEAT_MS, now),
                hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
                currentMood = prefs.getString(KEY_CURRENT_MOOD, "") ?: ""
            )
            initialized = true
        }
    }

    fun updateConfig(transform: (GlobalStealthConfig) -> GlobalStealthConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        if (::prefs.isInitialized) {
            prefs.edit()
                .putBoolean(KEY_GHOST_PRESENCE, updated.ghostPresence)
                .putBoolean(KEY_HIDE_READ, updated.hideReadReceipts)
                .putBoolean(KEY_HIDE_PLAYED, updated.hidePlayedReceipts)
                .putBoolean(KEY_HIDE_TYPING, updated.hideTypingRecording)
                .putBoolean(KEY_ANTI_DELETE, updated.antiDeleteEnabled)
                .putBoolean(KEY_VIEW_ONCE_VAULT, updated.viewOnceVaultEnabled)
                .putBoolean(KEY_DISAPPEARING_IMMUNITY, updated.disappearingImmunity)
                .putString(KEY_THEME_MODE, updated.themeMode.storageKey)
                .putString(KEY_ACCENT_COLOR, updated.accentColor.storageKey)
                .putBoolean(KEY_DARK_THEME, updated.themeMode != WuppThemeMode.LIGHT)
                .putBoolean(KEY_USE_SYSTEM_FONT, updated.useSystemFont)
                .putString(KEY_QUICK_REACTIONS, encodeQuickReactions(updated.quickReactionEmojis))
                .putBoolean(KEY_CLASSIC_LOOK, updated.classicWhatsAppLook)
                .putBoolean(KEY_STATUS_VERTICAL, updated.statusVerticalLayout)
                .putBoolean(KEY_ALWAYS_SHOW_BIO, updated.alwaysShowContactBio)
                .putBoolean(KEY_COMPACT_CHAT_ROWS, updated.compactChatRows)
                .putBoolean(KEY_SHOW_SECONDS_TIME, updated.showSecondsInTimestamps)
                .putBoolean(KEY_HIDE_HOME_CAMERA, updated.hideHomeCameraShortcut)
                .putBoolean(KEY_HIDE_STEALTH_BADGES, updated.hideStealthStatusBadges)
                .putBoolean(KEY_SEPARATE_GROUPS_CHATS, updated.separateGroupsAndChats)
                .putBoolean(KEY_HIDE_CHANNELS, updated.hideChannels)
                .putBoolean(KEY_ENTER_IS_SEND, updated.enterIsSend)
                .putFloat(KEY_CHAT_FONT_SCALE, updated.chatFontScale.coerceIn(0.85f, 1.25f))
                .putBoolean(KEY_NOTIFY_DM, updated.notifyDmEnabled)
                .putBoolean(KEY_NOTIFY_GROUP, updated.notifyGroupEnabled)
                .putBoolean(KEY_NOTIFY_PREVIEW, updated.notifyShowPreview)
                .putBoolean(KEY_NOTIFY_VIBRATE, updated.notifyVibrateEnabled)
                .putInt(KEY_DEFAULT_DISAPPEARING_SEC, updated.defaultDisappearingSec.coerceAtLeast(0))
                .putString(KEY_APP_LANGUAGE, updated.appLanguage)
                .putString(KEY_CHAT_WALLPAPER_URI, updated.chatWallpaperUri)
                .putFloat(KEY_CHAT_WALLPAPER_DIM, updated.chatWallpaperDim)
                .putBoolean(KEY_CHAT_WALLPAPER_DOODLE, updated.chatWallpaperShowDoodle)
                .putFloat(KEY_CHAT_WALLPAPER_SCALE, updated.chatWallpaperScale)
                .putFloat(KEY_CHAT_WALLPAPER_OFFSET_X, updated.chatWallpaperOffsetX)
                .putFloat(KEY_CHAT_WALLPAPER_OFFSET_Y, updated.chatWallpaperOffsetY)
                .putString(KEY_CHAT_WALLPAPER_OVERRIDES, encodePerChatWallpapers(updated.perChatWallpapers))
                .putBoolean(KEY_AUTO_DL_ENABLED, updated.autoDownloadEnabled)
                .putInt(KEY_AUTO_DL_MOBILE, updated.autoDownloadMobileMask)
                .putInt(KEY_AUTO_DL_WIFI, updated.autoDownloadWifiMask)
                .putInt(KEY_AUTO_DL_ROAMING, updated.autoDownloadRoamingMask)
                .putBoolean(KEY_MEDIA_VISIBILITY, updated.mediaVisibilityInGallery)
                .putStringSet(KEY_HIDDEN_GALLERY_JIDS, updated.hiddenGalleryJids.toSet())
                .putStringSet(KEY_VISIBLE_GALLERY_JIDS, updated.visibleGalleryJids.toSet())
                .putLong(KEY_PRIMARY_HEARTBEAT_MS, updated.primaryLastSeenEpochMs)
                .putBoolean(KEY_HAPTIC_FEEDBACK, updated.hapticFeedbackEnabled)
                .putString(KEY_CURRENT_MOOD, updated.currentMood)
                .apply()
        }
    }

    fun currentAutoDownloadNetwork(context: Context): AutoDownloadNetwork {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return AutoDownloadNetwork.WIFI
        val active = cm.activeNetwork ?: return AutoDownloadNetwork.WIFI
        val caps = cm.getNetworkCapabilities(active) ?: return AutoDownloadNetwork.WIFI
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return AutoDownloadNetwork.WIFI
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                AutoDownloadNetwork.ROAMING
            } else {
                AutoDownloadNetwork.MOBILE
            }
        }
        return AutoDownloadNetwork.WIFI
    }

    fun activeAutoDownloadMask(context: Context, cfg: GlobalStealthConfig = _config.value): Int {
        if (!cfg.autoDownloadEnabled) return MEDIA_NONE
        return when (currentAutoDownloadNetwork(context)) {
            AutoDownloadNetwork.WIFI -> cfg.autoDownloadWifiMask
            AutoDownloadNetwork.MOBILE -> cfg.autoDownloadMobileMask
            AutoDownloadNetwork.ROAMING -> cfg.autoDownloadRoamingMask
        }
    }

    fun isAutoDownloadAllowed(
        context: Context,
        msgType: String,
        mimeType: String = "",
        cfg: GlobalStealthConfig = _config.value
    ): Boolean {
        val mask = activeAutoDownloadMask(context, cfg)
        if (mask == MEDIA_NONE) return false
        val upperType = msgType.uppercase()
        val bit = when {
            upperType == "PTT" || upperType == "AUDIO" || mimeType.startsWith("audio/", ignoreCase = true) -> MEDIA_AUDIO
            upperType == "VIDEO" || mimeType.startsWith("video/", ignoreCase = true) -> MEDIA_VIDEO
            upperType == "DOCUMENT" -> MEDIA_DOCUMENT
            else -> MEDIA_PHOTO
        }
        return (mask and bit) != 0
    }

    fun formatAutoDownloadSummary(mask: Int): String {
        if (mask == MEDIA_NONE) return "No media"
        if ((mask and MEDIA_ALL) == MEDIA_ALL) return "All media"
        val parts = mutableListOf<String>()
        if ((mask and MEDIA_PHOTO) != 0) parts += "Photos"
        if ((mask and MEDIA_AUDIO) != 0) parts += "Audio"
        if ((mask and MEDIA_VIDEO) != 0) parts += "Videos"
        if ((mask and MEDIA_DOCUMENT) != 0) parts += "Documents"
        return if (parts.isEmpty()) "No media" else parts.joinToString(", ")
    }

    fun getChatGalleryOverride(chatJid: String, cfg: GlobalStealthConfig = _config.value): Boolean? = when (chatJid) {
        in cfg.hiddenGalleryJids -> false
        in cfg.visibleGalleryJids -> true
        else -> null
    }

    fun setChatGalleryVisibility(chatJid: String, visibleOverride: Boolean?) {
        if (chatJid.isBlank()) return
        updateConfig { cfg ->
            val hidden = cfg.hiddenGalleryJids.toMutableSet()
            val visible = cfg.visibleGalleryJids.toMutableSet()
            when (visibleOverride) {
                true -> {
                    hidden.remove(chatJid)
                    visible.add(chatJid)
                }
                false -> {
                    visible.remove(chatJid)
                    hidden.add(chatJid)
                }
                null -> {
                    hidden.remove(chatJid)
                    visible.remove(chatJid)
                }
            }
            cfg.copy(hiddenGalleryJids = hidden, visibleGalleryJids = visible)
        }
    }

    fun shouldShowChatMediaInGallery(
        chatJid: String,
        altChatJid: String = "",
        cfg: GlobalStealthConfig = _config.value
    ): Boolean {
        val override = getChatGalleryOverride(chatJid, cfg)
            ?: altChatJid.takeIf { it.isNotBlank() }?.let { getChatGalleryOverride(it, cfg) }
        return override ?: cfg.mediaVisibilityInGallery
    }

    fun recordPrimaryHeartbeat(timestampMs: Long = System.currentTimeMillis()) {
        updateConfig { it.copy(primaryLastSeenEpochMs = timestampMs) }
    }

    fun simulateElapsedDays(days: Int) {
        val offsetMs = TimeUnit.DAYS.toMillis(days.toLong())
        updateConfig { it.copy(primaryLastSeenEpochMs = System.currentTimeMillis() - offsetMs) }
    }

    fun computeHeartbeatStatus(cfg: GlobalStealthConfig = _config.value): PrimaryHeartbeatStatus {
        val now = System.currentTimeMillis()
        val elapsedMs = max(0L, now - cfg.primaryLastSeenEpochMs)
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(elapsedMs).toInt()
        val remainingMs = max(0L, FOURTEEN_DAYS_MS - elapsedMs)
        val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs).toInt()
        val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        return PrimaryHeartbeatStatus(
            lastPrimarySeenMs = cfg.primaryLastSeenEpochMs,
            elapsedDays = elapsedDays,
            remainingDays = remainingDays,
            remainingHours = remainingHours,
            isWarningActive = elapsedDays >= WARNING_THRESHOLD_DAYS,
            isExpiredRisk = elapsedMs >= FOURTEEN_DAYS_MS
        )
    }

    fun observeEffectivePrivacy(dao: WaDao, chatJid: String): Flow<EffectiveChatPrivacy> {
        return combine(config, dao.observePrivacyRule(chatJid)) { cfg, rule ->
            resolvePrivacy(cfg, rule)
        }
    }

    suspend fun getEffectivePrivacy(dao: WaDao, chatJid: String): EffectiveChatPrivacy {
        val rule = dao.getPrivacyRuleOnce(chatJid)
        return resolvePrivacy(_config.value, rule)
    }

    suspend fun setChatPrivacyOverride(
        dao: WaDao,
        chatJid: String,
        sendReadReceipts: Boolean,
        sendPlayedReceipts: Boolean,
        sendChatPresence: Boolean
    ) {
        dao.upsertPrivacyRule(
            PrivacyRuleEntity(
                jid = chatJid,
                sendReadReceipts = sendReadReceipts,
                sendPlayedReceipts = sendPlayedReceipts,
                sendChatPresence = sendChatPresence
            )
        )
    }

    suspend fun clearChatPrivacyOverride(dao: WaDao, chatJid: String) {
        dao.deletePrivacyRule(chatJid)
    }

    fun hasChatWallpaperOverride(
        chatJid: String,
        altChatJid: String = "",
        cfg: GlobalStealthConfig = _config.value
    ): Boolean {
        if (chatJid.isNotBlank() && cfg.perChatWallpapers.containsKey(chatJid)) return true
        return altChatJid.isNotBlank() && cfg.perChatWallpapers.containsKey(altChatJid)
    }

    fun resolveChatWallpaper(
        chatJid: String,
        altChatJid: String = "",
        cfg: GlobalStealthConfig = _config.value
    ): ChatWallpaperOverride {
        val direct = cfg.perChatWallpapers[chatJid]
            ?: altChatJid.takeIf { it.isNotBlank() }?.let { cfg.perChatWallpapers[it] }
        if (direct != null) return direct
        return ChatWallpaperOverride(
            uri = cfg.chatWallpaperUri,
            dim = cfg.chatWallpaperDim,
            showDoodle = cfg.chatWallpaperShowDoodle,
            scale = cfg.chatWallpaperScale,
            offsetXFraction = cfg.chatWallpaperOffsetX,
            offsetYFraction = cfg.chatWallpaperOffsetY
        )
    }

    fun setChatWallpaperOverride(
        chatJid: String,
        transform: (ChatWallpaperOverride) -> ChatWallpaperOverride
    ) {
        if (chatJid.isBlank()) return
        updateConfig { cfg ->
            val current = cfg.perChatWallpapers[chatJid] ?: ChatWallpaperOverride(
                uri = cfg.chatWallpaperUri,
                dim = cfg.chatWallpaperDim,
                showDoodle = cfg.chatWallpaperShowDoodle,
                scale = cfg.chatWallpaperScale,
                offsetXFraction = cfg.chatWallpaperOffsetX,
                offsetYFraction = cfg.chatWallpaperOffsetY
            )
            val updated = transform(current)
            val nextMap = cfg.perChatWallpapers.toMutableMap()
            nextMap[chatJid] = updated
            cfg.copy(perChatWallpapers = nextMap)
        }
    }

    fun clearChatWallpaperOverride(chatJid: String, altChatJid: String = "") {
        updateConfig { cfg ->
            val nextMap = cfg.perChatWallpapers.toMutableMap()
            var changed = nextMap.remove(chatJid) != null
            if (altChatJid.isNotBlank() && nextMap.remove(altChatJid) != null) {
                changed = true
            }
            if (!changed) cfg else cfg.copy(perChatWallpapers = nextMap)
        }
    }

    fun referencedWallpaperFilePaths(cfg: GlobalStealthConfig = _config.value): Set<String> {
        val paths = mutableSetOf<String>()
        if (cfg.chatWallpaperUri.startsWith("file:")) {
            paths.add(cfg.chatWallpaperUri.removePrefix("file:"))
        }
        cfg.perChatWallpapers.values.forEach { entry ->
            if (entry.uri.startsWith("file:")) {
                paths.add(entry.uri.removePrefix("file:"))
            }
        }
        return paths
    }

    private fun parsePerChatWallpapers(rawJson: String?): Map<String, ChatWallpaperOverride> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(rawJson)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val jid = keys.next()
                    val item = obj.optJSONObject(jid) ?: continue
                    put(
                        jid,
                        ChatWallpaperOverride(
                            uri = item.optString("uri", ""),
                            dim = item.optDouble("dim", 0.28).toFloat().coerceIn(0f, 0.75f),
                            showDoodle = item.optBoolean("showDoodle", true),
                            scale = item.optDouble("scale", 1.0).toFloat().coerceIn(1f, 3f),
                            offsetXFraction = item.optDouble("offsetX", 0.0).toFloat().coerceIn(-1f, 1f),
                            offsetYFraction = item.optDouble("offsetY", 0.0).toFloat().coerceIn(-1f, 1f)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodePerChatWallpapers(map: Map<String, ChatWallpaperOverride>): String {
        if (map.isEmpty()) return ""
        val root = JSONObject()
        map.forEach { (jid, entry) ->
            root.put(
                jid,
                JSONObject()
                    .put("uri", entry.uri)
                    .put("dim", entry.dim.toDouble())
                    .put("showDoodle", entry.showDoodle)
                    .put("scale", entry.scale.toDouble())
                    .put("offsetX", entry.offsetXFraction.toDouble())
                    .put("offsetY", entry.offsetYFraction.toDouble())
            )
        }
        return root.toString()
    }

    fun extractFirstGrapheme(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(trimmed)
        val start = bi.first()
        val end = bi.next()
        return if (start != BreakIterator.DONE && end != BreakIterator.DONE) {
            trimmed.substring(start, end)
        } else {
            trimmed.take(2)
        }
    }

    fun sanitizeQuickReactions(raw: List<String>): List<String> {
        val clean = raw.mapNotNull { candidate ->
            extractFirstGrapheme(candidate).takeIf { it.isNotBlank() }
        }.distinct().take(7)
        return if (clean.isEmpty()) DEFAULT_QUICK_REACTIONS else clean
    }

    private fun parseQuickReactions(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return DEFAULT_QUICK_REACTIONS
        return runCatching {
            val arr = JSONArray(rawJson)
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i, "")
                    if (item.isNotBlank()) add(item)
                }
            }
            sanitizeQuickReactions(parsed)
        }.getOrDefault(DEFAULT_QUICK_REACTIONS)
    }

    private fun encodeQuickReactions(list: List<String>): String {
        val arr = JSONArray()
        sanitizeQuickReactions(list).forEach { arr.put(it) }
        return arr.toString()
    }

    private fun resolvePrivacy(
        cfg: GlobalStealthConfig,
        rule: PrivacyRuleEntity?
    ): EffectiveChatPrivacy {
        if (rule != null) {
            return EffectiveChatPrivacy(
                sendReadReceipts = rule.sendReadReceipts,
                sendPlayedReceipts = rule.sendPlayedReceipts,
                sendChatPresence = rule.sendChatPresence,
                hasCustomOverride = true
            )
        }
        return EffectiveChatPrivacy(
            sendReadReceipts = !cfg.hideReadReceipts,
            sendPlayedReceipts = !cfg.hidePlayedReceipts,
            sendChatPresence = !cfg.hideTypingRecording,
            hasCustomOverride = false
        )
    }

    /**
     * Error 463 Ban Guard: Blocks sending an initial companion message to an unsaved/unknown
     * 1-on-1 contact that has 0 prior messages in local Room history.
     */
    suspend fun canInitiateOutboundMessage(dao: WaDao, chatJid: String): Boolean {
        if (chatJid.endsWith("@g.us") || chatJid == "status@broadcast" || chatJid.endsWith("@newsletter")) {
            return true
        }
        return dao.countMessagesInChat(chatJid) > 0
    }
}
