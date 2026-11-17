package com.wupp.meow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.wupp.meow.ui.theme.LocalWaColors
import com.wupp.meow.ui.theme.LocalWuppClassicUi
import com.wupp.meow.ui.theme.WaColors
import com.wupp.meow.ui.theme.rememberWuppHapticTrigger
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal enum class ChromeSurfaceStyle {
    LIQUID_GLASS,
    CLASSIC_FLAT
}

internal fun resolveChromeSurfaceStyle(classicMode: Boolean): ChromeSurfaceStyle =
    if (classicMode) ChromeSurfaceStyle.CLASSIC_FLAT else ChromeSurfaceStyle.LIQUID_GLASS

internal fun resolveLiquidGlassSurfaceAlpha(
    isDark: Boolean,
    renderEffectSupported: Boolean
): Float = if (renderEffectSupported) {
    if (isDark) 0.44f else 0.56f
} else {
    if (isDark) 0.88f else 0.94f
}

/**
 * Shared live backdrop state powered by Kyant0/AndroidLiquidGlass ([LayerBackdrop]) so floating
 * glass surfaces capture, blur, and refract scrolled content directly beneath them.
 */
@Stable
class WuppBackdropState internal constructor(
    val backdrop: LayerBackdrop
)

@Composable
fun rememberWuppBackdropState(): WuppBackdropState {
    val layerBackdrop = rememberLayerBackdrop()
    return remember(layerBackdrop) { WuppBackdropState(layerBackdrop) }
}

fun Modifier.wuppBackdropSource(state: WuppBackdropState): Modifier =
    this.layerBackdrop(state.backdrop)

/**
 * Apple-inspired Liquid Glass container backed by Kyant0/AndroidLiquidGlass (`drawBackdrop`)
 * with vibrancy, RenderEffect blur, AGSL edge refraction lens + specular highlight on Android 12+/13+,
 * clean high-contrast frosted fallback on Android 8–11, and flat Classic mode when enabled.
 */
@Composable
fun WuppGlassContainer(
    modifier: Modifier = Modifier,
    colors: WaColors = LocalWaColors.current,
    shape: Shape = CircleShape,
    elevation: Dp = 16.dp,
    backdropState: WuppBackdropState? = null,
    blurRadius: Dp = 9.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 22.dp,
    chromaticAberration: Boolean = false,
    classicMode: Boolean = LocalWuppClassicUi.current,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    if (resolveChromeSurfaceStyle(classicMode) == ChromeSurfaceStyle.CLASSIC_FLAT) {
        Box(
            modifier = modifier
                .shadow(
                    elevation = 1.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(shape)
                .background(colors.elevatedBar)
                .border(
                    width = 0.75.dp,
                    color = colors.divider,
                    shape = shape
                ),
            contentAlignment = contentAlignment
        ) {
            content()
        }
        return
    }

    val backdrop: Backdrop = backdropState?.backdrop ?: emptyBackdrop()
    val hasLiveBackdrop = backdropState != null && isRenderEffectSupported()
    val surfaceAlpha = resolveLiquidGlassSurfaceAlpha(
        isDark = colors.isDark,
        renderEffectSupported = hasLiveBackdrop
    )
    val surfaceTint = if (colors.isDark) Color(0xFF141E24) else Color(0xFFF6F9F7)

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (hasLiveBackdrop) {
                        vibrancy()
                        blur(blurRadius.toPx())
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = true,
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = {
                    Highlight(
                        width = 0.65.dp,
                        alpha = if (colors.isDark) 0.72f else 0.94f,
                        style = HighlightStyle.Default
                    )
                },
                shadow = {
                    Shadow(
                        radius = elevation,
                        color = Color.Black.copy(alpha = if (colors.isDark) 0.38f else 0.14f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        alpha = if (colors.isDark) 0.18f else 0.08f
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceTint.copy(alpha = surfaceAlpha))
                }
            ),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

private data class GlassNavTab(
    val index: Int,
    val label: String,
    val imageVector: ImageVector,
    val tag: String,
    val badgeCount: Int = 0
)

/**
 * Floating Liquid Glass bottom navigation pill inspired by Kyant0's LiquidBottomTabs:
 * - Outer refractive Capsule bar over live scrolled content
 * - Optical sliding active capsule with spring dynamics, velocity squeeze, and interactive scrub drag
 * - Automatic flat Classic mode when requested
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WuppGlassNavBar(
    currentTab: Int,
    unreadChatsCount: Int,
    onSelectTab: (Int) -> Unit,
    backdropState: WuppBackdropState? = null,
    classicMode: Boolean = false,
    separateGroupsAndChats: Boolean = false,
    unreadGroupsCount: Int = 0,
    chatsLabel: String = "Chats",
    groupsLabel: String = "Groups",
    updatesLabel: String = "Updates",
    communitiesLabel: String = "Communities",
    settingsLabel: String = "You",
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val triggerHaptic = rememberWuppHapticTrigger()
    val tabs = remember(
        separateGroupsAndChats,
        unreadChatsCount,
        unreadGroupsCount,
        chatsLabel,
        groupsLabel,
        updatesLabel,
        communitiesLabel,
        settingsLabel
    ) {
        buildList {
            add(GlassNavTab(0, chatsLabel, WuppIcons.ChatsFilled, "nav_tab_chats", badgeCount = unreadChatsCount))
            if (separateGroupsAndChats) {
                add(GlassNavTab(4, groupsLabel, WuppIcons.GroupsFilled, "nav_tab_groups", badgeCount = unreadGroupsCount))
            }
            add(GlassNavTab(1, updatesLabel, WuppIcons.StatusRing, "nav_tab_updates"))
            add(GlassNavTab(2, communitiesLabel, WuppIcons.CommunitiesOutline, "nav_tab_communities"))
            add(GlassNavTab(3, settingsLabel, WuppIcons.SettingsFilled, "nav_tab_settings"))
        }
    }
    val fiveTabs = tabs.size >= 5
    val labelFontSize = if (fiveTabs) 9.5.sp else 10.5.sp
    val classicLabelFontSize = if (fiveTabs) 9.5.sp else 11.sp

    if (classicMode) {
        androidx.compose.material3.Surface(
            color = colors.elevatedBar,
            tonalElevation = 0.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                androidx.compose.material3.HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = if (fiveTabs) 2.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.index == currentTab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(tab.tag)
                                .clickable {
                                    triggerHaptic(HapticFeedbackType.TextHandleMove)
                                    onSelectTab(tab.index)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.pillActiveBg else Color.Transparent)
                                    .padding(horizontal = if (fiveTabs) 12.dp else 16.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.imageVector,
                                    contentDescription = tab.label,
                                    tint = if (selected) colors.pillActiveText else colors.textSecondary,
                                    modifier = Modifier.size(21.dp)
                                )
                                if (tab.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-3).dp)
                                            .clip(CircleShape)
                                            .background(colors.brightGreen)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                            color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = if (selected) colors.textPrimary else colors.textSecondary,
                                fontSize = classicLabelFontSize,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val outerBackdrop: Backdrop = backdropState?.backdrop ?: emptyBackdrop()
    val tabsBackdrop = rememberLayerBackdrop()
    val hasLiveBackdrop = backdropState != null && isRenderEffectSupported()
    val barSurfaceTint = if (colors.isDark) Color(0xFF141E24) else Color(0xFFF6F9F7)
    val barSurfaceAlpha = resolveLiquidGlassSurfaceAlpha(
        isDark = colors.isDark,
        renderEffectSupported = hasLiveBackdrop
    )

    val activePosition = tabs.indexOfFirst { it.index == currentTab }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(activePosition.toFloat()) }
    val animatedIndex = remember { Animatable(activePosition.toFloat()) }
    val pressProgress by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "liquid_nav_press_progress"
    )
    val lensScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "liquid_nav_lens_scale"
    )

    LaunchedEffect(activePosition, isScrubbing) {
        if (!isScrubbing) {
            scrubFraction = activePosition.toFloat()
            animatedIndex.animateTo(
                targetValue = activePosition.toFloat(),
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f)
            )
        }
    }

    val panelOffsetPx by remember(density, isScrubbing) {
        derivedStateOf {
            if (!isScrubbing) 0f
            else {
                val maxIdx = (tabs.size - 1).coerceAtLeast(1).toFloat()
                val delta = ((animatedIndex.value - activePosition.toFloat()) / maxIdx).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * delta.sign * EaseOut.transform(abs(delta)) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = if (fiveTabs) 10.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer { translationX = panelOffsetPx }
                .drawBackdrop(
                    backdrop = outerBackdrop,
                    shape = { Capsule() },
                    effects = {
                        if (hasLiveBackdrop) {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 16.dp.toPx(),
                                refractionAmount = 28.dp.toPx(),
                                depthEffect = true
                            )
                        }
                    },
                    highlight = {
                        Highlight(
                            width = 0.65.dp,
                            alpha = if (colors.isDark) 0.72f else 0.94f,
                            style = HighlightStyle.Default
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 20.dp,
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.42f else 0.16f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(barSurfaceTint.copy(alpha = barSurfaceAlpha))
                    }
                )
                .pointerInput(tabs.size, isLtr) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isScrubbing = true
                            scrubFraction = animatedIndex.value
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val stepWidth = (size.width.toFloat() - 8.dp.toPx()) / tabs.size.toFloat()
                            if (stepWidth > 0f) {
                                val signedDelta = if (isLtr) dragAmount else -dragAmount
                                scrubFraction = (scrubFraction + signedDelta / stepWidth)
                                    .coerceIn(0f, (tabs.size - 1).toFloat())
                                scope.launch { animatedIndex.snapTo(scrubFraction) }
                            }
                        },
                        onDragEnd = {
                            val snapped = scrubFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                            isScrubbing = false
                            tabs.getOrNull(snapped)?.let {
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                onSelectTab(it.index)
                            }
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * animatedIndex.value,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                label = "liquid_nav_indicator_offset"
            )
            val lensBackdrop = rememberCombinedBackdrop(outerBackdrop, tabsBackdrop)
            val pillSurfaceTint = if (colors.isDark) {
                Color.White.copy(alpha = (0.14f - 0.08f * pressProgress).coerceAtLeast(0.05f))
            } else {
                colors.accentGreen.copy(alpha = (0.12f - 0.05f * pressProgress).coerceAtLeast(0.05f))
            }

            // Tab labels/icons row recorded into tabsBackdrop for optical magnification by active pill
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(tabsBackdrop),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab.index == currentTab
                    val tintColor by animateColorAsState(
                        targetValue = when {
                            selected && colors.isDark -> Color.White
                            selected -> colors.accentGreen
                            colors.isDark -> Color.White.copy(alpha = 0.86f)
                            else -> Color(0xFF1F2C34).copy(alpha = 0.78f)
                        },
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "glass_nav_tint_${tab.index}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.09f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "glass_nav_scale_${tab.index}"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .testTag(tab.tag)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    triggerHaptic(HapticFeedbackType.TextHandleMove)
                                    onSelectTab(tab.index)
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(width = 40.dp, height = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.imageVector,
                                contentDescription = tab.label,
                                tint = tintColor,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                            )

                            if (tab.badgeCount > 0) {
                                val badgeRing = if (colors.isDark) Color(0xFF1A2226) else Color.White
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-3).dp)
                                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                                        .clip(CircleShape)
                                        .background(colors.brightGreen)
                                        .border(1.5.dp, badgeRing, CircleShape)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                        color = Color(0xFF072B17),
                                        fontSize = 9.5.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = tab.label,
                            color = tintColor,
                            fontSize = labelFontSize,
                            lineHeight = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Floating refractive Liquid Glass active tab pill with lens + chromatic aberration on scrub
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = lensScale
                        scaleY = lensScale
                    }
                    .drawBackdrop(
                        backdrop = lensBackdrop,
                        shape = { Capsule() },
                        effects = {
                            if (hasLiveBackdrop) {
                                lens(
                                    refractionHeight = (6.dp + 4.dp * pressProgress).toPx(),
                                    refractionAmount = (10.dp + 6.dp * pressProgress).toPx(),
                                    depthEffect = true,
                                    chromaticAberration = isScrubbing
                                )
                            }
                        },
                        highlight = {
                            Highlight(
                                width = 0.6.dp,
                                alpha = (0.35f + 0.55f * pressProgress).coerceIn(0f, 1f),
                                style = HighlightStyle.Default
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = (4.dp + 4.dp * pressProgress),
                                alpha = if (colors.isDark) 0.14f + 0.16f * pressProgress else 0.06f + 0.10f * pressProgress
                            )
                        },
                        onDrawSurface = {
                            drawRect(pillSurfaceTint)
                        }
                    )
            )
        }
    }
}
