package com.wupp.meow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWuppHapticsEnabled
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * WhatsApp swipe-to-reply row wrapper with 0.55x drag resistance, 56dp haptic threshold,
 * and damped spring snap-back.
 */
@Composable
fun SwipeToReplyBox(
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalWaColors.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalWuppHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 78.dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { triggered = false },
                    onDragEnd = {
                        if (triggered) onReply()
                        triggered = false
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        triggered = false
                        scope.launch { offsetX.animateTo(0f) }
                    }
                ) { change, dragAmount ->
                    if (offsetX.value > 0f || dragAmount > 0f) {
                        change.consume()
                        val next = (offsetX.value + dragAmount * 0.55f).coerceIn(0f, maxDragPx)
                        scope.launch { offsetX.snapTo(next) }
                        if (!triggered && next >= thresholdPx) {
                            triggered = true
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                }
            }
    ) {
        val progress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
        if (progress > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(34.dp)
                    .scale(0.6f + 0.4f * progress)
                    .alpha(progress)
                    .background(colors.elevatedBar.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = "Reply",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}
