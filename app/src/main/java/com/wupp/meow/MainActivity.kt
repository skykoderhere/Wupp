package com.wupp.meow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wupp.meow.core.service.WaRealtimeService
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.ui.MainViewModel
import com.wupp.meow.ui.components.AppLockOverlay
import com.wupp.meow.ui.screens.ChatScreen
import com.wupp.meow.ui.screens.FirstInstallSetupScreen
import com.wupp.meow.ui.screens.HomeScreen
import com.wupp.meow.ui.screens.LinkDeviceScreen
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocaleHelper
import com.wupp.meow.ui.theme.WhatsAppTheme

class MainActivity : FragmentActivity() {
    private val pendingOpenChatJid = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.getStringExtra("open_chat_jid")?.takeIf { it.isNotBlank() }?.let { jid ->
            pendingOpenChatJid.value = jid
            intent?.removeExtra("open_chat_jid")
        }

        val setupPrefs = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
        val initialSetupDone = setupPrefs.getBoolean("setup_completed_v1", false)
        if (initialSetupDone) {
            WaRealtimeService.ensureStarted(this)
        }

        setContent {
            val vm: MainViewModel = viewModel()
            val stealth by vm.stealthConfig.collectAsState()
            val appLockConfig by vm.appLockConfig.collectAsState()
            val isAppLocked by vm.isAppLocked.collectAsState()
            val bridgeStatus by vm.bridgeStatus.collectAsState()
            var setupDone by remember { mutableStateOf(initialSetupDone) }
            var selectedChatJid by rememberSaveable { mutableStateOf<String?>(null) }
            val requestedChatJid by pendingOpenChatJid.collectAsState()

            LaunchedEffect(appLockConfig) {
                AppLockManager.applyWindowSecureFlag(this@MainActivity, appLockConfig)
            }

            LaunchedEffect(requestedChatJid, setupDone, bridgeStatus.isLoggedIn, isAppLocked) {
                val target = requestedChatJid
                if (!target.isNullOrBlank() && setupDone && bridgeStatus.isLoggedIn && !isAppLocked) {
                    selectedChatJid = target
                    pendingOpenChatJid.value = null
                }
            }

            BackHandler(enabled = setupDone && bridgeStatus.isLoggedIn && selectedChatJid != null && !isAppLocked) {
                selectedChatJid = null
            }

            WhatsAppTheme(
                themeMode = stealth.themeMode,
                accentColor = stealth.accentColor,
                useSystemFont = stealth.useSystemFont,
                showSecondsInTimestamps = stealth.showSecondsInTimestamps,
                chatFontScale = stealth.chatFontScale,
                classicUi = stealth.classicWhatsAppLook,
                hapticsEnabled = stealth.hapticFeedbackEnabled,
                assetManager = assets
            ) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                val motionScheme = MaterialTheme.motionScheme

                if (!setupDone) {
                    FirstInstallSetupScreen(
                        onCompleteSetup = {
                            setupPrefs.edit().putBoolean("setup_completed_v1", true).apply()
                            setupDone = true
                            WaRealtimeService.ensureStarted(this@MainActivity)
                        }
                    )
                } else if (!bridgeStatus.engineReady) {
                    val colors = LocalWaColors.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.appSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.wupp_logo),
                                contentDescription = "Wupp",
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(22.dp))
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            ContainedLoadingIndicator(
                                containerColor = colors.pillActiveBg,
                                indicatorColor = colors.brightGreen,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                } else if (!bridgeStatus.isLoggedIn) {
                    LinkDeviceScreen(
                        bridgeStatus = bridgeStatus,
                        onPairPhone = { phone, onCode -> vm.pairWithPhoneNumber(phone, onCode) },
                        onRefreshConnection = { vm.reconnectSocket() }
                    )
                } else {
                    var homeTab by rememberSaveable { mutableIntStateOf(0) }
                    val chatsListState = rememberLazyListState()
                    val groupsListState = rememberLazyListState()
                    val updatesListState = rememberLazyListState()
                    val communitiesListState = rememberLazyListState()
                    val toolsListState = rememberLazyListState()

                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    AnimatedContent(
                        targetState = selectedChatJid,
                        transitionSpec = {
                            if (targetState != null) {
                                (slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) { it / 3 } + scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) + fadeIn(
                                    animationSpec = motionScheme.fastEffectsSpec()
                                )) togetherWith (slideOutHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) { -it / 6 } + fadeOut(
                                    animationSpec = motionScheme.fastEffectsSpec()
                                ))
                            } else {
                                (slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) { -it / 6 } + fadeIn(
                                    animationSpec = motionScheme.fastEffectsSpec()
                                )) togetherWith (slideOutHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) { it / 3 } + scaleOut(
                                    targetScale = 0.96f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) + fadeOut(
                                    animationSpec = motionScheme.fastEffectsSpec()
                                ))
                            }
                        },
                        label = "home_chat_route"
                    ) { activeJid ->
                        if (activeJid == null) {
                            HomeScreen(
                                viewModel = vm,
                                onOpenChat = { jid -> selectedChatJid = jid },
                                currentTab = homeTab,
                                onSelectTab = { homeTab = it },
                                chatsListState = chatsListState,
                                groupsListState = groupsListState,
                                updatesListState = updatesListState,
                                communitiesListState = communitiesListState,
                                toolsListState = toolsListState
                            )
                        } else {
                            ChatScreen(
                                chatJid = activeJid,
                                viewModel = vm,
                                onBack = { selectedChatJid = null }
                            )
                        }
                    }
                }

                if (setupDone && isAppLocked) {
                    AppLockOverlay(config = appLockConfig)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_chat_jid")?.takeIf { it.isNotBlank() }?.let { jid ->
            pendingOpenChatJid.value = jid
            intent.removeExtra("open_chat_jid")
        }
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onAppForegrounded()
        WaRealtimeService.isUiForeground = true
        val setupDone = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        if (setupDone) {
            WaRealtimeService.ensureStarted(this)
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
        WaRealtimeService.isUiForeground = false
    }
}
