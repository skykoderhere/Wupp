package com.wupp.meow.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.ui.theme.LocalWaColors
import java.io.File

/**
 * Spring-animated 260.dp square WhatsApp Quick Contact preview modal triggered when tapping
 * an avatar in the Chats list.
 */
@Composable
fun QuickContactDialog(
    chat: ChatEntity,
    aboutOverride: String = "",
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenGhostInfo: () -> Unit,
    onOpenPresenceLog: () -> Unit,
    onOpenAvatarHistory: () -> Unit,
    onRequestFullAvatar: (() -> Unit)? = null,
    onSaveAvatar: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val scale = remember { Animatable(0.76f) }
    val showFullPhoto = remember(chat.jid) { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(chat.jid) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
        )
    }

    val avatarBmp = remember(chat.avatarPath) {
        chat.avatarPath?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    LaunchedEffect(chat.jid, chat.avatarPath) {
        if (avatarBmp == null || (avatarBmp.width <= 160 && avatarBmp.height <= 160)) {
            onRequestFullAvatar?.invoke()
        }
    }

    if (showFullPhoto.value) {
        ProfilePhotoLightbox(
            jid = chat.jid,
            title = chat.name,
            avatarPath = chat.avatarPath,
            onDismiss = { showFullPhoto.value = false },
            onRequestFullAvatar = onRequestFullAvatar,
            onSaveAvatar = onSaveAvatar
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .size(260.dp)
                .scale(scale.value),
            shape = RoundedCornerShape(14.dp),
            color = colors.elevatedBar,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.accentGreen.copy(alpha = 0.25f))
                        .clickable {
                            showFullPhoto.value = true
                        }
                ) {
                    if (avatarBmp != null) {
                        Image(
                            bitmap = avatarBmp,
                            contentDescription = chat.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.outgoingBubble),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.name.ifBlank { chat.jid.substringBefore("@") }.trim().take(1).uppercase(),
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x7A000000))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = chat.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    val resolvedBio = chat.aboutText.ifBlank { aboutOverride }.trim()
                    if (resolvedBio.isNotBlank() && !chat.isGroup) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x8A000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = resolvedBio,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.5.sp,
                                maxLines = 2
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.divider)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(colors.elevatedBar),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismiss(); onOpenChat() }) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, "Open Chat", tint = colors.accentGreen)
                    }
                    IconButton(onClick = { onDismiss(); onOpenGhostInfo() }) {
                        Icon(Icons.Rounded.Shield, "Ghost Privacy", tint = colors.accentGreen)
                    }
                    IconButton(onClick = { onDismiss(); onOpenPresenceLog() }) {
                        Icon(Icons.Rounded.Info, "Presence Log", tint = colors.accentGreen)
                    }
                    IconButton(onClick = { onDismiss(); onOpenAvatarHistory() }) {
                        Icon(Icons.Rounded.History, "Avatar Archive", tint = colors.accentGreen)
                    }
                }
            }
        }
    }
}
