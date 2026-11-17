package com.wupp.meow.domain.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

enum class AppLockMode(val storageKey: String) {
    OFF("off"),
    DEVICE("device"),
    CUSTOM_PIN("pin");

    companion object {
        fun fromStorageKey(key: String?): AppLockMode =
            entries.firstOrNull { it.storageKey == key } ?: OFF
    }
}

enum class AppLockTimeout(val storageKey: String, val timeoutMs: Long) {
    IMMEDIATE("immediate", 0L),
    ONE_MIN("1m", 60_000L),
    FIVE_MIN("5m", 300_000L),
    THIRTY_MIN("30m", 1_800_000L);

    companion object {
        fun fromStorageKey(key: String?): AppLockTimeout =
            entries.firstOrNull { it.storageKey == key } ?: IMMEDIATE
    }
}

data class AppLockConfig(
    val mode: AppLockMode = AppLockMode.OFF,
    val timeout: AppLockTimeout = AppLockTimeout.IMMEDIATE,
    val hideInRecents: Boolean = true,
    val hideLockedChatsBehindLock: Boolean = false,
    val hasCustomPin: Boolean = false
)

/**
 * Local-only App Lock controller (Zero WhatsApp flag risk).
 * Stores custom PINs as salted SHA-256 hashes and keeps background sync uninterrupted while locked.
 */
object AppLockManager {
    private const val PREFS_NAME = "wupp_applock_prefs"
    private const val KEY_MODE = "applock_mode"
    private const val KEY_TIMEOUT = "applock_timeout"
    private const val KEY_HIDE_RECENTS = "applock_hide_recents"
    private const val KEY_HIDE_LOCKED_CHATS = "applock_hide_locked_chats"
    private const val KEY_PIN_SALT = "applock_pin_salt"
    private const val KEY_PIN_HASH = "applock_pin_hash"

    private lateinit var prefs: SharedPreferences
    private val _config = MutableStateFlow(AppLockConfig())
    val config: StateFlow<AppLockConfig> = _config.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var lastBackgroundElapsedMs: Long = 0L

    @Volatile
    private var suppressNextBackgroundLock: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
                !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
            val savedMode = AppLockMode.fromStorageKey(prefs.getString(KEY_MODE, null))
            val effectiveMode = if (savedMode == AppLockMode.CUSTOM_PIN && !hasPin) {
                AppLockMode.OFF
            } else {
                savedMode
            }
            val loaded = AppLockConfig(
                mode = effectiveMode,
                timeout = AppLockTimeout.fromStorageKey(prefs.getString(KEY_TIMEOUT, null)),
                hideInRecents = prefs.getBoolean(KEY_HIDE_RECENTS, true),
                hideLockedChatsBehindLock = prefs.getBoolean(KEY_HIDE_LOCKED_CHATS, false),
                hasCustomPin = hasPin
            )
            _config.value = loaded
            _isLocked.value = loaded.mode != AppLockMode.OFF
            initialized = true
        }
    }

    fun isLockActive(cfg: AppLockConfig = _config.value): Boolean = cfg.mode != AppLockMode.OFF

    fun suspendNextBackgroundLock() {
        suppressNextBackgroundLock = true
    }

    fun onAppBackgrounded() {
        if (!initialized) return
        if (suppressNextBackgroundLock) {
            lastBackgroundElapsedMs = SystemClock.elapsedRealtime()
            return
        }
        if (isLockActive()) {
            lastBackgroundElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForegrounded() {
        if (!initialized) return
        if (suppressNextBackgroundLock) {
            suppressNextBackgroundLock = false
            return
        }
        val cfg = _config.value
        if (!isLockActive(cfg)) {
            _isLocked.value = false
            return
        }
        val bgAt = lastBackgroundElapsedMs
        if (bgAt <= 0L) return
        val elapsed = SystemClock.elapsedRealtime() - bgAt
        if (elapsed >= cfg.timeout.timeoutMs) {
            _isLocked.value = true
        }
    }

    fun lockNow() {
        if (isLockActive()) {
            _isLocked.value = true
        }
    }

    fun unlockAfterDeviceAuth() {
        lastBackgroundElapsedMs = 0L
        _isLocked.value = false
    }

    fun verifyCustomPin(candidatePin: String): Boolean {
        if (!initialized || candidatePin.length < 4) return false
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expectedHash = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actualHash = hashPin(salt, candidatePin)
        val matched = MessageDigest.isEqual(expectedHash, actualHash)
        if (matched) {
            lastBackgroundElapsedMs = 0L
            _isLocked.value = false
        }
        return matched
    }

    fun enableCustomPin(pin: String): Boolean {
        val clean = pin.filter { it.isDigit() }
        if (clean.length !in 4..8) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(salt, clean)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_MODE, AppLockMode.CUSTOM_PIN.storageKey)
            .apply()
        lastBackgroundElapsedMs = 0L
        _config.value = _config.value.copy(mode = AppLockMode.CUSTOM_PIN, hasCustomPin = true)
        _isLocked.value = false
        return true
    }

    fun enableDeviceLock() {
        prefs.edit()
            .putString(KEY_MODE, AppLockMode.DEVICE.storageKey)
            .apply()
        lastBackgroundElapsedMs = 0L
        _config.value = _config.value.copy(mode = AppLockMode.DEVICE)
        _isLocked.value = false
    }

    fun disableAppLock() {
        prefs.edit()
            .putString(KEY_MODE, AppLockMode.OFF.storageKey)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .apply()
        lastBackgroundElapsedMs = 0L
        _config.value = _config.value.copy(mode = AppLockMode.OFF, hasCustomPin = false)
        _isLocked.value = false
    }

    fun setTimeout(timeout: AppLockTimeout) {
        prefs.edit().putString(KEY_TIMEOUT, timeout.storageKey).apply()
        _config.value = _config.value.copy(timeout = timeout)
    }

    fun setHideInRecents(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_RECENTS, hide).apply()
        _config.value = _config.value.copy(hideInRecents = hide)
    }

    fun setHideLockedChatsBehindLock(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_LOCKED_CHATS, hide).apply()
        _config.value = _config.value.copy(hideLockedChatsBehindLock = hide)
    }

    fun applyWindowSecureFlag(activity: Activity, cfg: AppLockConfig = _config.value) {
        val shouldSecure = isLockActive(cfg) && cfg.hideInRecents
        if (shouldSecure) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun hashPin(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}
