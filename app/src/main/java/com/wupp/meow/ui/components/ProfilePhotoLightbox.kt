package com.wupp.meow.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@Composable
fun ProfilePhotoLightbox(
    jid: String,
    title: String,
    avatarPath: String?,
    onDismiss: () -> Unit,
    onRequestFullAvatar: (() -> Unit)? = null,
    onSaveAvatar: (() -> Unit)? = null
) {
    val bmp = remember(avatarPath) {
        avatarPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
                ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    val isLowResPreview = bmp == null || (bmp.width <= 160 && bmp.height <= 160)
    LaunchedEffect(jid, avatarPath) {
        if (isLowResPreview) {
            onRequestFullAvatar?.invoke()
        }
    }

    var scale by remember(avatarPath) { mutableFloatStateOf(1f) }
    var offset by remember(avatarPath) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale > 1.02f) {
            Offset(
                x = (offset.x + panChange.x).coerceIn(-600f, 600f),
                y = (offset.y + panChange.y).coerceIn(-600f, 600f)
            )
        } else {
            Offset.Zero
        }
    }

    val headerTitle = when {
        jid.endsWith("@g.us") -> "Group icon"
        jid.endsWith("@newsletter") -> title.ifBlank { "Channel icon" }
        else -> title.ifBlank { jid.substringBefore("@") }
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(avatarPath) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.15f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.4f
                                }
                            }
                        )
                    }
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = headerTitle,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141B20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (jid.endsWith("@g.us")) Icons.Filled.Group else Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color(0xFF8E9DA8),
                                modifier = Modifier.size(68.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No profile photo",
                            color = Color(0xFF8E9DA8),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (jid.endsWith("@g.us") && title.isNotBlank()) {
                        Text(
                            text = title,
                            color = Color(0xFFB1B8BE),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (bmp != null && onSaveAvatar != null) {
                    IconButton(onClick = onSaveAvatar) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Save icon",
                            tint = Color.White
                        )
                    }
                }
            }

            if (isLowResPreview && bmp != null && onRequestFullAvatar != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xCC141B20)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF25D366)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading HD photo...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
