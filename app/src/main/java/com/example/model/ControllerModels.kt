package com.example.model

enum class ControlId(val storageKey: String) {
    STICK("stick"),
    BUTTON_A("a"),
    BUTTON_B("b");

    val baseFraction: Float
        get() = when (this) {
            STICK -> 0.50f
            BUTTON_A -> 0.22f
            BUTTON_B -> 0.22f
        }
}

enum class CardinalDirection(val pressCode: Char, val releaseCode: Char) {
    UP('U', 'u'),
    RIGHT('R', 'r'),
    DOWN('D', 'd'),
    LEFT('L', 'l'),
}

enum class StageOrientation {
    PORTRAIT,
    LANDSCAPE,
}

data class ControlPlacement(
    val x: Float,
    val y: Float,
    val scale: Float = 1.0f,
) {
    fun clamped(): ControlPlacement = ControlPlacement(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
    )

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
    }
}

data class OrientationLayout(
    val stick: ControlPlacement,
    val buttonA: ControlPlacement,
    val buttonB: ControlPlacement,
) {
    fun placementFor(id: ControlId): ControlPlacement = when (id) {
        ControlId.STICK -> stick
        ControlId.BUTTON_A -> buttonA
        ControlId.BUTTON_B -> buttonB
    }

    fun updatePlacement(id: ControlId, updated: ControlPlacement): OrientationLayout {
        val safe = updated.clamped()
        return when (id) {
            ControlId.STICK -> copy(stick = safe)
            ControlId.BUTTON_A -> copy(buttonA = safe)
            ControlId.BUTTON_B -> copy(buttonB = safe)
        }
    }

    companion object {
        val DefaultPortrait = OrientationLayout(
            stick = ControlPlacement(x = 0.26f, y = 0.74f, scale = 1.0f),
            buttonA = ControlPlacement(x = 0.64f, y = 0.80f, scale = 1.0f),
            buttonB = ControlPlacement(x = 0.88f, y = 0.66f, scale = 1.0f),
        )

        val DefaultLandscape = OrientationLayout(
            stick = ControlPlacement(x = 0.20f, y = 0.55f, scale = 1.0f),
            buttonA = ControlPlacement(x = 0.70f, y = 0.66f, scale = 1.0f),
            buttonB = ControlPlacement(x = 0.88f, y = 0.46f, scale = 1.0f),
        )

        fun defaultFor(orientation: StageOrientation): OrientationLayout = when (orientation) {
            StageOrientation.PORTRAIT -> DefaultPortrait
            StageOrientation.LANDSCAPE -> DefaultLandscape
        }
    }
}

data class ControllerConfig(
    val rotationDegrees: Int = 0,
    val portraitLayout: OrientationLayout = OrientationLayout.DefaultPortrait,
    val landscapeLayout: OrientationLayout = OrientationLayout.DefaultLandscape,
    val hapticsEnabled: Boolean = true,
) {
    fun layoutFor(orientation: StageOrientation): OrientationLayout = when (orientation) {
        StageOrientation.PORTRAIT -> portraitLayout
        StageOrientation.LANDSCAPE -> landscapeLayout
    }

    fun updateLayout(
        orientation: StageOrientation,
        updated: OrientationLayout,
    ): ControllerConfig = when (orientation) {
        StageOrientation.PORTRAIT -> copy(portraitLayout = updated)
        StageOrientation.LANDSCAPE -> copy(landscapeLayout = updated)
    }

    companion object {
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

sealed interface BleConnectionState {
    data class Disconnected(val hadSession: Boolean = false) : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connecting(val deviceName: String) : BleConnectionState
    data class Connected(val deviceName: String, val address: String) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val advertisesNus: Boolean,
)
