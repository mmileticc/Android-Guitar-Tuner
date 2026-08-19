package dev.milinko.guitartuner.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milinko.guitartuner.model.Note
import dev.milinko.guitartuner.ui.theme.LocalTunerColors

@Composable
fun NoteIndicatorBar(
    notes: List<Note>,
    activeNote: Note?,
    isTuned: Boolean
) {
    val tunerColors = LocalTunerColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        notes.forEach { note ->
            val isActive = activeNote?.name == note.name

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            drawCircle(
                                color = when {
                                    isActive && isTuned -> tunerColors.inTune
                                    isActive -> tunerColors.indicatorActive
                                    else -> tunerColors.indicatorInactive
                                },
                                radius = size.minDimension / 2
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        // filter { !it.isDigit() } izbacuje brojeve (npr. E2 -> E, F#3 -> F#)
                        text = note.name.filter { !it.isDigit() },
                        color = if (isActive) Color.White else tunerColors.neutral,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (note.name.length > 2) 12.sp else 14.sp
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(4.dp)
                            .drawBehind { drawCircle(color = tunerColors.indicatorActive) }
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
