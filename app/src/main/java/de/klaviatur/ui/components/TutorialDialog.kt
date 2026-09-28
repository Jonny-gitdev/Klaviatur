package de.klaviatur.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.klaviatur.R
import de.klaviatur.ui.theme.BorderColor
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextMuted
import de.klaviatur.ui.theme.TextPrimary
import de.klaviatur.util.AppSettings

private data class TutorialPage(
    val icon: String,
    val titleRes: Int,
    val descRes: Int
)

private val pages = listOf(
    TutorialPage("🎹", R.string.tutorial_welcome_title, R.string.tutorial_welcome_desc),
    TutorialPage("🎼", R.string.tutorial_repertoire_title, R.string.tutorial_repertoire_desc),
    TutorialPage("⏱️", R.string.tutorial_sessions_title, R.string.tutorial_sessions_desc),
    TutorialPage("🎸", R.string.tutorial_chords_title, R.string.tutorial_chords_desc),
    TutorialPage("📊", R.string.tutorial_lists_stats_title, R.string.tutorial_lists_stats_desc)
)

@Composable
fun TutorialDialog(
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]

    Dialog(onDismissRequest = {
        AppSettings.hasSeenTutorial = true
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PaperWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header icon
                Text(
                    text = page.icon,
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(page.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(24.dp))

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentPage) ForestGreen else BorderColor
                                )
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPage > 0) {
                        TextButton(onClick = { currentPage-- }) {
                            Text(stringResource(R.string.tutorial_prev))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentPage < pages.lastIndex) {
                                currentPage++
                            } else {
                                AppSettings.hasSeenTutorial = true
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(
                                if (currentPage < pages.lastIndex) R.string.tutorial_next else R.string.tutorial_finish
                            )
                        )
                    }
                }
            }
        }
    }
}
