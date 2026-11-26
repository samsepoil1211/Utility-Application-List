package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dusk Instrument — Dark palette (default)
val DuskBackground = Color(0xFF13112A)
val DuskPanel = Color(0xFF1D1A3A)
val DuskPanelElevated = Color(0xFF252147)
val DuskLine = Color(0xFF2E2A55)
val DuskText = Color(0xFFF1EEFF)
val DuskMuted = Color(0xFFA9A3D1) // >5.8:1 on #13112A, >4.8:1 on #1D1A3A
val DuskAmber = Color(0xFFFFB86B)
val DuskOnAmber = Color(0xFF19132E)
val DuskLilac = Color(0xFFB9A7FF)
val DuskMint = Color(0xFF7FE3C0)
val DuskCoral = Color(0xFFFF8F8F)

// Dusk Instrument — Matching Light palette
val LightBackground = Color(0xFFF6F4FB)
val LightPanel = Color(0xFFFFFFFF)
val LightPanelElevated = Color(0xFFEEEAF7)
val LightLine = Color(0xFFD9D3EA)
val LightText = Color(0xFF17142E)
val LightMuted = Color(0xFF56507A) // >6.5:1 on #FFFFFF & #F6F4FB
val LightAmber = Color(0xFFC47019)
val LightDialAmber = Color(0xFFD8842B)
val LightOnAmber = Color(0xFFFFFFFF)
val LightLilac = Color(0xFF634EC4)
val LightMint = Color(0xFF1B8666)
val LightCoral = Color(0xFFC94444)

@Immutable
data class InstrumentColors(
    val background: Color,
    val panel: Color,
    val panelElevated: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val amber: Color,
    val dialAmber: Color,
    val onAmber: Color,
    val lilac: Color,
    val mint: Color,
    val coral: Color,
    val isDark: Boolean
)

val DarkInstrumentColors = InstrumentColors(
    background = DuskBackground,
    panel = DuskPanel,
    panelElevated = DuskPanelElevated,
    line = DuskLine,
    text = DuskText,
    muted = DuskMuted,
    amber = DuskAmber,
    dialAmber = DuskAmber,
    onAmber = DuskOnAmber,
    lilac = DuskLilac,
    mint = DuskMint,
    coral = DuskCoral,
    isDark = true
)

val LightInstrumentColors = InstrumentColors(
    background = LightBackground,
    panel = LightPanel,
    panelElevated = LightPanelElevated,
    line = LightLine,
    text = LightText,
    muted = LightMuted,
    amber = LightAmber,
    dialAmber = LightDialAmber,
    onAmber = LightOnAmber,
    lilac = LightLilac,
    mint = LightMint,
    coral = LightCoral,
    isDark = false
)

val LocalInstrumentColors = staticCompositionLocalOf { DarkInstrumentColors }
