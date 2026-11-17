package com.wupp.meow.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.wupp.meow.R
import com.wupp.meow.domain.security.AppLockConfig
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.domain.security.AppLockMode
import com.wupp.meow.domain.security.AppLockTimeout
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Triggers Android device authentication (biometric or device PIN/pattern/password) cleanly
 * across API 26–29 and API 30+, falling back to KeyguardManager credential confirmation when needed.
 */
fun launchDeviceAuthPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cancelText: String,
    keyguardFallbackLauncher: (android.content.Intent) -> Unit,
    onAuthenticated: () -> Unit,
    onErrorMessage: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricManager = BiometricManager.from(activity)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            onErrorMessage(errString.toString())
                        }
                    }
                }
            ).authenticate(promptInfo)
            return
        }
    } else {
        @Suppress("DEPRECATION")
        val canBiometric = biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        if (canBiometric) {
            @Suppress("DEPRECATION")
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDeviceCredentialAllowed(true)
                .build()
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            onErrorMessage(errString.toString())
                        }
                    }
                }
            ).authenticate(promptInfo)
            return
        }
    }

    val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    @Suppress("DEPRECATION")
    val intent = km?.createConfirmDeviceCredentialIntent(title, subtitle)
    if (intent != null) {
        AppLockManager.suspendNextBackgroundLock()
        keyguardFallbackLauncher(intent)
    } else {
        onErrorMessage(cancelText)
    }
}

@Composable
fun AppLockOverlay(
    config: AppLockConfig,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    val promptTitle = stringResource(R.string.applock_prompt_title)
    val promptSubtitle = stringResource(R.string.applock_prompt_subtitle)
    val noDeviceLockMsg = stringResource(R.string.applock_no_device_lock)
    val wrongPinMsg = stringResource(R.string.applock_wrong_pin)

    var pinDigits by remember(config.mode) { mutableStateOf("") }
    var errorText by remember(config.mode) { mutableStateOf<String?>(null) }

    val keyguardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppLockManager.unlockAfterDeviceAuth()
        }
    }

    fun triggerDeviceUnlock() {
        val host = activity ?: return
        errorText = null
        launchDeviceAuthPrompt(
            activity = host,
            title = promptTitle,
            subtitle = promptSubtitle,
            cancelText = noDeviceLockMsg,
            keyguardFallbackLauncher = { intent -> keyguardLauncher.launch(intent) },
            onAuthenticated = { AppLockManager.unlockAfterDeviceAuth() },
            onErrorMessage = { msg -> errorText = msg }
        )
    }

    LaunchedEffect(config.mode) {
        if (config.mode == AppLockMode.DEVICE) {
            triggerDeviceUnlock()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        color = colors.appSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen.copy(alpha = 0.16f))
                    .border(1.5.dp, colors.accentGreen.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (config.mode == AppLockMode.DEVICE) {
                        Icons.Rounded.Fingerprint
                    } else {
                        Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.applock_overlay_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (config.mode == AppLockMode.DEVICE) {
                    stringResource(R.string.applock_overlay_device_hint)
                } else {
                    stringResource(R.string.applock_overlay_pin_hint)
                },
                color = colors.textSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )

            if (!errorText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorText.orEmpty(),
                    color = colors.antiDeleteRed,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            if (config.mode == AppLockMode.DEVICE) {
                Button(
                    onClick = { triggerDeviceUnlock() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.applock_unlock_button),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            } else {
                // PIN indicator dots
                val shownSlots = maxOf(4, pinDigits.length.coerceAtMost(8))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until shownSlots) {
                        val filled = i < pinDigits.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (filled) colors.accentGreen else colors.pillInactiveBg)
                                .border(
                                    width = 1.2.dp,
                                    color = if (filled) colors.accentGreen else colors.divider,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                fun onDigit(digit: String) {
                    if (pinDigits.length >= 8) return
                    errorText = null
                    val next = pinDigits + digit
                    pinDigits = next
                    if (next.length >= 4 && AppLockManager.verifyCustomPin(next)) {
                        pinDigits = ""
                    } else if (next.length >= 8) {
                        errorText = wrongPinMsg
                        pinDigits = ""
                    }
                }

                fun onSubmitPin() {
                    if (pinDigits.length < 4) return
                    if (AppLockManager.verifyCustomPin(pinDigits)) {
                        pinDigits = ""
                    } else {
                        errorText = wrongPinMsg
                        pinDigits = ""
                    }
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("DEL", "0", "OK")
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (key) {
                                                "OK" -> colors.accentGreen.copy(alpha = 0.22f)
                                                "DEL" -> colors.pillInactiveBg.copy(alpha = 0.7f)
                                                else -> colors.elevatedBar
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (key == "OK") colors.accentGreen.copy(alpha = 0.45f) else colors.divider,
                                            shape = CircleShape
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptic()
                                            when (key) {
                                                "DEL" -> {
                                                    errorText = null
                                                    if (pinDigits.isNotEmpty()) {
                                                        pinDigits = pinDigits.dropLast(1)
                                                    }
                                                }
                                                "OK" -> onSubmitPin()
                                                else -> onDigit(key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (key) {
                                        "DEL" -> Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        "OK" -> Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.accentGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        else -> Text(
                                            text = key,
                                            color = colors.textPrimary,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLockConfigDialog(
    config: AppLockConfig,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    var showPinSetup by remember { mutableStateOf(false) }

    if (showPinSetup) {
        CustomPinSetupDialog(
            onSaved = { showPinSetup = false },
            onDismiss = { showPinSetup = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.applock_config_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.applock_config_subtitle),
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )

                Text(
                    text = stringResource(R.string.applock_mode_label),
                    color = colors.accentGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppLockModeOptionRow(
                        label = stringResource(R.string.applock_mode_off),
                        selected = config.mode == AppLockMode.OFF,
                        onClick = { AppLockManager.disableAppLock() }
                    )
                    AppLockModeOptionRow(
                        label = stringResource(R.string.applock_mode_device),
                        selected = config.mode == AppLockMode.DEVICE,
                        onClick = { AppLockManager.enableDeviceLock() }
                    )
                    AppLockModeOptionRow(
                        label = stringResource(R.string.applock_mode_pin),
                        selected = config.mode == AppLockMode.CUSTOM_PIN,
                        onClick = { showPinSetup = true }
                    )
                }

                if (config.mode == AppLockMode.CUSTOM_PIN) {
                    OutlinedButton(
                        onClick = { showPinSetup = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.applock_change_pin),
                            color = colors.accentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (config.mode != AppLockMode.OFF) {
                    Text(
                        text = stringResource(R.string.applock_timeout_label),
                        color = colors.accentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppLockTimeout.entries.forEach { timeout ->
                            val label = when (timeout) {
                                AppLockTimeout.IMMEDIATE -> stringResource(R.string.applock_timeout_immediate)
                                AppLockTimeout.ONE_MIN -> stringResource(R.string.applock_timeout_1m)
                                AppLockTimeout.FIVE_MIN -> stringResource(R.string.applock_timeout_5m)
                                AppLockTimeout.THIRTY_MIN -> stringResource(R.string.applock_timeout_30m)
                            }
                            FilterChip(
                                selected = config.timeout == timeout,
                                onClick = { AppLockManager.setTimeout(timeout) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentGreen.copy(alpha = 0.22f),
                                    selectedLabelColor = colors.accentGreen
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.applock_hide_recents_title),
                                color = colors.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.applock_hide_recents_subtitle),
                                color = colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = config.hideInRecents,
                            onCheckedChange = { AppLockManager.setHideInRecents(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentGreen
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.applock_hide_locked_chats_title),
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.applock_hide_locked_chats_subtitle),
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = config.hideLockedChatsBehindLock,
                        onCheckedChange = { AppLockManager.setHideLockedChatsBehindLock(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accentGreen
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_done),
                    color = colors.accentGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun AppLockModeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentGreen.copy(alpha = 0.16f) else colors.pillInactiveBg)
            .border(
                width = 1.dp,
                color = if (selected) colors.accentGreen else colors.divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) colors.accentGreen else colors.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun CustomPinSetupDialog(
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val lenErr = stringResource(R.string.applock_pin_length_error)
    val mismatchErr = stringResource(R.string.applock_pin_mismatch_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.applock_set_pin_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { raw ->
                        errorMsg = null
                        pin = raw.filter { it.isDigit() }.take(8)
                    },
                    label = { Text(stringResource(R.string.applock_pin_enter_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { raw ->
                        errorMsg = null
                        confirmPin = raw.filter { it.isDigit() }.take(8)
                    },
                    label = { Text(stringResource(R.string.applock_pin_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = colors.antiDeleteRed,
                        fontSize = 12.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length !in 4..8 -> errorMsg = lenErr
                        pin != confirmPin -> errorMsg = mismatchErr
                        else -> {
                            AppLockManager.enableCustomPin(pin)
                            onSaved()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
