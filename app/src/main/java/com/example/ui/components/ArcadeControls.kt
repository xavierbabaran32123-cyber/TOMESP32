package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardinalDirection
import com.example.ui.theme.ControllerBorder
import com.example.ui.theme.ControllerPanel
import com.example.ui.theme.ControllerPanelCenter
import com.example.ui.theme.DarkPlumOnAccent
import com.example.ui.theme.LavenderInk
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StickKnobBorder
import com.example.ui.theme.StickKnobIdle
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun Modifier.editSelectionOutline(
    isEditing: Boolean,
    isSelected: Boolean,
): Modifier {
    if (!isEditing) return this
    return this.drawBehind {
        val strokeWidthPx = (if (isSelected) 3.dp else 2.dp).toPx()
        val gapOffsetPx = 5.dp.toPx()
        val radius = (size.minDimension / 2f) + gapOffsetPx
        if (isSelected) {
            drawCircle(
                color = NeonMagenta,
                radius = radius,
                style = Stroke(width = strokeWidthPx),
            )
        } else {
            drawCircle(
                color = StickKnobBorder,
                radius = radius,
                style = Stroke(
                    width = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        phase = 0f,
                    ),
                ),
            )
        }
    }
}

@Composable
fun JoystickControl(
    sizePx: Float,
    isActive: Boolean,
    knobOffsetX: Float,
    knobOffsetY: Float,
    activeDirection: CardinalDirection?,
    isEditing: Boolean,
    isSelected: Boolean,
    contentDescriptionText: String,
    onStickMove: (dx: Float, dy: Float, radiusPx: Float) -> Unit,
    onStickRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sizeDp: Dp = with(density) { sizePx.toDp() }
    val knobSizeDp: Dp = sizeDp * 0.38f

    Box(
        modifier = modifier
            .size(sizeDp)
            .editSelectionOutline(isEditing = isEditing, isSelected = isSelected)
            .semantics {
                contentDescription = contentDescriptionText
                role = Role.Button
            }
            .testTag("stick_control")
            .then(
                if (!isEditing) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            var trackedPointer: PointerId? = null
                            val radius = sizePx / 2f
                            while (true) {
                                val event = awaitPointerEvent()
                                if (trackedPointer == null) {
                                    val firstDown = event.changes.firstOrNull { it.pressed }
                                    if (firstDown != null) {
                                        trackedPointer = firstDown.id
                                        firstDown.consume()
                                        onStickMove(
                                            firstDown.position.x - radius,
                                            firstDown.position.y - radius,
                                            radius,
                                        )
                                    }
                                } else {
                                    val change = event.changes.firstOrNull { it.id == trackedPointer }
                                    if (change == null || !change.pressed) {
                                        change?.consume()
                                        trackedPointer = null
                                        onStickRelease()
                                    } else {
                                        change.consume()
                                        onStickMove(
                                            change.position.x - radius,
                                            change.position.y - radius,
                                            radius,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Radial base + crosshairs + cardinal glow ticks
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to ControllerPanelCenter,
                    0.70f to ControllerPanel,
                    1.0f to ControllerPanel,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )

            // Outer border
            drawCircle(
                color = if (isActive) NeonMagenta.copy(alpha = 0.7f) else ControllerBorder,
                radius = r - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            // Dead-zone subtle guide ring
            drawCircle(
                color = ControllerBorder.copy(alpha = 0.35f),
                radius = r * 0.28f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Crosshair lines (inset 12%)
            val inset = size.minDimension * 0.12f
            val crosshairColor = ControllerBorder.copy(alpha = 0.45f)
            val lineWidth = 2.dp.toPx()
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, inset),
                end = Offset(center.x, size.height - inset),
                strokeWidth = lineWidth,
            )
            drawLine(
                color = crosshairColor,
                start = Offset(inset, center.y),
                end = Offset(size.width - inset, center.y),
                strokeWidth = lineWidth,
            )

            // Cardinal direction indicator dots near bezel
            val tickInset = r * 0.84f
            val tickRadius = 3.5.dp.toPx()
            val directions = listOf(
                CardinalDirection.UP to Offset(center.x, center.y - tickInset),
                CardinalDirection.RIGHT to Offset(center.x + tickInset, center.y),
                CardinalDirection.DOWN to Offset(center.x, center.y + tickInset),
                CardinalDirection.LEFT to Offset(center.x - tickInset, center.y),
            )
            directions.forEach { (dir, pos) ->
                val active = activeDirection == dir
                drawCircle(
                    color = if (active) NeonMagenta else StickKnobBorder.copy(alpha = 0.35f),
                    radius = if (active) tickRadius * 1.45f else tickRadius,
                    center = pos,
                )
            }
        }

        // Movable thumb knob (38% of stick diameter)
        val knobFill = if (isActive) NeonMagenta else StickKnobIdle
        val knobStroke = if (isActive) NeonMagenta else StickKnobBorder
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = knobOffsetX.roundToInt(),
                        y = knobOffsetY.roundToInt(),
                    )
                }
                .size(knobSizeDp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(knobFill, CircleShape)
                .border(width = 2.dp, color = knobStroke, shape = CircleShape),
        )
    }
}

@Composable
fun ArcadeActionButton(
    testTagName: String,
    mainLabel: String,
    subLabel: String,
    contentDescriptionText: String,
    sizePx: Float,
    isPressed: Boolean,
    isEditing: Boolean,
    isSelected: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "button_press_scale",
    )

    val mainFontSp = with(density) { (sizePx * 0.34f).toSp() }
    val subFontSp = with(density) { max(9.dp.toPx(), sizePx * 0.125f).toSp() }

    val bgColor = if (isPressed) NeonMagenta else ControllerPanel
    val borderColor = if (isPressed) NeonMagenta else ControllerBorder
    val contentColor = if (isPressed) DarkPlumOnAccent else LavenderInk
    val subColor = if (isPressed) {
        DarkPlumOnAccent.copy(alpha = 0.78f)
    } else {
        LavenderInk.copy(alpha = 0.70f)
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .editSelectionOutline(isEditing = isEditing, isSelected = isSelected)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(elevation = if (isPressed) 2.dp else 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(color = bgColor, shape = CircleShape)
            .border(width = 2.dp, color = borderColor, shape = CircleShape)
            .semantics {
                contentDescription = contentDescriptionText
                role = Role.Button
            }
            .testTag(testTagName)
            .then(
                if (!isEditing) {
                    Modifier.pointerInput(testTagName) {
                        awaitPointerEventScope {
                            val activePointers = HashSet<PointerId>()
                            while (true) {
                                val event = awaitPointerEvent()
                                var wasHeld = activePointers.isNotEmpty()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        activePointers.add(change.id)
                                        change.consume()
                                    } else if (activePointers.remove(change.id)) {
                                        change.consume()
                                    }
                                }
                                val isNowHeld = activePointers.isNotEmpty()
                                if (!wasHeld && isNowHeld) {
                                    onPress()
                                } else if (wasHeld && !isNowHeld) {
                                    onRelease()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = mainLabel,
                color = contentColor,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = mainFontSp,
                lineHeight = mainFontSp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subLabel,
                color = subColor,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = subFontSp,
                lineHeight = (subFontSp.value * 1.08f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
