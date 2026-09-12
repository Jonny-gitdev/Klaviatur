package de.klaviatur.util

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetronomeManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _bpm = MutableStateFlow(120)
    val bpm = _bpm.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(30, 300)
    }

    fun toggle() {
        if (_isPlaying.value) stop() else start()
    }

    fun start() {
        job?.cancel()
        _isPlaying.value = true
        job = scope.launch {
            while (isActive) {
                val delayMs = 60000L / _bpm.value
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                delay(delayMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _isPlaying.value = false
    }
}
