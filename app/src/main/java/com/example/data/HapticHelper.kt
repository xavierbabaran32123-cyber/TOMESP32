package com.example.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

interface HapticFeedbackController {
    fun tickButtonPress()
    fun tickStickDirection()
}

class AndroidHapticHelper(context: Context) : HapticFeedbackController {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun tickButtonPress() {
        vibrateShort(durationMs = 8L, amplitude = 150)
    }

    override fun tickStickDirection() {
        vibrateShort(durationMs = 6L, amplitude = 110)
    }

    private fun vibrateShort(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Ignore vibration failures on unsupported hardware
        }
    }
}
