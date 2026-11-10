package de.klaviatur.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.ui.theme.ForestGreenBg
import de.klaviatur.ui.theme.ForestGreenText
import de.klaviatur.util.AppSettings

/** Kleines Symbol für Listen und Karten. */
fun ChordInstrument.emoji(): String = emoji

@Composable
fun ChordInstrument.label(): String {
    return nameRes?.let { stringResource(it) } ?: defaultName.ifEmpty { id }
}

/** Instrumenten-Chips für Auswahl/Filter. Mit allowNone lässt sich die Auswahl durch erneutes Tippen aufheben. */
@Composable
fun InstrumentChips(
    selected: ChordInstrument?,
    onSelect: (ChordInstrument?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    instruments: List<ChordInstrument> = AppSettings.visibleInstruments
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        instruments.forEach { inst ->
            val isSelected = selected?.id == inst.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (allowNone && isSelected) null else inst) },
                label = { Text("${inst.emoji()} ${inst.label()}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForestGreenBg,
                    selectedLabelColor = ForestGreenText
                )
            )
        }
    }
}
