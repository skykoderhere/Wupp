package com.wupp.meow.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Custom WhatsApp bubble shape supporting a compact 6.dp curved tail on the leading cluster item
 * and rounder 16.dp corners matching Official WhatsApp proportions.
 */
class WhatsAppBubbleShape(
    private val isFromMe: Boolean,
    private val showTail: Boolean
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxR = minOf(size.width, size.height) / 2f
        val r = minOf(with(density) { 16.dp.toPx() }, maxR)
        if (!showTail) {
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    cornerRadius = CornerRadius(r)
                )
            )
        }

        val tailW = with(density) { 6.dp.toPx() }
        val tailDrop = with(density) { 10.dp.toPx() }.coerceAtMost(size.height * 0.55f)
        val w = size.width
        val h = size.height

        val path = Path().apply {
            if (isFromMe) {
                val bodyRight = (w - tailW).coerceAtLeast(r * 1.5f)
                moveTo(r, 0f)
                lineTo(w - 3f, 0f)
                quadraticTo(w + 1f, 0f, w - 1.5f, 3.5f)
                quadraticTo(bodyRight, tailDrop * 0.55f, bodyRight, tailDrop)
                lineTo(bodyRight, h - r)
                quadraticTo(bodyRight, h, bodyRight - r, h)
                lineTo(r, h)
                quadraticTo(0f, h, 0f, h - r)
                lineTo(0f, r)
                quadraticTo(0f, 0f, r, 0f)
                close()
            } else {
                val bodyLeft = tailW.coerceAtMost(w * 0.25f)
                moveTo(3f, 0f)
                lineTo(w - r, 0f)
                quadraticTo(w, 0f, w, r)
                lineTo(w, h - r)
                quadraticTo(w, h, w - r, h)
                lineTo(bodyLeft + r, h)
                quadraticTo(bodyLeft, h, bodyLeft, h - r)
                lineTo(bodyLeft, tailDrop)
                quadraticTo(bodyLeft, tailDrop * 0.55f, 1.5f, 3.5f)
                quadraticTo(-1f, 0f, 3f, 0f)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

val WhatsAppClusterInnerShape = RoundedCornerShape(16.dp)
