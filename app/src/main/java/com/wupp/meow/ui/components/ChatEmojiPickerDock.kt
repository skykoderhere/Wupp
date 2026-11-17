package com.wupp.meow.ui.components

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.wupp.meow.R
import com.wupp.meow.ui.theme.WaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.BreakIterator

/**
 * Inserts [insert] into [current] at the active selection range (replacing any selected text)
 * and advances the cursor cleanly right past the inserted text.
 */
internal fun insertTextAtSelection(current: TextFieldValue, insert: String): TextFieldValue {
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val updated = buildString(text.length + insert.length) {
        append(text, 0, start)
        append(insert)
        append(text, end, text.length)
    }
    return TextFieldValue(
        text = updated,
        selection = TextRange(start + insert.length)
    )
}

/**
 * Deletes the selected range, or one full Unicode grapheme cluster before the cursor,
 * preventing split surrogate pairs or broken ZWJ emoji sequences.
 */
internal fun deleteBeforeSelection(current: TextFieldValue): TextFieldValue {
    val text = current.text
    if (text.isEmpty()) return current
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    if (start != end) {
        return TextFieldValue(
            text = text.removeRange(start, end),
            selection = TextRange(start)
        )
    }
    if (start == 0) return current
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val prev = iterator.preceding(start).takeIf { it != BreakIterator.DONE } ?: (start - 1)
    val safePrev = prev.coerceIn(0, start)
    return TextFieldValue(
        text = text.removeRange(safePrev, start),
        selection = TextRange(safePrev)
    )
}

/**
 * Native AndroidX [EmojiPickerView] panel docked at IME height inside the chat screen,
 * with grapheme-aware repeat backspace and Dark/Light theme overlay matching.
 */
@Composable
fun ChatEmojiPickerDock(
    dockHeight: Dp,
    colors: WaColors,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnEmojiSelected by rememberUpdatedState(onEmojiSelected)
    val latestOnBackspace by rememberUpdatedState(onBackspace)
    val dockBg = colors.chatCanvas
    val barBg = colors.elevatedBar
    val backspaceCd = stringResource(R.string.chat_emoji_backspace)
    val haptic = rememberWuppHapticTrigger()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight)
            .background(dockBg)
    ) {
        HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(barBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chat_emoji_panel_title),
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.28f))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = backspaceCd
                        role = Role.Button
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            haptic()
                            latestOnBackspace()
                            val repeatJob = scope.launch {
                                delay(380L)
                                while (true) {
                                    latestOnBackspace()
                                    delay(65L)
                                }
                            }
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                repeatJob.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // Re-create AndroidView when light/dark mode flips so ContextThemeWrapper applies crisp colors.
        key(colors.isDark) {
            val themeRes = if (colors.isDark) {
                R.style.ThemeOverlay_Wupp_EmojiPicker_Dark
            } else {
                R.style.ThemeOverlay_Wupp_EmojiPicker_Light
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    EmojiPickerView(ContextThemeWrapper(ctx, themeRes)).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(dockBg.toArgb())
                        emojiGridColumns = 9
                        setOnEmojiPickedListener { item ->
                            haptic()
                            latestOnEmojiSelected(item.emoji)
                        }
                    }
                },
                update = { picker ->
                    picker.setBackgroundColor(dockBg.toArgb())
                    picker.setOnEmojiPickedListener { item ->
                        haptic()
                        latestOnEmojiSelected(item.emoji)
                    }
                }
            )
        }
    }
}
