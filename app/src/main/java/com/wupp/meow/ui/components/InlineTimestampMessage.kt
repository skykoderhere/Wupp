package com.wupp.meow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wupp.meow.data.db.MessageEntity
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWaMonoFamily
import com.wupp.meow.ui.theme.LocalWuppChatFontScale
import com.wupp.meow.ui.theme.LocalWuppShowSeconds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * WhatsApp-accurate message body layout that inspects TextLayoutResult.getLineRight(lastLine)
 * to tuck the timestamp, Edited label, and vector delivery ticks cleanly into the final line.
 * Preserved Anti-Delete messages render the original payload plus a crimson Deleted badge.
 */
@Composable
fun InlineTimestampMessage(
    msg: MessageEntity,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val fontScale = LocalWuppChatFontScale.current
    var lastLayout: TextLayoutResult? = null

    val displayText = msg.body
    val timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds)
    val linkColor = colors.accentGreen
    val codeBg = colors.elevatedBar.copy(alpha = 0.65f)
    val styledText = androidx.compose.runtime.remember(displayText, linkColor, codeBg) {
        formatWhatsAppBody(displayText, linkColor = linkColor, codeBg = codeBg)
    }

    Column(modifier = modifier) {
        if (msg.isDeletedBySender) {
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .background(colors.antiDeleteRed.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = WuppIcons.DeleteSweep,
                    contentDescription = "Anti-Deleted",
                    tint = colors.antiDeleteRed,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Deleted · ${formatMessageClock(msg.deletedAt.takeIf { it > 0L } ?: msg.timestamp, includeSeconds = showSeconds)}",
                    color = colors.antiDeleteRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono
                )
            }
        }

        if (displayText.isNotEmpty()) {
            Layout(
                content = {
                    Text(
                        text = styledText,
                        color = colors.textPrimary,
                        fontSize = (15f * fontScale).sp,
                        lineHeight = (20f * fontScale).sp,
                        onTextLayout = { lastLayout = it }
                    )
                    MessageMetaCluster(
                        timeText = timeText,
                        isEdited = msg.isEdited,
                        isEphemeral = msg.isEphemeral,
                        isFromMe = msg.isFromMe,
                        deliveryStatus = msg.deliveryStatus
                    )
                }
            ) { measurables, constraints ->
                val gapPx = 8.dp.roundToPx()
                val verticalDropGap = 2.dp.roundToPx()
                val textPlaceable = measurables[0].measure(
                    Constraints(maxWidth = constraints.maxWidth)
                )
                val metaPlaceable = measurables[1].measure(
                    Constraints(maxWidth = constraints.maxWidth)
                )

                val layoutRes = lastLayout
                val lastLineIdx = (layoutRes?.lineCount ?: 1) - 1
                val lastLineRight = if (layoutRes != null && lastLineIdx >= 0) {
                    layoutRes.getLineRight(lastLineIdx).roundToInt()
                } else {
                    textPlaceable.width
                }

                val canTuckSameLine =
                    lastLineRight + gapPx + metaPlaceable.width <= constraints.maxWidth
                val totalWidth: Int
                val totalHeight: Int
                val metaX: Int
                val metaY: Int

                if (canTuckSameLine) {
                    totalWidth = max(textPlaceable.width, lastLineRight + gapPx + metaPlaceable.width)
                        .coerceAtMost(constraints.maxWidth)
                    totalHeight = max(textPlaceable.height, metaPlaceable.height)
                    metaX = totalWidth - metaPlaceable.width
                    metaY = (totalHeight - metaPlaceable.height).coerceAtLeast(0)
                } else {
                    totalWidth = max(textPlaceable.width, metaPlaceable.width)
                        .coerceAtMost(constraints.maxWidth)
                    totalHeight = textPlaceable.height + verticalDropGap + metaPlaceable.height
                    metaX = totalWidth - metaPlaceable.width
                    metaY = textPlaceable.height + verticalDropGap
                }

                layout(totalWidth, totalHeight) {
                    textPlaceable.placeRelative(0, 0)
                    metaPlaceable.placeRelative(metaX, metaY)
                }
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageMetaCluster(
                    timeText = timeText,
                    isEdited = msg.isEdited,
                    isEphemeral = msg.isEphemeral,
                    isFromMe = msg.isFromMe,
                    deliveryStatus = msg.deliveryStatus
                )
            }
        }
    }
}

@Composable
fun MessageMetaCluster(
    timeText: String,
    isEdited: Boolean,
    isEphemeral: Boolean,
    isFromMe: Boolean,
    deliveryStatus: String,
    tintOverride: Color? = null
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val secondary = tintOverride ?: colors.textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isEphemeral) {
            Text(
                text = "⏳",
                fontSize = 10.sp,
                color = colors.accentGreen
            )
        }
        if (isEdited) {
            Text(
                text = "Edited",
                fontSize = 11.sp,
                color = secondary
            )
        }
        Text(
            text = timeText,
            fontSize = 11.sp,
            color = secondary
        )
        if (isFromMe) {
            Spacer(modifier = Modifier.width(1.dp))
            DeliveryTickIcon(status = deliveryStatus, mutedColor = secondary)
        }
    }
}

@Composable
fun DeliveryTickIcon(
    status: String,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val upper = status.uppercase()
    val (vector, tint, widthDp, heightDp) = when (upper) {
        "READ", "PLAYED" -> QuadTickSpec(WuppIcons.DoubleCheck, colors.blueTick, 18, 14)
        "DELIVERED" -> QuadTickSpec(WuppIcons.DoubleCheck, mutedColor, 18, 14)
        "SENT", "LOCAL_SENT" -> QuadTickSpec(WuppIcons.SingleCheck, mutedColor, 14, 14)
        else -> QuadTickSpec(WuppIcons.ClockPending, mutedColor, 13, 13)
    }
    Icon(
        imageVector = vector,
        contentDescription = status,
        tint = tint,
        modifier = modifier.size(width = widthDp.dp, height = heightDp.dp)
    )
}

private data class QuadTickSpec(
    val vector: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val widthDp: Int,
    val heightDp: Int
)

fun formatMessageClock(
    epochMs: Long,
    includeSeconds: Boolean = StealthMatrixManager.config.value.showSecondsInTimestamps
): String {
    if (epochMs <= 0L) return ""
    val pattern = if (includeSeconds) "h:mm:ss a" else "h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

private val WA_URL_REGEX = Regex("""(?:https?://|www\.)[^\s<>\]\[()"']+""")

/**
 * Formats WhatsApp inline markdown (*bold*, _italic_, ~strikethrough~, `mono`, ```code```)
 * while highlighting clickable URLs in WhatsApp green.
 */
fun formatWhatsAppBody(
    text: String,
    linkColor: Color,
    codeBg: Color
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        var pos = 0
        val urlMatches = WA_URL_REGEX.findAll(text).toList()
        for (match in urlMatches) {
            if (match.range.first > pos) {
                appendFormattedSegment(this, text.substring(pos, match.range.first), codeBg)
            }
            val rawUrl = match.value.trimEnd('.', ',', ';', ')', '!')
            val trailing = match.value.substring(rawUrl.length)
            val targetUrl = if (rawUrl.startsWith("http", ignoreCase = true)) rawUrl else "https://$rawUrl"
            withLink(
                LinkAnnotation.Url(
                    url = targetUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(rawUrl)
            }
            if (trailing.isNotEmpty()) {
                append(trailing)
            }
            pos = match.range.last + 1
        }
        if (pos < text.length) {
            appendFormattedSegment(this, text.substring(pos), codeBg)
        }
    }
}

private fun appendFormattedSegment(
    builder: AnnotatedString.Builder,
    segment: String,
    codeBg: Color
) {
    var i = 0
    val n = segment.length
    while (i < n) {
        if (segment.startsWith("```", i)) {
            val end = segment.indexOf("```", i + 3)
            if (end > i + 3) {
                val content = segment.substring(i + 3, end)
                builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(content)
                }
                i = end + 3
                continue
            }
        }
        val ch = segment[i]
        if (ch == '*' || ch == '_' || ch == '~' || ch == '`') {
            val prevOk = i == 0 || !segment[i - 1].isLetterOrDigit()
            val end = if (prevOk) segment.indexOf(ch, i + 1) else -1
            val validSpan = end > i + 1 &&
                segment[i + 1] != ' ' &&
                segment[end - 1] != ' ' &&
                (ch == '`' || !segment.substring(i + 1, end).contains('\n'))
            if (validSpan) {
                val inner = segment.substring(i + 1, end)
                val style = when (ch) {
                    '*' -> SpanStyle(fontWeight = FontWeight.Bold)
                    '_' -> SpanStyle(fontStyle = FontStyle.Italic)
                    '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    else -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
                }
                builder.withStyle(style) {
                    append(inner)
                }
                i = end + 1
                continue
            }
        }
        builder.append(ch)
        i++
    }
}
