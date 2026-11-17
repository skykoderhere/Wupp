package com.wupp.meow.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.R
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.data.db.WaDao
import com.wupp.meow.ui.screens.WhatsAppAvatar
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWuppClassicUi
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun isHiddenPeekPlaceholder(msg: MessageEntity): Boolean =
    msg.msgType == "UNKNOWN" &&
        msg.body.isBlank() &&
        msg.localMediaPath.isNullOrBlank() &&
        !msg.isViewOnce &&
        !msg.isDeletedBySender

/**
 * Converts DAO descending messages into chronological order for the peek timeline,
 * dropping invisible protocol placeholder rows.
 */
internal fun orderMessagesForPeek(
    messagesDescending: List<MessageEntity>,
    maxMessages: Int = 20
): List<MessageEntity> = messagesDescending
    .asSequence()
    .filterNot(::isHiddenPeekPlaceholder)
    .take(maxMessages.coerceAtLeast(0))
    .toList()
    .asReversed()

/**
 * Resolves a concise display label for incoming group messages inside the peek preview.
 */
internal fun resolvePeekSenderLabel(msg: MessageEntity, isGroupChat: Boolean): String? {
    if (msg.isFromMe || !isGroupChat) return null
    return msg.senderName
        .ifBlank { msg.senderJid.substringBefore("@").substringBefore(":") }
        .takeIf { it.isNotBlank() }
}

/**
 * Resolves consistent display text for a message inside the peek popup.
 */
internal fun resolvePeekMessageText(msg: MessageEntity): String =
    WaDao.previewTextFor(msg).trim()

private fun decodePeekThumbnail(path: String?): ImageBitmap? {
    val file = path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    val targetPx = 420
    while (bounds.outWidth / sampleSize > targetPx || bounds.outHeight / sampleSize > targetPx) {
        sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)?.asImageBitmap()
}

private data class PeekActionSpec(
    val label: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/**
 * iOS-style Peek & Pop chat preview + floating quick-action menu triggered on chat row long-press.
 * Reads only local messages and never sends read receipts or presence updates.
 */
@Composable
fun ChatPeekPopupOverlay(
    chat: ChatEntity,
    recentMessagesDescending: List<MessageEntity>,
    showSeconds: Boolean,
    backdropState: WuppBackdropState?,
    classicMode: Boolean = LocalWuppClassicUi.current,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onToggleUnread: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onSelectChat: () -> Unit,
    onDeleteChat: () -> Unit
) {
    val colors = LocalWaColors.current
    val scale = remember(chat.jid) { Animatable(0.90f) }
    LaunchedEffect(chat.jid) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val timeline = remember(recentMessagesDescending) {
        orderMessagesForPeek(recentMessagesDescending, maxMessages = 20)
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.56f else 0.34f))
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clickable(interactionSource = cardInteraction, indication = null) { /* Consume backdrop taps */ },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatPeekPreviewCard(
                chat = chat,
                messages = timeline,
                showSeconds = showSeconds,
                backdropState = backdropState,
                classicMode = classicMode,
                onOpenChat = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp, max = 360.dp)
                    .weight(1f, fill = false)
            )

            ChatPeekActionMenu(
                chat = chat,
                backdropState = backdropState,
                classicMode = classicMode,
                onDismiss = onDismiss,
                onToggleUnread = onToggleUnread,
                onTogglePin = onTogglePin,
                onToggleMute = onToggleMute,
                onToggleFavorite = onToggleFavorite,
                onToggleArchive = onToggleArchive,
                onSelectChat = onSelectChat,
                onDeleteChat = onDeleteChat
            )
        }
    }
}

@Composable
private fun ChatPeekPreviewCard(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    showSeconds: Boolean,
    backdropState: WuppBackdropState?,
    classicMode: Boolean,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val shape = RoundedCornerShape(24.dp)
    val rimColor = if (colors.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.72f)
    val listState = rememberLazyListState()

    LaunchedEffect(chat.jid, messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    val subtitle = when {
        chat.chatPresence.equals("composing", ignoreCase = true) -> stringResource(R.string.chat_status_typing)
        chat.chatPresence.equals("recording", ignoreCase = true) -> stringResource(R.string.chat_status_recording)
        chat.isOnline -> stringResource(R.string.chat_status_online)
        chat.isGroup -> stringResource(R.string.chat_peek_group_badge)
        else -> ""
    }

    Column(
        modifier = modifier
            .shadow(elevation = 22.dp, shape = shape, clip = false)
            .clip(shape)
            .background(colors.chatCanvas.copy(alpha = if (classicMode) 1f else 0.96f))
            .border(width = 1.dp, color = rimColor, shape = shape)
    ) {
        WuppGlassContainer(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChat),
            colors = colors,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            elevation = 0.dp,
            backdropState = backdropState,
            blurRadius = 10.dp,
            refractionHeight = 10.dp,
            refractionAmount = 18.dp,
            classicMode = classicMode
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WhatsAppAvatar(
                    jid = chat.jid,
                    avatarPath = chat.avatarPath,
                    isGroup = chat.isGroup,
                    size = 36.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.name.ifBlank { chat.jid.substringBefore("@") },
                        color = colors.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = if (chat.isOnline || chat.chatPresence.isNotBlank()) colors.brightGreen else colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brightGreen)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = Color(0xFF0B141A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.pillActiveBg.copy(alpha = 0.85f))
                        .clickable(onClick = onOpenChat)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_peek_open),
                        color = colors.pillActiveText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = colors.pillActiveText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.divider.copy(alpha = 0.6f))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clickable(onClick = onOpenChat),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.chat_no_messages),
                    color = colors.textSecondary,
                    fontSize = 13.5.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = messages, key = { it.id }) { msg ->
                    ChatPeekMessageBubble(
                        msg = msg,
                        isGroupChat = chat.isGroup,
                        showSeconds = showSeconds,
                        onClick = onOpenChat
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatPeekMessageBubble(
    msg: MessageEntity,
    isGroupChat: Boolean,
    showSeconds: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val bubbleShape = RoundedCornerShape(
        topStart = if (msg.isFromMe) 14.dp else 4.dp,
        topEnd = if (msg.isFromMe) 4.dp else 14.dp,
        bottomStart = 14.dp,
        bottomEnd = 14.dp
    )
    val bubbleColor = if (msg.isFromMe) colors.outgoingBubble else colors.incomingBubble
    val senderLabel = remember(msg.id, msg.senderName, msg.senderJid, msg.isFromMe, isGroupChat) {
        resolvePeekSenderLabel(msg, isGroupChat)
    }
    val displayText = remember(
        msg.id,
        msg.body,
        msg.msgType,
        msg.isDeletedBySender,
        msg.isViewOnce,
        msg.viewOnceState,
        msg.durationSec,
        msg.localMediaPath
    ) {
        resolvePeekMessageText(msg)
    }
    val formattedBody = remember(displayText, colors.accentGreen, colors.quotedBg) {
        formatWhatsAppBody(displayText, linkColor = colors.accentGreen, codeBg = colors.quotedBg)
    }
    val shouldLoadImage = !msg.isViewOnce &&
        !msg.isDeletedBySender &&
        (msg.msgType == "IMAGE" || msg.msgType == "STICKER") &&
        !msg.localMediaPath.isNullOrBlank()
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = msg.id, key2 = msg.localMediaPath, key3 = shouldLoadImage) {
        value = if (shouldLoadImage) {
            withContext(Dispatchers.IO) { decodePeekThumbnail(msg.localMediaPath) }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 276.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (!senderLabel.isNullOrBlank()) {
                Text(
                    text = senderLabel,
                    color = colors.brightGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val quoted = msg.quotedBody?.trim().orEmpty()
            if (quoted.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.quotedBg)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(18.dp)
                            .background(colors.brightGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = quoted,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            thumbnail?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = msg.msgType,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 128.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
            }

            if (displayText.isNotBlank() && !(thumbnail != null && msg.body.isBlank())) {
                Text(
                    text = formattedBody,
                    color = if (msg.isDeletedBySender) colors.antiDeleteRed else colors.textPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontStyle = if (msg.isDeletedBySender) FontStyle.Italic else FontStyle.Normal
                )
            }

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (msg.reactionSummary.isNotBlank()) {
                    Text(
                        text = msg.reactionSummary,
                        fontSize = 11.sp,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.quotedBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (msg.isEdited) {
                    Text(
                        text = "Edited",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
                Text(
                    text = formatMessageClock(msg.timestamp, includeSeconds = showSeconds),
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
                if (msg.isFromMe) {
                    DeliveryTickIcon(status = msg.deliveryStatus, mutedColor = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ChatPeekActionMenu(
    chat: ChatEntity,
    backdropState: WuppBackdropState?,
    classicMode: Boolean,
    onDismiss: () -> Unit,
    onToggleUnread: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onSelectChat: () -> Unit,
    onDeleteChat: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val actions = listOf(
        PeekActionSpec(
            label = stringResource(if (chat.unreadCount > 0) R.string.chat_peek_mark_read else R.string.chat_peek_mark_unread),
            icon = if (chat.unreadCount > 0) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread,
            onClick = {
                onToggleUnread()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(if (chat.isPinned) R.string.chat_action_unpin else R.string.chat_action_pin),
            icon = Icons.Rounded.PushPin,
            onClick = {
                onTogglePin()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(if (chat.isMuted) R.string.chat_action_unmute else R.string.chat_action_mute),
            icon = if (chat.isMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
            onClick = {
                onToggleMute()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(if (chat.isFavorite) R.string.chat_peek_remove_favorite else R.string.chat_peek_add_favorite),
            icon = if (chat.isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
            onClick = {
                onToggleFavorite()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(if (chat.isArchived) R.string.chat_action_unarchive else R.string.chat_action_archive),
            icon = if (chat.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
            onClick = {
                onToggleArchive()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(R.string.chat_peek_select),
            icon = Icons.Rounded.CheckCircleOutline,
            onClick = {
                onSelectChat()
                onDismiss()
            }
        ),
        PeekActionSpec(
            label = stringResource(R.string.chat_peek_delete),
            icon = Icons.Rounded.Delete,
            tint = colors.antiDeleteRed,
            onClick = {
                onDismiss()
                onDeleteChat()
            }
        )
    )

    WuppGlassContainer(
        modifier = Modifier.widthIn(min = 230.dp, max = 280.dp),
        colors = colors,
        shape = RoundedCornerShape(22.dp),
        elevation = 20.dp,
        backdropState = backdropState,
        blurRadius = 12.dp,
        refractionHeight = 12.dp,
        refractionAmount = 22.dp,
        classicMode = classicMode
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            actions.forEachIndexed { index, spec ->
                val tint = spec.tint ?: colors.textPrimary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            haptic()
                            spec.onClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = spec.label,
                        color = tint,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.tint ?: colors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (index < actions.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colors.divider.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
