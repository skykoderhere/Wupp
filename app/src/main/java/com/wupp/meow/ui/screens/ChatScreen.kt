package com.wupp.meow.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.wupp.meow.R
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.data.media.OutboundQualityMode
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.MainViewModel
import com.wupp.meow.ui.QuotedSenderMeta
import com.wupp.meow.ui.ResolvedParticipantUi
import com.wupp.meow.ui.components.ChatEmojiPickerDock
import com.wupp.meow.ui.components.ChatMediaVisibilitySelector
import com.wupp.meow.ui.components.DocumentMessageCard
import com.wupp.meow.ui.components.DoodleBackground
import com.wupp.meow.ui.components.FullScreenMediaViewer
import com.wupp.meow.ui.components.InAppPdfViewerDialog
import com.wupp.meow.ui.components.InlineTimestampMessage
import com.wupp.meow.ui.components.MessageMetaCluster
import com.wupp.meow.ui.components.OutboundMediaPreviewDialog
import com.wupp.meow.ui.components.ProfilePhotoLightbox
import com.wupp.meow.ui.components.StagedOutboundMedia
import com.wupp.meow.ui.components.SwipeToReplyBox
import com.wupp.meow.ui.components.VoiceNoteBubble
import com.wupp.meow.ui.components.WallpaperPickerDialog
import com.wupp.meow.ui.components.WhatsAppBubbleShape
import com.wupp.meow.ui.components.WhatsAppClusterInnerShape
import com.wupp.meow.ui.components.WuppGlassContainer
import com.wupp.meow.ui.components.WuppIcons
import com.wupp.meow.ui.components.buildDocumentExportFileName
import com.wupp.meow.ui.components.deleteBeforeSelection
import com.wupp.meow.ui.components.expressivePressScale
import com.wupp.meow.ui.components.formatMessageClock
import com.wupp.meow.ui.components.insertTextAtSelection
import com.wupp.meow.ui.components.isDocumentMessage
import com.wupp.meow.ui.components.openDocumentNatively
import com.wupp.meow.ui.components.rememberWuppBackdropState
import com.wupp.meow.ui.components.resolveDocumentDisplayName
import com.wupp.meow.ui.components.resolveDocumentMimeType
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWaMonoFamily
import com.wupp.meow.ui.theme.LocalWuppShowSeconds
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ChatScreen(
    chatJid: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val haptic = rememberWuppHapticTrigger()
    val showSeconds = LocalWuppShowSeconds.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    DisposableEffect(chatJid) {
        viewModel.setActiveChat(chatJid)
        onDispose { viewModel.setActiveChat(null) }
    }

    val chat by remember(chatJid) { viewModel.observeChat(chatJid) }.collectAsState(initial = null)
    val contact by remember(chatJid) { viewModel.observeContact(chatJid) }.collectAsState(initial = null)
    val isChannel = chatJid.endsWith("@newsletter")
    val isGroupAnnounceLocked by remember(chatJid) { viewModel.isGroupAnnounceLocked(chatJid) }.collectAsState(initial = false)
    val effectivePrivacy by remember(chatJid) { viewModel.observeEffectivePrivacy(chatJid) }.collectAsState(initial = null)
    val stealthConfig by viewModel.stealthConfig.collectAsState()
    val effectiveWallpaper = remember(chatJid, stealthConfig) {
        StealthMatrixManager.resolveChatWallpaper(chatJid, cfg = stealthConfig)
    }
    val hasChatWallpaperOverride = remember(chatJid, stealthConfig) {
        StealthMatrixManager.hasChatWallpaperOverride(chatJid, cfg = stealthConfig)
    }
    val canSendOutbound by remember(chatJid) { viewModel.canInitiateOutbound(chatJid) }.collectAsState(initial = true)
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val downloadingMediaIds by viewModel.downloadingMediaIds.collectAsState()
    val failedMediaIds by viewModel.failedMediaIds.collectAsState()
    val pagedItems = remember(chatJid) { viewModel.pagedMessages(chatJid) }.collectAsLazyPagingItems()
    val listState = remember(chatJid) { LazyListState() }
    var scrollToBottomPending by remember(chatJid) { mutableStateOf(false) }
    var highlightedMessageId by remember(chatJid) { mutableStateOf<String?>(null) }
    var activeJumpJob by remember(chatJid) { mutableStateOf<Job?>(null) }
    val quotedNotFoundLabel = stringResource(R.string.chat_quoted_not_found)

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(1500L)
            highlightedMessageId = null
        }
    }

    val jumpToQuotedMessage: (String) -> Unit = { rawQuotedId ->
        val qid = rawQuotedId.trim()
        if (qid.isNotEmpty()) {
            activeJumpJob?.cancel()
            activeJumpJob = scope.launch {
                val immediateIdx = findLoadedQuotedItemIndex(pagedItems.itemSnapshotList, qid)
                if (immediateIdx >= 0) {
                    if (abs(immediateIdx - listState.firstVisibleItemIndex) <= 35) {
                        listState.animateScrollToItem(immediateIdx)
                    } else {
                        listState.scrollToItem(immediateIdx)
                    }
                    highlightedMessageId = qid
                    return@launch
                }

                val existsInChat = viewModel.findMessageExistsInChat(chatJid, qid)
                if (!existsInChat) {
                    Toast.makeText(context, quotedNotFoundLabel, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var foundIdx = -1
                var step = 0
                while (step < 40) {
                    val prevCount = pagedItems.itemCount
                    if (prevCount > 0) {
                        pagedItems[prevCount - 1]
                    }
                    withTimeoutOrNull(2000L) {
                        snapshotFlow { pagedItems.itemSnapshotList to pagedItems.loadState.append }
                            .first { (snap, appendState) ->
                                findLoadedQuotedItemIndex(snap, qid) >= 0 ||
                                    snap.size > prevCount ||
                                    (appendState is LoadState.NotLoading && appendState.endOfPaginationReached)
                            }
                    }
                    foundIdx = findLoadedQuotedItemIndex(pagedItems.itemSnapshotList, qid)
                    if (foundIdx >= 0) break
                    val appendState = pagedItems.loadState.append
                    if (appendState is LoadState.NotLoading &&
                        appendState.endOfPaginationReached &&
                        pagedItems.itemCount == prevCount
                    ) {
                        break
                    }
                    step++
                }

                if (foundIdx >= 0) {
                    if (abs(foundIdx - listState.firstVisibleItemIndex) <= 35) {
                        listState.animateScrollToItem(foundIdx)
                    } else {
                        listState.scrollToItem(foundIdx)
                    }
                    highlightedMessageId = qid
                } else {
                    Toast.makeText(context, quotedNotFoundLabel, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var lastLatestMsgId by remember(chatJid) { mutableStateOf<String?>(null) }
    var latestVisibleMsg: MessageEntity? = null
    val latestPeekCount = minOf(pagedItems.itemCount, 8)
    for (idx in 0 until latestPeekCount) {
        val candidate = pagedItems.peek(idx)
        if (candidate != null && !isHiddenPlaceholderMessage(candidate)) {
            latestVisibleMsg = candidate
            break
        }
    }
    LaunchedEffect(chatJid, latestVisibleMsg?.id, pagedItems.itemCount) {
        val newest = latestVisibleMsg ?: return@LaunchedEffect
        val prevId = lastLatestMsgId
        lastLatestMsgId = newest.id
        if (prevId == null) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
            scrollToBottomPending = false
        } else if (newest.id != prevId) {
            val nearBottom = listState.firstVisibleItemIndex <= 3
            if (scrollToBottomPending || newest.isFromMe || nearBottom) {
                scrollToBottomPending = false
                listState.animateScrollToItem(0)
            }
        }
    }
    val allChats by viewModel.allChats.collectAsState()
    val participantMeta by viewModel.participantMeta.collectAsState()
    val quotedMsgMeta by viewModel.quotedMsgMeta.collectAsState()
    val chatsByJid = remember(allChats) { allChats.associateBy { it.jid } }
    val loadedSnapshot = pagedItems.itemSnapshotList.items
    val loadedMessagesById = remember(loadedSnapshot) { loadedSnapshot.associateBy { it.id } }
    val senderPushByJid = remember(loadedSnapshot) {
        buildMap {
            for (m in loadedSnapshot) {
                val sJid = m.senderJid.trim()
                val sName = m.senderName.trim()
                if (sJid.isNotBlank() && isMeaningfulDisplayName(sName, sJid) && !containsKey(sJid)) {
                    put(sJid, sName)
                }
            }
        }
    }
    LaunchedEffect(chatJid, pagedItems.itemCount) {
        val snap = pagedItems.itemSnapshotList.items
        val jids = linkedSetOf<String>()
        val quotedIds = linkedSetOf<String>()
        for (m in snap) {
            if (m.senderJid.contains("@")) jids += m.senderJid
            val q = m.quotedSender?.trim().orEmpty()
            if (q.contains("@")) jids += q
            val qid = m.quotedId?.trim().orEmpty()
            if (qid.isNotBlank() && !loadedMessagesById.containsKey(qid)) {
                quotedIds += qid
            }
        }
        if (jids.isNotEmpty()) viewModel.resolveParticipantsLocally(jids)
        if (quotedIds.isNotEmpty()) viewModel.resolveQuotedSendersLocally(quotedIds)
    }

    var draftValue by rememberSaveable(chatJid, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var ghostMentionEveryone by remember { mutableStateOf(false) }
    var qualityMode by remember { mutableStateOf(OutboundQualityMode.HD) }
    var showAttachTray by remember { mutableStateOf(false) }
    var showEmojiPicker by remember(chatJid) { mutableStateOf(false) }
    var holdDockForIme by remember(chatJid) { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val draftFocusRequester = remember { FocusRequester() }
    val draftInteractionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val maxLandscapeDockDp = (LocalConfiguration.current.screenHeightDp * 0.48f).dp
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeExclusivePx = (imeBottomPx - navBarBottomPx).coerceAtLeast(0)
    val imeExclusiveDp = with(density) { imeExclusivePx.toDp() }
    val uiPrefs = remember(context) {
        context.getSharedPreferences("wupp_ui_prefs", Context.MODE_PRIVATE)
    }
    var rememberedKeyboardDp by remember {
        mutableStateOf(uiPrefs.getFloat("cached_ime_height_dp", 296f).coerceIn(220f, 420f).dp)
    }
    LaunchedEffect(imeExclusivePx) {
        val minImePx = with(density) { 120.dp.toPx() }
        if (imeExclusivePx > minImePx) {
            val measured = with(density) { imeExclusivePx.toDp().value }.coerceIn(220f, 420f)
            if (abs(measured - rememberedKeyboardDp.value) > 4f) {
                rememberedKeyboardDp = measured.dp
                uiPrefs.edit().putFloat("cached_ime_height_dp", measured).apply()
            }
        }
    }
    LaunchedEffect(holdDockForIme, imeExclusivePx) {
        if (holdDockForIme) {
            if (imeExclusivePx > 0) {
                holdDockForIme = false
            } else {
                delay(320L)
                holdDockForIme = false
            }
        }
    }
    LaunchedEffect(draftInteractionSource) {
        draftInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release && showEmojiPicker) {
                holdDockForIme = true
                showEmojiPicker = false
                keyboardController?.show()
            }
        }
    }
    LaunchedEffect(isRecordingVoice, isGroupAnnounceLocked, canSendOutbound) {
        if (isRecordingVoice || isGroupAnnounceLocked || !canSendOutbound) {
            showEmojiPicker = false
            holdDockForIme = false
        }
    }

    var showSearchInChat by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var inChatMatches by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }

    var showGhostOverrideSheet by remember { mutableStateOf(false) }
    var editHistoryTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var groupReceiptsTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var actionMenuTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var showMoreReactionEmojis by remember { mutableStateOf(false) }
    var editMessageDialogTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaLightboxTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var pdfViewerTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var pendingOutboundPreview by remember { mutableStateOf<StagedOutboundMedia?>(null) }
    var showProfilePhotoLightbox by remember { mutableStateOf(false) }
    var participantAvatarTarget by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var showWallpaperDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showEmojiPicker) {
        showEmojiPicker = false
        holdDockForIme = false
    }

    BackHandler(enabled = actionMenuTarget != null) {
        actionMenuTarget = null
        showMoreReactionEmojis = false
        showSelectionOverflow = false
    }

    val typingComposition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/typing_indicator_bounce_v3.json")
    )

    fun handlePickedAttachment(uri: Uri, fallbackMime: String) {
        val mime = context.contentResolver.getType(uri) ?: fallbackMime
        showAttachTray = false
        if (mime.startsWith("image/", ignoreCase = true) || mime.startsWith("video/", ignoreCase = true)) {
            scope.launch {
                pendingOutboundPreview = viewModel.stageMediaForPreview(uri, mime)
            }
        } else {
            scrollToBottomPending = true
            viewModel.sendPickedMedia(chatJid, uri, mime, draftValue.text.trim(), qualityMode)
            draftValue = TextFieldValue("")
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handlePickedAttachment(uri, "image/jpeg")
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handlePickedAttachment(uri, "application/octet-stream")
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.toggleVoiceRecording(chatJid, granted)
    }

    LaunchedEffect(inChatQuery, showSearchInChat) {
        inChatMatches = if (showSearchInChat && inChatQuery.trim().length >= 2) {
            viewModel.searchInChat(chatJid, inChatQuery)
        } else emptyList()
    }

    val wallpaperBackdropState = rememberWuppBackdropState()

    Scaffold(
        containerColor = colors.chatCanvas,
        topBar = {
            val topMotion = MaterialTheme.motionScheme
            Column {
                AnimatedContent(
                    targetState = actionMenuTarget,
                    transitionSpec = {
                        (fadeIn(topMotion.fastEffectsSpec()) + slideInVertically(topMotion.fastSpatialSpec()) { -it / 4 }) togetherWith
                            (fadeOut(topMotion.fastEffectsSpec()) + slideOutVertically(topMotion.fastSpatialSpec()) { -it / 4 })
                    },
                    contentKey = { it?.id ?: "chat_normal_topbar" },
                    label = "chat_topbar_transition"
                ) { selectedMsg ->
                    if (selectedMsg != null) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.appSurface,
                            titleContentColor = colors.textPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    actionMenuTarget = null
                                    showMoreReactionEmojis = false
                                    showSelectionOverflow = false
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Clear selection", tint = colors.textPrimary)
                            }
                        },
                        title = {
                            Text(
                                text = "1",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        },
                        actions = {
                            if (!isChannel && !isGroupAnnounceLocked) {
                                IconButton(
                                    onClick = {
                                        replyTarget = selectedMsg
                                        actionMenuTarget = null
                                        showMoreReactionEmojis = false
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Reply, "Reply", tint = colors.textPrimary)
                                }
                            }
                            if (selectedMsg.body.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(selectedMsg.body))
                                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                        actionMenuTarget = null
                                        showMoreReactionEmojis = false
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "Copy", tint = colors.textPrimary)
                                }
                            }
                            if (chat?.isGroup == true || selectedMsg.isEdited) {
                                IconButton(
                                    onClick = {
                                        if (chat?.isGroup == true) {
                                            groupReceiptsTarget = selectedMsg
                                        } else {
                                            editHistoryTarget = selectedMsg
                                        }
                                        actionMenuTarget = null
                                        showMoreReactionEmojis = false
                                    }
                                ) {
                                    Icon(Icons.Rounded.Info, "Message info", tint = colors.textPrimary)
                                }
                            }
                            IconButton(
                                onClick = {
                                    deleteConfirmTarget = selectedMsg
                                    actionMenuTarget = null
                                    showMoreReactionEmojis = false
                                    showSelectionOverflow = false
                                }
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = colors.textPrimary)
                            }
                            Box {
                                IconButton(onClick = { showSelectionOverflow = true }) {
                                    Icon(Icons.Rounded.MoreVert, "More actions", tint = colors.textPrimary)
                                }
                                DropdownMenu(
                                    expanded = showSelectionOverflow,
                                    onDismissRequest = { showSelectionOverflow = false }
                                ) {
                                    if (selectedMsg.isFromMe && selectedMsg.msgType == "TEXT") {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                showSelectionOverflow = false
                                                editMessageDialogTarget = selectedMsg
                                                actionMenuTarget = null
                                                showMoreReactionEmojis = false
                                            }
                                        )
                                    }
                                    if (selectedMsg.isEdited) {
                                        DropdownMenuItem(
                                            text = { Text("Edit history") },
                                            onClick = {
                                                showSelectionOverflow = false
                                                editHistoryTarget = selectedMsg
                                                actionMenuTarget = null
                                                showMoreReactionEmojis = false
                                            }
                                        )
                                    }
                                    if (!selectedMsg.localMediaPath.isNullOrBlank()) {
                                        val isSelectedDoc = isDocumentMessage(selectedMsg)
                                        if (isSelectedDoc) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.chat_doc_open_with)) },
                                                onClick = {
                                                    showSelectionOverflow = false
                                                    val docFile = File(selectedMsg.localMediaPath)
                                                    if (docFile.exists()) {
                                                        openDocumentNatively(context, selectedMsg, docFile) { pdfFile ->
                                                            pdfViewerTarget = pdfFile to resolveDocumentDisplayName(
                                                                selectedMsg,
                                                                resolveDocumentMimeType(selectedMsg, pdfFile),
                                                                pdfFile
                                                            )
                                                        }
                                                    } else {
                                                        viewModel.requestMediaDownload(selectedMsg, userInitiated = true)
                                                    }
                                                    actionMenuTarget = null
                                                    showMoreReactionEmojis = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isSelectedDoc) stringResource(R.string.chat_doc_save_downloads)
                                                    else "Save to Gallery"
                                                )
                                            },
                                            onClick = {
                                                showSelectionOverflow = false
                                                val targetFile = File(selectedMsg.localMediaPath)
                                                val exportName = if (isSelectedDoc) {
                                                    buildDocumentExportFileName(selectedMsg, targetFile)
                                                } else {
                                                    "WA_${selectedMsg.msgType}_${selectedMsg.id}.${targetFile.extension.ifBlank { "bin" }}"
                                                }
                                                val exportMime = if (isSelectedDoc) {
                                                    resolveDocumentMimeType(selectedMsg, targetFile)
                                                } else {
                                                    selectedMsg.mimeType
                                                }
                                                viewModel.exportMediaToGallery(
                                                    selectedMsg.localMediaPath,
                                                    exportName,
                                                    exportMime
                                                )
                                                actionMenuTarget = null
                                                showMoreReactionEmojis = false
                                            }
                                        )
                                    }
                                    if (chat?.isGroup == true) {
                                        DropdownMenuItem(
                                            text = { Text("Message info") },
                                            onClick = {
                                                showSelectionOverflow = false
                                                groupReceiptsTarget = selectedMsg
                                                actionMenuTarget = null
                                                showMoreReactionEmojis = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    } else {
                    TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.appSurface,
                        titleContentColor = colors.textPrimary
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic()
                                onBack()
                            },
                            modifier = Modifier.testTag("chat_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.textPrimary)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clickable { showProfilePhotoLightbox = true }) {
                                WhatsAppAvatar(
                                    jid = chatJid,
                                    avatarPath = chat?.avatarPath,
                                    isGroup = chat?.isGroup == true,
                                    size = 38.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { showGhostOverrideSheet = true }
                            ) {
                                Text(
                                    text = chat?.name?.ifBlank { chatJid.substringBefore("@") }
                                        ?: chatJid.substringBefore("@"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val effectiveAbout = remember(chat?.aboutText, contact?.aboutText) {
                                    chat?.aboutText?.takeIf { it.isNotBlank() } ?: contact?.aboutText.orEmpty()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val presence = chat?.chatPresence.orEmpty()
                                    if (presence.isNotBlank()) {
                                        if (typingComposition != null) {
                                            LottieAnimation(
                                                composition = typingComposition,
                                                iterations = LottieConstants.IterateForever,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = presence,
                                            fontSize = 12.sp,
                                            color = colors.brightGreen
                                        )
                                    } else {
                                        val sub = when {
                                            isChannel -> "Public channel"
                                            chat?.isOnline == true -> stringResource(R.string.chat_status_online)
                                            (chat?.lastSeen ?: 0L) > 0L -> "last seen ${formatMessageClock(chat!!.lastSeen, includeSeconds = showSeconds)}"
                                            effectiveAbout.isNotBlank() -> effectiveAbout
                                            else -> ""
                                        }
                                        if (sub.isNotBlank()) {
                                            Text(
                                                text = sub,
                                                fontSize = 12.sp,
                                                color = if (chat?.isOnline == true && !isChannel) colors.brightGreen else colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (stealthConfig.alwaysShowContactBio &&
                                    effectiveAbout.isNotBlank() &&
                                    !isChannel &&
                                    !chatJid.endsWith("@g.us") &&
                                    (chat?.isOnline == true || (chat?.lastSeen ?: 0L) > 0L || !chat?.chatPresence.isNullOrBlank())
                                ) {
                                    Text(
                                        text = effectiveAbout,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (isChannel) {
                            IconButton(
                                onClick = {
                                    viewModel.toggleChannelMute(chatJid, chat?.isMuted == true)
                                }
                            ) {
                                Icon(
                                    imageVector = if (chat?.isMuted == true) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                    contentDescription = if (chat?.isMuted == true) "Unmute channel" else "Mute channel",
                                    tint = colors.textPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Rounded.Videocam, "Video call", tint = colors.textPrimary)
                            }
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Rounded.Call, "Voice call", tint = colors.textPrimary)
                            }
                        }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Rounded.MoreVert, "More", tint = colors.textPrimary)
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("View contact & privacy") },
                                onClick = {
                                    showOverflow = false
                                    showGhostOverrideSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search") },
                                onClick = {
                                    showOverflow = false
                                    showSearchInChat = !showSearchInChat
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark as read") },
                                onClick = {
                                    showOverflow = false
                                    viewModel.markChatReadExplicitly(chatJid)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (chat?.isPinned == true) stringResource(R.string.chat_action_unpin)
                                        else stringResource(R.string.chat_action_pin)
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    chat?.let { viewModel.togglePinChat(it) }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (chat?.isMuted == true) stringResource(R.string.chat_action_unmute)
                                        else stringResource(R.string.chat_action_mute)
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    viewModel.muteChats(listOf(chatJid), chat?.isMuted != true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (chat?.isFavorite == true) "Remove from Favorites" else "Add to Favorites") },
                                onClick = {
                                    showOverflow = false
                                    chat?.let { viewModel.toggleFavoriteChat(it) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat wallpaper") },
                                onClick = {
                                    showOverflow = false
                                    showWallpaperDialog = true
                                }
                            )
                        }
                    }
                )
                    }
                }

                AnimatedVisibility(visible = showSearchInChat && actionMenuTarget == null) {
                    var inChatMatchIndex by remember(inChatMatches) { mutableStateOf(0) }
                    LaunchedEffect(inChatMatches, inChatMatchIndex) {
                        inChatMatches.getOrNull(inChatMatchIndex)?.let { match ->
                            jumpToQuotedMessage(match.id)
                        }
                    }
                    Column(modifier = Modifier.background(colors.elevatedBar).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = inChatQuery,
                            onValueChange = { inChatQuery = it },
                            placeholder = { Text("Search messages or voice transcripts...") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inChatMatches.isNotEmpty()) {
                                        IconButton(
                                            enabled = inChatMatchIndex < inChatMatches.lastIndex,
                                            onClick = {
                                                if (inChatMatchIndex < inChatMatches.lastIndex) {
                                                    inChatMatchIndex += 1
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, "Older match")
                                        }
                                        IconButton(
                                            enabled = inChatMatchIndex > 0,
                                            onClick = {
                                                if (inChatMatchIndex > 0) {
                                                    inChatMatchIndex -= 1
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, "Newer match")
                                        }
                                    }
                                    IconButton(onClick = { showSearchInChat = false; inChatQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "Close")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (inChatMatches.isNotEmpty()) {
                            val currentMatch = inChatMatches.getOrElse(inChatMatchIndex) { inChatMatches.first() }
                            val snippet = currentMatch.body.ifBlank { currentMatch.audioTranscript.orEmpty() }
                            Text(
                                text = "${inChatMatchIndex + 1} / ${inChatMatches.size} · \"$snippet\"",
                                fontSize = 11.5.sp,
                                color = colors.brightGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (inChatQuery.trim().length >= 2) {
                            Text(
                                text = "No matches found",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        DoodleBackground(
            wallpaperUriOverride = effectiveWallpaper.uri,
            wallpaperDimOverride = effectiveWallpaper.dim,
            showDoodleOverride = effectiveWallpaper.showDoodle,
            wallpaperScaleOverride = effectiveWallpaper.scale,
            wallpaperOffsetXOverride = effectiveWallpaper.offsetXFraction,
            wallpaperOffsetYOverride = effectiveWallpaper.offsetYFraction,
            backdropState = if (stealthConfig.classicWhatsAppLook) null else wallpaperBackdropState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Error 463 Ban Guard Banner on unsaved first-contact chats (not channels or admin-only groups)
                if (!isChannel && !isGroupAnnounceLocked && !canSendOutbound) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.antiDeleteRed.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = colors.antiDeleteRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Error 463 Guard: 0 prior messages with unsaved contact. Send initial greeting from official primary app first.",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Paging 3 reverseLayout timeline + Floating WhatsApp Reaction Bar overlay
                val showScrollToBottom by remember(listState) {
                    derivedStateOf { listState.firstVisibleItemIndex > 2 }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id }
                    ) { index ->
                        val msg = pagedItems[index] ?: return@items
                        if (isHiddenPlaceholderMessage(msg)) {
                            return@items
                        }
                        // Remember: reverseLayout=true -> index + 1 is visually ABOVE index
                        var nextVisibleIdx = index + 1
                        var above: MessageEntity? = null
                        while (nextVisibleIdx < pagedItems.itemCount) {
                            val candidate = pagedItems.peek(nextVisibleIdx)
                            if (candidate != null && !isHiddenPlaceholderMessage(candidate)) {
                                above = candidate
                                break
                            }
                            nextVisibleIdx++
                        }
                        val showDateHeader = above == null || !isSameCalendarDay(msg.timestamp, above.timestamp)
                        val isFirstInCluster = above == null ||
                            showDateHeader ||
                            above.isFromMe != msg.isFromMe ||
                            above.senderJid != msg.senderJid
                        val isSelected = actionMenuTarget?.id == msg.id
                        val isHighlighted = highlightedMessageId == msg.id
                        val rowBgColor by animateColorAsState(
                            targetValue = when {
                                isHighlighted -> colors.brightGreen.copy(alpha = 0.24f)
                                isSelected -> colors.brightGreen.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 360),
                            label = "msg_row_highlight_bg"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBgColor)
                        ) {
                            if (showDateHeader && msg.timestamp > 0L) {
                                DateHeaderPill(timestamp = msg.timestamp)
                            }
                            Spacer(modifier = Modifier.height(if (isFirstInCluster || showDateHeader) 8.dp else 2.dp))

                            if (msg.msgType == "SYSTEM") {
                                SystemNoticePill(text = msg.body)
                            } else {
                                val channelDisplayName = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
                                val isGroupChat = chat?.isGroup == true && !isChannel
                                val senderMeta = participantMeta[msg.senderJid]
                                val altJid = senderMeta?.altJid.orEmpty()
                                val senderAvatarPath = senderMeta?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: chatsByJid[msg.senderJid]?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: altJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it]?.avatarPath?.takeIf { p -> p.isNotBlank() } }
                                val senderDisplay = resolveSenderDisplayInfo(
                                    rawJidOrText = msg.senderJid,
                                    explicitName = msg.senderName,
                                    isSelf = msg.isFromMe || senderMeta?.isSelf == true,
                                    participantMeta = participantMeta,
                                    chatsByJid = chatsByJid,
                                    senderPushByJid = senderPushByJid
                                )
                                val quotedDisplay = if (!msg.quotedId.isNullOrBlank()) {
                                    resolveQuotedBannerSender(
                                        msg = msg,
                                        isChannel = isChannel,
                                        channelDisplayName = channelDisplayName,
                                        loadedMessagesById = loadedMessagesById,
                                        quotedMsgMeta = quotedMsgMeta,
                                        participantMeta = participantMeta,
                                        chatsByJid = chatsByJid,
                                        senderPushByJid = senderPushByJid
                                    )
                                } else null
                                val clickAvatar: () -> Unit = {
                                    participantAvatarTarget = Triple(
                                        msg.senderJid,
                                        senderDisplay.primary,
                                        senderAvatarPath
                                    )
                                }
                                val handleOpenDocument: () -> Unit = {
                                    val docFile = msg.localMediaPath?.takeIf { it.isNotBlank() }?.let(::File)
                                    if (docFile != null && docFile.exists() && docFile.length() > 0L) {
                                        openDocumentNatively(context, msg, docFile) { pdfFile ->
                                            pdfViewerTarget = pdfFile to resolveDocumentDisplayName(
                                                msg,
                                                resolveDocumentMimeType(msg, pdfFile),
                                                pdfFile
                                            )
                                        }
                                    } else {
                                        viewModel.requestMediaDownload(msg, userInitiated = true)
                                    }
                                }
                                if (isChannel || isGroupAnnounceLocked) {
                                    MessageBubbleRow(
                                        msg = msg,
                                        isGroup = isGroupChat,
                                        isChannel = isChannel,
                                        channelDisplayName = channelDisplayName,
                                        showTail = if (isChannel) false else isFirstInCluster,
                                        senderDisplay = senderDisplay,
                                        senderAvatarPath = senderAvatarPath,
                                        quotedDisplay = quotedDisplay,
                                        onClickSenderAvatar = clickAvatar,
                                        ghostPlayedActive = effectivePrivacy?.sendPlayedReceipts == false,
                                        isDownloadingMedia = downloadingMediaIds.contains(msg.id),
                                        mediaDownloadError = failedMediaIds[msg.id],
                                        onClickEditBadge = { editHistoryTarget = msg },
                                        onClickQuoted = jumpToQuotedMessage,
                                        onManualSendPlayedReceipt = { viewModel.markAudioPlayedExplicitly(msg) },
                                        onAutoRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = false) },
                                        onRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = true) },
                                        onOpenMedia = { mediaLightboxTarget = msg },
                                        onOpenDocument = handleOpenDocument,
                                        onLongPress = {
                                            haptic(HapticFeedbackType.LongPress)
                                            showMoreReactionEmojis = false
                                            actionMenuTarget = msg
                                        }
                                    )
                                } else {
                                    SwipeToReplyBox(onReply = { replyTarget = msg }) {
                                        MessageBubbleRow(
                                            msg = msg,
                                            isGroup = chat?.isGroup == true,
                                            isChannel = false,
                                            channelDisplayName = channelDisplayName,
                                            showTail = isFirstInCluster,
                                            senderDisplay = senderDisplay,
                                            senderAvatarPath = senderAvatarPath,
                                            quotedDisplay = quotedDisplay,
                                            onClickSenderAvatar = clickAvatar,
                                            ghostPlayedActive = effectivePrivacy?.sendPlayedReceipts == false,
                                            isDownloadingMedia = downloadingMediaIds.contains(msg.id),
                                            mediaDownloadError = failedMediaIds[msg.id],
                                            onClickEditBadge = { editHistoryTarget = msg },
                                            onClickQuoted = jumpToQuotedMessage,
                                            onManualSendPlayedReceipt = { viewModel.markAudioPlayedExplicitly(msg) },
                                            onAutoRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = false) },
                                            onRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = true) },
                                            onOpenMedia = { mediaLightboxTarget = msg },
                                            onOpenDocument = handleOpenDocument,
                                            onLongPress = {
                                                haptic(HapticFeedbackType.LongPress)
                                                showMoreReactionEmojis = false
                                                actionMenuTarget = msg
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating WhatsApp Emoji Reaction Picker Pill with M3 Expressive spring scale & bounce
                var lastReactionTarget by remember { mutableStateOf<MessageEntity?>(null) }
                LaunchedEffect(actionMenuTarget) {
                    if (actionMenuTarget != null) lastReactionTarget = actionMenuTarget
                }
                val reactionMotion = MaterialTheme.motionScheme
                androidx.compose.animation.AnimatedVisibility(
                    visible = actionMenuTarget != null,
                    enter = fadeIn(reactionMotion.fastEffectsSpec()),
                    exit = fadeOut(reactionMotion.fastEffectsSpec())
                ) {
                    val target = actionMenuTarget ?: lastReactionTarget
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                actionMenuTarget = null
                                showMoreReactionEmojis = false
                                showSelectionOverflow = false
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (target != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 18.dp)
                                .animateEnterExit(
                                    enter = scaleIn(
                                        animationSpec = reactionMotion.fastSpatialSpec(),
                                        initialScale = 0.86f
                                    ) + slideInVertically(
                                        animationSpec = reactionMotion.fastSpatialSpec()
                                    ) { it / 3 },
                                    exit = scaleOut(
                                        animationSpec = reactionMotion.fastEffectsSpec(),
                                        targetScale = 0.90f
                                    )
                                )
                        ) {
                            AnimatedVisibility(
                                visible = showMoreReactionEmojis,
                                enter = fadeIn(reactionMotion.fastEffectsSpec()) + scaleIn(
                                    animationSpec = reactionMotion.fastSpatialSpec(),
                                    initialScale = 0.88f
                                ),
                                exit = fadeOut(reactionMotion.fastEffectsSpec()) + scaleOut(
                                    animationSpec = reactionMotion.fastEffectsSpec(),
                                    targetScale = 0.92f
                                )
                            ) {
                                WuppGlassContainer(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = colors,
                                    elevation = 12.dp,
                                    backdropState = wallpaperBackdropState,
                                    blurRadius = 10.dp,
                                    refractionHeight = 12.dp,
                                    refractionAmount = 20.dp,
                                    classicMode = stealthConfig.classicWhatsAppLook,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val extraRows = listOf(
                                            listOf("😍", "🔥", "👏", "🎉", "💯", "🤣"),
                                            listOf("🥳", "🤝", "🙌", "✨", "👀", "🫡"),
                                            listOf("😭", "😡", "👌", "✅", "💔", "🤔")
                                        )
                                        extraRows.forEach { rowEmojis ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                rowEmojis.forEach { emoji ->
                                                    val extraInteraction = remember(emoji) { MutableInteractionSource() }
                                                    Text(
                                                        text = emoji,
                                                        fontSize = 24.sp,
                                                        modifier = Modifier
                                                            .expressivePressScale(extraInteraction, pressedScale = 0.80f)
                                                            .clip(CircleShape)
                                                            .clickable(
                                                                interactionSource = extraInteraction,
                                                                indication = null
                                                            ) {
                                                                haptic()
                                                                viewModel.sendReaction(chatJid, target, emoji)
                                                                actionMenuTarget = null
                                                                showMoreReactionEmojis = false
                                                            }
                                                            .padding(4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            WuppGlassContainer(
                                shape = RoundedCornerShape(28.dp),
                                colors = colors,
                                elevation = 12.dp,
                                backdropState = wallpaperBackdropState,
                                blurRadius = 8.dp,
                                refractionHeight = 12.dp,
                                refractionAmount = 22.dp,
                                chromaticAberration = true,
                                classicMode = stealthConfig.classicWhatsAppLook
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val activeReactions = stealthConfig.quickReactionEmojis.ifEmpty {
                                        StealthMatrixManager.DEFAULT_QUICK_REACTIONS
                                    }
                                    val myReaction by remember(target.id) {
                                        viewModel.observeMyReaction(target.id)
                                    }.collectAsState(initial = null)
                                    activeReactions.forEach { emoji ->
                                        val isCurrent = myReaction == emoji
                                        val emojiInteraction = remember(emoji) { MutableInteractionSource() }
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .expressivePressScale(emojiInteraction, pressedScale = 0.80f)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) colors.brightGreen.copy(alpha = 0.24f) else Color.Transparent)
                                                .clickable(
                                                    interactionSource = emojiInteraction,
                                                    indication = null
                                                ) {
                                                    haptic()
                                                    viewModel.sendReaction(chatJid, target, emoji)
                                                    actionMenuTarget = null
                                                    showMoreReactionEmojis = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 23.sp)
                                        }
                                    }
                                    val moreInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .expressivePressScale(moreInteraction, pressedScale = 0.84f)
                                            .clip(CircleShape)
                                            .background(colors.quotedBg)
                                            .clickable(
                                                interactionSource = moreInteraction,
                                                indication = null
                                            ) { showMoreReactionEmojis = !showMoreReactionEmojis },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (showMoreReactionEmojis) Icons.Rounded.Close else Icons.Rounded.Add,
                                            contentDescription = "More reactions",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }

                if (showScrollToBottom && actionMenuTarget == null) {
                    WuppGlassContainer(
                        shape = CircleShape,
                        colors = colors,
                        elevation = 10.dp,
                        backdropState = wallpaperBackdropState,
                        blurRadius = 8.dp,
                        refractionHeight = 10.dp,
                        refractionAmount = 18.dp,
                        classicMode = stealthConfig.classicWhatsAppLook,
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Scroll to latest message",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                }

                if (isGroupAnnounceLocked && !isChannel) {
                    Surface(
                        color = colors.appSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Only admins can send messages",
                                color = colors.textSecondary,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                } else if (!isChannel) {
                // Attachment & Quality Mode Tray (M3 Expressive connected button group)
                val trayMotion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = showAttachTray,
                    enter = fadeIn(trayMotion.fastEffectsSpec()) + slideInVertically(trayMotion.fastSpatialSpec()) { it / 4 },
                    exit = fadeOut(trayMotion.fastEffectsSpec()) + slideOutVertically(trayMotion.fastSpatialSpec()) { it / 4 }
                ) {
                    WuppGlassContainer(
                        shape = RoundedCornerShape(24.dp),
                        colors = colors,
                        elevation = 12.dp,
                        backdropState = wallpaperBackdropState,
                        blurRadius = 10.dp,
                        refractionHeight = 12.dp,
                        refractionAmount = 22.dp,
                        classicMode = stealthConfig.classicWhatsAppLook,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Outbound Media Quality",
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 13.sp
                            )
                            val qualityToggleColors = ToggleButtonDefaults.colors(
                                containerColor = colors.pillInactiveBg,
                                contentColor = colors.textSecondary,
                                checkedContainerColor = colors.accentGreen,
                                checkedContentColor = Color(0xFF07090B)
                            )
                            val modes = OutboundQualityMode.entries
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                            ) {
                                modes.forEachIndexed { index, mode ->
                                    val selected = mode == qualityMode
                                    val shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    }
                                    ToggleButton(
                                        checked = selected,
                                        onCheckedChange = { if (it) qualityMode = mode },
                                        shapes = shapes,
                                        colors = qualityToggleColors
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val galleryInteraction = remember { MutableInteractionSource() }
                                Button(
                                    onClick = {
                                        AppLockManager.suspendNextBackgroundLock()
                                        pickVisualMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    interactionSource = galleryInteraction,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                                    modifier = Modifier.expressivePressScale(galleryInteraction, pressedScale = 0.96f)
                                ) {
                                    Icon(Icons.Rounded.Collections, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.chat_attach_gallery),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                val docInteraction = remember { MutableInteractionSource() }
                                OutlinedButton(
                                    onClick = {
                                        AppLockManager.suspendNextBackgroundLock()
                                        pickMediaLauncher.launch("*/*")
                                    },
                                    interactionSource = docInteraction,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.expressivePressScale(docInteraction, pressedScale = 0.96f)
                                ) {
                                    Icon(Icons.Rounded.Description, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.chat_attach_document),
                                        fontSize = 12.sp
                                    )
                                }
                                if (chat?.isGroup == true) {
                                    val mentionInteraction = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = { ghostMentionEveryone = !ghostMentionEveryone },
                                        interactionSource = mentionInteraction,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.expressivePressScale(mentionInteraction, pressedScale = 0.96f)
                                    ) {
                                        Icon(Icons.Rounded.AlternateEmail, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (ghostMentionEveryone) "@everyone Silent ON" else "@everyone Ghost Mention",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reply Context Preview Banner above Composer
                AnimatedVisibility(visible = replyTarget != null) {
                    replyTarget?.let { rep ->
                        WuppGlassContainer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            colors = colors,
                            shape = RoundedCornerShape(16.dp),
                            elevation = 8.dp,
                            backdropState = wallpaperBackdropState,
                            classicMode = stealthConfig.classicWhatsAppLook
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(32.dp)
                                        .background(colors.brightGreen, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rep.senderName.ifBlank { rep.senderJid.substringBefore("@") },
                                        color = colors.brightGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = rep.body.ifBlank { rep.msgType },
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { replyTarget = null },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, "Cancel Reply", tint = colors.textSecondary)
                                }
                            }
                        }
                    }
                }

                // Floating WhatsApp Composer Pill + Morphing Mic/Send FAB
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    WuppGlassContainer(
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        shape = RoundedCornerShape(26.dp),
                        elevation = 12.dp,
                        backdropState = wallpaperBackdropState,
                        classicMode = stealthConfig.classicWhatsAppLook
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        if (isRecordingVoice) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.antiDeleteRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording voice note... tap stop to send",
                                color = colors.antiDeleteRed,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.cancelVoiceRecording() }) {
                                Text("Cancel", color = colors.textSecondary)
                            }
                        } else {
                            IconButton(
                                enabled = canSendOutbound,
                                onClick = {
                                    haptic()
                                    if (showEmojiPicker) {
                                        holdDockForIme = true
                                        showEmojiPicker = false
                                        draftFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    } else {
                                        showAttachTray = false
                                        holdDockForIme = false
                                        showEmojiPicker = true
                                        keyboardController?.hide()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_emoji_btn")
                            ) {
                                Icon(
                                    imageVector = if (showEmojiPicker) Icons.Rounded.Keyboard else Icons.Rounded.SentimentSatisfiedAlt,
                                    contentDescription = stringResource(
                                        if (showEmojiPicker) R.string.chat_switch_to_keyboard else R.string.chat_switch_to_emoji
                                    ),
                                    tint = if (showEmojiPicker) colors.brightGreen else colors.textSecondary,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            if (ghostMentionEveryone && chat?.isGroup == true) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.pillActiveBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("@all", color = colors.pillActiveText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Box(modifier = Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 4.dp)) {
                                if (draftValue.text.isEmpty()) {
                                    Text(
                                        text = if (canSendOutbound) stringResource(R.string.chat_input_placeholder) else "Blocked by Error 463 guard",
                                        color = colors.textSecondary,
                                        fontSize = 15.5.sp
                                    )
                                }
                                val sendCurrentDraft: () -> Unit = {
                                    if (canSendOutbound && draftValue.text.isNotBlank()) {
                                        scrollToBottomPending = true
                                        viewModel.sendText(chatJid, draftValue.text, replyTarget, ghostMentionEveryone)
                                        draftValue = TextFieldValue("")
                                        replyTarget = null
                                        ghostMentionEveryone = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                }
                                BasicTextField(
                                    value = draftValue,
                                    onValueChange = { draftValue = it },
                                    enabled = canSendOutbound,
                                    interactionSource = draftInteractionSource,
                                    textStyle = TextStyle(
                                        color = colors.textPrimary,
                                        fontSize = 15.5.sp,
                                        lineHeight = 20.sp
                                    ),
                                    cursorBrush = SolidColor(colors.brightGreen),
                                    maxLines = 6,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                        imeAction = if (stealthConfig.enterIsSend) ImeAction.Send else ImeAction.Default
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = { sendCurrentDraft() }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(draftFocusRequester)
                                        .testTag("chat_input_field")
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic()
                                    val nextTray = !showAttachTray
                                    showAttachTray = nextTray
                                    if (nextTray) {
                                        showEmojiPicker = false
                                        holdDockForIme = false
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_attach_btn")
                            ) {
                                Icon(Icons.Rounded.AttachFile, "Attach", tint = colors.textSecondary)
                            }
                            AnimatedVisibility(visible = draftValue.text.isBlank()) {
                                IconButton(
                                    onClick = {
                                        haptic()
                                        AppLockManager.suspendNextBackgroundLock()
                                        pickVisualMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_camera_btn")
                                ) {
                                    Icon(
                                        imageVector = WuppIcons.Camera,
                                        contentDescription = "Camera / Photo",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Morphing Mic / Send / Stop FAB with M3 Expressive shape + spring physics
                    val fabMotion = MaterialTheme.motionScheme
                    val fabCorner by animateDpAsState(
                        targetValue = when {
                            isRecordingVoice -> 14.dp
                            stealthConfig.classicWhatsAppLook -> 24.dp
                            draftValue.text.isNotBlank() -> 16.dp
                            else -> 24.dp
                        },
                        animationSpec = fabMotion.fastSpatialSpec(),
                        label = "composer_fab_corner"
                    )
                    val fabBg by animateColorAsState(
                        targetValue = if (!canSendOutbound) colors.textSecondary
                        else if (isRecordingVoice) colors.antiDeleteRed
                        else colors.brightGreen,
                        animationSpec = fabMotion.fastEffectsSpec(),
                        label = "composer_fab_bg"
                    )
                    val fabInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .expressivePressScale(fabInteraction, pressedScale = 0.88f)
                            .clip(RoundedCornerShape(fabCorner))
                            .background(fabBg)
                            .testTag("chat_send_fab")
                            .clickable(
                                enabled = canSendOutbound,
                                interactionSource = fabInteraction,
                                indication = null
                            ) {
                                haptic()
                                when {
                                    isRecordingVoice -> {
                                        scrollToBottomPending = true
                                        viewModel.toggleVoiceRecording(chatJid, true)
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                    draftValue.text.isNotBlank() -> {
                                        scrollToBottomPending = true
                                        viewModel.sendText(chatJid, draftValue.text, replyTarget, ghostMentionEveryone)
                                        draftValue = TextFieldValue("")
                                        replyTarget = null
                                        ghostMentionEveryone = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                    else -> {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMic) {
                                            viewModel.toggleVoiceRecording(chatJid, true)
                                        } else {
                                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = when {
                                isRecordingVoice -> 2
                                draftValue.text.isNotBlank() -> 1
                                else -> 0
                            },
                            transitionSpec = {
                                (scaleIn(
                                    animationSpec = fabMotion.fastSpatialSpec(),
                                    initialScale = 0.72f
                                ) + fadeIn(fabMotion.fastEffectsSpec())) togetherWith
                                    (scaleOut(
                                        animationSpec = fabMotion.fastEffectsSpec(),
                                        targetScale = 0.72f
                                    ) + fadeOut(fabMotion.fastEffectsSpec()))
                            },
                            label = "morph_fab"
                        ) { mode ->
                            when (mode) {
                                2 -> Icon(Icons.Rounded.Stop, "Stop & Send Audio", tint = Color.White)
                                1 -> Icon(WuppIcons.Send, "Send", tint = Color.White)
                                else -> Icon(
                                    imageVector = WuppIcons.Mic,
                                    contentDescription = "Record Voice Note",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Native Keyboard-Height Emoji Dock
                val desiredDockDp = minOf(rememberedKeyboardDp, maxLandscapeDockDp)
                val targetDockDp = if (showEmojiPicker || holdDockForIme) {
                    (desiredDockDp - imeExclusiveDp).coerceAtLeast(0.dp)
                } else {
                    0.dp
                }
                val animatedDockDp by animateDpAsState(
                    targetValue = targetDockDp,
                    animationSpec = tween(
                        durationMillis = if (imeExclusivePx > 0 || holdDockForIme) 0 else 190
                    ),
                    label = "emoji_dock_height"
                )
                if (showEmojiPicker || animatedDockDp > 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedDockDp)
                            .clipToBounds()
                    ) {
                        if (showEmojiPicker) {
                            ChatEmojiPickerDock(
                                dockHeight = desiredDockDp,
                                colors = colors,
                                onEmojiSelected = { emoji ->
                                    draftValue = insertTextAtSelection(draftValue, emoji)
                                },
                                onBackspace = {
                                    draftValue = deleteBeforeSelection(draftValue)
                                }
                            )
                        }
                    }
                }
                }
            }
            }
        }
    }

    // Per-Chat Ghost Override Sheet
    if (showGhostOverrideSheet) {
        val currentPrivacy = effectivePrivacy
        var sendRead by remember(currentPrivacy) { mutableStateOf(currentPrivacy?.sendReadReceipts ?: false) }
        var sendPlayed by remember(currentPrivacy) { mutableStateOf(currentPrivacy?.sendPlayedReceipts ?: false) }
        var sendTyping by remember(currentPrivacy) { mutableStateOf(currentPrivacy?.sendChatPresence ?: false) }

        ModalBottomSheet(
            onDismissRequest = { showGhostOverrideSheet = false },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Per-Chat Ghost Matrix (${chat?.name ?: chatJid})",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = "Override global stealth policies exclusively for this thread:",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Read Receipts (Blue Ticks)", color = colors.textPrimary)
                    Switch(checked = sendRead, onCheckedChange = { sendRead = it })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Audio Played Receipts", color = colors.textPrimary)
                    Switch(checked = sendPlayed, onCheckedChange = { sendPlayed = it })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Live Typing & Recording", color = colors.textPrimary)
                    Switch(checked = sendTyping, onCheckedChange = { sendTyping = it })
                }
                HorizontalDivider(color = colors.divider)
                ChatMediaVisibilitySelector(
                    selectedOverride = StealthMatrixManager.getChatGalleryOverride(chatJid, stealthConfig),
                    globalDefaultVisible = stealthConfig.mediaVisibilityInGallery,
                    onSelectOverride = { overrideVisible ->
                        viewModel.setChatMediaVisibility(chatJid, overrideVisible)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearChatPrivacyOverride(chatJid)
                            viewModel.setChatMediaVisibility(chatJid, null)
                            showGhostOverrideSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use Global Default")
                    }
                    Button(
                        onClick = {
                            viewModel.saveChatPrivacyOverride(chatJid, sendRead, sendPlayed, sendTyping)
                            showGhostOverrideSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Override", color = Color.White)
                    }
                }
            }
        }
    }

    if (showWallpaperDialog) {
        val chatTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        WallpaperPickerDialog(
            currentUri = stealthConfig.chatWallpaperUri,
            currentDim = stealthConfig.chatWallpaperDim,
            showDoodleOnPresets = stealthConfig.chatWallpaperShowDoodle,
            currentScale = stealthConfig.chatWallpaperScale,
            currentOffsetX = stealthConfig.chatWallpaperOffsetX,
            currentOffsetY = stealthConfig.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false },
            chatTitle = chatTitle,
            hasChatOverride = hasChatWallpaperOverride,
            chatUri = effectiveWallpaper.uri,
            chatDim = effectiveWallpaper.dim,
            chatShowDoodle = effectiveWallpaper.showDoodle,
            chatScale = effectiveWallpaper.scale,
            chatOffsetX = effectiveWallpaper.offsetXFraction,
            chatOffsetY = effectiveWallpaper.offsetYFraction,
            onSelectChatUri = { viewModel.setPerChatWallpaper(chatJid, it) },
            onPickChatCustomImage = { viewModel.importPerChatWallpaperFromUri(chatJid, it) },
            onChatDimChange = { viewModel.setPerChatWallpaperDim(chatJid, it) },
            onToggleChatDoodle = { viewModel.setPerChatWallpaperShowDoodle(chatJid, it) },
            onChatPositionChange = { scale, ox, oy -> viewModel.setPerChatWallpaperPosition(chatJid, scale, ox, oy) },
            onResetChatToGlobal = { viewModel.clearPerChatWallpaper(chatJid) }
        )
    }

    // Unlimited Edit History Bottom Sheet
    editHistoryTarget?.let { target ->
        val edits by viewModel.observeEdits(target.id).collectAsState(initial = emptyList())
        ModalBottomSheet(
            onDismissRequest = { editHistoryTarget = null },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Unlimited Edit History",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 17.sp
                )
                Card(colors = CardDefaults.cardColors(containerColor = colors.pillActiveBg)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Current Version · ${formatMessageClock(target.editedAt, includeSeconds = showSeconds)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.pillActiveText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(target.body, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
                if (edits.isEmpty()) {
                    Text("No prior revisions recorded yet.", color = colors.textSecondary, fontSize = 13.sp)
                } else {
                    edits.forEachIndexed { idx, rev ->
                        Card(colors = CardDefaults.cardColors(containerColor = colors.pillInactiveBg)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Revision #${edits.size - idx} · ${formatMessageClock(rev.editedAt, includeSeconds = showSeconds)}",
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(rev.oldText, color = colors.textPrimary, fontSize = 13.5.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Group Receipts Bottom Sheet
    groupReceiptsTarget?.let { target ->
        val receipts by viewModel.observeGroupReceipts(target.id).collectAsState(initial = emptyList())
        ModalBottomSheet(
            onDismissRequest = { groupReceiptsTarget = null },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Group Per-Participant Receipts", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 16.sp)
                if (receipts.isEmpty()) {
                    Text("No individual receipts recorded for this message yet.", color = colors.textSecondary)
                } else {
                    receipts.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(r.participantName.ifBlank { r.participantJid }, color = colors.textPrimary)
                            Text("${r.status} · ${formatMessageClock(r.timestamp, includeSeconds = showSeconds)}", color = colors.blueTick, fontFamily = mono, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Edit Message Text Dialog
    editMessageDialogTarget?.let { target ->
        var newBody by remember(target.id) { mutableStateOf(target.body) }
        AlertDialog(
            onDismissRequest = { editMessageDialogTarget = null },
            containerColor = colors.elevatedBar,
            title = { Text("Edit Message", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newBody,
                    onValueChange = { newBody = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editMessage(chatJid, target.id, newBody)
                        editMessageDialogTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text("Save Edit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editMessageDialogTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showProfilePhotoLightbox) {
        val avatarTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        ProfilePhotoLightbox(
            jid = chatJid,
            title = avatarTitle,
            avatarPath = chat?.avatarPath,
            onDismiss = { showProfilePhotoLightbox = false },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(chatJid) },
            onSaveAvatar = {
                viewModel.exportMediaToGallery(chat?.avatarPath, "${avatarTitle}_avatar.jpg", "image/jpeg")
            }
        )
    }

    participantAvatarTarget?.let { (pJid, pName, pPath) ->
        ProfilePhotoLightbox(
            jid = pJid,
            title = pName.ifBlank { pJid.substringBefore("@") },
            avatarPath = pPath,
            onDismiss = { participantAvatarTarget = null },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(pJid) },
            onSaveAvatar = {
                viewModel.exportMediaToGallery(pPath, "${pName.ifBlank { pJid.substringBefore("@") }}_avatar.jpg", "image/jpeg")
            }
        )
    }

    // Full-screen Media / View Once Lightbox
    mediaLightboxTarget?.let { target ->
        val galleryItems by remember(chatJid, target.isViewOnce) {
            if (target.isViewOnce) kotlinx.coroutines.flow.flowOf(emptyList())
            else viewModel.observeChatGalleryMedia(chatJid)
        }.collectAsState(initial = emptyList())
        val resolveSenderLabel: (MessageEntity) -> String = { item ->
            if (item.isFromMe) {
                "You"
            } else if (chat?.isGroup == true) {
                item.senderName.ifBlank { item.senderJid.substringBefore("@") }
            } else {
                chat?.name?.takeIf { it.isNotBlank() }
                    ?: item.senderName.ifBlank { item.senderJid.substringBefore("@") }
            }
        }
        FullScreenMediaViewer(
            msg = target,
            senderDisplayName = resolveSenderLabel(target),
            onDismiss = { mediaLightboxTarget = null },
            onSaveToGallery = { item ->
                val (ext, fallbackMime) = when {
                    item.mimeType.contains("mp4", ignoreCase = true) || item.msgType == "VIDEO" ->
                        "mp4" to "video/mp4"
                    item.msgType == "PTT" || item.msgType == "AUDIO" || item.mimeType.startsWith("audio/", ignoreCase = true) ->
                        (if (item.mimeType.contains("m4a", ignoreCase = true) || item.mimeType.contains("mp4", ignoreCase = true)) "m4a" else "ogg") to "audio/ogg"
                    else -> "png" to "image/png"
                }
                val mime = item.mimeType.ifBlank { fallbackMime }
                viewModel.exportMediaToGallery(
                    item.localMediaPath,
                    "WA_Vault_${item.id}.$ext",
                    mime
                )
            },
            onReply = if (isChannel) null else {
                { item -> replyTarget = item }
            },
            onQuickReaction = if (isChannel) null else {
                { item, emoji -> viewModel.sendReaction(chatJid, item, emoji) }
            },
            gallery = galleryItems,
            senderDisplayNameFor = resolveSenderLabel
        )
    }

    pdfViewerTarget?.let { (file, title) ->
        InAppPdfViewerDialog(
            file = file,
            title = title,
            colors = colors,
            onDismiss = { pdfViewerTarget = null }
        )
    }

    pendingOutboundPreview?.let { staged ->
        val recipientLabel = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        OutboundMediaPreviewDialog(
            staged = staged,
            recipientLabel = recipientLabel,
            initialCaption = draftValue.text.trim(),
            initialQualityMode = qualityMode,
            onDismiss = { pendingOutboundPreview = null },
            onSend = { captionText, chosenQuality, isViewOnce ->
                pendingOutboundPreview = null
                qualityMode = chosenQuality
                scrollToBottomPending = true
                viewModel.sendStagedMedia(chatJid, staged, captionText, chosenQuality, isViewOnce)
                draftValue = TextFieldValue("")
                scope.launch { listState.animateScrollToItem(0) }
            }
        )
    }

    deleteConfirmTarget?.let { target ->
        val mode = resolveMessageDeleteDialogMode(target)
        val dialogTitle = when (mode) {
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED -> stringResource(R.string.chat_delete_captured_title)
            MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING,
            MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS -> stringResource(R.string.chat_delete_dialog_title)
        }
        val dialogBody = when (mode) {
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED -> stringResource(R.string.chat_delete_captured_body)
            MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING -> stringResource(R.string.chat_delete_incoming_body)
            MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS -> stringResource(R.string.chat_delete_outgoing_body)
        }
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(dialogTitle, fontWeight = FontWeight.SemiBold) },
            text = { Text(dialogBody, fontSize = 14.sp) },
            confirmButton = {
                if (mode == MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.revokeMessageForEveryone(chatJid, target.id)
                                deleteConfirmTarget = null
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.chat_delete_for_everyone),
                                color = colors.antiDeleteRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageFromDevice(target.id)
                                deleteConfirmTarget = null
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.chat_delete_for_me),
                                color = colors.accentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { deleteConfirmTarget = null }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessageFromDevice(target.id)
                            deleteConfirmTarget = null
                        }
                    ) {
                        Text(
                            text = if (mode == MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED) {
                                stringResource(R.string.chat_delete_from_device)
                            } else {
                                stringResource(R.string.chat_delete_for_me)
                            },
                            color = colors.antiDeleteRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = if (mode == MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS) {
                null
            } else {
                {
                    TextButton(onClick = { deleteConfirmTarget = null }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            }
        )
    }
}

internal fun findLoadedQuotedItemIndex(
    items: List<MessageEntity?>,
    quotedId: String
): Int {
    val cleanId = quotedId.trim()
    if (cleanId.isEmpty()) return -1
    return items.indexOfFirst { it != null && it.id == cleanId }
}

internal enum class MessageDeleteDialogMode {
    PERMANENT_DELETE_CAPTURED,
    DELETE_FOR_ME_INCOMING,
    DELETE_OUTGOING_OPTIONS
}

internal fun resolveMessageDeleteDialogMode(msg: MessageEntity): MessageDeleteDialogMode = when {
    msg.isDeletedBySender -> MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED
    msg.isFromMe -> MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS
    else -> MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING
}

@Composable
private fun ActionSheetRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.accentGreen, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, color = colors.textPrimary, fontSize = 14.5.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBubbleRow(
    msg: MessageEntity,
    isGroup: Boolean,
    isChannel: Boolean = false,
    channelDisplayName: String = "",
    showTail: Boolean,
    senderDisplay: SenderDisplayInfo? = null,
    senderAvatarPath: String? = null,
    quotedDisplay: SenderDisplayInfo? = null,
    onClickSenderAvatar: () -> Unit = {},
    ghostPlayedActive: Boolean,
    isDownloadingMedia: Boolean = false,
    mediaDownloadError: String? = null,
    onClickEditBadge: () -> Unit,
    onClickQuoted: (String) -> Unit = {},
    onManualSendPlayedReceipt: () -> Unit,
    onAutoRequestDownload: () -> Unit = {},
    onRequestDownload: () -> Unit = {},
    onOpenMedia: () -> Unit,
    onOpenDocument: () -> Unit = {},
    onLongPress: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val context = LocalContext.current
    val effectiveOutgoing = msg.isFromMe && !isChannel
    val bubbleColor = if (effectiveOutgoing) colors.outgoingBubble else colors.incomingBubble
    val shape = when {
        isChannel -> RoundedCornerShape(12.dp)
        showTail -> WhatsAppBubbleShape(effectiveOutgoing, true)
        else -> WhatsAppClusterInnerShape
    }

    val hasVaultFile = remember(msg.localMediaPath) {
        !msg.localMediaPath.isNullOrBlank() &&
            File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
    }
    val isDocCard = remember(msg.id, msg.msgType, msg.mimeType, msg.body, msg.isViewOnce, msg.isDeletedBySender) {
        isDocumentMessage(msg)
    }
    val isMediaCard = (msg.msgType == "IMAGE" || msg.msgType == "VIDEO") &&
        !msg.isViewOnce &&
        msg.msgType != "VIEW_ONCE_STRIPPED" &&
        !msg.isDeletedBySender

    val sideStartPad = when {
        isMediaCard -> if (!isChannel && showTail && !effectiveOutgoing) 9.dp else 3.dp
        isDocCard -> if (!isChannel && showTail && !effectiveOutgoing) 10.dp else 6.dp
        else -> if (!isChannel && showTail && !effectiveOutgoing) 15.dp else 12.dp
    }
    val sideEndPad = when {
        isMediaCard -> if (!isChannel && showTail && effectiveOutgoing) 9.dp else 3.dp
        isDocCard -> if (!isChannel && showTail && effectiveOutgoing) 10.dp else 6.dp
        else -> if (!isChannel && showTail && effectiveOutgoing) 15.dp else 12.dp
    }
    val topBottomPad = when {
        isMediaCard -> 3.dp
        isDocCard -> 5.dp
        else -> 8.dp
    }
    val bubbleMaxW = when {
        isMediaCard -> 256.dp
        isDocCard -> 285.dp
        else -> 305.dp
    }
    val showGroupGutter = isGroup && !effectiveOutgoing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = when {
                    isChannel -> 4.dp
                    effectiveOutgoing -> 52.dp
                    else -> 0.dp
                },
                end = when {
                    isChannel -> 4.dp
                    effectiveOutgoing -> 0.dp
                    showGroupGutter -> 38.dp
                    else -> 52.dp
                }
            ),
        horizontalArrangement = when {
            isChannel -> Arrangement.Center
            effectiveOutgoing -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Top
    ) {
        if (showGroupGutter) {
            if (showTail) {
                val resolvedPrimary = senderDisplay?.primary ?: msg.senderName
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp, end = 6.dp)
                        .clickable(onClick = onClickSenderAvatar)
                ) {
                    WhatsAppAvatar(
                        jid = msg.senderJid,
                        avatarPath = senderAvatarPath,
                        isGroup = false,
                        size = 28.dp,
                        initialChar = extractAvatarInitialChar(resolvedPrimary)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(34.dp))
            }
        }
        Column(
            horizontalAlignment = if (effectiveOutgoing) Alignment.End else Alignment.Start
        ) {
        Box(
            modifier = Modifier
                .then(if (isChannel) Modifier.fillMaxWidth().widthIn(max = 390.dp) else Modifier.widthIn(max = bubbleMaxW))
                .clip(shape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {
                        when {
                            msg.isEdited -> onClickEditBadge()
                            msg.isViewOnce || msg.msgType == "VIEW_ONCE_STRIPPED" -> {
                                if (hasVaultFile) {
                                    onOpenMedia()
                                } else {
                                Toast.makeText(
                                    context,
                                    "Open this View Once message in WhatsApp on your phone",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        isDocCard -> if (hasVaultFile) onOpenDocument() else onRequestDownload()
                        !msg.localMediaPath.isNullOrBlank() && msg.msgType != "PTT" && msg.msgType != "AUDIO" -> onOpenMedia()
                        msg.msgType == "IMAGE" || msg.msgType == "VIDEO" -> onRequestDownload()
                    }
                },
                onLongClick = onLongPress
            )
            .padding(start = sideStartPad, end = sideEndPad, top = topBottomPad, bottom = topBottomPad)
        ) {
            Column {
                // Sender color label in Groups
                if (isGroup && !effectiveOutgoing && showTail) {
                    val palette = listOf(Color(0xFF53BDEB), Color(0xFFFFB74D), Color(0xFF7CE8A5), Color(0xFFF48FB1))
                    val c = palette[abs(msg.senderJid.hashCode()) % palette.size]
                    val resolvedPrimary = senderDisplay?.primary
                        ?.takeIf { it.isNotBlank() }
                        ?: msg.senderName.ifBlank { msg.senderJid.substringBefore("@") }
                    val resolvedPhone = senderDisplay?.secondaryPhone.orEmpty()
                    Row(
                        modifier = Modifier
                            .then(
                                when {
                                    isMediaCard -> Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    isDocCard -> Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                    else -> Modifier.padding(bottom = 2.dp)
                                }
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = resolvedPrimary,
                            color = c,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (resolvedPhone.isNotBlank()) {
                            Text(
                                text = resolvedPhone,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Quoted Context Banner
                if (!msg.quotedId.isNullOrBlank()) {
                    val rawQuoted = msg.quotedSender.orEmpty()
                    val qPrimary = quotedDisplay?.primary?.takeIf { it.isNotBlank() } ?: when {
                        isChannel && (rawQuoted.isBlank() || rawQuoted == msg.chatJid || rawQuoted.endsWith("@newsletter")) ->
                            channelDisplayName.ifBlank { "Channel" }
                        rawQuoted.endsWith("@newsletter") ->
                            channelDisplayName.ifBlank { rawQuoted.substringBefore("@") }
                        rawQuoted.contains("@") -> rawQuoted.substringBefore("@")
                        rawQuoted.isNotBlank() -> rawQuoted
                        else -> "Quoted message"
                    }
                    val qPhone = quotedDisplay?.secondaryPhone.orEmpty()
                    val qAccent = when {
                        isChannel || qPrimary == "You" -> colors.brightGreen
                        else -> listOf(
                            Color(0xFF53BDEB),
                            Color(0xFFFFB74D),
                            Color(0xFF7CE8A5),
                            Color(0xFFF48FB1)
                        )[abs((quotedDisplay?.keyForColor ?: rawQuoted).hashCode()) % 4]
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isMediaCard) 3.dp else 0.dp,
                                end = if (isMediaCard) 3.dp else 0.dp,
                                bottom = 5.dp
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.quotedBg)
                            .combinedClickable(
                                onClick = {
                                    val targetId = msg.quotedId?.trim().orEmpty()
                                    if (targetId.isNotEmpty()) {
                                        onClickQuoted(targetId)
                                    }
                                },
                                onLongClick = onLongPress
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .background(qAccent, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = qPrimary,
                                    color = qAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (qPhone.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = qPhone,
                                        color = colors.textSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = msg.quotedBody.orEmpty(),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                when {
                    // Already opened / stripped View-Once (original WhatsApp style)
                    (msg.viewOnceState == "STRIPPED" && !hasVaultFile) || msg.msgType == "VIEW_ONCE_STRIPPED" -> {
                        val voSubType = when (msg.msgType.uppercase()) {
                            "VIDEO" -> " · Video"
                            "PTT", "AUDIO" -> " · Voice message"
                            "IMAGE" -> " · Photo"
                            else -> ""
                        }
                        Row(
                            modifier = Modifier
                                .widthIn(min = 165.dp)
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = WuppIcons.ViewOnce,
                                contentDescription = "View once opened",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Opened$voSubType",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Open in original WhatsApp",
                                    color = colors.textSecondary.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                                isEdited = false,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus
                            )
                        }
                    }

                    // Active View-Once (Photo, Video, Voice message -> concise companion notice)
                    msg.isViewOnce -> {
                        val voLabel = when (msg.msgType.uppercase()) {
                            "VIDEO" -> "Video"
                            "PTT", "AUDIO" -> "Voice message"
                            else -> "Photo"
                        }
                        val tintColor = if (effectiveOutgoing) colors.textPrimary else colors.brightGreen
                        val subLabel = if (hasVaultFile) {
                            if (msg.durationSec > 0) "${formatVideoDuration(msg.durationSec)} · Vaulted" else "Vaulted"
                        } else {
                            if (msg.durationSec > 0) "${formatVideoDuration(msg.durationSec)} · Open in original WhatsApp" else "Open in original WhatsApp"
                        }
                        Row(
                            modifier = Modifier
                                .widthIn(min = 175.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hasVaultFile) colors.pillActiveBg.copy(alpha = 0.55f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (hasVaultFile) {
                                            onOpenMedia()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "View-Once media can only be opened in official WhatsApp",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onLongClick = onLongPress
                                )
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = WuppIcons.ViewOnce,
                                contentDescription = "View once $voLabel",
                                tint = tintColor,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = voLabel,
                                    color = if (hasVaultFile) colors.textPrimary else tintColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = subLabel,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                                isEdited = msg.isEdited,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus
                            )
                        }
                    }

                    // Voice Note (PTT) / Audio
                    msg.msgType == "PTT" || msg.msgType == "AUDIO" -> {
                        VoiceNoteBubble(
                            msg = msg,
                            ghostPlayedActive = ghostPlayedActive,
                            onManualSendPlayedReceipt = onManualSendPlayedReceipt,
                            onRequestDownload = onRequestDownload
                        )
                    }

                    // Call Log Bubble ("Voice call · No answer / 1 min / 39 sec")
                    msg.msgType == "CALL" -> {
                        val parts = msg.body.split("|", limit = 2)
                        val callTitle = parts.getOrNull(0)?.ifBlank { "Voice call" } ?: "Voice call"
                        val callSubtitle = parts.getOrNull(1)?.ifBlank {
                            when {
                                msg.durationSec in 1..59 -> "${msg.durationSec} sec"
                                msg.durationSec >= 60 -> "${(msg.durationSec + 30) / 60} min"
                                else -> "No answer"
                            }
                        } ?: "No answer"
                        val isVideoCall = callTitle.contains("Video", ignoreCase = true)
                        val isMissed = callSubtitle.contains("No answer", ignoreCase = true) ||
                            callSubtitle.contains("Missed", ignoreCase = true) ||
                            callSubtitle.contains("Declined", ignoreCase = true)
                        val callCardBg = if (msg.isFromMe) {
                            if (colors.isDark) Color(0xFF0A4D3E) else Color(0xFFC5F2C0)
                        } else {
                            if (colors.isDark) Color(0xFF182229) else Color(0xFFF3F5F7)
                        }
                        val badgeBg = if (msg.isFromMe) {
                            if (colors.isDark) Color(0xFF1B6151) else Color(0xFFB3E6AE)
                        } else {
                            if (colors.isDark) Color(0xFF28353D) else Color(0xFFE1E6EA)
                        }
                        val iconVec = when {
                            isVideoCall -> Icons.Rounded.Videocam
                            isMissed && !msg.isFromMe -> Icons.Rounded.CallMissed
                            msg.isFromMe -> Icons.Rounded.CallMade
                            else -> Icons.Rounded.CallReceived
                        }
                        val iconTint = if (isMissed && !msg.isFromMe) colors.antiDeleteRed else colors.textPrimary

                        Column(modifier = Modifier.widthIn(min = 195.dp, max = 250.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(callCardBg)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = callTitle,
                                        tint = iconTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = callTitle,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = callSubtitle,
                                        color = colors.textSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(modifier = Modifier.align(Alignment.End).padding(horizontal = 2.dp)) {
                                MessageMetaCluster(
                                    timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                                    isEdited = false,
                                    isEphemeral = false,
                                    isFromMe = false,
                                    deliveryStatus = msg.deliveryStatus
                                )
                            }
                        }
                    }

                    // Photo / Video (auto-download images only; small videos <=5MB download in Go, large videos stay tap-to-download)
                    msg.msgType == "IMAGE" || msg.msgType == "VIDEO" -> {
                        val isVideo = msg.msgType == "VIDEO" || msg.mimeType.startsWith("video/", ignoreCase = true)
                        val localFile = remember(msg.localMediaPath) {
                            msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { p ->
                                File(p).takeIf { it.exists() && it.length() > 0L }
                            }
                        }
                        val bmp by produceState<ImageBitmap?>(
                            initialValue = null,
                            key1 = localFile?.absolutePath,
                            key2 = isVideo
                        ) {
                            value = if (localFile == null) {
                                null
                            } else if (!isVideo) {
                                withContext(Dispatchers.IO) {
                                    runCatching { BitmapFactory.decodeFile(localFile.absolutePath)?.asImageBitmap() }.getOrNull()
                                }
                            } else {
                                withContext(Dispatchers.IO) {
                                    extractVideoThumbnailBitmap(localFile.absolutePath)?.asImageBitmap()
                                }
                            }
                        }
                        LaunchedEffect(msg.id, localFile != null) {
                            // Never auto-request VIDEO on scroll so large videos never auto-download
                            if (localFile == null && !isVideo && !isDownloadingMedia && mediaDownloadError == null) {
                                onAutoRequestDownload()
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (localFile != null) onOpenMedia() else onRequestDownload()
                                    },
                                    onLongClick = onLongPress
                                )
                        ) {
                            if (localFile != null) {
                                val loadedBmp = bmp
                                if (loadedBmp != null) {
                                    val ratio = if (loadedBmp.height > 0) {
                                        (loadedBmp.width.toFloat() / loadedBmp.height.toFloat()).coerceIn(0.55f, 1.85f)
                                    } else {
                                        1f
                                    }
                                    Image(
                                        bitmap = loadedBmp,
                                        contentDescription = msg.body,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(ratio)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp)
                                            .background(Color(0xFF101519))
                                    )
                                }
                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x99000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play video",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    if (msg.durationSec > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x99000000))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatVideoDuration(msg.durationSec),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .background(colors.quotedBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x73000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDownloadingMedia) {
                                                LoadingIndicator(
                                                    color = colors.accentGreen,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isVideo || msg.fileSize > 0) Icons.Rounded.Download else Icons.Rounded.Image,
                                                    contentDescription = "Download ${msg.msgType}",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        val sizeLabel = formatMediaSizeLabel(msg.fileSize)
                                        val stateLabel = when {
                                            isDownloadingMedia -> if (sizeLabel.isNotBlank()) "Downloading$sizeLabel..." else "Downloading..."
                                            mediaDownloadError != null -> "Tap to retry$sizeLabel"
                                            else -> (if (isVideo) "Video" else "Photo") + sizeLabel
                                        }
                                        Text(
                                            text = stateLabel,
                                            color = if (mediaDownloadError != null && !isDownloadingMedia) Color(0xFFFFB74D) else colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (isVideo && msg.durationSec > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x88000000))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatVideoDuration(msg.durationSec),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                            if (msg.body.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.mediaScrim)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    MessageMetaCluster(
                                        timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                                        isEdited = msg.isEdited,
                                        isEphemeral = msg.isEphemeral,
                                        isFromMe = msg.isFromMe,
                                        deliveryStatus = msg.deliveryStatus,
                                        tintOverride = Color.White
                                    )
                                }
                            }
                        }
                        if (msg.body.isNotBlank()) {
                            Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                InlineTimestampMessage(msg = msg)
                            }
                        }
                    }

                    // Documents (PDFs, Office files, archives, etc.)
                    isDocCard -> {
                        val localDocFile = remember(msg.localMediaPath) {
                            msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { p ->
                                File(p).takeIf { it.exists() && it.length() > 0L }
                            }
                        }
                        LaunchedEffect(msg.id, localDocFile != null) {
                            if (localDocFile == null && !isDownloadingMedia && mediaDownloadError == null) {
                                onAutoRequestDownload()
                            }
                        }
                        DocumentMessageCard(
                            msg = msg,
                            localFile = localDocFile,
                            colors = colors,
                            isDownloading = isDownloadingMedia,
                            errorMsg = mediaDownloadError,
                            onClick = {
                                if (localDocFile != null) onOpenDocument() else onRequestDownload()
                            },
                            onLongClick = onLongPress
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 4.dp)
                        ) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                                isEdited = msg.isEdited,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus
                            )
                        }
                    }

                    // Standard Text, Sticker, or Anti-Deleted text bubble
                    else -> {
                        InlineTimestampMessage(msg = msg)
                    }
                }
            }
        }
        // Reaction & forward pill underneath the bubble/card
        if (msg.reactionSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp, start = 4.dp, end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.elevatedBar)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = msg.reactionSummary,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        }
    }
}

private fun isHiddenPlaceholderMessage(msg: MessageEntity): Boolean {
    return msg.msgType == "UNKNOWN" &&
        msg.body.isBlank() &&
        msg.localMediaPath.isNullOrBlank() &&
        !msg.isViewOnce &&
        !msg.isDeletedBySender
}

private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
    if (ts1 <= 0L || ts2 <= 0L) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun formatChatDateHeader(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun DateHeaderPill(timestamp: Long) {
    val colors = LocalWaColors.current
    val pillBg = if (colors.isDark) Color(0xFF182229) else Color(0xFFE7EFF3)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(pillBg)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = formatChatDateHeader(timestamp),
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SystemNoticePill(text: String) {
    if (text.isBlank()) return
    val colors = LocalWaColors.current
    val pillBg = if (colors.isDark) Color(0xFF182229) else Color(0xFFE7EFF3)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(pillBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = colors.textSecondary,
                fontSize = 12.5.sp
            )
        }
    }
}

private fun extractVideoThumbnailBitmap(path: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val raw = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return null
        val maxSide = maxOf(raw.width, raw.height)
        if (maxSide <= 720) return raw
        val scale = 720f / maxSide.toFloat()
        val w = (raw.width * scale).toInt().coerceAtLeast(1)
        val h = (raw.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
        if (scaled != raw) raw.recycle()
        scaled
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatMediaSizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.US, " · %.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        " · ${maxOf(1L, bytes / 1024L)} KB"
    }
}

private fun formatVideoDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val mins = s / 60
    val rem = s % 60
    return String.format(Locale.US, "%d:%02d", mins, rem)
}

private data class SenderDisplayInfo(
    val primary: String,
    val secondaryPhone: String = "",
    val keyForColor: String = primary
)

private fun isMeaningfulDisplayName(candidate: String?, jid: String = ""): Boolean {
    val t = candidate?.trim().orEmpty()
    if (t.isBlank() || t.contains("@")) return false
    val bare = t.removePrefix("+").replace(" ", "").replace("-", "")
    if (bare.isNotEmpty() && bare.all { it.isDigit() }) {
        val jidUser = jid.substringBefore("@")
        if (bare == jidUser || bare.length >= 7) return false
    }
    return true
}

private fun formatIntlPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return when {
        digits.startsWith("92") && digits.length == 12 ->
            "+92 ${digits.substring(2, 5)} ${digits.substring(5)}"
        digits.startsWith("1") && digits.length == 11 ->
            "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
        digits.length >= 11 ->
            "+${digits.substring(0, 2)} ${digits.substring(2, 5)} ${digits.substring(5)}"
        else -> "+$digits"
    }
}

private fun extractAvatarInitialChar(displayName: String): String? {
    val cleaned = displayName.removePrefix("~").trim()
    if (cleaned.isBlank() || cleaned.startsWith("+")) return null
    val cp = cleaned.codePointAt(0)
    if (Character.isDigit(cp)) return null
    return String(Character.toChars(cp)).uppercase(Locale.getDefault())
}

private fun resolveSenderDisplayInfo(
    rawJidOrText: String,
    explicitName: String,
    isSelf: Boolean,
    participantMeta: Map<String, ResolvedParticipantUi>,
    chatsByJid: Map<String, ChatEntity>,
    senderPushByJid: Map<String, String>
): SenderDisplayInfo {
    if (isSelf) return SenderDisplayInfo(primary = "You", keyForColor = rawJidOrText)
    val jid = rawJidOrText.trim()
    val meta = participantMeta[jid]
    val altJid = meta?.altJid.orEmpty()
    val chatMatch = chatsByJid[jid] ?: altJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it] }

    val savedName = meta?.savedName?.takeIf { isMeaningfulDisplayName(it, jid) }.orEmpty()
    val pushName = sequenceOf(
        explicitName.takeIf { isMeaningfulDisplayName(it, jid) },
        meta?.pushName?.takeIf { isMeaningfulDisplayName(it, jid) },
        senderPushByJid[jid]?.takeIf { isMeaningfulDisplayName(it, jid) },
        altJid.takeIf { it.isNotBlank() }?.let { senderPushByJid[it]?.takeIf { n -> isMeaningfulDisplayName(n, it) } },
        chatMatch?.name?.takeIf { isMeaningfulDisplayName(it, jid) }
    ).firstOrNull().orEmpty()

    val rawPhone = when {
        !meta?.phone.isNullOrBlank() -> meta!!.phone
        jid.endsWith("@s.whatsapp.net") -> jid.substringBefore("@")
        altJid.endsWith("@s.whatsapp.net") -> altJid.substringBefore("@")
        else -> {
            val bare = jid.removePrefix("+")
            if (jid.all { it.isDigit() || it == '+' } && bare.length in 7..13 && !jid.endsWith("@lid")) bare else ""
        }
    }
    val formattedPhone = formatIntlPhone(rawPhone)

    return when {
        savedName.isNotBlank() ->
            SenderDisplayInfo(primary = savedName, keyForColor = jid.ifBlank { savedName })
        pushName.isNotBlank() ->
            SenderDisplayInfo(primary = "~ $pushName", secondaryPhone = formattedPhone, keyForColor = jid.ifBlank { pushName })
        formattedPhone.isNotBlank() ->
            SenderDisplayInfo(primary = formattedPhone, keyForColor = jid.ifBlank { formattedPhone })
        jid.endsWith("@lid") -> {
            val tail = jid.substringBefore("@").takeLast(4)
            SenderDisplayInfo(primary = if (tail.isNotBlank()) "~ User · …$tail" else "~ User", keyForColor = jid)
        }
        jid.contains("@") ->
            SenderDisplayInfo(primary = jid.substringBefore("@"), keyForColor = jid)
        jid.isNotBlank() ->
            SenderDisplayInfo(primary = jid, keyForColor = jid)
        else ->
            SenderDisplayInfo(primary = "Participant", keyForColor = "participant")
    }
}

private fun resolveQuotedBannerSender(
    msg: MessageEntity,
    isChannel: Boolean,
    channelDisplayName: String,
    loadedMessagesById: Map<String, MessageEntity>,
    quotedMsgMeta: Map<String, QuotedSenderMeta>,
    participantMeta: Map<String, ResolvedParticipantUi>,
    chatsByJid: Map<String, ChatEntity>,
    senderPushByJid: Map<String, String>
): SenderDisplayInfo {
    val rawQuoted = msg.quotedSender.orEmpty().trim()
    if (isChannel && (rawQuoted.isBlank() || rawQuoted == msg.chatJid || rawQuoted.endsWith("@newsletter"))) {
        return SenderDisplayInfo(primary = channelDisplayName.ifBlank { "Channel" }, keyForColor = msg.chatJid)
    }
    if (rawQuoted.endsWith("@newsletter")) {
        return SenderDisplayInfo(primary = channelDisplayName.ifBlank { rawQuoted.substringBefore("@") }, keyForColor = rawQuoted)
    }

    val quotedId = msg.quotedId.orEmpty()
    val loadedQ = loadedMessagesById[quotedId]
    val cachedQ = quotedMsgMeta[quotedId]
    val qFromMe = loadedQ?.isFromMe ?: cachedQ?.isFromMe ?: false
    val qSenderJidFromMsg = loadedQ?.senderJid ?: cachedQ?.senderJid.orEmpty()
    val qSenderNameFromMsg = loadedQ?.senderName ?: cachedQ?.senderName.orEmpty()

    val chosenJid = when {
        qSenderJidFromMsg.endsWith("@s.whatsapp.net") -> qSenderJidFromMsg
        rawQuoted.endsWith("@s.whatsapp.net") -> rawQuoted
        qSenderJidFromMsg.contains("@") -> qSenderJidFromMsg
        rawQuoted.isNotBlank() -> rawQuoted
        else -> qSenderJidFromMsg
    }
    val explicitName = if (isMeaningfulDisplayName(qSenderNameFromMsg, chosenJid)) {
        qSenderNameFromMsg
    } else if (!rawQuoted.contains("@") && isMeaningfulDisplayName(rawQuoted, chosenJid)) {
        rawQuoted
    } else ""

    val isSelf = qFromMe || participantMeta[chosenJid]?.isSelf == true || participantMeta[rawQuoted]?.isSelf == true
    return resolveSenderDisplayInfo(
        rawJidOrText = chosenJid,
        explicitName = explicitName,
        isSelf = isSelf,
        participantMeta = participantMeta,
        chatsByJid = chatsByJid,
        senderPushByJid = senderPushByJid
    )
}
