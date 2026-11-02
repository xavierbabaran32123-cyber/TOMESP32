package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.BleControllerClient
import com.example.data.ControllerPreferencesDataSource
import com.example.data.HapticFeedbackController
import com.example.model.BleConnectionState
import com.example.model.ControllerConfig
import com.example.model.ScannedBleDevice
import com.example.ui.ControllerRoute
import com.example.ui.ControllerViewModel
import com.example.ui.theme.MaskControllerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameMatchesMaskController() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mask Controller", appName)
    }

    @Test
    fun arcadeControlsSendNordicUartPacketsAndSupportEditMode() {
        val fakePrefs = FakePreferencesDataSource()
        val fakeBle = FakeBleControllerClient()
        val fakeHaptics = FakeHapticController()
        val viewModel = ControllerViewModel(
            preferences = fakePrefs,
            bleClient = fakeBle,
            haptics = fakeHaptics,
        )

        composeTestRule.setContent {
            MaskControllerTheme {
                ControllerRoute(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_text").assertTextContains("Not connected")
        composeTestRule.onNodeWithTag("stick_control").assertIsDisplayed()
        composeTestRule.onNodeWithTag("button_a").assertIsDisplayed()
        composeTestRule.onNodeWithTag("button_b").assertIsDisplayed()

        // Press and release Button A -> sends "A" then "a"
        composeTestRule.onNodeWithTag("button_a").performTouchInput {
            down(center)
        }
        composeTestRule.waitForIdle()
        assertTrue(fakeBle.sentCommands.contains("A"))

        composeTestRule.onNodeWithTag("button_a").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("A", "a"), fakeBle.sentCommands)

        // Drag Joystick right -> sends "R", then release -> sends "r"
        fakeBle.sentCommands.clear()
        composeTestRule.onNodeWithTag("stick_control").performTouchInput {
            down(center)
            moveTo(Offset(right - 4f, centerY))
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("R"), fakeBle.sentCommands)

        composeTestRule.onNodeWithTag("stick_control").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("R", "r"), fakeBle.sentCommands)

        // Enter Edit Mode, resize Joystick up to 105%, then reset to 100%
        composeTestRule.onNodeWithTag("edit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("size_label").assertTextContains("Joystick 100%")

        composeTestRule.onNodeWithTag("size_up_button").performTouchInput {
            down(center)
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("size_label").assertTextContains("Joystick 105%")

        composeTestRule.onNodeWithTag("reset_layout_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("size_label").assertTextContains("Joystick 100%")

        // Rotate 90 degrees
        composeTestRule.onNodeWithTag("rotate_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(90, viewModel.uiState.value.config.rotationDegrees)
    }

    private class FakePreferencesDataSource : ControllerPreferencesDataSource {
        private val state = MutableStateFlow(ControllerConfig())
        override val configFlow: Flow<ControllerConfig> = state
        override suspend fun saveConfig(config: ControllerConfig) {
            state.value = config
        }
    }

    private class FakeHapticController : HapticFeedbackController {
        var buttonTicks = 0
        var stickTicks = 0
        override fun tickButtonPress() {
            buttonTicks++
        }
        override fun tickStickDirection() {
            stickTicks++
        }
    }

    private class FakeBleControllerClient : BleControllerClient {
        override val connectionState =
            MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected(false))
        override val scannedDevices: StateFlow<List<ScannedBleDevice>> =
            MutableStateFlow(emptyList())
        override val isScanning: StateFlow<Boolean> = MutableStateFlow(false)
        private val _lastSentPacket = MutableStateFlow<String?>(null)
        override val lastSentPacket: StateFlow<String?> = _lastSentPacket

        val sentCommands = mutableListOf<String>()

        override fun isBleSupported(): Boolean = true
        override fun isBluetoothEnabled(): Boolean = true
        override fun hasRequiredPermissions(): Boolean = true
        override fun requiredPermissions(): Array<String> = emptyArray()
        override fun startScan(filterByNus: Boolean) {}
        override fun stopScan() {}
        override fun connect(address: String, fallbackName: String) {}
        override fun disconnect() {}
        override fun send(command: String) {
            sentCommands.add(command)
            _lastSentPacket.value = command
        }
        override fun close() {}
    }
}
