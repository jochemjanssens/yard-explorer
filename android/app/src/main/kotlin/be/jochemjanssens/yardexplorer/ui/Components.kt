package be.jochemjanssens.yardexplorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.jochemjanssens.yardexplorer.ui.theme.Ink
import be.jochemjanssens.yardexplorer.ui.theme.LocalYardColors
import be.jochemjanssens.yardexplorer.ui.theme.Signal
import be.jochemjanssens.yardexplorer.ui.theme.YardType
import be.jochemjanssens.yardexplorer.ui.theme.textOn

// Building blocks of the tile-grid design: square corners, 1.5 dp ink borders and hard offset shadows.

/** A hard, offset "pixel" shadow, drawn behind the element. */
fun Modifier.hardShadow(color: Color, offset: Dp = 3.dp) = drawBehind {
  val o = offset.toPx()
  drawRect(color, topLeft = Offset(o, o), size = size)
}

/** A faint tile grid, like the website's background. */
fun Modifier.gridded(line: Color, cell: Dp = 16.dp) = drawBehind {
  val step = cell.toPx()
  var x = 0f
  while (x < size.width) { drawRect(line, Offset(x, 0f), Size(1f, size.height)); x += step }
  var y = 0f
  while (y < size.height) { drawRect(line, Offset(0f, y), Size(size.width, 1f)); y += step }
}

/** A small mono, upper-case label. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalYardColors.current.muted) =
  Text(text.uppercase(), modifier, style = YardType.label, color = color)

/** A square chip: rank numbers in their yard colour. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp, fontSize: Int = 12) = Box(
  modifier
    .defaultMinSize(size, size)
    .background(color)
    .border(1.5.dp, Ink, RectangleShape)
    .padding(horizontal = 4.dp),
  contentAlignment = Alignment.Center,
) {
  Text(text, style = YardType.number.copy(fontSize = fontSize.sp), color = textOn(color), textAlign = TextAlign.Center)
}

/** The plain number chip used from rank 21 down. */
@Composable
fun PlainChip(text: String) {
  val c = LocalYardColors.current
  Box(Modifier.defaultMinSize(26.dp, 26.dp).border(1.dp, c.line, RectangleShape), contentAlignment = Alignment.Center) {
    Text(text, style = YardType.number.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = c.muted)
  }
}

/** A square, bordered button with a hard shadow; `signal` for the main action. */
@Composable
fun TileButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, signal: Boolean = false) {
  val c = LocalYardColors.current
  Box(
    modifier
      .hardShadow(c.shade)
      .background(if (signal) Signal else c.panel)
      .border(1.5.dp, if (signal) Ink else c.ink, RectangleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Text(text.uppercase(), style = YardType.number.copy(fontSize = 13.sp, letterSpacing = 0.6.sp), color = if (signal) Ink else c.ink)
  }
}

/** The 3x3 tile logo. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
  val off = LocalYardColors.current.line
  val on = listOf(false, true, false, true, true, true, false, true, true)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
    for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
      for (col in 0 until 3) Box(Modifier.size(7.dp).background(if (on[row * 3 + col]) Signal else off))
    }
  }
}

/** The locate icon: a square with four ticks, as on the website. */
@Composable
fun LocateIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(18.dp)) {
  val w = size.width
  val s = 2.dp.toPx()
  val a = w * 0.25f
  drawRect(color, Offset(a, a), Size(w / 2, w / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(s))
  drawRect(color, Offset(w / 2 - s / 2, 0f), Size(s, w * 0.19f))
  drawRect(color, Offset(w / 2 - s / 2, w * 0.81f), Size(s, w * 0.19f))
  drawRect(color, Offset(0f, w / 2 - s / 2), Size(w * 0.19f, s))
  drawRect(color, Offset(w * 0.81f, w / 2 - s / 2), Size(w * 0.19f, s))
}
