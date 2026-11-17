package com.wupp.meow.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.wupp.meow.R
import com.wupp.meow.core.service.WaRealtimeService
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

private data class SetupPermissionSnapshot(
    val notificationsGranted: Boolean,
    val mediaGranted: Boolean,
    val micGranted: Boolean,
    val batteryUnrestricted: Boolean
) {
    val allRuntimeGranted: Boolean
        get() = notificationsGranted && mediaGranted && micGranted

    val allReady: Boolean
        get() = allRuntimeGranted && batteryUnrestricted
}

private fun readSetupSnapshot(context: Context): SetupPermissionSnapshot {
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryOk = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    return SetupPermissionSnapshot(
        notificationsGranted = notifGranted,
        mediaGranted = mediaGranted,
        micGranted = micGranted,
        batteryUnrestricted = batteryOk
    )
}

private fun missingRuntimePermissions(snapshot: SetupPermissionSnapshot): List<String> {
    val missing = mutableListOf<String>()
    if (!snapshot.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        missing.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!snapshot.mediaGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            missing.add(Manifest.permission.READ_MEDIA_IMAGES)
            missing.add(Manifest.permission.READ_MEDIA_VIDEO)
            missing.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            missing.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    if (!snapshot.micGranted) {
        missing.add(Manifest.permission.RECORD_AUDIO)
    }
    return missing
}

private fun openBatteryOptimizationPrompt(context: Context) {
    WaRealtimeService.openBatteryOptimizationSettings(context)
}

private fun openAppPermissionSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

@Composable
fun FirstInstallSetupScreen(
    onCompleteSetup: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot = remember(refreshTick) { readSetupSnapshot(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.appSurface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                Image(
                    painter = painterResource(R.drawable.wupp_logo),
                    contentDescription = "Wupp Logo",
                    modifier = Modifier.size(88.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.setup_welcome_title),
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.setup_welcome_subtitle),
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.brightGreen.copy(alpha = 0.12f))
                        .border(1.dp, colors.brightGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.brightGreen,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Install alongside WhatsApp — don't replace it",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Wupp works as a linked companion app. Keep official WhatsApp installed for calls, View-Once media, unknown-number DMs, and your 14-day device link.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedBar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SetupChecklistRow(
                            icon = Icons.Rounded.Notifications,
                            title = "Notifications",
                            subtitle = "Show new message alerts (you can hide the sync banner anytime in Settings)",
                            granted = snapshot.notificationsGranted,
                            actionLabel = "Allow",
                            onActionClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                } else {
                                    refreshTick++
                                }
                            }
                        )
                        HorizontalDivider(color = colors.divider)
                        SetupChecklistRow(
                            icon = Icons.Rounded.PermMedia,
                            title = "Photos, Videos & Audio",
                            subtitle = "View, send, and save media and status updates locally",
                            granted = snapshot.mediaGranted,
                            actionLabel = "Allow",
                            onActionClick = { permissionLauncher.launch(mediaPermissions) }
                        )
                        HorizontalDivider(color = colors.divider)
                        SetupChecklistRow(
                            icon = Icons.Rounded.Mic,
                            title = "Microphone",
                            subtitle = "Record and send voice notes in chats and groups",
                            granted = snapshot.micGranted,
                            actionLabel = "Allow",
                            onActionClick = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        )
                        HorizontalDivider(color = colors.divider)
                        SetupChecklistRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = "Background Running",
                            subtitle = "Disable battery restrictions so messages sync instantaneously even when locked",
                            granted = snapshot.batteryUnrestricted,
                            actionLabel = "Enable",
                            onActionClick = { openBatteryOptimizationPrompt(context) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!snapshot.allRuntimeGranted) {
                    Text(
                        text = "Denied a prompt by mistake? Open App Settings",
                        color = colors.accentGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { openAppPermissionSettings(context) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val missing = missingRuntimePermissions(snapshot)
                val primaryLabel = when {
                    missing.isNotEmpty() -> "Grant Permissions (${missing.size})"
                    !snapshot.batteryUnrestricted -> "Allow Background Running"
                    else -> "Continue to Wupp"
                }

                Button(
                    onClick = {
                        haptic()
                        when {
                            missing.isNotEmpty() -> permissionLauncher.launch(missing.toTypedArray())
                            !snapshot.batteryUnrestricted -> openBatteryOptimizationPrompt(context)
                            else -> onCompleteSetup()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = Color(0xFF07090B)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("setup_primary_button")
                ) {
                    Text(
                        text = primaryLabel,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        haptic()
                        onCompleteSetup()
                    },
                    modifier = Modifier.testTag("setup_skip_button")
                ) {
                    Text(
                        text = if (snapshot.allReady) "Enter Wupp" else "Continue anyway",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SetupChecklistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !granted) {
                haptic()
                onActionClick()
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (granted) colors.pillActiveBg else colors.quotedBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) colors.brightGreen else colors.textSecondary,
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = colors.brightGreen,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.brightGreen.copy(alpha = 0.16f))
                    .clickable {
                        haptic()
                        onActionClick()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    color = colors.brightGreen,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
