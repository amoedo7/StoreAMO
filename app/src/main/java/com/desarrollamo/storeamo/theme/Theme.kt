package com.desarrollamo.storeamo.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val StoreScheme = darkColorScheme(
    primary = AmoCyan,
    secondary = AmoPink,
    tertiary = AmoViolet,
    background = AmoBackground,
    surface = AmoSurface,
    surfaceVariant = AmoSurface2,
    outline = AmoLine,
    onPrimary = AmoBackground,
    onSecondary = AmoBackground,
    onTertiary = AmoBackground,
    onBackground = AmoText,
    onSurface = AmoText,
    onSurfaceVariant = AmoMuted,
)

@Composable
fun StoreAmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreScheme) {
        // MaterialTheme no establece por sí solo un LocalContentColor global.
        // La Surface raíz evita texto negro sobre el fondo oscuro y fija el
        // contraste de toda la tienda aunque un Text no declare color explícito.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = StoreScheme.background,
            contentColor = StoreScheme.onBackground,
        ) {
            content()
        }
    }
}
