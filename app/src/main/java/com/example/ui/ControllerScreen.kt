package com.example.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.BleConnectionState
import com.example.model.ControlId
import com.example.model.JoystickMath
import com.example.model.StageOrientation
import com.example.ui.components.ArcadeActionButton
import com.example.ui.components.BleScannerSheet
import com.example.ui.components.EditToolbar
import com.example.ui.components.JoystickControl
import com.example.ui.theme.AmberConnecting
import com.example.ui.theme.ControllerBorder
import com.example.ui.theme.ControllerPanel
import com.example.ui.theme.CoralDisconnected
import com.example.ui.theme.CosmosBg
import com.example.ui.theme.CosmosStageGlow
import com.example.ui.theme.DarkEmeraldOnMint
import com.example.ui.theme.DarkPlumOnAccent
import com.example.ui.theme.LavenderInk
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.MintConnected
import com.example.ui.theme.NeonMagenta
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ControllerRoute(
    viewModel: ControllerViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Never leave a key stuck if the app pauses or loses focus
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.releaseAllControls(notifyReceiver = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Exit edit mode gracefully on Android system Back
    BackHandler(enabled = uiState.isEditing) {
        viewModel.setEditing(false)
    }

    // Track permission & adapter state dynamically when scanner sheet opens
    var permissionEpoch by remember { mutableStateOf(0) }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionEpoch++
        if (results.values.all { it }) {
            viewModel.startOrRestartScan()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionEpoch++
        if (viewModel.isBluetoothEnabled() && viewModel.hasBlePermissions()) {
            viewModel.startOrRestartScan()
        }
    }

    ControllerScreen(
        uiState = uiState,
        onRotateClick = viewModel::rotate90,
        onEditToggle = viewModel::toggleEditMode,
        onConnectClick = viewModel::onConnectButtonClicked,
        onStickMove = viewModel::onStickMove,
        onStickRelease = viewModel::onStickRelease,
        onButtonPress = viewModel::onButtonPress,
        onButtonRelease = viewModel::onButtonRelease,
        onSelectControl = viewModel::selectControl,
        onMoveControlInStage = viewModel::moveControlInStage,
        onCommitControlDrag = viewModel::commitControlDrag,
        onBumpScale = viewModel::bumpSelectedControlScale,
        onResetLayout = viewModel::resetCurrentOrientationLayout,
        onToggleHaptics = viewModel::toggleHaptics,
        modifier = modifier,
    )

    if (uiState.showScannerSheet) {
        // Read fresh permission/adapter state whenever permissionEpoch updates
        val hasPermissions = remember(permissionEpoch, uiState.showScannerSheet) {
            viewModel.hasBlePermissions()
        }
        val btEnabled = remember(permissionEpoch, uiState.showScannerSheet, uiState.isScanning) {
            viewModel.isBluetoothEnabled()
        }
        BleScannerSheet(
            isBleSupported = viewModel.isBleSupported(),
            isBluetoothEnabled = btEnabled,
            hasPermissions = hasPermissions,
            isScanning = uiState.isScanning,
            filterNusOnly = uiState.filterNusOnly,
            scannedDevices = uiState.scannedDevices,
            onDismiss = viewModel::dismissScannerSheet,
            onRequestPermissions = {
                permissionsLauncher.launch(viewModel.requiredBlePermissions())
            },
            onRequestEnableBluetooth = {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onToggleFilterNus = viewModel::setFilterNusOnly,
            onStartScan = viewModel::startOrRestartScan,
            onStopScan = viewModel::stopScan,
            onDeviceSelected = viewModel::connectToScannedDevice,
        )
    }
}

@Composable
fun ControllerScreen(
    uiState: ControllerUiState,
    onRotateClick: () -> Unit,
    onEditToggle: () -> Unit,
    onConnectClick: () -> Unit,
    onStickMove: (dx: Float, dy: Float, radiusPx: Float) -> Unit,
    onStickRelease: () -> Unit,
    onButtonPress: (ControlId) -> Unit,
    onButtonRelease: (ControlId) -> Unit,
    onSelectControl: (ControlId) -> Unit,
    onMoveControlInStage: (StageOrientation, ControlId, Float, Float) -> Unit,
    onCommitControlDrag: () -> Unit,
    onBumpScale: (StageOrientation, Float) -> Unit,
    onResetLayout: (StageOrientation) -> Unit,
    onToggleHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full-bleed cosmos background with safeDrawing insets so notches & gesture bars stay clear
    // across all four 90-degree rotations (0, 90, 180, 270).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CosmosBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("controller_root"),
        contentAlignment = Alignment.Center,
    ) {
        val rot = uiState.config.rotationDegrees
        val swapAxes = rot % 180 != 0
        val logicalWidth = if (swapAxes) maxHeight else maxWidth
        val logicalHeight = if (swapAxes) maxWidth else maxHeight

        Column(
            modifier = Modifier
                .requiredSize(width = logicalWidth, height = logicalHeight)
                .graphicsLayer {
                    rotationZ = rot.toFloat()
                }
                .testTag("rotatable_controller_container"),
        ) {
            ControllerHeaderBar(
                connectionState = uiState.connectionState,
                rotationDegrees = rot,
                lastSentPacket = uiState.lastSentPacket,
                isEditing = uiState.isEditing,
                onRotateClick = onRotateClick,
                onEditToggle = onEditToggle,
                onConnectClick = onConnectClick,
            )

            ControllerStage(
                uiState = uiState,
                onStickMove = onStickMove,
                onStickRelease = onStickRelease,
                onButtonPress = onButtonPress,
                onButtonRelease = onButtonRelease,
                onSelectControl = onSelectControl,
                onMoveControlInStage = onMoveControlInStage,
                onCommitControlDrag = onCommitControlDrag,
                onBumpScale = onBumpScale,
                onResetLayout = onResetLayout,
                onToggleHaptics = onToggleHaptics,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Bottom Hint Bar
            val hintText = when {
                uiState.isEditing -> stringResource(R.string.hint_edit_mode)
                uiState.connectionState is BleConnectionState.Error ->
                    stringResource(R.string.error_connect_format, uiState.connectionState.message)
                else -> null
            }
            if (!hintText.isNullOrEmpty()) {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LavenderInk.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("hint_text"),
                )
            }
        }
    }
}

@Composable
private fun ControllerHeaderBar(
    connectionState: BleConnectionState,
    rotationDegrees: Int,
    lastSentPacket: String?,
    isEditing: Boolean,
    onRotateClick: () -> Unit,
    onEditToggle: () -> Unit,
    onConnectClick: () -> Unit,
) {
    val (statusText, dotColor) = when (connectionState) {
        is BleConnectionState.Disconnected -> {
            val label = if (connectionState.hadSession) {
                stringResource(R.string.status_disconnected)
            } else {
                stringResource(R.string.status_not_connected)
            }
            label to CoralDisconnected
        }
        is BleConnectionState.Scanning -> stringResource(R.string.status_searching) to AmberConnecting
        is BleConnectionState.Connecting -> stringResource(R.string.status_connecting) to AmberConnecting
        is BleConnectionState.Connected -> {
            val name = connectionState.deviceName.ifBlank {
                stringResource(R.string.default_device_name)
            }
            stringResource(R.string.status_connected_format, name) to MintConnected
        }
        is BleConnectionState.Error -> stringResource(R.string.status_not_connected) to CoralDisconnected
    }

    val isConnectedOrConnecting = connectionState is BleConnectionState.Connected ||
        connectionState is BleConnectionState.Connecting

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status Indicator + Optional Telemetry Badge
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LavenderInk.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("status_text"),
                )
                if (!lastSentPacket.isNullOrEmpty() || rotationDegrees != 0) {
                    val metaParts = buildList {
                        if (rotationDegrees != 0) add("${rotationDegrees}°")
                        if (!lastSentPacket.isNullOrEmpty()) add("TX:$lastSentPacket")
                    }
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = LavenderMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("telemetry_text"),
                    )
                }
            }
        }

        // Rotate 90-degree pill
        HeaderPillButton(
            text = stringResource(R.string.action_rotate),
            icon = Icons.Filled.RotateRight,
            contentDescriptionText = stringResource(R.string.cd_rotate_controller),
            backgroundColor = ControllerPanel,
            borderColor = ControllerBorder,
            contentColor = LavenderInk,
            testTagName = "rotate_button",
            onClick = onRotateClick,
        )

        // Edit / Done pill
        HeaderPillButton(
            text = if (isEditing) {
                stringResource(R.string.action_done)
            } else {
                stringResource(R.string.action_edit)
            },
            icon = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
            contentDescriptionText = stringResource(R.string.cd_edit_controls),
            backgroundColor = if (isEditing) MintConnected else ControllerPanel,
            borderColor = if (isEditing) MintConnected else ControllerBorder,
            contentColor = if (isEditing) DarkEmeraldOnMint else LavenderInk,
            testTagName = "edit_button",
            onClick = onEditToggle,
        )

        // Connect / Disconnect accent pill
        HeaderPillButton(
            text = if (isConnectedOrConnecting) {
                stringResource(R.string.action_disconnect)
            } else {
                stringResource(R.string.action_connect)
            },
            icon = null,
            contentDescriptionText = null,
            backgroundColor = NeonMagenta,
            borderColor = NeonMagenta,
            contentColor = DarkPlumOnAccent,
            fontWeight = FontWeight.Bold,
            testTagName = "connect_button",
            onClick = onConnectClick,
        )
    }
}

@Composable
private fun HeaderPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    contentDescriptionText: String?,
    backgroundColor: Color,
    borderColor: Color,
    contentColor: Color,
    testTagName: String,
    fontWeight: FontWeight = FontWeight.SemiBold,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(width = 2.dp, color = borderColor, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (contentDescriptionText != null) {
                    Modifier.semantics { contentDescription = contentDescriptionText }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTagName),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = fontWeight,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ControllerStage(
    uiState: ControllerUiState,
    onStickMove: (dx: Float, dy: Float, radiusPx: Float) -> Unit,
    onStickRelease: () -> Unit,
    onButtonPress: (ControlId) -> Unit,
    onButtonRelease: (ControlId) -> Unit,
    onSelectControl: (ControlId) -> Unit,
    onMoveControlInStage: (StageOrientation, ControlId, Float, Float) -> Unit,
    onCommitControlDrag: () -> Unit,
    onBumpScale: (StageOrientation, Float) -> Unit,
    onResetLayout: (StageOrientation) -> Unit,
    onToggleHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minTouchPx = with(density) { 48.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                // Subtle atmospheric radial glow & arcade grid lines
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(CosmosStageGlow, CosmosBg),
                        center = Offset(size.width * 0.5f, size.height * 0.55f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
                val gridStep = 40.dp.toPx()
                val gridColor = ControllerBorder.copy(alpha = 0.14f)
                var x = gridStep
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += gridStep
                }
                var y = gridStep
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += gridStep
                }
            }
            .testTag("controller_stage"),
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val stageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val orientation = if (stageWidthPx >= stageHeightPx) {
            StageOrientation.LANDSCAPE
        } else {
            StageOrientation.PORTRAIT
        }

        val currentLayout = uiState.config.layoutFor(orientation)
        val stickBounds = JoystickMath.computeControlBounds(
            controlId = ControlId.STICK,
            placement = currentLayout.stick,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            minTouchSizePx = minTouchPx,
        )
        val buttonABounds = JoystickMath.computeControlBounds(
            controlId = ControlId.BUTTON_A,
            placement = currentLayout.buttonA,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            minTouchSizePx = minTouchPx,
        )
        val buttonBBounds = JoystickMath.computeControlBounds(
            controlId = ControlId.BUTTON_B,
            placement = currentLayout.buttonB,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            minTouchSizePx = minTouchPx,
        )

        val latestStickBounds by rememberUpdatedState(stickBounds)
        val latestABounds by rememberUpdatedState(buttonABounds)
        val latestBBounds by rememberUpdatedState(buttonBBounds)
        val latestOrientation by rememberUpdatedState(orientation)

        // Stage-level drag layer active only in Edit Mode so control offsets never jitter
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (uiState.isEditing) {
                        Modifier.pointerInput(stageWidthPx, stageHeightPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val hitControl = listOf(
                                    ControlId.BUTTON_B to latestBBounds,
                                    ControlId.BUTTON_A to latestABounds,
                                    ControlId.STICK to latestStickBounds,
                                ).firstOrNull { (_, bounds) ->
                                    val r = bounds.sizePx / 2f
                                    val cx = bounds.leftPx + r
                                    val cy = bounds.topPx + r
                                    hypot(down.position.x - cx, down.position.y - cy) <= r + 12f
                                }

                                if (hitControl != null) {
                                    val (targetId, startBounds) = hitControl
                                    down.consume()
                                    onSelectControl(targetId)
                                    var centerPxX = startBounds.leftPx + startBounds.sizePx / 2f
                                    var centerPxY = startBounds.topPx + startBounds.sizePx / 2f
                                    val pointerId = down.id

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed) {
                                            change?.consume()
                                            onCommitControlDrag()
                                            break
                                        }
                                        val delta = change.positionChange()
                                        if (delta != Offset.Zero) {
                                            change.consume()
                                            centerPxX += delta.x
                                            centerPxY += delta.y
                                            onMoveControlInStage(
                                                latestOrientation,
                                                targetId,
                                                centerPxX / stageWidthPx,
                                                centerPxY / stageHeightPx,
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
        ) {
            JoystickControl(
                sizePx = stickBounds.sizePx,
                isActive = uiState.isStickActive,
                knobOffsetX = uiState.knobOffsetX,
                knobOffsetY = uiState.knobOffsetY,
                activeDirection = uiState.activeDirection,
                isEditing = uiState.isEditing,
                isSelected = uiState.selectedControl == ControlId.STICK,
                contentDescriptionText = stringResource(R.string.cd_joystick),
                onStickMove = onStickMove,
                onStickRelease = onStickRelease,
                modifier = Modifier.offset {
                    IntOffset(
                        x = stickBounds.leftPx.roundToInt(),
                        y = stickBounds.topPx.roundToInt(),
                    )
                },
            )

            ArcadeActionButton(
                testTagName = "button_a",
                mainLabel = stringResource(R.string.button_a_label),
                subLabel = stringResource(R.string.button_a_sub),
                contentDescriptionText = stringResource(R.string.cd_button_a),
                sizePx = buttonABounds.sizePx,
                isPressed = uiState.isButtonADown,
                isEditing = uiState.isEditing,
                isSelected = uiState.selectedControl == ControlId.BUTTON_A,
                onPress = { onButtonPress(ControlId.BUTTON_A) },
                onRelease = { onButtonRelease(ControlId.BUTTON_A) },
                modifier = Modifier.offset {
                    IntOffset(
                        x = buttonABounds.leftPx.roundToInt(),
                        y = buttonABounds.topPx.roundToInt(),
                    )
                },
            )

            ArcadeActionButton(
                testTagName = "button_b",
                mainLabel = stringResource(R.string.button_b_label),
                subLabel = stringResource(R.string.button_b_sub),
                contentDescriptionText = stringResource(R.string.cd_button_b),
                sizePx = buttonBBounds.sizePx,
                isPressed = uiState.isButtonBDown,
                isEditing = uiState.isEditing,
                isSelected = uiState.selectedControl == ControlId.BUTTON_B,
                onPress = { onButtonPress(ControlId.BUTTON_B) },
                onRelease = { onButtonRelease(ControlId.BUTTON_B) },
                modifier = Modifier.offset {
                    IntOffset(
                        x = buttonBBounds.leftPx.roundToInt(),
                        y = buttonBBounds.topPx.roundToInt(),
                    )
                },
            )
        }

        // Floating Edit Mode Bar
        AnimatedVisibility(
            visible = uiState.isEditing,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val selectedScale = uiState.selectedControl?.let {
                currentLayout.placementFor(it).scale
            }
            EditToolbar(
                selectedControl = uiState.selectedControl,
                selectedScale = selectedScale,
                hapticsEnabled = uiState.config.hapticsEnabled,
                onScaleStepDown = { onBumpScale(orientation, -0.05f) },
                onScaleStepUp = { onBumpScale(orientation, 0.05f) },
                onResetLayout = { onResetLayout(orientation) },
                onToggleHaptics = onToggleHaptics,
            )
        }
    }
}
