package com.desarrollamo.storeamo.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StoreScheme = darkColorScheme(
    primary = AmoCyan,
    secondary = AmoPink,
    tertiary = AmoViolet,
    background = AmoBackground,
    surface = AmoSurface,
    onPrimary = AmoBackground,
    onSecondary = AmoText,
    onBackground = AmoText,
    onSurface = AmoText,
)

@Composable
fun StoreAmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreScheme, content = content)
}
