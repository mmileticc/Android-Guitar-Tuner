package dev.milinko.guitartuner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Semantičke boje za stanje štimovanja - da UI ekrani (TunerScreen, TunerScale,
 * NoteIndicatorBar) ne hardkoduju Color.Green/Color.Gray/itd. direktno, nego čitaju
 * iz teme. Ovo drži vizuelni identitet konzistentan i lako promenljiv na jednom mestu.
 */
data class TunerColors(
    val inTune: Color,
    val flat: Color,              // treba zategnuti (frekvencija ispod cilja)
    val sharp: Color,              // treba opustiti (frekvencija iznad cilja)
    val neutral: Color,
    val indicatorActive: Color,
    val indicatorInactive: Color,
    val error: Color
)

private val DarkTunerColors = TunerColors(
    inTune = BrandGreen,
    flat = BrandBlue,
    sharp = BrandAmber,
    neutral = NeutralGrayDark,
    indicatorActive = BrandBlue,
    indicatorInactive = NeutralGrayDark.copy(alpha = 0.3f),
    error = BrandRed
)

private val LightTunerColors = TunerColors(
    inTune = BrandGreenDark,
    flat = BrandBlue,
    sharp = BrandAmber,
    neutral = NeutralGrayLight,
    indicatorActive = BrandBlue,
    indicatorInactive = NeutralGrayLight.copy(alpha = 0.3f),
    error = BrandRed
)

val LocalTunerColors = staticCompositionLocalOf { DarkTunerColors }

private val DarkColorScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    secondary = BrandBlue,
    tertiary = BrandAmber,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = BrandRed
)

private val LightColorScheme = lightColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color.White,
    secondary = BrandBlue,
    tertiary = BrandAmber,
    background = LightBackground,
    surface = LightSurface,
    error = BrandRed
)

@Composable
fun GuitarTunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Podrazumevano ISKLJUČENO: hoćemo dosledan, prepoznatljiv izgled aplikacije,
    // ne boje izvučene iz pozadine korisnikovog telefona (Material You).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val tunerColors = if (darkTheme) DarkTunerColors else LightTunerColors

    CompositionLocalProvider(LocalTunerColors provides tunerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
