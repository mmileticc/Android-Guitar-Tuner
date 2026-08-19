package dev.milinko.guitartuner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milinko.guitartuner.audio.TunerConfig
import dev.milinko.guitartuner.ui.composables.NoteIndicatorBar
import dev.milinko.guitartuner.ui.composables.TunerScale
import dev.milinko.guitartuner.ui.composables.TuningSelector
import dev.milinko.guitartuner.ui.theme.LocalTunerColors
import dev.milinko.guitartuner.viewmodel.TunerViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(viewModel: TunerViewModel) {
    val status by viewModel.tuningStatus.collectAsState()
    val volume by viewModel.volumeFlow.collectAsState()
    val currentTuning by viewModel.selectedTuning.collectAsState()
    val referencePitch by viewModel.referencePitch.collectAsState()
    val micError by viewModel.micError.collectAsState()

    val tunerColors = LocalTunerColors.current
    val volumeAnim by animateFloatAsState(targetValue = if (status.frequency > 0) volume else 0f, label = "vol")

    val isTuned = status.frequency > 0 && abs(status.diffCents) < TunerConfig.IN_TUNE_THRESHOLD_CENTS

    // Haptic feedback samo na PRELAZ u "u štimu" (edge-triggered), ne kontinuirano
    // dok igla ostaje u zoni - to bi bilo naporno umesto korisno.
    val haptic = LocalHapticFeedback.current
    var wasTuned by remember { mutableStateOf(false) }
    LaunchedEffect(isTuned) {
        if (isTuned && !wasTuned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasTuned = isTuned
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        micError?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tunerColors.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = micError!!,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = tunerColors.error
                )
                TextButton(onClick = { viewModel.startListening() }) {
                    Text("Pokušaj ponovo")
                }
            }
        }

        TuningSelector(
            currentTuning = currentTuning,
            onTuningChange = { viewModel.changeTuning(it) }
        )

        // Kalibracija referentne frekvencije (A4) - podesivo 438-445Hz
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            IconButton(
                onClick = { viewModel.setReferencePitch(referencePitch - 1f) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Smanji A4", tint = tunerColors.neutral)
            }
            Text(
                text = "A4 = ${referencePitch.toInt()} Hz",
                fontSize = 12.sp,
                color = tunerColors.neutral,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = { viewModel.setReferencePitch(referencePitch + 1f) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Povećaj A4", tint = tunerColors.neutral)
            }
        }

        // 1. Gornji info - Fiksna visina
        Box(
            modifier = Modifier.height(80.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (status.frequency > 0) "${"%.1f".format(status.frequency)} Hz" else "--- Hz",
                    fontSize = 18.sp,
                    color = tunerColors.neutral
                )
                Text(
                    text = if (status.closestNote != null && status.frequency > 0)
                        "Cilj: ${status.closestNote!!.frequency} Hz" else "",
                    fontSize = 12.sp,
                    color = tunerColors.neutral.copy(alpha = 0.6f)
                )
            }
        }

        // 2. Centralni deo
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer(scaleX = 1f + volumeAnim * 5, scaleY = 1f + volumeAnim * 5)
                    .drawBehind {
                        val glowColor = when {
                            isTuned -> tunerColors.inTune
                            status.frequency > 0 && status.diffCents > 0 -> tunerColors.sharp
                            status.frequency > 0 -> tunerColors.flat
                            else -> tunerColors.neutral
                        }
                        drawCircle(
                            color = glowColor.copy(alpha = if (isTuned) 0.12f else 0.06f),
                            radius = size.minDimension / 2
                        )
                    }
            )

            // Stvarno detektovana nota (hromatski, nezavisno od izabranog štima) -
            // ako je žica jako raštimovana, ovde ćeš videti pravu notu koju sviraš,
            // ne nasilno mapiranu na najbližu ciljnu žicu.
            Text(
                text = status.detectedNote?.fullName ?: status.closestNote?.name ?: "--",
                fontSize = 100.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    status.frequency <= 0 -> tunerColors.neutral.copy(alpha = 0.5f)
                    isTuned -> tunerColors.inTune
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        NoteIndicatorBar(
            notes = currentTuning.notes,
            activeNote = if (status.frequency > 0) status.closestNote else null,
            isTuned = isTuned
        )

        // 3. Donji deo
        Column(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TunerScale(diffCents = status.diffCents)

            Spacer(modifier = Modifier.height(20.dp))

            // diffCents > 0 -> frekvencija VEĆA od cilja -> OPUŠTAJ
            // diffCents < 0 -> frekvencija MANJA od cilja -> ZATEŽI
            Text(
                text = when {
                    status.frequency <= 0 -> "SVIRAJ ŽICU"
                    status.diffCents > TunerConfig.IN_TUNE_THRESHOLD_CENTS -> "OPUŠTAJ ↓"
                    status.diffCents < -TunerConfig.IN_TUNE_THRESHOLD_CENTS -> "ZATEŽI ↑"
                    else -> "IDEALNO"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTuned) tunerColors.inTune else tunerColors.neutral
            )

            Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                if (status.frequency > 0) {
                    Text(
                        text = "${if (status.diffCents > 0) "+" else ""}${status.diffCents.toInt()} cents",
                        fontSize = 14.sp,
                        color = tunerColors.neutral
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
