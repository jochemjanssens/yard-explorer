package io.github.jochemjanssens.yardexplorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The website's "tile grid" design: one signal colour for the interface, square corners, hard shadows.

/** Map ink: borders and casings drawn on the (always light) OpenStreetMap tiles. */
val Ink = Color(0xFF121316)
val Signal = Color(0xFF00E676)
val Steel = Color(0xFF5B6270)

/** The top 20 each get their own colour; neighbouring ranks contrast most. Same order as the website. */
val RankColors = listOf(
  0xFFFFD400, 0xFFE4002B, 0xFF0050FF, 0xFFA21FE0, 0xFF00A3A3, 0xFFFF7A00, 0xFF00B4FF, 0xFF8D5A2B, 0xFF7A1F3D, 0xFF1D2B7A,
  0xFFF032E6, 0xFF7D7D00, 0xFFFF8F7A, 0xFF4B1D9E, 0xFFC9A227, 0xFF2E8B57, 0xFFC79BFF, 0xFF4A6D8C, 0xFFB5651D, 0xFFC2185B,
).map(::Color)

fun rankColor(rank: Int): Color = RankColors.getOrElse(rank) { Steel }

/** Dark or light text, whichever reads better on a chip of this colour. */
fun textOn(c: Color): Color = if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.5f) Ink else Color.White

fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Immutable
data class YardColors(
  val bg: Color,
  val panel: Color,
  val hover: Color,
  val ink: Color,
  val muted: Color,
  val line: Color,
  val shade: Color,
  val signalText: Color,
)

private val Light = YardColors(
  bg = Color(0xFFF4F4F0), panel = Color(0xFFFBFBF8), hover = Color(0xFFECECE6), ink = Ink,
  muted = Color(0xFF6A6C71), line = Color(0xFFD8D8D2), shade = Ink, signalText = Color(0xFF00813B),
)
private val Dark = YardColors(
  bg = Color(0xFF0E0F11), panel = Color(0xFF141518), hover = Color(0xFF1C1E22), ink = Color(0xFFEFEFE9),
  muted = Color(0xFF989AA0), line = Color(0xFF2A2C31), shade = Color.Black, signalText = Color(0xFF2BF07A),
)

val LocalYardColors = staticCompositionLocalOf { Light }

object YardType {
  val mono = FontFamily.Monospace
  val label = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.08.em)
  val number = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold)
  val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp)
}

@Composable
fun YardExplorerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val c = if (dark) Dark else Light
  val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = Signal, onPrimary = Ink, background = c.bg, surface = c.panel, onBackground = c.ink, onSurface = c.ink,
  )
  CompositionLocalProvider(LocalYardColors provides c) {
    MaterialTheme(colorScheme = scheme, content = content)
  }
}
