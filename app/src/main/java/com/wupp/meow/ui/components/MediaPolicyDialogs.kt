package com.wupp.meow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.domain.stealth.AutoDownloadNetwork
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.screens.WhatsAppAvatar
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

@Composable
fun AutoDownloadNetworkDialog(
    network: AutoDownloadNetwork,
    initialMask: Int,
    onDismiss: () -> Unit,
    onConfirmMask: (Int) -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    var mask by remember(network, initialMask) { mutableIntStateOf(initialMask) }

    val options = listOf(
        StealthMatrixManager.MEDIA_PHOTO to "Photos",
        StealthMatrixManager.MEDIA_AUDIO to "Audio",
        StealthMatrixManager.MEDIA_VIDEO to "Videos",
        StealthMatrixManager.MEDIA_DOCUMENT to "Documents"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        title = {
            Text(
                text = network.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (bit, label) ->
                    val checked = (mask and bit) != 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic()
                                mask = if (checked) mask and bit.inv() else mask or bit
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { want ->
                                mask = if (want) mask or bit else mask and bit.inv()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentGreen,
                                checkmarkColor = Color.White,
                                uncheckedColor = colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmMask(mask)
                onDismiss()
            }) {
                Text("OK", color = colors.brightGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun GalleryVisibilityContactsDialog(
    chats: List<ChatEntity>,
    hiddenJids: Set<String>,
    onToggleHideContact: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    var query by remember { mutableStateOf("") }
    val filteredChats = remember(chats, query) {
        val q = query.trim().lowercase()
        chats.filter {
            !it.jid.endsWith("@newsletter") &&
                it.jid != "status@broadcast" &&
                !it.isCommunityParent &&
                (q.isEmpty() || it.name.lowercase().contains(q) || it.jid.lowercase().contains(q))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        title = {
            Column {
                Text(
                    text = "Hide media from gallery",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Turn on for contacts or groups whose new photos and videos stay inside Wupp only.",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search contacts or groups") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredChats, key = { it.jid }) { chat ->
                        val isHidden = chat.jid in hiddenJids
                        val displayName = chat.name.ifBlank { chat.jid.substringBefore("@") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleHideContact(chat.jid, !isHidden) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhatsAppAvatar(
                                jid = chat.jid,
                                avatarPath = chat.avatarPath,
                                isGroup = chat.isGroup,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    color = colors.textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isHidden) "Hidden from device gallery" else "Follows global visibility",
                                    color = if (isHidden) colors.warningAmber else colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = isHidden,
                                onCheckedChange = { onToggleHideContact(chat.jid, it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brightGreen)
            }
        }
    )
}

@Composable
fun ChatMediaVisibilitySelector(
    selectedOverride: Boolean?,
    globalDefaultVisible: Boolean,
    onSelectOverride: (Boolean?) -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val options: List<Pair<Boolean?, String>> = listOf(
        null to "Default (${if (globalDefaultVisible) "Yes" else "No"})",
        true to "Yes (Show)",
        false to "No (Hide)"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Media visibility (Gallery)",
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            text = "Show newly downloaded media from this chat in your device's gallery",
            color = colors.textSecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                val active = selectedOverride == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.pillActiveBg else colors.appSurface.copy(alpha = 0.55f))
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) colors.brightGreen else colors.divider,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            haptic()
                            onSelectOverride(value)
                        }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) colors.pillActiveText else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
