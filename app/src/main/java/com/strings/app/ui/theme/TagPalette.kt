package com.strings.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.strings.app.domain.model.Tag

data class CardColors(
    val accent: Color,
    val container: Color
)

data class PaletteEntry(
    val lightContainer: Color,
    val darkContainer: Color
)

val AppPalette: List<PaletteEntry> = listOf(
    PaletteEntry(Color(0xFFF5ECE3), Color(0xFF6E5B51)), // 0 Almond
    PaletteEntry(Color(0xFFFCECEF), Color(0xFF7A535A)), // 1 Rose
    PaletteEntry(Color(0xFFEBF2EB), Color(0xFF536956)), // 2 Sage
    PaletteEntry(Color(0xFFFFF6E5), Color(0xFF73634B)), // 3 Vanilla
    PaletteEntry(Color(0xFFEFEBF4), Color(0xFF625573)), // 4 Lavender
    PaletteEntry(Color(0xFFFDF1E6), Color(0xFF7D5945)), // 5 Peach
    PaletteEntry(Color(0xFFF1E8D9), Color(0xFF6B5F4C)), // 6 Sand
    PaletteEntry(Color(0xFFE9F1F3), Color(0xFF4B616B)), // 7 Slate
    PaletteEntry(Color(0xFFF9ECE6), Color(0xFF7A564A)), // 8 Clay
    PaletteEntry(Color(0xFFFAF6EE), Color(0xFF635F55))  // 9 Ivory
)

object TransactionColors {
    val creditLight = Color(0xFF2E7D50)
    val creditDark = Color(0xFF81C995)
    val debitLight = Color(0xFFC62828)
    val debitDark = Color(0xFFEF9A9A)
}

object PaletteIndex {
    const val ALMOND = 0
    const val ROSE = 1
    const val SAGE = 2
    const val VANILLA = 3
    const val LAVENDER = 4
    const val PEACH = 5
    const val SAND = 6
    const val SLATE = 7
    const val CLAY = 8
    const val IVORY = 9
}

@Composable
fun resolveCardColors(index: Int): CardColors {
    val entry: PaletteEntry = AppPalette[Math.floorMod(index, AppPalette.size)]
    val onLight = Color(0xFF1C1B1F)
    val onDark = Color(0xFFFFFBFE)
    return if (LocalAppDarkTheme.current) {
        CardColors(accent = onDark, container = entry.darkContainer)
    } else {
        CardColors(accent = onLight, container = entry.lightContainer)
    }
}

fun tagPaletteIndex(tag: Tag): Int {
    val seed: Int = if (tag.id > 0L) tag.id.toInt() else tag.name.hashCode()
    return Math.floorMod(seed, AppPalette.size)
}

@Composable
fun rememberTagColors(tag: Tag): CardColors {
    return resolveCardColors(tagPaletteIndex(tag))
}
