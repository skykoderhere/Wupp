package com.wupp.meow.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.theme.LocalWaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class ChatWallpaperPreset(
    val id: String,
    val label: String,
    val topColor: Color,
    val bottomColor: Color
) {
    val storageUri: String get() = "preset:$id"
}

val ChatWallpaperPresets: List<ChatWallpaperPreset> = listOf(
    ChatWallpaperPreset("midnight_slate", "Midnight", Color(0xFF0D1520), Color(0xFF172638)),
    ChatWallpaperPreset("deep_emerald", "Emerald", Color(0xFF071B15), Color(0xFF0F3126)),
    ChatWallpaperPreset("obsidian", "Obsidian", Color(0xFF050507), Color(0xFF111218)),
    ChatWallpaperPreset("ocean_teal", "Ocean", Color(0xFF081D24), Color(0xFF103744)),
    ChatWallpaperPreset("lavender_dusk", "Dusk", Color(0xFF1A1427), Color(0xFF2B2040)),
    ChatWallpaperPreset("warm_sand", "Sand", Color(0xFFEDE4D7), Color(0xFFDCD0BE))
)

fun describeChatWallpaper(uri: String): String = when {
    uri.isBlank() -> "Default doodle"
    uri.startsWith("file:") -> "Custom photo"
    uri.startsWith("preset:") -> {
        val id = uri.removePrefix("preset:")
        ChatWallpaperPresets.firstOrNull { it.id == id }?.label ?: "Preset"
    }
    else -> "Default doodle"
}

fun copyCustomWallpaperImage(
    context: Context,
    sourceUri: Uri,
    keepFilePaths: Set<String> = emptySet()
): String? {
    return runCatching {
        val resolver = context.applicationContext.contentResolver
        val dir = File(context.applicationContext.filesDir, "wallpapers").apply { mkdirs() }
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
        val maxDim = 1920
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: return null

        val orientation = resolver.openInputStream(sourceUri)?.use { input ->
            runCatching {
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }

        val scale = max(bitmap.width, bitmap.height).let { largest ->
            if (largest > maxDim) maxDim.toFloat() / largest.toFloat() else 1f
        }
        if (scale < 1f) {
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val outFile = File(dir, "chat_bg_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()

        dir.listFiles()?.forEach { f ->
            if (f.name != outFile.name && f.name.startsWith("chat_bg_") && f.absolutePath !in keepFilePaths) {
                f.delete()
            }
        }
        "file:${outFile.absolutePath}"
    }.getOrNull()
}

/**
 * Renders either the default WhatsApp doodle wallpaper, a curated preset gradient,
 * or a user-chosen custom background photo with optional dimming overlay.
 */
@Composable
fun DoodleBackground(
    modifier: Modifier = Modifier,
    wallpaperUriOverride: String? = null,
    wallpaperDimOverride: Float? = null,
    showDoodleOverride: Boolean? = null,
    wallpaperScaleOverride: Float? = null,
    wallpaperOffsetXOverride: Float? = null,
    wallpaperOffsetYOverride: Float? = null,
    backdropState: WuppBackdropState? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalWaColors.current
    val stealth by StealthMatrixManager.config.collectAsState()
    val wallpaperUri = wallpaperUriOverride ?: stealth.chatWallpaperUri
    val wallpaperDim = (wallpaperDimOverride ?: stealth.chatWallpaperDim).coerceIn(0f, 0.75f)
    val showDoodle = showDoodleOverride ?: stealth.chatWallpaperShowDoodle
    val wallpaperScale = (wallpaperScaleOverride ?: stealth.chatWallpaperScale).coerceIn(1f, 3f)
    val wallpaperOffsetX = (wallpaperOffsetXOverride ?: stealth.chatWallpaperOffsetX).coerceIn(-1f, 1f)
    val wallpaperOffsetY = (wallpaperOffsetYOverride ?: stealth.chatWallpaperOffsetY).coerceIn(-1f, 1f)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val customFilePath = remember(wallpaperUri) {
        if (wallpaperUri.startsWith("file:")) wallpaperUri.removePrefix("file:") else ""
    }
    val customBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = customFilePath) {
        value = if (customFilePath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(customFilePath)
                    if (f.exists() && f.length() > 0L) {
                        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                    } else null
                }.getOrNull()
            }
        }
    }

    val preset = remember(wallpaperUri) {
        if (wallpaperUri.startsWith("preset:")) {
            val id = wallpaperUri.removePrefix("preset:")
            ChatWallpaperPresets.firstOrNull { it.id == id }
        } else null
    }

    val shaderBrush = remember(colors.doodleColor) {
        val tilePx = 360
        val bmp = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colors.doodleColor.toArgb()
        }
        drawDoodleTile(c, paint)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    val tileAlpha = colors.doodleTintAlpha
    Box(modifier = modifier.background(colors.chatCanvas)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .then(if (backdropState != null) Modifier.wuppBackdropSource(backdropState) else Modifier)
                .background(colors.chatCanvas)
        ) {
            when {
                customBitmap != null -> {
                    Image(
                        bitmap = customBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = wallpaperScale
                                scaleY = wallpaperScale
                                translationX = wallpaperOffsetX * canvasSize.width.toFloat()
                                translationY = wallpaperOffsetY * canvasSize.height.toFloat()
                            }
                    )
                    if (wallpaperDim > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = wallpaperDim))
                        )
                    }
                }
                preset != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(preset.topColor)
                    )
                    if (showDoodle) {
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(brush = shaderBrush, alpha = tileAlpha)
                        }
                    }
                }
                else -> {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(brush = shaderBrush, alpha = tileAlpha)
                    }
                }
            }
        }
        content()
    }
}

private fun drawDoodleTile(c: Canvas, p: Paint) {
    fun withTransform(x: Float, y: Float, deg: Float, scale: Float = 1f, block: () -> Unit) {
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        c.scale(scale, scale)
        block()
        c.restore()
    }

    // 1. Speech bubble + lines
    withTransform(46f, 44f, -14f, 0.95f) {
        c.drawRoundRect(RectF(-18f, -13f, 18f, 11f), 7f, 7f, p)
        c.drawLine(-9f, -3f, 9f, -3f, p)
        c.drawLine(-9f, 3f, 4f, 3f, p)
    }

    // 2. Ringed planet
    withTransform(138f, 36f, -20f, 1.0f) {
        c.drawCircle(0f, 0f, 11f, p)
        c.drawArc(RectF(-22f, -6f, 22f, 6f), -25f, 230f, false, p)
    }

    // 3. Crescent moon + star
    withTransform(232f, 42f, 18f, 0.95f) {
        val path = Path().apply {
            addArc(RectF(-13f, -13f, 13f, 13f), -70f, 220f)
            arcTo(RectF(-5f, -11f, 15f, 11f), 150f, -180f, false)
        }
        c.drawPath(path, p)
        c.drawPoint(14f, -8f, p)
    }

    // 4. Coffee mug
    withTransform(316f, 48f, -10f, 0.95f) {
        c.drawRoundRect(RectF(-12f, -9f, 10f, 13f), 4f, 4f, p)
        c.drawArc(RectF(6f, -4f, 17f, 7f), -90f, 180f, false, p)
        c.drawLine(-4f, -14f, -2f, -19f, p)
        c.drawLine(3f, -14f, 5f, -19f, p)
    }

    // 5. Camera
    withTransform(38f, 134f, 12f, 0.95f) {
        c.drawRoundRect(RectF(-17f, -11f, 17f, 13f), 4f, 4f, p)
        c.drawRoundRect(RectF(-7f, -16f, 7f, -11f), 2f, 2f, p)
        c.drawCircle(0f, 1f, 6f, p)
        c.drawPoint(11f, -5f, p)
    }

    // 6. Paper plane
    withTransform(128f, 118f, -24f, 1.0f) {
        val plane = Path().apply {
            moveTo(-16f, 2f)
            lineTo(17f, -10f)
            lineTo(4f, 14f)
            lineTo(-3f, 4f)
            close()
        }
        c.drawPath(plane, p)
        c.drawLine(-3f, 4f, 17f, -10f, p)
    }

    // 7. Phone handset
    withTransform(214f, 128f, 22f, 0.95f) {
        val phone = Path().apply {
            moveTo(-10f, -12f)
            quadTo(-14f, 0f, -6f, 10f)
            quadTo(4f, 16f, 12f, 12f)
        }
        c.drawPath(phone, p)
        c.drawCircle(-10f, -12f, 3.5f, p)
        c.drawCircle(12f, 12f, 3.5f, p)
    }

    // 8. Smiley face
    withTransform(306f, 132f, -8f, 1.0f) {
        c.drawCircle(0f, 0f, 14f, p)
        c.drawCircle(-5f, -4f, 1.4f, p)
        c.drawCircle(5f, -4f, 1.4f, p)
        c.drawArc(RectF(-7f, -2f, 7f, 8f), 15f, 150f, false, p)
    }

    // 9. Music notes
    withTransform(58f, 224f, -12f, 0.95f) {
        c.drawCircle(-8f, 10f, 4.5f, p)
        c.drawCircle(9f, 7f, 4.5f, p)
        c.drawLine(-4f, 10f, -4f, -10f, p)
        c.drawLine(13f, 7f, 13f, -13f, p)
        c.drawLine(-4f, -10f, 13f, -13f, p)
    }

    // 10. Heart
    withTransform(150f, 214f, 16f, 0.95f) {
        val heart = Path().apply {
            moveTo(0f, 13f)
            cubicTo(-16f, 3f, -16f, -13f, 0f, -5f)
            cubicTo(16f, -13f, 16f, 3f, 0f, 13f)
        }
        c.drawPath(heart, p)
    }

    // 11. Envelope
    withTransform(246f, 218f, -14f, 0.95f) {
        c.drawRoundRect(RectF(-17f, -11f, 17f, 11f), 3f, 3f, p)
        val flap = Path().apply {
            moveTo(-16f, -9f)
            lineTo(0f, 3f)
            lineTo(16f, -9f)
        }
        c.drawPath(flap, p)
    }

    // 12. Microphone
    withTransform(326f, 228f, 10f, 0.95f) {
        c.drawRoundRect(RectF(-6f, -14f, 6f, 4f), 6f, 6f, p)
        c.drawArc(RectF(-11f, -6f, 11f, 10f), 0f, 180f, false, p)
        c.drawLine(0f, 10f, 0f, 16f, p)
        c.drawLine(-6f, 16f, 6f, 16f, p)
    }

    // 13. Clock
    withTransform(48f, 314f, 8f, 0.95f) {
        c.drawCircle(0f, 0f, 14f, p)
        c.drawLine(0f, 0f, 0f, -8f, p)
        c.drawLine(0f, 0f, 6f, 3f, p)
    }

    // 14. 5-point Star
    withTransform(142f, 312f, 14f, 1.0f) {
        val star = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 14f else 6f
            val angle = (-90.0 + i * 36.0) * PI / 180.0
            val sx = (r * cos(angle)).toFloat()
            val sy = (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(sx, sy) else star.lineTo(sx, sy)
        }
        star.close()
        c.drawPath(star, p)
    }

    // 15. Padlock
    withTransform(234f, 316f, -10f, 0.95f) {
        c.drawRoundRect(RectF(-11f, -3f, 11f, 13f), 4f, 4f, p)
        c.drawArc(RectF(-7f, -14f, 7f, 1f), 180f, 180f, false, p)
        c.drawCircle(0f, 5f, 2f, p)
    }

    // 16. Sparkle / Four-point star
    withTransform(318f, 314f, 20f, 0.95f) {
        val sp = Path().apply {
            moveTo(0f, -13f)
            quadTo(0f, 0f, 13f, 0f)
            quadTo(0f, 0f, 0f, 13f)
            quadTo(0f, 0f, -13f, 0f)
            quadTo(0f, 0f, 0f, -13f)
        }
        c.drawPath(sp, p)
    }
}
