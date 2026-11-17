package com.wupp.meow.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWaMonoFamily
import com.wupp.meow.ui.theme.LocalWuppShowSeconds
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max

/**
 * Interactive Voice Note (PTT) bubble with 42.dp avatar + R.drawable.msg_status_mic badge,
 * Media3 ExoPlayer playback, 40-bar scrubbing waveform, 1x/1.5x/2x pill, Ghost Audio guard,
 * manual Played receipt trigger, and collapsible offline transcript drawer.
 */
@Composable
fun VoiceNoteBubble(
    msg: MessageEntity,
    ghostPlayedActive: Boolean,
    onManualSendPlayedReceipt: () -> Unit,
    onRequestDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current
    val triggerHaptic = rememberWuppHapticTrigger()

    val peaks = remember(msg.waveformCsv) {
        val parsed = msg.waveformCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parsed.size >= 40) parsed.take(40)
        else List(40) { idx -> parsed.getOrElse(idx) { 18 + ((idx * 17) % 64) } }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var autoPlayOnDownload by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var currentSec by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(0) }
    val speeds = listOf(1.0f, 1.5f, 2.0f)

    val mediaFileExists = remember(msg.localMediaPath) {
        !msg.localMediaPath.isNullOrBlank() &&
            File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
    }
    val exoPlayer = remember(msg.id, msg.localMediaPath, mediaFileExists) {
        if (mediaFileExists) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttrs, true)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(msg.localMediaPath!!))))
                    prepare()
                }
        } else null
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    playbackFraction = 0f
                    currentSec = 0
                    exoPlayer?.seekTo(0L)
                    exoPlayer?.playWhenReady = false
                }
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    LaunchedEffect(exoPlayer, autoPlayOnDownload) {
        if (exoPlayer != null) {
            isDownloading = false
            if (autoPlayOnDownload) {
                autoPlayOnDownload = false
                exoPlayer.playbackParameters = PlaybackParameters(speeds[speedIndex])
                exoPlayer.play()
                isPlaying = true
                if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                    onManualSendPlayedReceipt()
                }
            }
        }
    }

    LaunchedEffect(isDownloading, mediaFileExists) {
        if (isDownloading && !mediaFileExists) {
            delay(15_000L)
            isDownloading = false
            autoPlayOnDownload = false
        }
    }

    LaunchedEffect(isPlaying, exoPlayer, speedIndex) {
        exoPlayer?.playbackParameters = PlaybackParameters(speeds[speedIndex])
        while (isPlaying && exoPlayer != null) {
            val dur = max(1L, exoPlayer.duration)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            playbackFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            currentSec = (pos / 1000L).toInt()
            delay(45L)
        }
    }

    val showSeconds = LocalWuppShowSeconds.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 42.dp avatar with vector mic badge
            Box(modifier = Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.accentGreen.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = msg.senderName.ifBlank { msg.senderJid }.take(1).uppercase()
                    Text(
                        text = initials,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (msg.isFromMe) colors.outgoingBubble else colors.incomingBubble)
                        .border(1.dp, colors.appSurface.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WuppIcons.Mic,
                        contentDescription = "PTT Mic",
                        tint = if (msg.deliveryStatus == "PLAYED") colors.blueTick else colors.brightGreen,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Play/Pause / Download Button
            IconButton(
                onClick = {
                    triggerHaptic(HapticFeedbackType.TextHandleMove)
                    if (exoPlayer != null) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            exoPlayer.playbackParameters = PlaybackParameters(speeds[speedIndex])
                            exoPlayer.play()
                            if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                                onManualSendPlayedReceipt()
                            }
                        }
                    } else {
                        isDownloading = true
                        autoPlayOnDownload = true
                        onRequestDownload()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                if (isDownloading && exoPlayer == null) {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    LoadingIndicator(
                        modifier = Modifier.size(26.dp),
                        color = colors.accentGreen
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 40-bar interactive Canvas waveform + duration row
            Column(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(exoPlayer, peaks) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                playbackFraction = frac
                                if (exoPlayer != null && exoPlayer.duration > 0) {
                                    exoPlayer.seekTo((exoPlayer.duration * frac).toLong())
                                }
                            }
                        }
                ) {
                    val barCount = peaks.size
                    val slotW = size.width / barCount
                    val barW = (slotW * 0.56f).coerceAtLeast(2f)
                    val maxH = size.height * 0.88f
                    val centerY = size.height / 2f

                    peaks.forEachIndexed { idx, amp ->
                        val ratio = idx.toFloat() / barCount.toFloat()
                        val barH = ((amp.coerceIn(10, 99) / 99f) * maxH).coerceAtLeast(4f)
                        val x = idx * slotW + (slotW - barW) / 2f
                        val active = ratio <= playbackFraction
                        drawRoundRect(
                            color = if (active) colors.blueTick else colors.textSecondary.copy(alpha = 0.65f),
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shownSec = if (isPlaying || currentSec > 0) currentSec else msg.durationSec.coerceAtLeast(1)
                    val durationText = "%d:%02d".format(shownSec / 60, shownSec % 60)
                    Text(
                        text = durationText,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    MessageMetaCluster(
                        timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                        isEdited = msg.isEdited,
                        isEphemeral = msg.isEphemeral,
                        isFromMe = msg.isFromMe,
                        deliveryStatus = msg.deliveryStatus
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 1x / 1.5x / 2x Speed Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.quotedBg)
                    .clickable {
                        triggerHaptic(HapticFeedbackType.TextHandleMove)
                        speedIndex = (speedIndex + 1) % speeds.size
                        exoPlayer?.playbackParameters = PlaybackParameters(speeds[speedIndex])
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (speedIndex) {
                        1 -> "1.5x"
                        2 -> "2x"
                        else -> "1x"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = colors.textPrimary
                )
            }
        }
    }
}
