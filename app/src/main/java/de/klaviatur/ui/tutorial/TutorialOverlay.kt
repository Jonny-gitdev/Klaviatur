package de.klaviatur.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.klaviatur.R
import de.klaviatur.ui.theme.*
import de.klaviatur.util.AppSettings
import kotlin.math.roundToInt

/** Abdunkelung im Farbschema der App: ein sehr dunkles Waldgrün statt neutralem Schwarz. */
private val ScrimColor = Color(0xD1102A1F)

/**
 * Das Tutorial-Overlay. Wird in MainActivity ÜBER dem Scaffold platziert
 * (gleiche Box, fillMaxSize) und zeichnet sich nur, wenn das Tutorial aktiv ist.
 */
@Composable
fun TutorialOverlay() {
    val step = TutorialManager.currentStep ?: return

    // Zurück-Taste = ein Tutorial-Schritt zurück (statt die App-Navigation zu zerlegen).
    BackHandler(enabled = true) { TutorialManager.previous() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        if (step.kind == TutorialKind.WELCOME) {
            WelcomePanel(step)
        } else {
            GuidedStep(step, origin)
        }
    }
}

// ── Touch-Blocker ─────────────────────────────────────────────────────────────

private fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun Blocker(rect: Rect) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(
                with(density) { rect.width.toDp() },
                with(density) { rect.height.toDp() }
            )
            .consumeAllTouches()
    )
}

// ── Geführter Schritt: Abdunkelung + Spotlight + Erklärkarte ──────────────────

@Composable
private fun GuidedStep(step: TutorialStep, origin: Offset) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val boxMaxHeight = this.maxHeight

        // Zielrechteck in Overlay-Koordinaten (mit etwas Luft rundherum).
        val pad = with(density) { 6.dp.toPx() }
        val hole: Rect? = step.target
            ?.let { TutorialManager.targetRect(it) }
            ?.takeIf { it.width > 1f && it.height > 1f }
            ?.let { r ->
                Rect(
                    left = (r.left - origin.x - pad).coerceAtLeast(0f),
                    top = (r.top - origin.y - pad).coerceAtLeast(0f),
                    right = (r.right - origin.x + pad).coerceAtMost(w),
                    bottom = (r.bottom - origin.y + pad).coerceAtMost(h)
                )
            }

        val corner = with(density) { 14.dp.toPx() }

        // 1) Abdunkeln + Loch ausstanzen
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(ScrimColor)
            if (hole != null) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )
            }
        }

        // 2) Pulsierender Rahmen um das Ziel
        if (hole != null) {
            val pulse = rememberInfiniteTransition(label = "tutorial-pulse")
            val glow by pulse.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                label = "glow"
            )
            Canvas(Modifier.fillMaxSize()) {
                val outer = 7.dp.toPx() * glow
                drawRoundRect(
                    color = Amber.copy(alpha = 0.35f * glow),
                    topLeft = Offset(hole.left - outer, hole.top - outer),
                    size = Size(hole.width + outer * 2, hole.height + outer * 2),
                    cornerRadius = CornerRadius(corner + outer, corner + outer),
                    style = Stroke(width = 4.dp.toPx())
                )
                drawRoundRect(
                    color = Amber,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // 3) Berührungen abfangen
        val passThrough = step.kind == TutorialKind.ACTION && hole != null
        if (passThrough && hole != null) {
            // Nur das markierte Element ist bedienbar: vier Blocker rundherum, im Loch bleibt alles frei.
            Blocker(Rect(0f, 0f, w, hole.top))
            Blocker(Rect(0f, hole.bottom, w, h))
            Blocker(Rect(0f, hole.top, hole.left, hole.bottom))
            Blocker(Rect(hole.right, hole.top, w, hole.bottom))
        } else {
            Box(Modifier.fillMaxSize().consumeAllTouches())
        }

        // 4) Erklärkarte dort, wo mehr Platz ist
        val cardAlignment = when {
            hole == null -> Alignment.Center
            hole.top >= h - hole.bottom -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(12.dp),
            contentAlignment = cardAlignment
        ) {
            CoachCard(step = step, maxCardHeight = boxMaxHeight * 0.62f)
        }
    }
}

@Composable
private fun CoachCard(step: TutorialStep, maxCardHeight: androidx.compose.ui.unit.Dp) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperWhite,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            // Fortschritt + Beenden
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { TutorialManager.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ForestGreen,
                    trackColor = BorderColor
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { TutorialManager.finish() }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.tut_skip_all),
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Text (scrollt, falls er lang ist)
            Column(
                Modifier
                    .heightIn(max = maxCardHeight)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(step.titleRes),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(step.textRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))

            when (step.kind) {
                TutorialKind.INFO -> InfoButtons()
                TutorialKind.ACTION -> ActionButtons()
                TutorialKind.CHOICE -> ChoiceButtons()
                TutorialKind.FINISH -> {
                    Button(
                        onClick = { TutorialManager.finish() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.tutorial_finish)) }
                }
                TutorialKind.WELCOME -> Unit
            }
        }
    }
}

@Composable
private fun InfoButtons() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (TutorialManager.canGoBack) {
            TextButton(onClick = { TutorialManager.previous() }) {
                Text(stringResource(R.string.tutorial_prev), color = TextMuted)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Button(
            onClick = { TutorialManager.next() },
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
            shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.tutorial_next)) }
    }
}

@Composable
private fun ActionButtons() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AmberBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.tut_hint_action),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500),
            color = AmberText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (TutorialManager.canGoBack) {
            TextButton(onClick = { TutorialManager.previous() }) {
                Text(stringResource(R.string.tutorial_prev), color = TextMuted)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        // Notausgang: Wer nicht auf das Element tippen kann/will, kommt trotzdem weiter.
        TextButton(onClick = { TutorialManager.next() }) {
            Text(stringResource(R.string.tut_skip_step), color = ForestGreen)
        }
    }
}

@Composable
private fun ChoiceButtons() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { TutorialManager.next() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
            shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.tut_choice_yes)) }
        OutlinedButton(
            onClick = { TutorialManager.jumpTo("finish") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.tut_choice_no), color = ForestGreen) }
        if (TutorialManager.canGoBack) {
            TextButton(
                onClick = { TutorialManager.previous() },
                modifier = Modifier.align(Alignment.Start)
            ) { Text(stringResource(R.string.tutorial_prev), color = TextMuted) }
        }
    }
}

// ── Willkommensbildschirm mit Sprachauswahl ───────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePanel(step: TutorialStep) {
    Box(
        Modifier
            .fillMaxSize()
            .background(PaperBeige)
            .consumeAllTouches()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎹", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(step.titleRes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(step.textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.tut_welcome_language).uppercase(),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppSettings.languages.forEach { lang ->
                            FilterChip(
                                selected = AppSettings.language == lang,
                                onClick = { AppSettings.language = lang },
                                label = {
                                    Text(
                                        if (lang == "System") stringResource(R.string.system_language) else lang
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreenBg,
                                    selectedLabelColor = ForestGreenText
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tut_welcome_language_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { TutorialManager.next() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.tut_welcome_start)) }
            TextButton(onClick = { TutorialManager.finish() }) {
                Text(stringResource(R.string.tut_welcome_skip), color = TextMuted)
            }
        }
    }
}
