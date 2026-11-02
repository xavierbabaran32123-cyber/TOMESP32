package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ControlId
import com.example.model.ControlPlacement
import com.example.model.ControllerConfig
import com.example.model.OrientationLayout
import com.example.model.StageOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.controllerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "maskmaze_controller_v1",
)

interface ControllerPreferencesDataSource {
    val configFlow: Flow<ControllerConfig>
    suspend fun saveConfig(config: ControllerConfig)
}

class ControllerPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : ControllerPreferencesDataSource {

    constructor(context: Context) : this(context.applicationContext.controllerDataStore)

    override val configFlow: Flow<ControllerConfig> = dataStore.data.map { prefs ->
        val rawRot = prefs[KEY_ROTATION] ?: 0
        val validRot = if (rawRot in ControllerConfig.VALID_ROTATIONS) rawRot else 0
        val haptics = prefs[KEY_HAPTICS_ENABLED] ?: true

        ControllerConfig(
            rotationDegrees = validRot,
            portraitLayout = readLayout(prefs, StageOrientation.PORTRAIT),
            landscapeLayout = readLayout(prefs, StageOrientation.LANDSCAPE),
            hapticsEnabled = haptics,
        )
    }

    override suspend fun saveConfig(config: ControllerConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_ROTATION] = if (config.rotationDegrees in ControllerConfig.VALID_ROTATIONS) {
                config.rotationDegrees
            } else {
                0
            }
            prefs[KEY_HAPTICS_ENABLED] = config.hapticsEnabled
            writeLayout(prefs, StageOrientation.PORTRAIT, config.portraitLayout)
            writeLayout(prefs, StageOrientation.LANDSCAPE, config.landscapeLayout)
        }
    }

    private fun readLayout(
        prefs: Preferences,
        orientation: StageOrientation,
    ): OrientationLayout {
        val defaults = OrientationLayout.defaultFor(orientation)
        return OrientationLayout(
            stick = readPlacement(prefs, orientation, ControlId.STICK, defaults.stick),
            buttonA = readPlacement(prefs, orientation, ControlId.BUTTON_A, defaults.buttonA),
            buttonB = readPlacement(prefs, orientation, ControlId.BUTTON_B, defaults.buttonB),
        )
    }

    private fun readPlacement(
        prefs: Preferences,
        orientation: StageOrientation,
        id: ControlId,
        fallback: ControlPlacement,
    ): ControlPlacement {
        val prefix = "${orientation.name.lowercase()}_${id.storageKey}"
        val x = prefs[floatPreferencesKey("${prefix}_x")] ?: fallback.x
        val y = prefs[floatPreferencesKey("${prefix}_y")] ?: fallback.y
        val s = prefs[floatPreferencesKey("${prefix}_s")] ?: fallback.scale
        if (!x.isFinite() || !y.isFinite() || !s.isFinite()) return fallback
        return ControlPlacement(x = x, y = y, scale = s).clamped()
    }

    private fun writeLayout(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        orientation: StageOrientation,
        layout: OrientationLayout,
    ) {
        ControlId.entries.forEach { id ->
            val p = layout.placementFor(id).clamped()
            val prefix = "${orientation.name.lowercase()}_${id.storageKey}"
            prefs[floatPreferencesKey("${prefix}_x")] = p.x
            prefs[floatPreferencesKey("${prefix}_y")] = p.y
            prefs[floatPreferencesKey("${prefix}_s")] = p.scale
        }
    }

    companion object {
        private val KEY_ROTATION = intPreferencesKey("rot_degrees")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }
}
