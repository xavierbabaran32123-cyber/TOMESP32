package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ScannedBleDevice
import com.example.ui.theme.ControllerBorder
import com.example.ui.theme.ControllerPanel
import com.example.ui.theme.ControllerPanelCenter
import com.example.ui.theme.CoralDisconnected
import com.example.ui.theme.CosmosBg
import com.example.ui.theme.DarkEmeraldOnMint
import com.example.ui.theme.DarkPlumOnAccent
import com.example.ui.theme.LavenderInk
import com.example.ui.theme.LavenderMuted
import com.example.ui.theme.MintConnected
import com.example.ui.theme.NeonMagenta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScannerSheet(
    isBleSupported: Boolean,
    isBluetoothEnabled: Boolean,
    hasPermissions: Boolean,
    isScanning: Boolean,
    filterNusOnly: Boolean,
    scannedDevices: List<ScannedBleDevice>,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onToggleFilterNus: (Boolean) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (ScannedBleDevice) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CosmosBg,
        contentColor = LavenderInk,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("ble_scanner_sheet"),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ControllerPanelCenter)
                            .border(1.5.dp, ControllerBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = NeonMagenta,
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.scanner_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = LavenderInk,
                        )
                        Text(
                            text = stringResource(R.string.scanner_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LavenderMuted,
                        )
                    }
                }

                if (isBleSupported && isBluetoothEnabled && hasPermissions) {
                    Button(
                        onClick = { if (isScanning) onStopScan() else onStartScan() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) ControllerPanel else NeonMagenta,
                            contentColor = if (isScanning) LavenderInk else DarkPlumOnAccent,
                        ),
                        border = if (isScanning) BorderStroke(1.5.dp, ControllerBorder) else null,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
                            .testTag("scanner_toggle_scan_button"),
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MintConnected,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.action_stop_scan))
                        } else {
                            Text(stringResource(R.string.action_rescan))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                !isBleSupported -> {
                    ScannerNoticeCard(
                        icon = Icons.Filled.BluetoothDisabled,
                        message = stringResource(R.string.scanner_bt_unavailable),
                        actionLabel = null,
                        onAction = {},
                    )
                }
                !hasPermissions -> {
                    ScannerNoticeCard(
                        icon = Icons.Filled.Bluetooth,
                        message = stringResource(R.string.scanner_permission_needed),
                        actionLabel = stringResource(R.string.action_grant_permissions),
                        actionTestTag = "grant_ble_permissions_button",
                        onAction = onRequestPermissions,
                    )
                }
                !isBluetoothEnabled -> {
                    ScannerNoticeCard(
                        icon = Icons.Filled.BluetoothDisabled,
                        message = stringResource(R.string.scanner_bt_disabled),
                        actionLabel = stringResource(R.string.action_enable_bluetooth),
                        actionTestTag = "enable_bluetooth_button",
                        onAction = onRequestEnableBluetooth,
                    )
                }
                else -> {
                    // Filter chips (NUS vs All BLE)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = filterNusOnly,
                            onClick = { onToggleFilterNus(true) },
                            label = { Text(stringResource(R.string.filter_nus_only)) },
                            leadingIcon = if (filterNusOnly) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ControllerPanelCenter,
                                selectedLabelColor = LavenderInk,
                                containerColor = ControllerPanel,
                                labelColor = LavenderMuted,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filterNusOnly,
                                borderColor = ControllerBorder,
                                selectedBorderColor = NeonMagenta,
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("filter_nus_chip"),
                        )

                        FilterChip(
                            selected = !filterNusOnly,
                            onClick = { onToggleFilterNus(false) },
                            label = { Text(stringResource(R.string.filter_all_ble)) },
                            leadingIcon = if (!filterNusOnly) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ControllerPanelCenter,
                                selectedLabelColor = LavenderInk,
                                containerColor = ControllerPanel,
                                labelColor = LavenderMuted,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = !filterNusOnly,
                                borderColor = ControllerBorder,
                                selectedBorderColor = NeonMagenta,
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("filter_all_ble_chip"),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (scannedDevices.isEmpty()) {
                        Surface(
                            color = ControllerPanel,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, ControllerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Radar,
                                    contentDescription = null,
                                    tint = if (isScanning) MintConnected else LavenderMuted,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = if (isScanning) {
                                        stringResource(R.string.scanner_empty_scanning)
                                    } else {
                                        stringResource(R.string.scanner_empty_idle)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LavenderMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = scannedDevices,
                                key = { it.address },
                            ) { device ->
                                ScannedDeviceRow(
                                    device = device,
                                    onClick = { onDeviceSelected(device) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScannedDeviceRow(
    device: ScannedBleDevice,
    onClick: () -> Unit,
) {
    Surface(
        color = ControllerPanel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (device.advertisesNus) NeonMagenta.copy(alpha = 0.6f) else ControllerBorder),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("ble_device_item_${device.address}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = LavenderInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (device.advertisesNus) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MintConnected)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.badge_nus),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkEmeraldOnMint,
                            )
                        }
                    }
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelMedium,
                    color = LavenderMuted,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalCellularAlt,
                    contentDescription = null,
                    tint = when {
                        device.rssi >= -65 -> MintConnected
                        device.rssi >= -82 -> LavenderInk
                        else -> CoralDisconnected
                    },
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.rssi_format, device.rssi),
                    style = MaterialTheme.typography.labelMedium,
                    color = LavenderMuted,
                )
            }
        }
    }
}

@Composable
private fun ScannerNoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String?,
    actionTestTag: String = "scanner_notice_action",
    onAction: () -> Unit,
) {
    Surface(
        color = ControllerPanel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, ControllerBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CoralDisconnected,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LavenderInk,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonMagenta,
                        contentColor = DarkPlumOnAccent,
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(actionTestTag),
                ) {
                    Text(
                        text = actionLabel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
