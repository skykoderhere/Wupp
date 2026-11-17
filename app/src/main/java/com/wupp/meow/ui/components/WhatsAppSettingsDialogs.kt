package com.wupp.meow.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.core.service.AccountPrivacyUi
import com.wupp.meow.core.service.BlockedContactUi
import com.wupp.meow.core.service.OwnProfileUi
import com.wupp.meow.core.service.WuppStorageStats
import com.wupp.meow.data.db.ChatEntity
import com.wupp.meow.ui.screens.WhatsAppAvatar
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.launch
import java.util.Locale

internal fun formatBytesCompact(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
        safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
    }
}

internal fun formatPrivacyValueLabel(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
    "all" -> "Everyone"
    "contacts" -> "My contacts"
    "contact_blacklist" -> "My contacts except…"
    "contact_allowlist" -> "Only share with…"
    "match_last_seen" -> "Same as last seen"
    "known" -> "Silence unknown callers"
    "none" -> "Nobody"
    else -> raw.ifBlank { "My contacts" }
}

internal fun formatDisappearingTimerLabel(seconds: Int): String = when (seconds) {
    0 -> "Off"
    86_400 -> "24 hours"
    604_800 -> "7 days"
    7_776_000 -> "90 days"
    else -> if (seconds > 0) "${seconds / 3600}h" else "Off"
}

internal fun formatFontScaleLabel(scale: Float): String = when {
    scale <= 0.90f -> "Small"
    scale >= 1.10f -> "Large"
    else -> "Medium"
}

@Composable
fun SettingsProfileHeaderCard(
    ownJid: String,
    profile: OwnProfileUi,
    avatarPath: String?,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.elevatedBar,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WhatsAppAvatar(
                jid = ownJid.ifBlank { profile.ownJid },
                avatarPath = avatarPath,
                isGroup = false,
                size = 56.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.pushName.ifBlank {
                        profile.phone.ifBlank { "My WhatsApp Account" }
                    },
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.about.ifBlank { "Tap to edit display name & About" },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.phone.isNotBlank()) {
                    Text(
                        text = profile.phone,
                        color = colors.accentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit profile",
                tint = colors.accentGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ProfileEditorDialog(
    initialProfile: OwnProfileUi,
    onSaveName: suspend (String) -> Result<Unit>,
    onSaveAbout: suspend (String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val scope = rememberCoroutineScope()
    var nameDraft by remember(initialProfile.pushName) { mutableStateOf(initialProfile.pushName) }
    var aboutDraft by remember(initialProfile.about) { mutableStateOf(initialProfile.about) }
    var saving by remember { mutableStateOf(false) }

    val quickAbouts = remember {
        listOf("Available", "Busy", "At work", "In a meeting", "Urgent calls only")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("WhatsApp Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (initialProfile.phone.isNotBlank()) {
                    Text(
                        text = "Phone: ${initialProfile.phone}",
                        color = colors.accentGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!initialProfile.syncSupported) {
                    Text(
                        text = "Server profile sync requires rebuilding whatsbridge.aar with the latest Go changes.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { if (it.length <= 45) nameDraft = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brightGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = aboutDraft,
                    onValueChange = { if (it.length <= 139) aboutDraft = it },
                    label = { Text("About") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brightGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Quick About presets",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    quickAbouts.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { aboutDraft = preset }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (aboutDraft == preset) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.brightGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && initialProfile.syncSupported &&
                    (nameDraft.trim().isNotEmpty() || aboutDraft.trim().isNotEmpty()),
                onClick = {
                    saving = true
                    scope.launch {
                        val trimmedName = nameDraft.trim()
                        val trimmedAbout = aboutDraft.trim()
                        if (trimmedName.isNotEmpty() && trimmedName != initialProfile.pushName) {
                            onSaveName(trimmedName)
                        }
                        if (trimmedAbout.isNotEmpty() && trimmedAbout != initialProfile.about) {
                            onSaveAbout(trimmedAbout)
                        }
                        saving = false
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brightGreen,
                    contentColor = Color(0xFF072B17)
                )
            ) {
                Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.Bold)
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
fun WhatsAppAccountPrivacyDialog(
    initialTimerSec: Int,
    onLoadPrivacy: suspend () -> Result<AccountPrivacyUi>,
    onUpdateCategory: suspend (String, String) -> Result<AccountPrivacyUi>,
    onUpdateDefaultTimer: suspend (Int) -> Result<Unit>,
    onOpenBlockedContacts: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(AccountPrivacyUi(defaultDisappearingSec = initialTimerSec))
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editingCategory by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingTimer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadPrivacy()
            .onSuccess {
                state = it
                loading = false
            }
            .onFailure {
                loadError = it.message
                loading = false
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("WhatsApp Account Privacy", fontWeight = FontWeight.Bold) },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.brightGreen)
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.appSurface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = colors.brightGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (state.syncSupported) {
                                "Synced directly with your WhatsApp account."
                            } else {
                                "Server privacy controls activate once whatsbridge.aar is rebuilt."
                            },
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (!loadError.isNullOrBlank()) {
                        Text(
                            text = loadError.orEmpty(),
                            color = colors.antiDeleteRed,
                            fontSize = 12.sp
                        )
                    }

                    PrivacyRowItem(
                        title = "Last seen",
                        valueLabel = formatPrivacyValueLabel(state.lastSeen),
                        enabled = state.syncSupported,
                        onClick = { editingCategory = "last" to "Last seen" }
                    )
                    PrivacyRowItem(
                        title = "Who can see when I'm online",
                        valueLabel = formatPrivacyValueLabel(state.online),
                        enabled = state.syncSupported,
                        onClick = { editingCategory = "online" to "Online visibility" }
                    )
                    PrivacyRowItem(
                        title = "Profile photo",
                        valueLabel = formatPrivacyValueLabel(state.profile),
                        enabled = state.syncSupported,
                        onClick = { editingCategory = "profile" to "Profile photo" }
                    )
                    PrivacyRowItem(
                        title = "Groups (who can add me)",
                        valueLabel = formatPrivacyValueLabel(state.groupAdd),
                        enabled = state.syncSupported,
                        onClick = { editingCategory = "groupadd" to "Groups" }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Account Read Receipts", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Server-wide toggle (Wupp Ghost Mode adds stealth per chat)", color = colors.textSecondary, fontSize = 11.5.sp)
                        }
                        Switch(
                            checked = state.readReceipts == "all",
                            enabled = state.syncSupported,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    onUpdateCategory("readreceipts", if (checked) "all" else "none")
                                        .onSuccess { state = it }
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Silence Unknown Callers", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Mute calls from unsaved numbers on your WhatsApp account", color = colors.textSecondary, fontSize = 11.5.sp)
                        }
                        Switch(
                            checked = state.callAdd == "known",
                            enabled = state.syncSupported,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    onUpdateCategory("calladd", if (checked) "known" else "all")
                                        .onSuccess { state = it }
                                }
                            }
                        )
                    }
                    PrivacyRowItem(
                        title = "Default message timer",
                        valueLabel = formatDisappearingTimerLabel(state.defaultDisappearingSec),
                        enabled = true,
                        onClick = { editingTimer = true }
                    )
                    HorizontalDivider(color = colors.divider.copy(alpha = 0.5f))
                    PrivacyRowItem(
                        title = "Blocked contacts",
                        valueLabel = "View & manage",
                        enabled = true,
                        onClick = {
                            onDismiss()
                            onOpenBlockedContacts()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    )

    editingCategory?.let { (cat, label) ->
        val options = when (cat) {
            "online" -> listOf("all" to "Everyone", "match_last_seen" to "Same as last seen")
            else -> listOf("all" to "Everyone", "contacts" to "My contacts", "none" to "Nobody")
        }
        val currentVal = when (cat) {
            "last" -> state.lastSeen
            "online" -> state.online
            "profile" -> state.profile
            "groupadd" -> state.groupAdd
            else -> "contacts"
        }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            containerColor = colors.elevatedBar,
            title = { Text(label, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (valueKey, valueTitle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    editingCategory = null
                                    scope.launch {
                                        onUpdateCategory(cat, valueKey).onSuccess { state = it }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentVal == valueKey,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brightGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(valueTitle, color = colors.textPrimary, fontSize = 14.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (editingTimer) {
        val timers = listOf(0, 86_400, 604_800, 7_776_000)
        AlertDialog(
            onDismissRequest = { editingTimer = false },
            containerColor = colors.elevatedBar,
            title = { Text("Default message timer", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    timers.forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    editingTimer = false
                                    scope.launch {
                                        onUpdateDefaultTimer(sec).onSuccess {
                                            state = state.copy(defaultDisappearingSec = sec)
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.defaultDisappearingSec == sec,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brightGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatDisappearingTimerLabel(sec), color = colors.textPrimary, fontSize = 14.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingTimer = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun PrivacyRowItem(
    title: String,
    valueLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic()
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = valueLabel,
            color = colors.accentGreen.copy(alpha = if (enabled) 1f else 0.55f),
            fontSize = 12.5.sp
        )
    }
}

@Composable
fun BlockedContactsDialog(
    dmCandidates: List<ChatEntity>,
    onLoadBlocked: suspend () -> Result<Pair<List<BlockedContactUi>, Boolean>>,
    onSetBlocked: suspend (String, Boolean) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val scope = rememberCoroutineScope()
    var blockedList by remember { mutableStateOf<List<BlockedContactUi>>(emptyList()) }
    var syncSupported by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        onLoadBlocked()
            .onSuccess { (list, supported) ->
                blockedList = list
                syncSupported = supported
                loading = false
            }
            .onFailure {
                errorText = it.message
                loading = false
            }
    }

    LaunchedEffect(Unit) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Blocked contacts (${blockedList.size})", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!syncSupported) {
                    Text(
                        text = "Blocked list sync activates once whatsbridge.aar is rebuilt.",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp
                    )
                } else if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.brightGreen)
                    }
                } else {
                    if (!errorText.isNullOrBlank()) {
                        Text(errorText.orEmpty(), color = colors.antiDeleteRed, fontSize = 12.sp)
                    }
                    if (blockedList.isEmpty()) {
                        Text(
                            text = "No blocked contacts on your WhatsApp account.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(blockedList, key = { it.jid }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = null,
                                        tint = colors.antiDeleteRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = colors.textPrimary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.phone.isNotBlank() && item.phone != item.name) {
                                            Text(item.phone, color = colors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                onSetBlocked(item.jid, false).onSuccess { refresh() }
                                            }
                                        }
                                    ) {
                                        Text("Unblock", color = colors.accentGreen, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (dmCandidates.isNotEmpty()) {
                        Button(
                            onClick = { showAddPicker = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.appSurface,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Block a contact")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.accentGreen)
            }
        }
    )

    if (showAddPicker) {
        val blockedJids = remember(blockedList) { blockedList.map { it.jid }.toSet() }
        val candidates = remember(dmCandidates, blockedJids) {
            dmCandidates.filter { !it.isGroup && !it.jid.endsWith("@newsletter") && it.jid !in blockedJids }
        }
        AlertDialog(
            onDismissRequest = { showAddPicker = false },
            containerColor = colors.elevatedBar,
            title = { Text("Select contact to block", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.jid }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddPicker = false
                                    scope.launch {
                                        onSetBlocked(chat.jid, true).onSuccess { refresh() }
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhatsAppAvatar(jid = chat.jid, avatarPath = chat.avatarPath, isGroup = false, size = 34.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = chat.name.ifBlank { chat.jid.substringBefore("@") },
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddPicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun StorageUsageDialog(
    onLoadStats: suspend () -> WuppStorageStats,
    onClearTempCache: suspend () -> Long,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(WuppStorageStats()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        stats = onLoadStats()
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Storage & Cache", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.brightGreen)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StorageStatRow("Saved media vault", formatBytesCompact(stats.vaultBytes))
                    StorageStatRow("Local chat database", formatBytesCompact(stats.dbBytes))
                    StorageStatRow("Temporary previews & cache", formatBytesCompact(stats.tempCacheBytes))
                    HorizontalDivider(color = colors.divider.copy(alpha = 0.5f))
                    Text(
                        text = "Clearing temp cache frees temporary PDF previews and shared media copies without touching your messages or saved vault.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val freed = onClearTempCache()
                        stats = onLoadStats()
                        Toast.makeText(
                            context,
                            "Cleared ${formatBytesCompact(freed)} cache",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brightGreen,
                    contentColor = Color(0xFF072B17)
                )
            ) {
                Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Temp Cache", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun StorageStatRow(label: String, value: String) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.textPrimary, fontSize = 14.sp)
        Text(value, color = colors.accentGreen, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChatFontSizeDialog(
    currentScale: Float,
    onSelectScale: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val presets = listOf(
        0.85f to "Small",
        1.0f to "Medium (Default)",
        1.15f to "Large"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Chat Font Size", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (scale, label) ->
                    val selected = kotlin.math.abs(currentScale - scale) < 0.05f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic()
                                onSelectScale(scale)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.brightGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, color = colors.textPrimary, fontSize = (15f * scale).sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun CompanionScopeInfoDialog(onDismiss: () -> Unit) {
    val colors = LocalWaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        icon = { Icon(Icons.Rounded.Info, null, tint = colors.accentGreen) },
        title = { Text("Linked Companion Settings Scope", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Managed directly inside Wupp:",
                    color = colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Account Privacy (Last seen, Online, Photo, Groups, Silence unknown callers, Default timer)\n• Blocked contacts & Profile Push Name / About\n• Ghost Mode, Anti-Delete, View Once & Media Auto-Download\n• Enter-to-send, Chat font size, Wallpaper, App Lock, Notifications & Storage cache",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "Requires Official WhatsApp (primary device lock):",
                    color = colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Two-step verification PIN & Passkeys\n• Change phone number / Delete account\n• Google Drive cloud backups & native voice/video calls",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
