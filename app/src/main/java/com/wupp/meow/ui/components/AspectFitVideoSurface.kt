package com.wupp.meow.ui.components

import android.media.MediaMetadataRetriever
import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ProbedVideoMeta(
    val aspectRatio: Float?,
    val durationSec: Int
)

/**
 * Computes the effective display aspect ratio (width / height) accounting for container rotation
 * (90 or 270 degrees) and non-square pixel aspect ratio ([pixelWidthHeightRatio]).
 */
internal fun computeVideoDisplayRatio(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0,
    pixelWidthHeightRatio: Float = 1f
): Float? {
    if (width <= 0 || height <= 0) return null
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val rotated = normalizedRotation == 90 || normalizedRotation == 270
    val effectiveW = if (rotated) height.toFloat() else width.toFloat() * pixelWidthHeightRatio.coerceIn(0.5f, 2f)
    val effectiveH = if (rotated) width.toFloat() * pixelWidthHeightRatio.coerceIn(0.5f, 2f) else height.toFloat()
    if (effectiveH <= 0f) return null
    return (effectiveW / effectiveH).coerceIn(0.25f, 4f)
}

internal fun probeVideoMetadata(file: File): ProbedVideoMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val durSec = if (durMs > 0L) ((durMs + 500L) / 1000L).toInt().coerceAtLeast(1) else 0
        ProbedVideoMeta(
            aspectRatio = computeVideoDisplayRatio(width = w, height = h, rotationDegrees = rot),
            durationSec = durSec
        )
    } catch (_: Throwable) {
        ProbedVideoMeta(aspectRatio = null, durationSec = 0)
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Renders [player] into a [TextureView] fitted to the video's true aspect ratio inside the available box,
 * avoiding full-screen stretching across portrait, landscape, and square videos.
 */
@Composable
fun AspectFitVideoSurface(
    player: ExoPlayer,
    file: File,
    modifier: Modifier = Modifier
) {
    var videoRatio by remember(file.absolutePath) {
        mutableStateOf(probeVideoMetadata(file).aspectRatio)
    }

    DisposableEffect(player) {
        val sizeListener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                computeVideoDisplayRatio(
                    width = videoSize.width,
                    height = videoSize.height,
                    rotationDegrees = 0,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                )?.let { next ->
                    val current = videoRatio
                    if (current == null || abs(current - next) > 0.01f) {
                        videoRatio = next
                    }
                }
            }
        }
        player.addListener(sizeListener)
        onDispose {
            player.removeListener(sizeListener)
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val ratio = videoRatio
        val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0f
        val surfaceModifier = if (ratio != null && ratio > 0f && containerRatio > 0f) {
            if (ratio >= containerRatio) {
                Modifier.fillMaxWidth().aspectRatio(ratio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(ratio)
            }
        } else {
            Modifier.fillMaxSize()
        }

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    player.setVideoTextureView(tv)
                }
            },
            update = { tv ->
                player.setVideoTextureView(tv)
            },
            modifier = surfaceModifier
        )
    }
}
