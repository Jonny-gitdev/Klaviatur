package de.klaviatur.util

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.klaviatur.data.model.Epoch

object AppSettings {
    var language by mutableStateOf("Deutsch")
    var activeEpochs by mutableStateOf(Epoch.entries.toSet())
    
    var olgaFolderUri by mutableStateOf<Uri?>(null)
    
    val languages = listOf("Deutsch", "English", "Français", "Español")
    
    fun toggleEpoch(epoch: Epoch) {
        activeEpochs = if (activeEpochs.contains(epoch)) {
            activeEpochs - epoch
        } else {
            activeEpochs + epoch
        }
    }
}
