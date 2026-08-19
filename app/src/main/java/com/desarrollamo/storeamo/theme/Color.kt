package com.desarrollamo.storeamo.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class StoreThemeStyle(val key: String, val label: String) {
    SYSTEM("system", "Sistema"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    AESTHETIC("aesthetic", "Estetic"),
    MONOCHROME("monochrome", "Blanco y negro"),
    OLED("oled", "OLED"),
    OCEAN("ocean", "Ocean"),
    SUNSET("sunset", "Sunset");

    companion object {
        fun fromKey(value: String?): StoreThemeStyle = entries.firstOrNull { it.key == value } ?: AESTHETIC
    }
}

data class AmoPalette(
    val background: Color,
    val background2: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val cyan: Color,
    val pink: Color,
    val violet: Color,
    val green: Color,
    val amber: Color,
    val isDark: Boolean,
)

private val DarkPalette = AmoPalette(
    background = Color(0xFF071321), background2 = Color(0xFF0B1B2D),
    surface = Color(0xFF10243A), surface2 = Color(0xFF17304B), line = Color(0xFF294B6C),
    text = Color(0xFFF7FAFD), muted = Color(0xFFA5B7C8), cyan = Color(0xFF67D2FF),
    pink = Color(0xFFF16AB5), violet = Color(0xFF8C74FF), green = Color(0xFF78E6AD),
    amber = Color(0xFFFFC56E), isDark = true,
)

private val LightPalette = AmoPalette(
    background = Color(0xFFF7FAFD), background2 = Color(0xFFEDF4F8),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE7F0F6), line = Color(0xFFC6D7E3),
    text = Color(0xFF10202E), muted = Color(0xFF5F7586), cyan = Color(0xFF087EBC),
    pink = Color(0xFFC93F8A), violet = Color(0xFF6553D7), green = Color(0xFF167C52),
    amber = Color(0xFF9B6500), isDark = false,
)

private val AestheticPalette = AmoPalette(
    background = Color(0xFF10182A), background2 = Color(0xFF17243A),
    surface = Color(0xFF1E2D45), surface2 = Color(0xFF283B57), line = Color(0xFF3D5572),
    text = Color(0xFFF9FBFF), muted = Color(0xFFB5C3D2), cyan = Color(0xFF79DBFF),
    pink = Color(0xFFFF77BE), violet = Color(0xFFA18BFF), green = Color(0xFF88ECC0),
    amber = Color(0xFFFFCC76), isDark = true,
)

private val MonochromePalette = AmoPalette(
    background = Color(0xFF111111), background2 = Color(0xFF191919),
    surface = Color(0xFF222222), surface2 = Color(0xFF2C2C2C), line = Color(0xFF4A4A4A),
    text = Color(0xFFF5F5F5), muted = Color(0xFFB0B0B0), cyan = Color(0xFFE8E8E8),
    pink = Color(0xFFFFFFFF), violet = Color(0xFFD0D0D0), green = Color(0xFFE4E4E4),
    amber = Color(0xFFC7C7C7), isDark = true,
)

private val OledPalette = AmoPalette(
    background = Color.Black, background2 = Color(0xFF050505),
    surface = Color(0xFF0D0D0D), surface2 = Color(0xFF151515), line = Color(0xFF303030),
    text = Color.White, muted = Color(0xFFB1B1B1), cyan = Color(0xFF5ED7FF),
    pink = Color(0xFFFF63B6), violet = Color(0xFF947BFF), green = Color(0xFF70E6AC),
    amber = Color(0xFFFFC568), isDark = true,
)

private val OceanPalette = AmoPalette(
    background = Color(0xFF061C28), background2 = Color(0xFF082A38),
    surface = Color(0xFF0D3546), surface2 = Color(0xFF12475A), line = Color(0xFF21677D),
    text = Color(0xFFF1FCFF), muted = Color(0xFFA4C6D0), cyan = Color(0xFF63DEFF),
    pink = Color(0xFF7DBBFF), violet = Color(0xFF8F94FF), green = Color(0xFF62E6C1),
    amber = Color(0xFFFFD47B), isDark = true,
)

private val SunsetPalette = AmoPalette(
    background = Color(0xFF24151D), background2 = Color(0xFF33202B),
    surface = Color(0xFF432A37), surface2 = Color(0xFF553547), line = Color(0xFF775064),
    text = Color(0xFFFFF8FB), muted = Color(0xFFD8B7C5), cyan = Color(0xFFFFBE87),
    pink = Color(0xFFFF79B7), violet = Color(0xFFC99CFF), green = Color(0xFF9BE6B8),
    amber = Color(0xFFFFC678), isDark = true,
)

internal fun paletteFor(style: StoreThemeStyle, systemDark: Boolean): AmoPalette = when (style) {
    StoreThemeStyle.SYSTEM -> if (systemDark) DarkPalette else LightPalette
    StoreThemeStyle.DARK -> DarkPalette
    StoreThemeStyle.LIGHT -> LightPalette
    StoreThemeStyle.AESTHETIC -> AestheticPalette
    StoreThemeStyle.MONOCHROME -> MonochromePalette
    StoreThemeStyle.OLED -> OledPalette
    StoreThemeStyle.OCEAN -> OceanPalette
    StoreThemeStyle.SUNSET -> SunsetPalette
}

internal object AmoPaletteState {
    var current by mutableStateOf(AestheticPalette)
}

val AmoBackground: Color get() = AmoPaletteState.current.background
val AmoBackground2: Color get() = AmoPaletteState.current.background2
val AmoSurface: Color get() = AmoPaletteState.current.surface
val AmoSurface2: Color get() = AmoPaletteState.current.surface2
val AmoLine: Color get() = AmoPaletteState.current.line
val AmoText: Color get() = AmoPaletteState.current.text
val AmoMuted: Color get() = AmoPaletteState.current.muted
val AmoCyan: Color get() = AmoPaletteState.current.cyan
val AmoPink: Color get() = AmoPaletteState.current.pink
val AmoViolet: Color get() = AmoPaletteState.current.violet
val AmoGreen: Color get() = AmoPaletteState.current.green
val AmoAmber: Color get() = AmoPaletteState.current.amber
