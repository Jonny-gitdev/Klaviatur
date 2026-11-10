package de.klaviatur.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaviatur.util.ChordLine
import de.klaviatur.util.ChordSheet
import kotlinx.coroutines.delay

@Composable
fun ChordSheetRenderer(
    chordSheet: ChordSheet,
    modifier: Modifier = Modifier,
    isAutoScrolling: Boolean = false,
    scrollSpeed: Float = 1f, // pixels per frame approx, or scaled
    scrollState: ScrollState = rememberScrollState(),
    onChordClick: ((String) -> Unit)? = null
) {
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (true) {
                // Scroll small amount every frame (roughly 16ms)
                // Speed 1f = ~60 pixels per second
                scrollState.scrollBy(scrollSpeed)
                delay(16)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        chordSheet.lines.forEach { line ->
            ChordLineView(line, onChordClick)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordLineView(line: ChordLine, onChordClick: ((String) -> Unit)?) {
    if (line.label != null) {
        Text(
            text = line.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF7A7570)),
            modifier = Modifier.padding(top = 10.dp)
        )
        return
    }
    val hasChords = line.segments.any { !it.chord.isNullOrBlank() }
    
    if (hasChords) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            line.segments.forEach { segment ->
                Column {
                    val chord = segment.chord
                    Text(
                        text = chord ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF2D5A27),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        modifier = if (chord.isNullOrBlank() || onChordClick == null) Modifier
                        else Modifier.clickable { onChordClick(chord) }
                    )
                    Text(
                        text = segment.text.ifEmpty { " " },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    } else {
        // Just lyrics
        val text = line.segments.joinToString("") { it.text }
        if (text.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
