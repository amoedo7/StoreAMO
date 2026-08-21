package com.desarrollamo.storeamo.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StoreAmoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val StoreAmoTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black),
)

@Composable
fun StoreAmoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }
    var styleKey by remember { mutableStateOf(prefs.getString("theme_style", StoreThemeStyle.AESTHETIC.key)) }
    val systemDark = isSystemInDarkTheme()
    val style = StoreThemeStyle.fromKey(styleKey)
    val palette = paletteFor(style, systemDark)

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_style") styleKey = prefs.getString("theme_style", StoreThemeStyle.AESTHETIC.key)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    SideEffect {
        if (AmoPaletteState.current != palette) AmoPaletteState.current = palette
    }

    // Amber is the global focus color in 0.4.3.73. Violet stays inside the
    // StoreAMO identity instead of becoming a competing interaction color.
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.cyan,
            secondary = palette.pink,
            tertiary = palette.amber,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surface2,
            outline = palette.line,
            onPrimary = palette.background,
            onSecondary = palette.background,
            onTertiary = palette.background,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted,
        )
    } else {
        lightColorScheme(
            primary = palette.cyan,
            secondary = palette.pink,
            tertiary = palette.amber,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surface2,
            outline = palette.line,
            onPrimary = ColorContrast.onAccent(palette.cyan),
            onSecondary = ColorContrast.onAccent(palette.pink),
            onTertiary = ColorContrast.onAccent(palette.amber),
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = StoreAmoTypography,
        shapes = StoreAmoShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.background,
            contentColor = palette.text,
        ) {
            content()
        }
    }
}

private object ColorContrast {
    fun onAccent(color: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
        val luminance = color.luminance()
        return if (luminance > .45f) androidx.compose.ui.graphics.Color(0xFF07111F) else androidx.compose.ui.graphics.Color.White
    }
}
