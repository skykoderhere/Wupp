package com.wupp.meow.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wupp.meow.R
import com.wupp.meow.data.media.OutboundQualityMode
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import java.io.File
import java.util.Locale

data class StagedOutboundMedia(
    val file: File,
    val mimeType: String,
    val isVideo: Boolean,
    val durationSec: Int,
    val fileSize: Long
)

/**
 * Full-screen native-style pre-send preview for picked photos and videos, featuring
 * aspect-fit video playback, duration/size chip, HD/Standard quality toggle, and caption input.
 */
@Composable
fun OutboundMediaPreviewDialog(
    staged: StagedOutboundMedia,
    recipientLabel: String,
    initialCaption: String,
    initialQualityMode: OutboundQualityMode,
    onDismiss: () -> Unit,
    onSend: (caption: String, qualityMode: OutboundQualityMode, isViewOnce: Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberWuppHapticTrigger()
    var caption by remember(staged.file.absolutePath) { mutableStateOf(initialCaption) }
    var isViewOnce by remember(staged.file.absolutePath) { mutableStateOf(false) }
    var qualityMode by remember(staged.file.absolutePath) {
        mutableStateOf(
            if (staged.isVideo && initialQualityMode == OutboundQualityMode.ORIGINAL) {
                OutboundQualityMode.HD
            } else {
                initialQualityMode
            }
        )
    }

    val previewBitmap = remember(staged.file.absolutePath, staged.isVideo) {
        if (!staged.isVideo) {
            runCatching { BitmapFactory.decodeFile(staged.file.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    val player = remember(staged.file.absolutePath, staged.isVideo) {
        if (staged.isVideo) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(staged.file)))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } else {
            null
        }
    }
    var isPlaying by remember(player) { mutableStateOf(true) }

    DisposableEffect(player) {
        if (player != null) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        } else {
            onDispose { }
        }
    }

    val sizeBadge = remember(staged.fileSize, staged.durationSec, staged.isVideo) {
        val sizeStr = if (staged.fileSize >= 1024L * 1024L) {
            String.format(Locale.US, "%.1f MB", staged.fileSize / (1024.0 * 1024.0))
        } else {
            "${maxOf(1L, staged.fileSize / 1024L)} kB"
        }
        if (staged.isVideo && staged.durationSec > 0) {
            "%d:%02d · %s".format(staged.durationSec / 60, staged.durationSec % 60, sizeStr)
        } else {
            sizeStr
        }
    }

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
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Center Aspect-Fit Stage
            if (staged.isVideo && player != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp, bottom = 112.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AspectFitVideoSurface(
                        player = player,
                        file = staged.file,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = !isPlaying,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.56f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play preview",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            } else if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = caption.ifBlank { "Photo preview" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp, bottom = 112.dp)
                )
            }

            // Top Toolbar: Close + Duration/Size pill + Quality badge
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xB807090B))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = sizeBadge,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val isHdActive = qualityMode != OutboundQualityMode.STANDARD
                    val qualityPillLabel = when {
                        staged.isVideo -> "HD"
                        qualityMode == OutboundQualityMode.ORIGINAL -> "RAW"
                        qualityMode == OutboundQualityMode.HD -> "HD"
                        else -> "SD"
                    }
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHdActive) Color(0xFF25D366).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.14f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isHdActive) Color(0xFF25D366) else Color.White.copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                            .clickable(enabled = !staged.isVideo) {
                                haptic()
                                qualityMode = when (qualityMode) {
                                    OutboundQualityMode.STANDARD -> OutboundQualityMode.HD
                                    OutboundQualityMode.HD -> if (isViewOnce) OutboundQualityMode.STANDARD else OutboundQualityMode.ORIGINAL
                                    OutboundQualityMode.ORIGINAL -> OutboundQualityMode.STANDARD
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hd,
                            contentDescription = "Quality",
                            tint = if (isHdActive) Color(0xFF25D366) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = qualityPillLabel,
                            color = if (isHdActive) Color(0xFF25D366) else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Caption Input + Recipient Pill + Send FAB
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC07090B))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 110.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF141B20).copy(alpha = 0.94f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (caption.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_media_add_caption),
                                color = Color(0xFF8E9DA8),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF25D366)),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isViewOnce) Color(0xFF25D366).copy(alpha = 0.24f)
                                else Color(0xFF141B20).copy(alpha = 0.94f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isViewOnce) Color(0xFF25D366) else Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .clickable {
                                haptic()
                                isViewOnce = !isViewOnce
                                if (isViewOnce && qualityMode == OutboundQualityMode.ORIGINAL) {
                                    qualityMode = OutboundQualityMode.HD
                                }
                                val kind = if (staged.isVideo) "Video" else "Photo"
                                val msg = if (isViewOnce) "$kind set to view once" else "View once off"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WuppIcons.ViewOnce,
                            contentDescription = "View once",
                            tint = if (isViewOnce) Color(0xFF25D366) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                            .clickable {
                                haptic()
                                onSend(caption.trim(), qualityMode, isViewOnce)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WuppIcons.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF07090B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (recipientLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF141B20))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = recipientLabel,
                            color = Color(0xFFE9EDEF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
