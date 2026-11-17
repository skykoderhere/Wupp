package com.wupp.meow.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.wupp.meow.BuildConfig
import com.wupp.meow.R
import com.wupp.meow.core.service.OwnProfileUi
import com.wupp.meow.core.service.WaRealtimeService
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.domain.security.AppLockMode
import com.wupp.meow.domain.stealth.AutoDownloadNetwork
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.MainViewModel
import com.wupp.meow.ui.components.AppLanguageDialog
import com.wupp.meow.ui.components.AppLockConfigDialog
import com.wupp.meow.ui.components.AutoDownloadNetworkDialog
import com.wupp.meow.ui.components.BlockedContactsDialog
import com.wupp.meow.ui.components.ChatFontSizeDialog
import com.wupp.meow.ui.components.CompanionScopeInfoDialog
import com.wupp.meow.ui.components.ExpressiveIconBadge
import com.wupp.meow.ui.components.ExpressiveSegmentPosition
import com.wupp.meow.ui.components.ExpressiveSegmentedActionRow
import com.wupp.meow.ui.components.ExpressiveSegmentedContainer
import com.wupp.meow.ui.components.ExpressiveSegmentedGroup
import com.wupp.meow.ui.components.ExpressiveSegmentedSwitchRow
import com.wupp.meow.ui.components.GalleryVisibilityContactsDialog
import com.wupp.meow.ui.components.ProfileEditorDialog
import com.wupp.meow.ui.components.ReactionEmojiCustomizerDialog
import com.wupp.meow.ui.components.StorageUsageDialog
import com.wupp.meow.ui.components.ThemeSelectorGrid
import com.wupp.meow.ui.components.WallpaperPickerDialog
import com.wupp.meow.ui.components.WhatsAppAccountPrivacyDialog
import com.wupp.meow.ui.components.WuppIcons
import com.wupp.meow.ui.components.describeChatWallpaper
import com.wupp.meow.ui.components.findFragmentActivity
import com.wupp.meow.ui.components.formatFontScaleLabel
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWaMonoFamily
import com.wupp.meow.ui.theme.LocaleHelper
import com.wupp.meow.ui.theme.WuppThemeMode
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.launch

private enum class SettingsRoute(val title: String) {
    ROOT("You"),
    LINKED_DEVICES("Linked devices"),
    ACCOUNT("Account"),
    PRIVACY("Privacy"),
    CHATS("Chats"),
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    STORAGE("Storage and data"),
    ACCESSIBILITY("Accessibility"),
    APP_INFO("App Info")
}

private data class SettingsRootItem(
    val route: SettingsRoute?,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val testTag: String,
    val searchKeywords: String,
    val badge: String? = null,
    val onDirectAction: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    onOpenPairingDialog: () -> Unit,
    onOpenVaultMessage: (MessageEntity) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val haptic = rememberWuppHapticTrigger()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val stealth by viewModel.stealthConfig.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val voVault by viewModel.viewOnceVault.collectAsState()
    val presenceLogs by viewModel.presenceLogs.collectAsState()
    val profileHistory by viewModel.profileHistory.collectAsState()
    val bulkResult by viewModel.bulkVerifyOutput.collectAsState()

    var activeRoute by rememberSaveable { mutableStateOf(SettingsRoute.ROOT) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var ownProfile by remember { mutableStateOf(OwnProfileUi()) }

    // Shared dialogs
    var showMoodDialog by remember { mutableStateOf(false) }
    var showProfileEditorDialog by remember { mutableStateOf(false) }
    var showAccountPrivacyDialog by remember { mutableStateOf(false) }
    var showBlockedContactsDialog by remember { mutableStateOf(false) }
    var showCompanionScopeDialog by remember { mutableStateOf(false) }
    var showStorageUsageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var editingAutoDlNetwork by remember { mutableStateOf<AutoDownloadNetwork?>(null) }
    var showGalleryContactsDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showReactionDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHideNotifGuide by remember { mutableStateOf(false) }

    // Wupp Privacy sub-dialogs
    var showViewOnceVaultDialog by remember { mutableStateOf(false) }
    var showPresenceLogsDialog by remember { mutableStateOf(false) }
    var showProfileHistoryDialog by remember { mutableStateOf(false) }
    var showBulkVerifyDialog by remember { mutableStateOf(false) }

    var isSyncNotifHidden by remember {
        mutableStateOf(WaRealtimeService.isRealtimeStatusNotificationHidden(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(WaRealtimeService.isIgnoringBatteryOptimizations(context))
    }

    LaunchedEffect(bridgeStatus.isLoggedIn, bridgeStatus.connectionState) {
        viewModel.fetchOwnProfile().onSuccess { ownProfile = it }
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isSyncNotifHidden = WaRealtimeService.isRealtimeStatusNotificationHidden(context)
                isBatteryUnrestricted = WaRealtimeService.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    BackHandler(enabled = activeRoute != SettingsRoute.ROOT) {
        haptic()
        activeRoute = SettingsRoute.ROOT
    }

    BackHandler(enabled = activeRoute == SettingsRoute.ROOT && searchOpen) {
        searchOpen = false
        searchQuery = ""
    }

    val ownAvatarPath = remember(bridgeStatus.ownJid, allChats) {
        val ownUser = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
        allChats.firstOrNull { ch ->
            !ch.avatarPath.isNullOrBlank() &&
                ownUser.isNotBlank() &&
                ch.jid.substringBefore("@").substringBefore(":") == ownUser
        }?.avatarPath
    }

    val displayName = ownProfile.pushName.takeIf { it.isNotBlank() }
        ?: bridgeStatus.ownJid.substringBefore("@").substringBefore(":").takeIf { it.isNotBlank() }
        ?: "Wupp Companion"

    val displayHandle = remember(bridgeStatus.ownJid, ownProfile.about) {
        val user = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
        when {
            user.isNotBlank() -> "+$user"
            ownProfile.about.isNotBlank() -> ownProfile.about
            else -> "@wupp.meow"
        }
    }

    val langSubtitle = when (stealth.appLanguage) {
        LocaleHelper.LANG_TR -> stringResource(R.string.language_turkish)
        LocaleHelper.LANG_EN -> stringResource(R.string.language_english)
        else -> stringResource(R.string.language_system)
    }

    val rootItems = remember(
        bridgeStatus.connectionState,
        heartbeat.isWarningActive,
        stealth.themeMode,
        langSubtitle,
        appLockConfig.mode
    ) {
        listOf(
            SettingsRootItem(
                route = SettingsRoute.LINKED_DEVICES,
                icon = WuppIcons.LinkedDevices,
                title = "Linked devices",
                subtitle = "Use WhatsApp on other devices · ${bridgeStatus.connectionState.lowercase().replaceFirstChar { it.uppercase() }}",
                testTag = "settings_row_linked_devices",
                searchKeywords = "linked devices companion qr pairing heartbeat 14 day sync",
                badge = if (heartbeat.isWarningActive) "${heartbeat.remainingDays}d" else null
            ),
            SettingsRootItem(
                route = SettingsRoute.ACCOUNT,
                icon = WuppIcons.Key,
                title = "Account",
                subtitle = "Profile, App Lock PIN & companion info",
                testTag = "settings_row_account",
                searchKeywords = "account profile app lock pin biometric security mood",
                badge = if (appLockConfig.mode != AppLockMode.OFF) "LOCKED" else null
            ),
            SettingsRootItem(
                route = SettingsRoute.PRIVACY,
                icon = WuppIcons.Lock,
                title = "Privacy",
                subtitle = "Ghost Mode, Anti-Delete & Blocked contacts",
                testTag = "settings_row_privacy",
                searchKeywords = "privacy ghost mode anti delete disappearing blocked view once vault presence history verify"
            ),
            SettingsRootItem(
                route = SettingsRoute.CHATS,
                icon = WuppIcons.ChatsOutline,
                title = "Chats",
                subtitle = "Wallpaper, font size, quick reactions & composer",
                testTag = "settings_row_chats",
                searchKeywords = "chats wallpaper font size reactions enter send seconds bio compact groups channels"
            ),
            SettingsRootItem(
                route = SettingsRoute.APPEARANCE,
                icon = WuppIcons.Palette,
                title = "Appearance",
                subtitle = "${stealth.themeMode.label}, accent colors & classic UI",
                testTag = "settings_row_appearance",
                searchKeywords = "appearance theme amoled dark light accent color classic ui system font status camera badges"
            ),
            SettingsRootItem(
                route = SettingsRoute.NOTIFICATIONS,
                icon = WuppIcons.Notifications,
                title = "Notifications",
                subtitle = "DM & group alerts, previews & sync banner",
                testTag = "settings_row_notifications",
                searchKeywords = "notifications dm group alert preview vibration persistent banner battery"
            ),
            SettingsRootItem(
                route = SettingsRoute.STORAGE,
                icon = WuppIcons.Storage,
                title = "Storage and data",
                subtitle = "Network usage, auto-download & gallery",
                testTag = "settings_row_storage",
                searchKeywords = "storage data cache auto download wifi mobile roaming gallery visibility"
            ),
            SettingsRootItem(
                route = SettingsRoute.ACCESSIBILITY,
                icon = WuppIcons.Accessibility,
                title = "Accessibility",
                subtitle = "Tactile haptics, font scale & contrast",
                testTag = "settings_row_accessibility",
                searchKeywords = "accessibility haptic vibration contrast font scale"
            ),
            SettingsRootItem(
                route = null,
                icon = WuppIcons.Language,
                title = "App language",
                subtitle = langSubtitle,
                testTag = "settings_row_language",
                searchKeywords = "language english turkish system",
                onDirectAction = { showLanguageDialog = true }
            ),
            SettingsRootItem(
                route = SettingsRoute.APP_INFO,
                icon = WuppIcons.Info,
                title = "App Info",
                subtitle = "Local version v${BuildConfig.VERSION_NAME} & offline diagnostics",
                testTag = "settings_row_app_info",
                searchKeywords = "app info version build diagnostics showcase offline"
            )
        )
    }

    val motion = MaterialTheme.motionScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appSurface),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = activeRoute,
            transitionSpec = {
                if (targetState != SettingsRoute.ROOT) {
                    (slideInHorizontally(motion.fastSpatialSpec()) { it / 5 } + fadeIn(motion.fastEffectsSpec())) togetherWith
                        (slideOutHorizontally(motion.fastSpatialSpec()) { -it / 8 } + fadeOut(motion.fastEffectsSpec()))
                } else {
                    (slideInHorizontally(motion.fastSpatialSpec()) { -it / 5 } + fadeIn(motion.fastEffectsSpec())) togetherWith
                        (slideOutHorizontally(motion.fastSpatialSpec()) { it / 8 } + fadeOut(motion.fastEffectsSpec()))
                }
            },
            label = "settings_route_transition",
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) { route ->
            when (route) {
                SettingsRoute.ROOT -> {
                    val filteredRootItems = remember(rootItems, searchQuery) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty()) rootItems
                        else rootItems.filter {
                            it.title.lowercase().contains(q) ||
                                it.subtitle.lowercase().contains(q) ||
                                it.searchKeywords.contains(q)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
                    ) {
                        // Top Reference-Style Doodle Profile Header (Zero Gradients)
                        item(key = "you_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.appSurface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.appSurface)
                                ) {
                                    // Subtle doodle wallpaper strip behind header (matches reference screenshot)
                                    Image(
                                        painter = painterResource(R.drawable.whatsapp_doodle),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = ColorFilter.tint(
                                            colors.doodleColor.copy(alpha = if (colors.isDark) 0.10f else 0.16f)
                                        ),
                                        modifier = Modifier
                                            .matchParentSize()
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(bottom = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top-right Search / Share / Edit toolbar icons (48dp min targets)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    haptic()
                                                    searchOpen = !searchOpen
                                                    if (!searchOpen) searchQuery = ""
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("settings_search_btn")
                                            ) {
                                                Icon(
                                                    imageVector = if (searchOpen) WuppIcons.Close else WuppIcons.Search,
                                                    contentDescription = "Search settings",
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic()
                                                    val summary = buildString {
                                                        append(displayName)
                                                        if (displayHandle.isNotBlank()) append(" ($displayHandle)")
                                                        if (stealth.currentMood.isNotBlank()) append("\nMood: ${stealth.currentMood}")
                                                        if (ownProfile.about.isNotBlank()) append("\n${ownProfile.about}")
                                                    }
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, summary)
                                                    }
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent.createChooser(shareIntent, "Share profile info")
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("settings_share_btn")
                                            ) {
                                                Icon(
                                                    imageVector = WuppIcons.Share,
                                                    contentDescription = "Share profile summary",
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(21.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic()
                                                    showProfileEditorDialog = true
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("settings_edit_profile_btn")
                                            ) {
                                                Icon(
                                                    imageVector = WuppIcons.Edit,
                                                    contentDescription = "Edit profile",
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(21.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = searchOpen) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                placeholder = { Text("Search settings & Wupp features...") },
                                                singleLine = true,
                                                trailingIcon = {
                                                    if (searchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { searchQuery = "" },
                                                            modifier = Modifier.size(48.dp)
                                                        ) {
                                                            Icon(WuppIcons.Close, "Clear search")
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                                                    .testTag("settings_search_input")
                                            )
                                        }

                                        // Centered "Current mood" pill above Avatar
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp, bottom = 10.dp)
                                                .heightIn(min = 36.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(colors.incomingBubble)
                                                .border(1.dp, colors.divider, RoundedCornerShape(999.dp))
                                                .clickable {
                                                    haptic()
                                                    showMoodDialog = true
                                                }
                                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                                .testTag("settings_current_mood_pill"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stealth.currentMood.ifBlank { "Current mood" },
                                                color = if (stealth.currentMood.isNotBlank()) colors.textPrimary else colors.textSecondary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Centered 88dp Circular Avatar + Bright Green "+" Badge
                                        Box(
                                            modifier = Modifier
                                                .size(92.dp)
                                                .clickable {
                                                    haptic()
                                                    showMoodDialog = true
                                                }
                                                .testTag("settings_profile_avatar_btn"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            WhatsAppAvatar(
                                                jid = bridgeStatus.ownJid.ifBlank { "self@s.whatsapp.net" },
                                                avatarPath = ownAvatarPath,
                                                isGroup = false,
                                                size = 88.dp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.brightGreen)
                                                    .border(2.5.dp, colors.appSurface, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = WuppIcons.Add,
                                                    contentDescription = "Set mood",
                                                    tint = Color(0xFF07090B),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Bold Name + Chevron
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptic()
                                                    showProfileEditorDialog = true
                                                }
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                                .testTag("settings_profile_name_btn"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = displayName,
                                                color = colors.textPrimary,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = WuppIcons.ChevronDown,
                                                contentDescription = "Edit account profile",
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Secondary Handle / Phone Subtitle
                                        Text(
                                            text = displayHandle,
                                            color = colors.textSecondary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ownProfile.about.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = ownProfile.about,
                                                color = colors.textSecondary.copy(alpha = 0.8f),
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 28.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    thickness = 0.75.dp,
                                    color = colors.divider.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (filteredRootItems.isEmpty()) {
                            item(key = "settings_search_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching settings for \"$searchQuery\"",
                                        color = colors.textSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            items(filteredRootItems, key = { it.title }) { item ->
                                SettingsReferenceRow(
                                    icon = item.icon,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    badge = item.badge,
                                    testTag = item.testTag,
                                    onClick = {
                                        haptic()
                                        if (item.onDirectAction != null) {
                                            item.onDirectAction.invoke()
                                        } else if (item.route != null) {
                                            activeRoute = item.route
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.appSurface)
                    ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colors.appSurface,
                                titleContentColor = colors.textPrimary
                            ),
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        haptic()
                                        activeRoute = SettingsRoute.ROOT
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("settings_sub_back_btn")
                                ) {
                                    Icon(
                                        imageVector = WuppIcons.Back,
                                        contentDescription = "Back to You",
                                        tint = colors.textPrimary
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = route.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        )
                        HorizontalDivider(thickness = 0.6.dp, color = colors.divider)

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = bottomPadding + 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            when (route) {
                                SettingsRoute.LINKED_DEVICES -> {
                                    item(key = "sub_linked_bridge") {
                                        ExpressiveSegmentedGroup(title = "COMPANION SESSION") {
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.LinkedDevices,
                                                title = "Link or Pair Device",
                                                subtitle = "Engine: ${bridgeStatus.connectionState}" +
                                                    if (bridgeStatus.ownJid.isNotBlank()) " · ${bridgeStatus.ownJid}" else "",
                                                badgeText = if (bridgeStatus.isLoggedIn) "LINKED" else "PAIR",
                                                position = ExpressiveSegmentPosition.TOP,
                                                onClick = onOpenPairingDialog
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.QrCode,
                                                title = "Reconnect Realtime Engine",
                                                subtitle = "Refresh WebSocket bridge connection immediately",
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = {
                                                    viewModel.reconnectSocket()
                                                    Toast.makeText(context, "Reconnecting companion session...", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                    item(key = "sub_linked_heartbeat") {
                                        ExpressiveSegmentedGroup(title = "14-DAY PRIMARY HEARTBEAT") {
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.History,
                                                title = "Primary Phone Heartbeat",
                                                subtitle = "${heartbeat.elapsedDays}d elapsed · ${heartbeat.remainingDays}d ${heartbeat.remainingHours}h remaining before unlink check",
                                                badgeText = if (heartbeat.isWarningActive) "ALERT" else "HEALTHY",
                                                position = ExpressiveSegmentPosition.SINGLE,
                                                onClick = { viewModel.recordPrimaryHeartbeatNow() }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.ACCOUNT -> {
                                    item(key = "sub_account_profile") {
                                        ExpressiveSegmentedGroup(title = "PROFILE & MOOD") {
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Person,
                                                title = "WhatsApp Profile",
                                                subtitle = "$displayName · ${ownProfile.about.ifBlank { "Edit name & about" }}",
                                                position = ExpressiveSegmentPosition.TOP,
                                                onClick = { showProfileEditorDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.EmojiEmotions,
                                                title = "Current Mood",
                                                subtitle = stealth.currentMood.ifBlank { "Tap to set a mood status badge" },
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showMoodDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Info,
                                                title = stringResource(R.string.settings_account_limits_title),
                                                subtitle = stringResource(R.string.settings_account_limits_subtitle),
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = { showCompanionScopeDialog = true }
                                            )
                                        }
                                    }
                                    item(key = "sub_account_lock") {
                                        val lockSubtitle = when (appLockConfig.mode) {
                                            AppLockMode.OFF -> stringResource(R.string.settings_applock_row_subtitle_off)
                                            AppLockMode.DEVICE -> stringResource(R.string.settings_applock_row_subtitle_device)
                                            AppLockMode.CUSTOM_PIN -> stringResource(R.string.settings_applock_row_subtitle_pin)
                                        }
                                        ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_security)) {
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Lock,
                                                title = stringResource(R.string.settings_applock_row_title),
                                                subtitle = lockSubtitle,
                                                badgeText = if (appLockConfig.mode != AppLockMode.OFF) {
                                                    stringResource(R.string.common_enabled)
                                                } else null,
                                                position = if (appLockConfig.mode != AppLockMode.OFF) {
                                                    ExpressiveSegmentPosition.TOP
                                                } else {
                                                    ExpressiveSegmentPosition.SINGLE
                                                },
                                                onClick = { showAppLockDialog = true }
                                            )
                                            if (appLockConfig.mode != AppLockMode.OFF) {
                                                ExpressiveSegmentedActionRow(
                                                    icon = Icons.Rounded.Shield,
                                                    title = stringResource(R.string.settings_lock_now_title),
                                                    subtitle = stringResource(R.string.settings_lock_now_subtitle),
                                                    position = ExpressiveSegmentPosition.BOTTOM,
                                                    onClick = { AppLockManager.lockNow() }
                                                )
                                            }
                                        }
                                    }
                                }

                                SettingsRoute.PRIVACY -> {
                                    item(key = "sub_privacy_ghost") {
                                        ExpressiveSegmentedGroup(title = "STEALTH & GHOST MATRIX") {
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = "Ghost Mode (Freeze Last Seen)",
                                                subtitle = "Hide online status and freeze your last seen timestamp",
                                                checked = stealth.ghostPresence,
                                                position = ExpressiveSegmentPosition.TOP,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(ghostPresence = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.CheckCircle,
                                                title = "Hide Read Receipts",
                                                subtitle = "Read messages privately without sending blue ticks",
                                                checked = stealth.hideReadReceipts,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hideReadReceipts = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Campaign,
                                                title = "Hide Played Receipts",
                                                subtitle = "Play voice notes and audio without marking them played",
                                                checked = stealth.hidePlayedReceipts,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hidePlayedReceipts = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Shield,
                                                title = "Hide Typing & Recording",
                                                subtitle = "Suppress typing and recording audio indicators in chats",
                                                checked = stealth.hideTypingRecording,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hideTypingRecording = it) }
                                                }
                                            )
                                        }
                                    }
                                    item(key = "sub_privacy_antidelete") {
                                        ExpressiveSegmentedGroup(title = "ANTI-DELETE & EPHEMERAL IMMUNITY") {
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.DeleteSweep,
                                                title = "Anti-Delete Messages & Statuses",
                                                subtitle = "Keep messages, media, and statuses deleted by everyone",
                                                checked = stealth.antiDeleteEnabled,
                                                position = ExpressiveSegmentPosition.TOP,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(antiDeleteEnabled = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Lock,
                                                title = "Disappearing Message Immunity",
                                                subtitle = "Prevent ephemeral timers from expiring messages in chats",
                                                checked = stealth.disappearingImmunity,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(disappearingImmunity = it) }
                                                }
                                            )
                                        }
                                    }
                                    item(key = "sub_privacy_wa_account") {
                                        ExpressiveSegmentedGroup(title = "WHATSAPP PRIVACY & VAULTS") {
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.AdminPanelSettings,
                                                title = stringResource(R.string.settings_wa_privacy_title),
                                                subtitle = stringResource(R.string.settings_wa_privacy_subtitle),
                                                position = ExpressiveSegmentPosition.TOP,
                                                onClick = { showAccountPrivacyDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Block,
                                                title = stringResource(R.string.settings_blocked_contacts_title),
                                                subtitle = stringResource(R.string.settings_blocked_contacts_subtitle),
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showBlockedContactsDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.ViewOnce,
                                                title = "View-Once Media Vault",
                                                subtitle = "${voVault.size} preserved view-once item(s)",
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showViewOnceVaultDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.History,
                                                title = "Presence & Profile Logs",
                                                subtitle = "${presenceLogs.size} presence events · ${profileHistory.size} avatar changes",
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showPresenceLogsDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.People,
                                                title = "Profile & Avatar History",
                                                subtitle = "Inspect historical name and avatar changes",
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showProfileHistoryDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = WuppIcons.SearchBold,
                                                title = "Bulk Number Verifier",
                                                subtitle = "Check WhatsApp registration for CSV phone numbers",
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = { showBulkVerifyDialog = true }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.CHATS -> {
                                    val reactionPreview = stealth.quickReactionEmojis
                                        .ifEmpty { StealthMatrixManager.DEFAULT_QUICK_REACTIONS }
                                        .joinToString("  ")
                                    item(key = "sub_chats_composer") {
                                        ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_chats)) {
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Image,
                                                title = stringResource(R.string.settings_wallpaper_title),
                                                subtitle = "${describeChatWallpaper(stealth.chatWallpaperUri)} · ${stringResource(R.string.settings_wallpaper_subtitle)}",
                                                position = ExpressiveSegmentPosition.TOP,
                                                onClick = { showWallpaperDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.FormatSize,
                                                title = stringResource(R.string.settings_font_size_title),
                                                subtitle = formatFontScaleLabel(stealth.chatFontScale),
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showFontSizeDialog = true }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.EmojiEmotions,
                                                title = stringResource(R.string.settings_reactions_title),
                                                subtitle = reactionPreview,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showReactionDialog = true }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.KeyboardReturn,
                                                title = stringResource(R.string.settings_enter_sends_title),
                                                subtitle = stringResource(R.string.settings_enter_sends_subtitle),
                                                checked = stealth.enterIsSend,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(enterIsSend = it) }
                                                }
                                            )
                                        }
                                    }
                                    item(key = "sub_chats_layout") {
                                        ExpressiveSegmentedGroup(title = "CHAT LIST & TIMELINE") {
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Person,
                                                title = stringResource(R.string.settings_look_always_bio_title),
                                                subtitle = stringResource(R.string.settings_look_always_bio_subtitle),
                                                checked = stealth.alwaysShowContactBio,
                                                position = ExpressiveSegmentPosition.TOP,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(alwaysShowContactBio = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.ViewAgenda,
                                                title = stringResource(R.string.settings_look_compact_rows_title),
                                                subtitle = stringResource(R.string.settings_look_compact_rows_subtitle),
                                                checked = stealth.compactChatRows,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(compactChatRows = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Schedule,
                                                title = stringResource(R.string.settings_look_seconds_title),
                                                subtitle = stringResource(R.string.settings_look_seconds_subtitle),
                                                checked = stealth.showSecondsInTimestamps,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(showSecondsInTimestamps = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Groups,
                                                title = stringResource(R.string.settings_look_separate_groups_title),
                                                subtitle = stringResource(R.string.settings_look_separate_groups_subtitle),
                                                checked = stealth.separateGroupsAndChats,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(separateGroupsAndChats = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = stringResource(R.string.settings_look_hide_channels_title),
                                                subtitle = stringResource(R.string.settings_look_hide_channels_subtitle),
                                                checked = stealth.hideChannels,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hideChannels = it) }
                                                }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.APPEARANCE -> {
                                    item(key = "sub_appearance_theme") {
                                        ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_appearance)) {
                                            ExpressiveSegmentedContainer(position = ExpressiveSegmentPosition.TOP) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 15.dp, vertical = 14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        ExpressiveIconBadge(
                                                            icon = Icons.Rounded.Palette,
                                                            contentDescription = stringResource(R.string.settings_theme_title),
                                                            active = true
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = stringResource(R.string.settings_theme_title),
                                                                color = colors.textPrimary,
                                                                fontSize = 15.5.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Text(
                                                                text = "${stealth.themeMode.label} — ${stealth.themeMode.tagline}",
                                                                color = colors.textSecondary,
                                                                fontSize = 12.5.sp
                                                            )
                                                        }
                                                    }
                                                    ThemeSelectorGrid(
                                                        selectedTheme = stealth.themeMode,
                                                        onSelectTheme = { viewModel.setThemeMode(it) },
                                                        selectedAccent = stealth.accentColor,
                                                        onSelectAccent = { viewModel.setAccentColor(it) }
                                                    )
                                                }
                                            }
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.ViewAgenda,
                                                title = stringResource(R.string.settings_look_classic_ui_title),
                                                subtitle = stringResource(R.string.settings_look_classic_ui_subtitle),
                                                checked = stealth.classicWhatsAppLook,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(classicWhatsAppLook = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.TextFields,
                                                title = stringResource(R.string.settings_system_font_title),
                                                subtitle = stringResource(R.string.settings_system_font_subtitle),
                                                checked = stealth.useSystemFont,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = { viewModel.setUseSystemFont(it) }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.ViewAgenda,
                                                title = stringResource(R.string.settings_look_vertical_status_title),
                                                subtitle = stringResource(R.string.settings_look_vertical_status_subtitle),
                                                checked = stealth.statusVerticalLayout,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(statusVerticalLayout = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = stringResource(R.string.settings_look_hide_camera_title),
                                                subtitle = stringResource(R.string.settings_look_hide_camera_subtitle),
                                                checked = stealth.hideHomeCameraShortcut,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hideHomeCameraShortcut = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = stringResource(R.string.settings_look_hide_badges_title),
                                                subtitle = stringResource(R.string.settings_look_hide_badges_subtitle),
                                                checked = stealth.hideStealthStatusBadges,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(hideStealthStatusBadges = it) }
                                                }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.NOTIFICATIONS -> {
                                    item(key = "sub_notifications_alerts") {
                                        ExpressiveSegmentedGroup(title = "NOTIFICATIONS & BACKGROUND SYNC") {
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Notifications,
                                                title = stringResource(R.string.settings_notify_dm_title),
                                                subtitle = stringResource(R.string.settings_notify_dm_subtitle),
                                                checked = stealth.notifyDmEnabled,
                                                position = ExpressiveSegmentPosition.TOP,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(notifyDmEnabled = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Groups,
                                                title = stringResource(R.string.settings_notify_group_title),
                                                subtitle = stringResource(R.string.settings_notify_group_subtitle),
                                                checked = stealth.notifyGroupEnabled,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(notifyGroupEnabled = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = stringResource(R.string.settings_notify_preview_title),
                                                subtitle = stringResource(R.string.settings_notify_preview_subtitle),
                                                checked = stealth.notifyShowPreview,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(notifyShowPreview = it) }
                                                }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Vibration,
                                                title = stringResource(R.string.settings_notify_vibrate_title),
                                                subtitle = stringResource(R.string.settings_notify_vibrate_subtitle),
                                                checked = stealth.notifyVibrateEnabled,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = {
                                                    viewModel.updateGlobalStealth { c -> c.copy(notifyVibrateEnabled = it) }
                                                }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = if (isSyncNotifHidden) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                                title = if (isSyncNotifHidden) "Persistent Sync Banner" else "Hide Persistent Sync Banner",
                                                subtitle = if (isSyncNotifHidden) {
                                                    "Hidden — 24/7 background sync & message alerts are still active"
                                                } else {
                                                    "Remove the constant notification without interrupting real-time sync"
                                                },
                                                badgeText = if (isSyncNotifHidden) "HIDDEN" else "GUIDE",
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = {
                                                    if (isSyncNotifHidden) {
                                                        WaRealtimeService.openNotificationChannelSettings(
                                                            context = context,
                                                            channelId = WaRealtimeService.CHANNEL_ID
                                                        )
                                                    } else {
                                                        showHideNotifGuide = true
                                                    }
                                                }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.BatteryChargingFull,
                                                title = "Unrestricted Battery (24/7 Sync)",
                                                subtitle = if (isBatteryUnrestricted) {
                                                    "Allowed — Android won't pause background sync while locked"
                                                } else {
                                                    "Allow unrestricted background use for continuous sync"
                                                },
                                                badgeText = if (isBatteryUnrestricted) "ACTIVE" else "ALLOW",
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = {
                                                    WaRealtimeService.openBatteryOptimizationSettings(context)
                                                }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.STORAGE -> {
                                    item(key = "sub_storage_data") {
                                        ExpressiveSegmentedGroup(title = "STORAGE & MEDIA AUTO-DOWNLOAD") {
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.CleaningServices,
                                                title = stringResource(R.string.settings_manage_storage_title),
                                                subtitle = stringResource(R.string.settings_manage_storage_subtitle),
                                                position = ExpressiveSegmentPosition.TOP,
                                                onClick = { showStorageUsageDialog = true }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.CloudDownload,
                                                title = "Media auto-download",
                                                subtitle = if (stealth.autoDownloadEnabled) {
                                                    "Automatically download incoming photos, audio, videos, and files"
                                                } else {
                                                    "Off — tap media items in chats to download manually"
                                                },
                                                checked = stealth.autoDownloadEnabled,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = { viewModel.setAutoDownloadEnabled(it) }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.SignalCellularAlt,
                                                title = "When using mobile data",
                                                subtitle = if (stealth.autoDownloadEnabled) {
                                                    StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadMobileMask)
                                                } else {
                                                    "Disabled (auto-download is off)"
                                                },
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { editingAutoDlNetwork = AutoDownloadNetwork.MOBILE }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Wifi,
                                                title = "When connected on Wi-Fi",
                                                subtitle = if (stealth.autoDownloadEnabled) {
                                                    StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadWifiMask)
                                                } else {
                                                    "Disabled (auto-download is off)"
                                                },
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { editingAutoDlNetwork = AutoDownloadNetwork.WIFI }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.Flight,
                                                title = "When roaming",
                                                subtitle = if (stealth.autoDownloadEnabled) {
                                                    StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadRoamingMask)
                                                } else {
                                                    "Disabled (auto-download is off)"
                                                },
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { editingAutoDlNetwork = AutoDownloadNetwork.ROAMING }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Collections,
                                                title = "Media visibility",
                                                subtitle = "Show newly downloaded media in your device's gallery",
                                                checked = stealth.mediaVisibilityInGallery,
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onCheckedChange = { viewModel.setMediaVisibilityInGallery(it) }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.VisibilityOff,
                                                title = "Hide contact media from gallery",
                                                subtitle = if (stealth.hiddenGalleryJids.isEmpty()) {
                                                    "Choose specific contacts or groups to keep out of gallery"
                                                } else {
                                                    "${stealth.hiddenGalleryJids.size} contact(s)/group(s) hidden from gallery"
                                                },
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = { showGalleryContactsDialog = true }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.ACCESSIBILITY -> {
                                    item(key = "sub_accessibility") {
                                        ExpressiveSegmentedGroup(title = "TACTILE FEEDBACK & READABILITY") {
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Vibration,
                                                title = stringResource(R.string.settings_haptic_feedback_title),
                                                subtitle = stringResource(R.string.settings_haptic_feedback_subtitle),
                                                checked = stealth.hapticFeedbackEnabled,
                                                position = ExpressiveSegmentPosition.TOP,
                                                onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.FormatSize,
                                                title = stringResource(R.string.settings_font_size_title),
                                                subtitle = formatFontScaleLabel(stealth.chatFontScale),
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = { showFontSizeDialog = true }
                                            )
                                            ExpressiveSegmentedSwitchRow(
                                                icon = Icons.Rounded.Contrast,
                                                title = "High-Contrast Pure AMOLED",
                                                subtitle = "Switch between 70% dark obsidian (#07090B) and #000000 pitch black",
                                                checked = stealth.themeMode == WuppThemeMode.AMOLED,
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onCheckedChange = { pure ->
                                                    viewModel.setThemeMode(if (pure) WuppThemeMode.AMOLED else WuppThemeMode.DARK)
                                                }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.APP_INFO -> {
                                    item(key = "sub_app_info_card") {
                                        val diagText = remember(
                                            bridgeStatus.connectionState,
                                            bridgeStatus.ownJid,
                                            allChats.size,
                                            voVault.size
                                        ) {
                                            "App: Wupp (com.wupp.meow)\n" +
                                                "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                                                "Engine: ${bridgeStatus.connectionState}\n" +
                                                "Chats cached: ${allChats.size}\n" +
                                                "View-Once vault items: ${voVault.size}"
                                        }
                                        ExpressiveSegmentedGroup(title = "LOCAL BUILD & DIAGNOSTICS") {
                                            ExpressiveSegmentedContainer(position = ExpressiveSegmentPosition.TOP) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Image(
                                                            painter = painterResource(R.drawable.wupp_logo),
                                                            contentDescription = "Wupp",
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                        )
                                                        Column {
                                                            Text(
                                                                text = "Wupp Companion",
                                                                color = colors.textPrimary,
                                                                fontSize = 17.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}",
                                                                color = colors.textSecondary,
                                                                fontFamily = mono,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(color = colors.divider)
                                                    Text(
                                                        text = diagText,
                                                        color = colors.textSecondary,
                                                        fontFamily = mono,
                                                        fontSize = 12.sp,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.ContentCopy,
                                                title = "Copy Local Diagnostics",
                                                subtitle = "Copy offline sync & build health summary to clipboard",
                                                position = ExpressiveSegmentPosition.MIDDLE,
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(diagText))
                                                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            ExpressiveSegmentedActionRow(
                                                icon = Icons.Rounded.CleaningServices,
                                                title = "Seed Local Showcase Dataset",
                                                subtitle = "Populate sample local threads & vault items for offline preview",
                                                position = ExpressiveSegmentPosition.BOTTOM,
                                                onClick = { viewModel.seedShowcaseData() }
                                            )
                                        }
                                    }
                                }

                                SettingsRoute.ROOT -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    // Current Mood Dialog
    if (showMoodDialog) {
        var moodDraft by remember(stealth.currentMood) { mutableStateOf(stealth.currentMood) }
        AlertDialog(
            onDismissRequest = { showMoodDialog = false },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = stringResource(R.string.settings_current_mood_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = moodDraft,
                        onValueChange = { if (it.length <= 60) moodDraft = it },
                        placeholder = { Text("Add an emoji or short status mood...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("😎 Chill", "🎧 Music", "🚀 Busy", "😴 Resting").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = colors.incomingBubble,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        haptic()
                                        moodDraft = preset
                                    }
                            ) {
                                Text(
                                    text = preset,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCurrentMood(moodDraft)
                        showMoodDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = Color(0xFF07090B)
                    )
                ) {
                    Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    if (stealth.currentMood.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.setCurrentMood("")
                                showMoodDialog = false
                            }
                        ) {
                            Text("Clear", color = colors.antiDeleteRed)
                        }
                    }
                    TextButton(onClick = { showMoodDialog = false }) {
                        Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                    }
                }
            }
        )
    }

    if (showViewOnceVaultDialog) {
        AlertDialog(
            onDismissRequest = { showViewOnceVaultDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("View-Once Media Vault", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                if (voVault.isEmpty()) {
                    Text(
                        text = "Captured view-once photos, videos, and voice notes will appear here.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(voVault, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.incomingBubble)
                                    .clickable {
                                        showViewOnceVaultDialog = false
                                        onOpenVaultMessage(item)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = WuppIcons.ViewOnce,
                                    contentDescription = null,
                                    tint = colors.brightGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.senderName.ifBlank { item.chatJid.substringBefore("@") },
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.msgType} · ${formatFullDateTime(item.timestamp)}",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewOnceVaultDialog = false }) {
                    Text(stringResource(R.string.common_close), color = colors.brightGreen)
                }
            }
        )
    }

    if (showPresenceLogsDialog) {
        AlertDialog(
            onDismissRequest = { showPresenceLogsDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("Presence & Status Logs", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                if (presenceLogs.isEmpty()) {
                    Text(
                        text = "No presence events logged locally yet.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presenceLogs.take(50), key = { it.id }) { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.incomingBubble)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = log.contactName.ifBlank { log.jid.substringBefore("@") },
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "${log.state} · ${formatFullDateTime(log.timestamp)}",
                                    color = colors.textSecondary,
                                    fontFamily = mono,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresenceLogsDialog = false }) {
                    Text(stringResource(R.string.common_close), color = colors.brightGreen)
                }
            }
        )
    }

    if (showProfileHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showProfileHistoryDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("Profile & Avatar History", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                if (profileHistory.isEmpty()) {
                    Text(
                        text = "No contact profile or avatar changes recorded yet.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(profileHistory.take(50), key = { it.id }) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.incomingBubble)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = entry.contactName.ifBlank { entry.jid.substringBefore("@") },
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                                val detail = entry.aboutText.ifBlank { "Profile / avatar captured" }
                                Text(
                                    text = "$detail · ${formatFullDateTime(entry.capturedAt)}",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileHistoryDialog = false }) {
                    Text(stringResource(R.string.common_close), color = colors.brightGreen)
                }
            }
        )
    }

    if (showBulkVerifyDialog) {
        var numbersCsv by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBulkVerifyDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("Bulk Number Verifier", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = numbersCsv,
                        onValueChange = { numbersCsv = it },
                        label = { Text("Phone numbers (comma separated)") },
                        placeholder = { Text("15551234567, 447700900123") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!bulkResult.isNullOrBlank()) {
                        Text(
                            text = bulkResult.orEmpty(),
                            color = colors.textSecondary,
                            fontFamily = mono,
                            fontSize = 11.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.verifyBulkNumbers(numbersCsv) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = Color(0xFF07090B)
                    )
                ) {
                    Text("Verify", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkVerifyDialog = false }) {
                    Text(stringResource(R.string.common_close), color = colors.textSecondary)
                }
            }
        )
    }

    editingAutoDlNetwork?.let { net ->
        val mask = when (net) {
            AutoDownloadNetwork.MOBILE -> stealth.autoDownloadMobileMask
            AutoDownloadNetwork.WIFI -> stealth.autoDownloadWifiMask
            AutoDownloadNetwork.ROAMING -> stealth.autoDownloadRoamingMask
        }
        AutoDownloadNetworkDialog(
            network = net,
            initialMask = mask,
            onDismiss = { editingAutoDlNetwork = null },
            onConfirmMask = { updatedMask ->
                viewModel.setAutoDownloadMask(net, updatedMask)
            }
        )
    }

    if (showGalleryContactsDialog) {
        GalleryVisibilityContactsDialog(
            chats = allChats,
            hiddenJids = stealth.hiddenGalleryJids,
            onToggleHideContact = { jid, hide ->
                viewModel.setChatMediaVisibility(jid, if (hide) false else null)
            },
            onDismiss = { showGalleryContactsDialog = false }
        )
    }

    if (showProfileEditorDialog) {
        ProfileEditorDialog(
            initialProfile = ownProfile,
            onSaveName = { viewModel.saveOwnPushName(it) },
            onSaveAbout = { viewModel.saveOwnAbout(it) },
            onDismiss = {
                showProfileEditorDialog = false
                scope.launch { viewModel.fetchOwnProfile().onSuccess { ownProfile = it } }
            }
        )
    }

    if (showAccountPrivacyDialog) {
        WhatsAppAccountPrivacyDialog(
            initialTimerSec = stealth.defaultDisappearingSec,
            onLoadPrivacy = { viewModel.fetchAccountPrivacy() },
            onUpdateCategory = { cat, valStr -> viewModel.setAccountPrivacy(cat, valStr) },
            onUpdateDefaultTimer = { sec -> viewModel.setDefaultDisappearingTimer(sec) },
            onOpenBlockedContacts = { showBlockedContactsDialog = true },
            onDismiss = { showAccountPrivacyDialog = false }
        )
    }

    if (showBlockedContactsDialog) {
        BlockedContactsDialog(
            dmCandidates = allChats,
            onLoadBlocked = { viewModel.fetchBlockedContacts() },
            onSetBlocked = { jid, blk -> viewModel.setContactBlocked(jid, blk) },
            onDismiss = { showBlockedContactsDialog = false }
        )
    }

    if (showStorageUsageDialog) {
        StorageUsageDialog(
            onLoadStats = { viewModel.loadStorageStats() },
            onClearTempCache = { viewModel.clearTempCache() },
            onDismiss = { showStorageUsageDialog = false }
        )
    }

    if (showFontSizeDialog) {
        ChatFontSizeDialog(
            currentScale = stealth.chatFontScale,
            onSelectScale = { scale -> viewModel.updateGlobalStealth { c -> c.copy(chatFontScale = scale) } },
            onDismiss = { showFontSizeDialog = false }
        )
    }

    if (showCompanionScopeDialog) {
        CompanionScopeInfoDialog(onDismiss = { showCompanionScopeDialog = false })
    }

    if (showWallpaperDialog) {
        WallpaperPickerDialog(
            currentUri = stealth.chatWallpaperUri,
            currentDim = stealth.chatWallpaperDim,
            showDoodleOnPresets = stealth.chatWallpaperShowDoodle,
            currentScale = stealth.chatWallpaperScale,
            currentOffsetX = stealth.chatWallpaperOffsetX,
            currentOffsetY = stealth.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false }
        )
    }

    if (showReactionDialog) {
        ReactionEmojiCustomizerDialog(
            currentEmojis = stealth.quickReactionEmojis,
            onSave = { viewModel.setQuickReactionEmojis(it) },
            onReset = { viewModel.resetQuickReactionEmojis() },
            onDismiss = { showReactionDialog = false }
        )
    }

    if (showAppLockDialog) {
        AppLockConfigDialog(
            config = appLockConfig,
            onDismiss = { showAppLockDialog = false }
        )
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            currentLanguage = stealth.appLanguage,
            onSelectLanguage = { chosen ->
                val changed = chosen != stealth.appLanguage
                viewModel.setAppLanguage(chosen)
                if (changed) {
                    context.findFragmentActivity()?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showHideNotifGuide) {
        AlertDialog(
            onDismissRequest = { showHideNotifGuide = false },
            containerColor = colors.elevatedBar,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hide Constant Banner",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android requires a background service for 24/7 WhatsApp sync and doesn't let apps mute that status notification automatically.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.appSurface)
                            .border(0.8.dp, colors.accentGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "On the next screen (1 tap):",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Switch \"Show notifications\" OFF at the top of \"Wupp Realtime Sync\".",
                            color = colors.accentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        text = "• Real-time sync stays 100% active\n• Chat message alerts & sounds keep working",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideNotifGuide = false
                        WaRealtimeService.openNotificationChannelSettings(
                            context = context,
                            channelId = WaRealtimeService.CHANNEL_ID
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentGreen,
                        contentColor = Color(0xFF07090B)
                    )
                ) {
                    Text("Open Setting", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideNotifGuide = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsReferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.textSecondary,
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.pillActiveBg)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    color = colors.pillActiveText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
