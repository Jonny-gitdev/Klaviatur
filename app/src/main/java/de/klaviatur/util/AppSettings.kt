package de.klaviatur.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.data.model.Epoch
import de.klaviatur.ui.screens.stats.StatTile

private data class CustomInstrumentData(
    val id: String,
    val name: String,
    val emoji: String
)

object AppSettings {
    private const val PREFS_NAME = "klaviatur_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"
    private const val KEY_HAS_INITIALIZED_DEFAULTS = "has_initialized_defaults"
    private const val KEY_OLGA_URI = "olga_folder_uri"
    private const val KEY_ACTIVE_EPOCHS = "active_epochs"
    private const val KEY_WEEKLY_GOAL_MINUTES = "weekly_goal_minutes"
    private const val KEY_HIDDEN_STAT_TILES = "hidden_stat_tiles"
    private const val KEY_CUSTOM_INSTRUMENTS = "custom_instruments_json"
    private const val KEY_ENABLED_INSTRUMENT_IDS = "enabled_instrument_ids"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    val languages = listOf("System", "Deutsch", "English", "Français", "Español")

    private var _language by mutableStateOf("System")
    var language: String
        get() = _language
        set(value) {
            _language = value
            prefs?.edit()?.putString(KEY_LANGUAGE, value)?.apply()
        }

    private var _hasSeenTutorial by mutableStateOf(false)
    var hasSeenTutorial: Boolean
        get() = _hasSeenTutorial
        set(value) {
            _hasSeenTutorial = value
            prefs?.edit()?.putBoolean(KEY_HAS_SEEN_TUTORIAL, value)?.apply()
        }

    private var _hasInitializedDefaults by mutableStateOf(false)
    var hasInitializedDefaults: Boolean
        get() = _hasInitializedDefaults
        set(value) {
            _hasInitializedDefaults = value
            prefs?.edit()?.putBoolean(KEY_HAS_INITIALIZED_DEFAULTS, value)?.apply()
        }

    /** Wochenziel in Minuten; 0 = nicht festgelegt. */
    private var _weeklyGoalMinutes by mutableStateOf(0)
    var weeklyGoalMinutes: Int
        get() = _weeklyGoalMinutes
        set(value) {
            _weeklyGoalMinutes = value.coerceAtLeast(0)
            prefs?.edit()?.putInt(KEY_WEEKLY_GOAL_MINUTES, _weeklyGoalMinutes)?.apply()
        }

    var activeEpochs by mutableStateOf(Epoch.entries.toSet())
        private set

    /** Im Statistik-Screen ausgeblendete Kacheln (StatTile-Namen). Standard: nichts ausgeblendet. */
    var hiddenStatTiles by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Benutzerdefinierte Instrumente. */
    var customInstruments by mutableStateOf<List<ChordInstrument>>(emptyList())
        private set

    /** Aktivierte Instrument-IDs fürs Repertoire (Standard: Klavier, Gitarre, Geige). */
    var enabledInstrumentIds by mutableStateOf<Set<String>>(ChordInstrument.defaultEnabledIds)
        private set

    val allInstruments: List<ChordInstrument>
        get() = ChordInstrument.builtInList + customInstruments

    val visibleInstruments: List<ChordInstrument>
        get() = allInstruments.filter { it.id in enabledInstrumentIds }

    fun isInstrumentVisible(inst: ChordInstrument): Boolean = inst.id in enabledInstrumentIds

    fun toggleInstrumentVisibility(inst: ChordInstrument) {
        enabledInstrumentIds = if (inst.id in enabledInstrumentIds) {
            enabledInstrumentIds - inst.id
        } else {
            enabledInstrumentIds + inst.id
        }
        prefs?.edit()?.putStringSet(KEY_ENABLED_INSTRUMENT_IDS, enabledInstrumentIds)?.apply()
    }

    fun addCustomInstrument(name: String, emoji: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val cleanEmoji = emoji.trim().ifBlank { "🎵" }
        val id = "custom_" + System.currentTimeMillis()
        val newInst = ChordInstrument(
            id = id,
            defaultName = cleanName,
            emoji = cleanEmoji,
            isCustom = true
        )
        customInstruments = customInstruments + newInst
        enabledInstrumentIds = enabledInstrumentIds + id
        prefs?.edit()?.putStringSet(KEY_ENABLED_INSTRUMENT_IDS, enabledInstrumentIds)?.apply()
        saveCustomInstruments()
    }

    fun removeCustomInstrument(inst: ChordInstrument) {
        if (!inst.isCustom) return
        customInstruments = customInstruments.filter { it.id != inst.id }
        enabledInstrumentIds = enabledInstrumentIds - inst.id
        prefs?.edit()?.putStringSet(KEY_ENABLED_INSTRUMENT_IDS, enabledInstrumentIds)?.apply()
        saveCustomInstruments()
    }

    fun findCustomInstrument(id: String): ChordInstrument? {
        return customInstruments.find { it.id == id }
    }

    private fun saveCustomInstruments() {
        val list = customInstruments.map { CustomInstrumentData(it.id, it.defaultName, it.emoji) }
        val json = gson.toJson(list)
        prefs?.edit()?.putString(KEY_CUSTOM_INSTRUMENTS, json)?.apply()
    }

    fun isStatTileVisible(tile: StatTile): Boolean = tile.name !in hiddenStatTiles

    fun toggleStatTile(tile: StatTile) {
        hiddenStatTiles = if (tile.name in hiddenStatTiles) hiddenStatTiles - tile.name else hiddenStatTiles + tile.name
        prefs?.edit()?.putStringSet(KEY_HIDDEN_STAT_TILES, hiddenStatTiles)?.apply()
    }

    private var _olgaFolderUri by mutableStateOf<Uri?>(null)
    var olgaFolderUri: Uri?
        get() = _olgaFolderUri
        set(value) {
            _olgaFolderUri = value
            prefs?.edit()?.putString(KEY_OLGA_URI, value?.toString())?.apply()
        }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        _language = p.getString(KEY_LANGUAGE, "System") ?: "System"
        _hasSeenTutorial = p.getBoolean(KEY_HAS_SEEN_TUTORIAL, false)
        _hasInitializedDefaults = p.getBoolean(KEY_HAS_INITIALIZED_DEFAULTS, false)
        _weeklyGoalMinutes = p.getInt(KEY_WEEKLY_GOAL_MINUTES, 0)

        val uriStr = p.getString(KEY_OLGA_URI, null)
        _olgaFolderUri = if (!uriStr.isNullOrBlank()) Uri.parse(uriStr) else null

        hiddenStatTiles = p.getStringSet(KEY_HIDDEN_STAT_TILES, null)?.toSet() ?: emptySet()

        val enabledSet = p.getStringSet(KEY_ENABLED_INSTRUMENT_IDS, null)
        if (enabledSet != null) {
            enabledInstrumentIds = enabledSet.toSet()
        } else {
            val oldHiddenSet = p.getStringSet("hidden_instrument_ids", null)
            if (oldHiddenSet != null) {
                enabledInstrumentIds = ChordInstrument.builtInList.map { it.id }.toSet() - oldHiddenSet
            } else {
                enabledInstrumentIds = ChordInstrument.defaultEnabledIds
            }
        }

        val customJson = p.getString(KEY_CUSTOM_INSTRUMENTS, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<CustomInstrumentData>>() {}.type
                val list: List<CustomInstrumentData> = gson.fromJson(customJson, type) ?: emptyList()
                customInstruments = list.map {
                    ChordInstrument(
                        id = it.id,
                        defaultName = it.name,
                        emoji = it.emoji,
                        isCustom = true
                    )
                }
            } catch (_: Exception) {}
        }

        val epochsSet = p.getStringSet(KEY_ACTIVE_EPOCHS, null)
        if (epochsSet != null) {
            activeEpochs = epochsSet.mapNotNull { name ->
                try { Epoch.valueOf(name) } catch (_: Exception) { null }
            }.toSet()
        } else {
            activeEpochs = Epoch.entries.toSet()
        }
    }

    fun toggleEpoch(epoch: Epoch) {
        activeEpochs = if (activeEpochs.contains(epoch)) {
            activeEpochs - epoch
        } else {
            activeEpochs + epoch
        }
        prefs?.edit()?.putStringSet(KEY_ACTIVE_EPOCHS, activeEpochs.map { it.name }.toSet())?.apply()
    }
}
