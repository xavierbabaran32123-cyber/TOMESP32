package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AndroidBleNusManager
import com.example.data.AndroidHapticHelper
import com.example.data.BleControllerClient
import com.example.data.ControllerPreferencesDataSource
import com.example.data.ControllerPreferencesRepository
import com.example.data.HapticFeedbackController
import com.example.model.BleConnectionState
import com.example.model.CardinalDirection
import com.example.model.ControlId
import com.example.model.ControlPlacement
import com.example.model.ControllerConfig
import com.example.model.JoystickMath
import com.example.model.OrientationLayout
import com.example.model.ScannedBleDevice
import com.example.model.StageOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ControllerUiState(
    val config: ControllerConfig = ControllerConfig(),
    val connectionState: BleConnectionState = BleConnectionState.Disconnected(hadSession = false),
    val scannedDevices: List<ScannedBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val filterNusOnly: Boolean = true,
    val showScannerSheet: Boolean = false,
    val isStickActive: Boolean = false,
    val knobOffsetX: Float = 0f,
    val knobOffsetY: Float = 0f,
    val activeDirection: CardinalDirection? = null,
    val isButtonADown: Boolean = false,
    val isButtonBDown: Boolean = false,
    val isEditing: Boolean = false,
    val selectedControl: ControlId? = null,
    val lastSentPacket: String? = null,
)

class ControllerViewModel(
    private val preferences: ControllerPreferencesDataSource,
    private val bleClient: BleControllerClient,
    private val haptics: HapticFeedbackController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.configFlow.collect { loadedConfig ->
                _uiState.update { it.copy(config = loadedConfig) }
            }
        }
        viewModelScope.launch {
            var wasConnected = false
            bleClient.connectionState.collect { connState ->
                val isNowConnected = connState is BleConnectionState.Connected
                if (wasConnected && !isNowConnected) {
                    releaseAllControls(notifyReceiver = false)
                }
                wasConnected = isNowConnected
                _uiState.update { state ->
                    state.copy(
                        connectionState = connState,
                        showScannerSheet = if (isNowConnected) false else state.showScannerSheet,
                    )
                }
            }
        }
        viewModelScope.launch {
            bleClient.scannedDevices.collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
        viewModelScope.launch {
            bleClient.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        viewModelScope.launch {
            bleClient.lastSentPacket.collect { packet ->
                _uiState.update { it.copy(lastSentPacket = packet) }
            }
        }
    }

    fun isBleSupported(): Boolean = bleClient.isBleSupported()
    fun isBluetoothEnabled(): Boolean = bleClient.isBluetoothEnabled()
    fun hasBlePermissions(): Boolean = bleClient.hasRequiredPermissions()
    fun requiredBlePermissions(): Array<String> = bleClient.requiredPermissions()

    // --- Play Controls ---

    fun onStickMove(dx: Float, dy: Float, radiusPx: Float) {
        if (_uiState.value.isEditing) return
        val prevDir = _uiState.value.activeDirection
        val sample = JoystickMath.evaluateStick(
            dx = dx,
            dy = dy,
            radiusPx = radiusPx,
            currentDirection = prevDir,
        )
        _uiState.update {
            it.copy(
                isStickActive = true,
                knobOffsetX = sample.knobOffsetX,
                knobOffsetY = sample.knobOffsetY,
                activeDirection = sample.direction,
            )
        }
        if (sample.direction != prevDir) {
            val cmd = JoystickMath.buildDirectionTransitionCommand(prevDir, sample.direction)
            if (cmd.isNotEmpty()) {
                bleClient.send(cmd)
            }
            if (sample.direction != null && _uiState.value.config.hapticsEnabled) {
                haptics.tickStickDirection()
            }
        }
    }

    fun onStickRelease() {
        val prevDir = _uiState.value.activeDirection
        _uiState.update {
            it.copy(
                isStickActive = false,
                knobOffsetX = 0f,
                knobOffsetY = 0f,
                activeDirection = null,
            )
        }
        if (prevDir != null) {
            val cmd = JoystickMath.buildDirectionTransitionCommand(prevDir, null)
            if (cmd.isNotEmpty()) {
                bleClient.send(cmd)
            }
        }
    }

    fun onButtonPress(controlId: ControlId) {
        if (_uiState.value.isEditing) return
        when (controlId) {
            ControlId.BUTTON_A -> {
                if (_uiState.value.isButtonADown) return
                _uiState.update { it.copy(isButtonADown = true) }
                bleClient.send("A")
                if (_uiState.value.config.hapticsEnabled) haptics.tickButtonPress()
            }
            ControlId.BUTTON_B -> {
                if (_uiState.value.isButtonBDown) return
                _uiState.update { it.copy(isButtonBDown = true) }
                bleClient.send("B")
                if (_uiState.value.config.hapticsEnabled) haptics.tickButtonPress()
            }
            ControlId.STICK -> Unit
        }
    }

    fun onButtonRelease(controlId: ControlId) {
        when (controlId) {
            ControlId.BUTTON_A -> {
                if (!_uiState.value.isButtonADown) return
                _uiState.update { it.copy(isButtonADown = false) }
                bleClient.send("a")
            }
            ControlId.BUTTON_B -> {
                if (!_uiState.value.isButtonBDown) return
                _uiState.update { it.copy(isButtonBDown = false) }
                bleClient.send("b")
            }
            ControlId.STICK -> Unit
        }
    }

    fun releaseAllControls(notifyReceiver: Boolean = true) {
        val prevDir = _uiState.value.activeDirection
        _uiState.update {
            it.copy(
                isStickActive = false,
                knobOffsetX = 0f,
                knobOffsetY = 0f,
                activeDirection = null,
                isButtonADown = false,
                isButtonBDown = false,
            )
        }
        if (prevDir != null && notifyReceiver) {
            val releaseDir = JoystickMath.buildDirectionTransitionCommand(prevDir, null)
            if (releaseDir.isNotEmpty()) {
                bleClient.send(releaseDir)
            }
        }
        if (notifyReceiver) {
            bleClient.send(AndroidBleNusManager.RESET_ALL_COMMAND)
        }
    }

    // --- Layout & Rotation ---

    fun rotate90() {
        releaseAllControls(notifyReceiver = true)
        val nextRot = (_uiState.value.config.rotationDegrees + 90) % 360
        val updated = _uiState.value.config.copy(rotationDegrees = nextRot)
        _uiState.update { it.copy(config = updated) }
        persistConfig(updated)
    }

    fun toggleEditMode() {
        setEditing(!_uiState.value.isEditing)
    }

    fun setEditing(editing: Boolean) {
        releaseAllControls(notifyReceiver = true)
        _uiState.update { state ->
            state.copy(
                isEditing = editing,
                selectedControl = if (editing) (state.selectedControl ?: ControlId.STICK) else null,
            )
        }
        if (!editing) {
            persistConfig(_uiState.value.config)
        }
    }

    fun selectControl(controlId: ControlId) {
        _uiState.update { it.copy(selectedControl = controlId) }
    }

    fun moveControlInStage(
        orientation: StageOrientation,
        controlId: ControlId,
        normalizedX: Float,
        normalizedY: Float,
    ) {
        val currentConfig = _uiState.value.config
        val layout = currentConfig.layoutFor(orientation)
        val existing = layout.placementFor(controlId)
        val updatedPlacement = ControlPlacement(
            x = normalizedX.coerceIn(0f, 1f),
            y = normalizedY.coerceIn(0f, 1f),
            scale = existing.scale,
        )
        val updatedConfig = currentConfig.updateLayout(
            orientation = orientation,
            updated = layout.updatePlacement(controlId, updatedPlacement),
        )
        _uiState.update {
            it.copy(
                config = updatedConfig,
                selectedControl = controlId,
            )
        }
    }

    fun commitControlDrag() {
        persistConfig(_uiState.value.config)
    }

    fun bumpSelectedControlScale(orientation: StageOrientation, delta: Float) {
        val selected = _uiState.value.selectedControl ?: return
        val currentConfig = _uiState.value.config
        val layout = currentConfig.layoutFor(orientation)
        val existing = layout.placementFor(selected)
        val nextScale = JoystickMath.stepScale(existing.scale, delta)
        val updatedConfig = currentConfig.updateLayout(
            orientation = orientation,
            updated = layout.updatePlacement(selected, existing.copy(scale = nextScale)),
        )
        _uiState.update { it.copy(config = updatedConfig) }
        persistConfig(updatedConfig)
    }

    fun resetCurrentOrientationLayout(orientation: StageOrientation) {
        val updatedConfig = _uiState.value.config.updateLayout(
            orientation = orientation,
            updated = OrientationLayout.defaultFor(orientation),
        )
        _uiState.update { it.copy(config = updatedConfig) }
        persistConfig(updatedConfig)
    }

    fun toggleHaptics() {
        val next = !_uiState.value.config.hapticsEnabled
        val updated = _uiState.value.config.copy(hapticsEnabled = next)
        _uiState.update { it.copy(config = updated) }
        if (next) haptics.tickButtonPress()
        persistConfig(updated)
    }

    // --- Bluetooth Scanner & Connection ---

    fun onConnectButtonClicked() {
        when (_uiState.value.connectionState) {
            is BleConnectionState.Connected,
            is BleConnectionState.Connecting -> {
                bleClient.disconnect()
            }
            else -> {
                _uiState.update { it.copy(showScannerSheet = true) }
                if (bleClient.isBleSupported() &&
                    bleClient.isBluetoothEnabled() &&
                    bleClient.hasRequiredPermissions()
                ) {
                    bleClient.startScan(_uiState.value.filterNusOnly)
                }
            }
        }
    }

    fun dismissScannerSheet() {
        bleClient.stopScan()
        _uiState.update { it.copy(showScannerSheet = false) }
    }

    fun setFilterNusOnly(nusOnly: Boolean) {
        if (_uiState.value.filterNusOnly == nusOnly) return
        _uiState.update { it.copy(filterNusOnly = nusOnly) }
        if (bleClient.isBleSupported() &&
            bleClient.isBluetoothEnabled() &&
            bleClient.hasRequiredPermissions()
        ) {
            bleClient.startScan(nusOnly)
        }
    }

    fun startOrRestartScan() {
        bleClient.startScan(_uiState.value.filterNusOnly)
    }

    fun stopScan() {
        bleClient.stopScan()
    }

    fun connectToScannedDevice(device: ScannedBleDevice) {
        bleClient.connect(address = device.address, fallbackName = device.name)
    }

    private fun persistConfig(config: ControllerConfig) {
        viewModelScope.launch {
            preferences.saveConfig(config)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleClient.close()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ControllerViewModel(
                        preferences = ControllerPreferencesRepository(appCtx),
                        bleClient = AndroidBleNusManager(appCtx),
                        haptics = AndroidHapticHelper(appCtx),
                    ) as T
                }
            }
        }
    }
}
