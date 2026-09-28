package de.klaviatur.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.klaviatur.R
import de.klaviatur.ui.theme.BorderColor
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextMuted
import de.klaviatur.ui.theme.TextPrimary

@Composable
fun ChordImportDialog(
    onDismiss: () -> Unit,
    onSelectFolder: () -> Unit,
    onImportUg: () -> Unit,
    onAddManual: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PaperWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.import_dialog_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.import_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(Modifier.height(16.dp))

                // Option 1: Folder Import
                ImportOptionCard(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.import_option_folder_title),
                    description = stringResource(R.string.import_option_folder_desc),
                    buttonText = stringResource(R.string.import_option_folder_btn),
                    onClick = {
                        onDismiss()
                        onSelectFolder()
                    }
                )
                Spacer(Modifier.height(12.dp))

                // Option 2: UG Import
                ImportOptionCard(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.import_option_ug_title),
                    description = stringResource(R.string.import_option_ug_desc),
                    buttonText = stringResource(R.string.import_option_ug_btn),
                    onClick = {
                        onDismiss()
                        onImportUg()
                    }
                )
                Spacer(Modifier.height(12.dp))

                // Option 3: Manual Entry
                ImportOptionCard(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.import_option_manual_title),
                    description = stringResource(R.string.import_option_manual_desc),
                    buttonText = stringResource(R.string.import_option_manual_btn),
                    onClick = {
                        onDismiss()
                        onAddManual()
                    }
                )
                Spacer(Modifier.height(16.dp))

                // Cancel Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperBeige,
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
