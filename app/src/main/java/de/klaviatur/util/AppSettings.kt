package de.klaviatur.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.klaviatur.data.model.Epoch

object AppSettings {
    private const val PREFS_NAME = "klaviatur_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"
    private const val KEY_OLGA_URI = "olga_folder_uri"
    private const val KEY_ACTIVE_EPOCHS = "active_epochs"

    private var prefs: SharedPreferences? = null

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

    var activeEpochs by mutableStateOf(Epoch.entries.toSet())
        private set

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

        val uriStr = p.getString(KEY_OLGA_URI, null)
        _olgaFolderUri = if (!uriStr.isNullOrBlank()) Uri.parse(uriStr) else null

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
