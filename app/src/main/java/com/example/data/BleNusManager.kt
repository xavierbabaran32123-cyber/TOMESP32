package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.model.BleConnectionState
import com.example.model.ScannedBleDevice
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface BleControllerClient {
    val connectionState: StateFlow<BleConnectionState>
    val scannedDevices: StateFlow<List<ScannedBleDevice>>
    val isScanning: StateFlow<Boolean>
    val lastSentPacket: StateFlow<String?>

    fun isBleSupported(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun hasRequiredPermissions(): Boolean
    fun requiredPermissions(): Array<String>

    fun startScan(filterByNus: Boolean)
    fun stopScan()
    fun connect(address: String, fallbackName: String)
    fun disconnect()
    fun send(command: String)
    fun close()
}

class AndroidBleNusManager(
    context: Context,
) : BleControllerClient {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected(hadSession = false))
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<ScannedBleDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastSentPacket = MutableStateFlow<String?>(null)
    override val lastSentPacket: StateFlow<String?> = _lastSentPacket.asStateFlow()

    private val discoveredMap = LinkedHashMap<String, ScannedBleDevice>()
    private var scanTimeoutJob: Job? = null

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var pendingWriteAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var hadConnectedInCurrentAttempt: Boolean = false

    private val writeQueue = Channel<String>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            for (payload in writeQueue) {
                performSerializedWrite(payload)
            }
        }
    }

    override fun isBleSupported(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    override fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    override fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun hasRequiredPermissions(): Boolean =
        requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    override fun startScan(filterByNus: Boolean) {
        if (!isBleSupported() || !isBluetoothEnabled() || !hasRequiredPermissions()) {
            _isScanning.value = false
            return
        }
        stopScan()
        synchronized(discoveredMap) {
            discoveredMap.clear()
            _scannedDevices.value = emptyList()
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filters = if (filterByNus) {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(NUS_SERVICE_UUID))
                    .build(),
            )
        } else {
            emptyList()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            if (_connectionState.value !is BleConnectionState.Connected) {
                _connectionState.value = BleConnectionState.Scanning
            }
            scanTimeoutJob = scope.launch {
                delay(SCAN_DURATION_MS)
                stopScan()
            }
        } catch (e: Exception) {
            _isScanning.value = false
            _connectionState.value = BleConnectionState.Error(e.message ?: "Scan failed")
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (_isScanning.value) {
            try {
                if (hasRequiredPermissions() && isBluetoothEnabled()) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                }
            } catch (_: Exception) {
                // Ignore scanner stop race
            }
            _isScanning.value = false
        }
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected(hadSession = false)
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(address: String, fallbackName: String) {
        if (!hasRequiredPermissions() || !isBluetoothEnabled()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth permission or adapter unavailable")
            return
        }
        stopScan()
        closeActiveGatt()

        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _connectionState.value = BleConnectionState.Error("Invalid Bluetooth address")
            return
        }

        val resolvedName = runCatching { device.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName.ifBlank { DEFAULT_DEVICE_NAME }

        hadConnectedInCurrentAttempt = false
        _connectionState.value = BleConnectionState.Connecting(resolvedName)

        try {
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }
        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        stopScan()
        val gatt = activeGatt
        rxCharacteristic = null
        pendingWriteAck?.complete(false)
        pendingWriteAck = null
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
                // Ignore close failure
            }
            activeGatt = null
        }
        _connectionState.value = BleConnectionState.Disconnected(hadSession = true)
    }

    override fun send(command: String) {
        if (command.isEmpty()) return
        _lastSentPacket.value = command
        if (rxCharacteristic == null || activeGatt == null) return
        writeQueue.trySend(command)
    }

    override fun close() {
        disconnect()
        writeQueue.close()
    }

    @SuppressLint("MissingPermission")
    private fun closeActiveGatt() {
        rxCharacteristic = null
        pendingWriteAck?.complete(false)
        pendingWriteAck = null
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun performSerializedWrite(command: String) {
        val gatt = activeGatt ?: return
        val rx = rxCharacteristic ?: return
        if (!hasRequiredPermissions()) return

        val data = command.toByteArray(Charsets.UTF_8)
        val supportsNoResponse =
            (rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val writeType = if (supportsNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val ack = CompletableDeferred<Boolean>()
        pendingWriteAck = ack

        val initiated = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rx, data, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.writeType = writeType
                @Suppress("DEPRECATION")
                rx.value = data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }
        } catch (_: Exception) {
            false
        }

        if (initiated) {
            withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) {
                ack.await()
            }
        }
        if (pendingWriteAck === ack) {
            pendingWriteAck = null
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val rawName = runCatching { device.name }.getOrNull()
                ?: result.scanRecord?.deviceName
                ?: ""
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
            val hasNus = serviceUuids.any { it.uuid == NUS_SERVICE_UUID } ||
                rawName.contains("Mask", ignoreCase = true)

            if (rawName.isBlank() && !hasNus) return

            val entry = ScannedBleDevice(
                name = rawName.ifBlank { DEFAULT_DEVICE_NAME },
                address = address,
                rssi = result.rssi,
                advertisesNus = hasNus,
            )
            synchronized(discoveredMap) {
                discoveredMap[address] = entry
                _scannedDevices.value = discoveredMap.values
                    .sortedWith(
                        compareByDescending<ScannedBleDevice> { it.advertisesNus }
                            .thenByDescending { it.rssi },
                    )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _connectionState.value = BleConnectionState.Error("BLE scan error ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== activeGatt) {
                runCatching { gatt.close() }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runCatching {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = hadConnectedInCurrentAttempt
                    closeActiveGatt()
                    _connectionState.value = if (wasConnected) {
                        BleConnectionState.Disconnected(hadSession = true)
                    } else {
                        BleConnectionState.Error(
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                "GATT disconnected (status $status)"
                            } else {
                                "Connection closed"
                            },
                        )
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== activeGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeActiveGatt()
                _connectionState.value = BleConnectionState.Error("Service discovery failed ($status)")
                return
            }
            val nusService = gatt.getService(NUS_SERVICE_UUID)
            val rxChar = nusService?.getCharacteristic(NUS_RX_UUID)
            if (rxChar == null) {
                closeActiveGatt()
                _connectionState.value =
                    BleConnectionState.Error("Device does not expose Nordic UART Service")
                return
            }

            rxCharacteristic = rxChar
            hadConnectedInCurrentAttempt = true
            val name = runCatching { gatt.device?.name }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_DEVICE_NAME
            val address = gatt.device?.address.orEmpty()
            _connectionState.value = BleConnectionState.Connected(
                deviceName = name,
                address = address,
            )
            // Reset all held keys on the Mask Maze receiver upon initial connection
            send(RESET_ALL_COMMAND)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWriteAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    companion object {
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        const val DEFAULT_DEVICE_NAME = "MaskMaze"
        const val RESET_ALL_COMMAND = "0"
        private const val SCAN_DURATION_MS = 12_000L
        private const val WRITE_ACK_TIMEOUT_MS = 350L
    }
}
