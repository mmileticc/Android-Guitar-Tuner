package dev.milinko.guitartuner.ui.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import dev.milinko.guitartuner.audio.TunerConfig
import dev.milinko.guitartuner.ui.theme.LocalTunerColors
import kotlin.math.abs

@Composable
fun TunerScale(diffCents: Float) {

    val animatedDiff by animateFloatAsState(
        targetValue = diffCents,
        animationSpec = tween(90), // brz ali stabilan
        label = "diff"
    )

    val tunerColors = LocalTunerColors.current
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val centerTickColor = tunerColors.neutral

    val needleColor = when {
        abs(diffCents) < TunerConfig.IN_TUNE_THRESHOLD_CENTS -> tunerColors.inTune
        diffCents > 0 -> tunerColors.sharp // previsoko -> opuštaj
        else -> tunerColors.flat            // prenisko -> zateži
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 20.dp)
    ) {
        val center = size.width / 2
        val rangeCents = 50f
        val pixelsPerCent = size.width / (rangeCents * 2)

        // crtice
        for (i in -50..50 step 10) {
            val x = center + (i * pixelsPerCent)
            val lineHeight = if (i == 0) 40.dp.toPx() else 20.dp.toPx()
            val color = if (i == 0) centerTickColor else tickColor

            drawLine(
                color = color,
                start = Offset(x, size.height / 2 - lineHeight / 2),
                end = Offset(x, size.height / 2 + lineHeight / 2),
                strokeWidth = if (i == 0) 3.dp.toPx() else 1.dp.toPx()
            )
        }

        // igla
        val pointerX = center + (animatedDiff.coerceIn(-50f, 50f) * pixelsPerCent)

        drawLine(
            color = needleColor,
            start = Offset(pointerX, 0f),
            end = Offset(pointerX, size.height),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
