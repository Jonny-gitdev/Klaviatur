package de.klaviatur.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.util.MetronomeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeBottomSheet(
    manager: MetronomeManager,
    onDismiss: () -> Unit
) {
    val bpm by manager.bpm.collectAsState()
    val isPlaying by manager.isPlaying.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Metronom", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "$bpm BPM",
                style = MaterialTheme.typography.displayMedium,
                color = ForestGreen
            )
            
            Slider(
                value = bpm.toFloat(),
                onValueChange = { manager.setBpm(it.toInt()) },
                valueRange = 30f..300f,
                modifier = Modifier.padding(vertical = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ForestGreen,
                    activeTrackColor = ForestGreen
                )
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { manager.setBpm(bpm - 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ForestGreen.copy(alpha = 0.1f), contentColor = ForestGreen)
                ) { Icon(Icons.Default.Remove, null) }
                
                Button(
                    onClick = { manager.toggle() },
                    modifier = Modifier.height(64.dp).width(120.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color.Red else ForestGreen
                    )
                ) {
                    Text(if (isPlaying) "STOP" else "START", style = MaterialTheme.typography.titleLarge)
                }

                FilledIconButton(
                    onClick = { manager.setBpm(bpm + 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ForestGreen.copy(alpha = 0.1f), contentColor = ForestGreen)
                ) { Icon(Icons.Default.Add, null) }
            }
        }
    }
}
