package com.wupp.meow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.R
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger

private val PRESET_REACTION_EMOJIS = listOf(
    "👍", "❤️", "😂", "😮", "😢", "🙏", "😊",
    "🔥", "👏", "😍", "🤔", "🎉", "💯", "🥺",
    "😭", "🤝", "❤️‍🔥", "💀", "👀", "🚀", "🫡",
    "✅", "❌", "⚡", "🥳", "🫶", "😎", "🤯"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionEmojiCustomizerDialog(
    currentEmojis: List<String>,
    onSave: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = rememberWuppHapticTrigger()
    val initial = remember(currentEmojis) {
        StealthMatrixManager.sanitizeQuickReactions(currentEmojis)
    }
    val slots = remember(initial) {
        mutableStateListOf<String>().apply { addAll(initial) }
    }
    var selectedSlot by remember { mutableIntStateOf(0) }
    var customInput by remember { mutableStateOf("") }

    fun assignEmoji(rawEmoji: String) {
        val grapheme = StealthMatrixManager.extractFirstGrapheme(rawEmoji)
        if (grapheme.isBlank()) return
        val existingIdx = slots.indexOf(grapheme)
        if (existingIdx in slots.indices && existingIdx != selectedSlot) {
            val prev = slots[selectedSlot]
            slots[selectedSlot] = grapheme
            slots[existingIdx] = prev
        } else {
            slots[selectedSlot] = grapheme
        }
        selectedSlot = (selectedSlot + 1) % slots.size
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.reaction_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.reaction_dialog_subtitle, selectedSlot + 1),
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slots.forEachIndexed { idx, emoji ->
                        val isSelected = idx == selectedSlot
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.accentGreen.copy(alpha = 0.20f)
                                    else colors.pillInactiveBg
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) colors.accentGreen else colors.divider,
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptic()
                                    selectedSlot = idx
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { value ->
                            val clean = StealthMatrixManager.extractFirstGrapheme(value)
                            customInput = clean
                            if (clean.isNotBlank()) {
                                assignEmoji(clean)
                                customInput = ""
                            }
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.reaction_custom_hint),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentGreen,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = stringResource(R.string.reaction_presets_label),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PRESET_REACTION_EMOJIS.forEach { emoji ->
                        val active = emoji == slots.getOrNull(selectedSlot)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) colors.accentGreen.copy(alpha = 0.18f)
                                    else colors.pillInactiveBg
                                )
                                .border(
                                    width = if (active) 1.dp else 0.5.dp,
                                    color = if (active) colors.accentGreen else colors.divider,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    haptic()
                                    assignEmoji(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                TextButton(
                    onClick = {
                        onReset()
                        slots.clear()
                        slots.addAll(StealthMatrixManager.DEFAULT_QUICK_REACTIONS)
                        selectedSlot = 0
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reaction_reset_defaults),
                        color = colors.accentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = colors.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(StealthMatrixManager.sanitizeQuickReactions(slots))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
