package dev.milinko.guitartuner.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milinko.guitartuner.model.Tuning
import dev.milinko.guitartuner.model.GuitarTunings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningSelector(
    currentTuning: Tuning,
    onTuningChange: (Tuning) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp) // Malo više prostora do Hz
    ) {
        OutlinedTextField(
            value = currentTuning.name,
            onValueChange = {},
            readOnly = true,
            // 1. Manji font i diskretnija labela
            label = { Text("Štim", fontSize = 12.sp) },
            // 2. Dodajemo ikonicu note levo da vizuelno razbijemo tekst
            leadingIcon = {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // 3. Zaobljeni uglovi (Shape) prave veliku razliku u estetici
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = Color.Gray
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        // 4. Stilizovanje samog menija (Popup-a)
        MaterialTheme(
            // Ovde možemo lokalno da "prevarimo" uglove menija
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
        ) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                GuitarTunings.ALL_TUNINGS.forEach { tuning ->
                    val isSelected = tuning.name == currentTuning.name
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tuning.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onTuningChange(tuning)
                            expanded = false
                        },
                        // Dodajemo suptilan indikator pored selektovanog štima u listi
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}
