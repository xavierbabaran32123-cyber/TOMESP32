package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ControlId
import com.example.ui.theme.ControllerBorder
import com.example.ui.theme.ControllerPanel
import com.example.ui.theme.DarkEmeraldOnMint
import com.example.ui.theme.LavenderInk
import com.example.ui.theme.MintConnected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditToolbar(
    selectedControl: ControlId?,
    selectedScale: Float?,
    hapticsEnabled: Boolean,
    onScaleStepDown: () -> Unit,
    onScaleStepUp: () -> Unit,
    onResetLayout: () -> Unit,
    onToggleHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelText = if (selectedControl != null && selectedScale != null) {
        val controlName = when (selectedControl) {
            ControlId.STICK -> stringResource(R.string.control_name_joystick)
            ControlId.BUTTON_A -> stringResource(R.string.control_name_a)
            ControlId.BUTTON_B -> stringResource(R.string.control_name_b)
        }
        stringResource(R.string.edit_size_format, controlName, (selectedScale * 100f).roundToInt())
    } else {
        stringResource(R.string.edit_tap_control)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("edit_toolbar"),
        color = ControllerPanel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, ControllerBorder),
        shadowElevation = 8.dp,
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            HoldRepeatStepButton(
                symbol = "−",
                contentDescriptionText = stringResource(R.string.cd_size_smaller),
                testTagName = "size_down_button",
                onStep = onScaleStepDown,
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 104.dp, minHeight = 48.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LavenderInk.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("size_label"),
                )
            }

            HoldRepeatStepButton(
                symbol = "+",
                contentDescriptionText = stringResource(R.string.cd_size_bigger),
                testTagName = "size_up_button",
                onStep = onScaleStepUp,
            )

            // Reset layout button
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(ControllerPanel, CircleShape)
                    .border(2.dp, ControllerBorder, CircleShape)
                    .clickable(role = Role.Button, onClick = onResetLayout)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("reset_layout_button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = LavenderInk,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_reset_layout),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LavenderInk,
                    )
                }
            }

            // Haptics toggle pill
            val hapticsBg = if (hapticsEnabled) MintConnected else ControllerPanel
            val hapticsBorder = if (hapticsEnabled) MintConnected else ControllerBorder
            val hapticsTint = if (hapticsEnabled) DarkEmeraldOnMint else LavenderInk.copy(alpha = 0.65f)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(hapticsBg, CircleShape)
                    .border(2.dp, hapticsBorder, CircleShape)
                    .clickable(role = Role.Switch, onClick = onToggleHaptics)
                    .padding(horizontal = 12.dp)
                    .testTag("toggle_haptics_button"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = stringResource(R.string.cd_toggle_haptics),
                    tint = hapticsTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HoldRepeatStepButton(
    symbol: String,
    contentDescriptionText: String,
    testTagName: String,
    onStep: () -> Unit,
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(ControllerPanel, CircleShape)
            .border(2.dp, ControllerBorder, CircleShape)
            .semantics {
                contentDescription = contentDescriptionText
                role = Role.Button
            }
            .testTag(testTagName)
            .pointerInput(testTagName) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnStep()
                    val repeatJob = scope.launch {
                        delay(350L)
                        while (true) {
                            currentOnStep()
                            delay(90L)
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeatJob.cancel()
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = LavenderInk,
            lineHeight = 20.sp,
        )
    }
}
