package de.klaviatur.ui.components

import androidx.compose.foundation.ScrollState
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
    scrollState: ScrollState = rememberScrollState()
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
            ChordLineView(line)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ChordLineView(line: ChordLine) {
    val hasChords = line.segments.any { it.chord != null }
    
    if (hasChords) {
        Column {
            // Chords row
            Row {
                line.segments.forEach { segment ->
                    if (segment.chord != null) {
                        Text(
                            text = segment.chord,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF2D5A27),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                    // Add space proportional to the text length to align the next chord
                    val textSpace = segment.text
                    Text(
                        text = " ".repeat(textSpace.length.coerceAtLeast(1)),
                        fontSize = 13.sp
                    )
                }
            }
            // Lyrics row
            Text(
                text = line.segments.joinToString("") { it.text },
                style = MaterialTheme.typography.bodyLarge
            )
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
