package com.wupp.meow.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pages shown by the lightbox: the surrounding chat media when [target] is part of it. */
internal fun resolveLightboxPages(target: MessageEntity, gallery: List<MessageEntity>): List<MessageEntity> =
    if (gallery.any { it.id == target.id }) gallery else listOf(target)

internal fun resolveLightboxStartIndex(pages: List<MessageEntity>, targetId: String): Int =
    pages.indexOfFirst { it.id == targetId }.coerceAtLeast(0)

internal fun resolveLightboxPageKey(pages: List<MessageEntity>, index: Int): String =
    pages.getOrNull(index)?.id ?: "page_$index"

/** Largest side kept in memory for full-screen photos so big camera images never OOM. */
private const val LIGHTBOX_MAX_BITMAP_SIDE = 2400

private fun decodeLightboxBitmap(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
    var sample = 1
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        while (bounds.outWidth / sample > LIGHTBOX_MAX_BITMAP_SIDE ||
            bounds.outHeight / sample > LIGHTBOX_MAX_BITMAP_SIDE
        ) {
            sample *= 2
        }
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap() }.getOrNull()
}

/**
 * Pinch-zoom + pan detector that only claims touches while zooming (two fingers) or while the
 * image is already zoomed in, so one-finger swipes at 1x still reach the surrounding pager.
 */
private suspend fun PointerInputScope.detectLightboxZoomPan(
    isZoomed: () -> Boolean,
    onGesture: (zoom: Float, pan: Offset) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled && (pressedCount > 1 || isZoomed())) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) {
                    onGesture(zoom, pan)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@Composable
fun FullScreenMediaViewer(
    msg: MessageEntity,
    senderDisplayName: String,
    onDismiss: () -> Unit,
    onSaveToGallery: (MessageEntity) -> Unit,
    onReply: ((MessageEntity) -> Unit)? = null,
    onQuickReaction: ((MessageEntity, String) -> Unit)? = null,
    gallery: List<MessageEntity> = emptyList(),
    senderDisplayNameFor: (MessageEntity) -> String = { senderDisplayName }
) {
    val context = LocalContext.current
    val haptic = rememberWuppHapticTrigger()
    val pages = remember(msg.id, gallery) { resolveLightboxPages(msg, gallery) }
    val pagerState = rememberPagerState(
        initialPage = resolveLightboxStartIndex(pages, msg.id),
        pageCount = { pages.size }
    )
    var syncedInitialForGallery by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id, pages) {
        if (!syncedInitialForGallery && gallery.any { it.id == msg.id }) {
            val targetIdx = resolveLightboxStartIndex(pages, msg.id)
            if (pagerState.currentPage != targetIdx) {
                pagerState.scrollToPage(targetIdx)
            }
            syncedInitialForGallery = true
        }
    }
    val current = pages.getOrElse(pagerState.currentPage) { msg }
    val mediaFile = remember(current.id, current.localMediaPath) {
        current.localMediaPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
        }
    }

    var showChrome by remember { mutableStateOf(true) }
    var showOverflow by remember { mutableStateOf(false) }

    val formattedTime = remember(current.timestamp) {
        if (current.timestamp > 0L) {
            SimpleDateFormat("MMMM d, h:mm a", Locale.getDefault()).format(Date(current.timestamp))
        } else {
            ""
        }
    }
    val headerTitle = if (current.isFromMe) {
        "You"
    } else {
        senderDisplayNameFor(current).ifBlank { current.senderName.ifBlank { "Contact" } }
    }
    val positionLabel = if (pages.size > 1) "${pagerState.currentPage + 1} / ${pages.size}" else ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> resolveLightboxPageKey(pages, index) }
            ) { pageIndex ->
                val pageMsg = pages.getOrElse(pageIndex) { msg }
                MediaLightboxPage(
                    msg = pageMsg,
                    isActivePage = pagerState.settledPage == pageIndex,
                    showChrome = showChrome,
                    onToggleChrome = { showChrome = !showChrome }
                )
            }

            // Top bar overlay (solid obsidian dark scrim)
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xD907090B))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = headerTitle,
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (current.isViewOnce) {
                                Text(
                                    text = "View Once Vault",
                                    color = Color(0xFF25D366),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .background(Color(0xFF0E2C1F), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val subtitle = listOf(formattedTime, positionLabel)
                            .filter { it.isNotBlank() }
                            .joinToString("  ·  ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Color(0xFFD1D7DB),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (mediaFile != null) {
                        IconButton(onClick = { onSaveToGallery(current) }) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Save to Gallery",
                                tint = Color.White
                            )
                        }
                    }
                    if (onReply != null) {
                        IconButton(
                            onClick = {
                                onReply(current)
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "Reply",
                                tint = Color.White
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                            containerColor = Color(0xFF141B20)
                        ) {
                            if (mediaFile != null) {
                                DropdownMenuItem(
                                    text = { Text("Save to gallery", color = Color.White) },
                                    onClick = {
                                        showOverflow = false
                                        onSaveToGallery(current)
                                    }
                                )
                            }
                            if (onReply != null) {
                                DropdownMenuItem(
                                    text = { Text("Reply", color = Color.White) },
                                    onClick = {
                                        showOverflow = false
                                        onReply(current)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom caption + Reply pill + Quick emoji reactions
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xD907090B))
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (current.body.isNotBlank()) {
                        Text(
                            text = current.body,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF141B20))
                                .clickable {
                                    haptic()
                                    if (onReply != null) {
                                        onReply(current)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Reply",
                                color = Color(0xFFD1D7DB),
                                fontSize = 15.sp
                            )
                        }
                        listOf("❤️", "🙏", "👍", "😊").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF141B20))
                                    .clickable {
                                        haptic()
                                        if (onQuickReaction != null) {
                                            onQuickReaction(current, emoji)
                                            Toast.makeText(context, "Reacted $emoji", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class LightboxImageLoad(val bitmap: ImageBitmap?)

@Composable
private fun MediaLightboxPage(
    msg: MessageEntity,
    isActivePage: Boolean,
    showChrome: Boolean,
    onToggleChrome: () -> Unit
) {
    val mediaFile = remember(msg.id, msg.localMediaPath) {
        msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
        }
    }
    val isVideo = msg.msgType.uppercase() == "VIDEO" || msg.mimeType.startsWith("video/", ignoreCase = true)
    val isAudio = msg.msgType.uppercase() == "PTT" || msg.msgType.uppercase() == "AUDIO" ||
        msg.mimeType.startsWith("audio/", ignoreCase = true)

    when {
        isAudio && mediaFile != null -> {
            if (isActivePage) {
                AudioLightboxStage(
                    file = mediaFile,
                    durationSec = msg.durationSec,
                    onToggleChrome = onToggleChrome
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        isVideo && mediaFile != null -> {
            // Only the settled page owns a player so neighbouring videos never start playing mid-swipe.
            if (isActivePage) {
                VideoLightboxStage(
                    file = mediaFile,
                    showChrome = showChrome,
                    onToggleChrome = onToggleChrome
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        mediaFile != null -> {
            val load by produceState<LightboxImageLoad?>(initialValue = null, mediaFile) {
                value = LightboxImageLoad(
                    withContext(Dispatchers.IO) { decodeLightboxBitmap(mediaFile) }
                )
            }
            val bitmap = load?.bitmap
            when {
                bitmap != null -> ZoomableLightboxImage(
                    msgId = msg.id,
                    bitmap = bitmap,
                    contentDescription = msg.body,
                    onToggleChrome = onToggleChrome
                )

                load == null -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                else -> LightboxMessagePlaceholder(msg = msg, onToggleChrome = onToggleChrome)
            }
        }

        else -> LightboxMessagePlaceholder(msg = msg, onToggleChrome = onToggleChrome)
    }
}

@Composable
private fun ZoomableLightboxImage(
    msgId: String,
    bitmap: ImageBitmap,
    contentDescription: String,
    onToggleChrome: () -> Unit
) {
    var scale by remember(msgId) { mutableFloatStateOf(1f) }
    var offsetX by remember(msgId) { mutableFloatStateOf(0f) }
    var offsetY by remember(msgId) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(msgId) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.4f
                        }
                    }
                )
            }
            .pointerInput(msgId) {
                detectLightboxZoomPan(isZoomed = { scale > 1.02f }) { zoom, pan ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    if (nextScale > 1.02f) {
                        val maxOffset = (nextScale - 1f) * 650f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun LightboxMessagePlaceholder(
    msg: MessageEntity,
    onToggleChrome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onToggleChrome() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = if (msg.localMediaPath.isNullOrBlank()) "Downloading media..." else "Unable to open media file",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (msg.body.isNotBlank()) {
                Text(
                    text = msg.body,
                    color = Color(0xFFB8C2C7),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun VideoLightboxStage(
    file: File,
    showChrome: Boolean = true,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome
            ),
        contentAlignment = Alignment.Center
    ) {
        AspectFitVideoSurface(
            player = player,
            file = file,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = showChrome || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = durationMs > 0L && (showChrome || !isPlaying),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 92.dp)
            ) {
                Slider(
                    value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        player.seekTo((frac * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF25D366),
                        activeTrackColor = Color(0xFF25D366),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AudioLightboxStage(
    file: File,
    durationSec: Int,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf((durationSec.coerceAtLeast(0) * 1000L)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    positionMs = 0L
                    player.seekTo(0L)
                    player.playWhenReady = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val dur = player.duration
            if (dur > 0L) durationMs = dur
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(100L)
        }
    }

    fun formatSec(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleChrome),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141B20)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Voice message",
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(56.dp)
                )
            }
            Text(
                text = "Voice message · View once",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
                    .clickable {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF07090B),
                    modifier = Modifier.size(36.dp)
                )
            }
            val safeDur = durationMs.coerceAtLeast(1L)
            Slider(
                value = (positionMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    player.seekTo((frac * safeDur).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF25D366),
                    activeTrackColor = Color(0xFF25D366),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Text(
                text = "${formatSec(positionMs)} / ${formatSec(durationMs)}",
                color = Color(0xFFD1D7DB),
                fontSize = 13.sp
            )
        }
    }
}
