package io.github.jochemjanssens.yardexplorer.model

import org.junit.Assert.assertEquals
import org.junit.Test

// The same made-up exports as the website's tests (test/squadrats.test.js), never a real one:
// those reveal where someone rides.
class SquadratsTest {
  private val z = 17
  private val x0 = 67200
  private val y0 = 43900

  /** Draw a grid as tiles: '#' = visited. Row 0 is the top (smallest y). */
  private fun grid(vararg rows: String, x: Int = x0, y: Int = y0) = rows.flatMapIndexed { dy, row ->
    row.mapIndexedNotNull { dx, c -> if (c == '#') Tile(x + dx, y + dy) else null }
  }

  /** One polygon per tile; the even-odd rasteriser turns shared edges back into solid areas. */
  private fun polygons(tiles: List<Tile>, zoom: Int) = tiles.joinToString("") { (x, y) ->
    val c = listOf(x to y, x + 1 to y, x + 1 to y + 1, x to y + 1, x to y).joinToString(" ") { (tx, ty) ->
      "${Squadrats.tileToLon(tx.toDouble(), zoom)},${Squadrats.tileToLat(ty.toDouble(), zoom)}"
    }
    "<Polygon><outerBoundaryIs><LinearRing><coordinates>$c</coordinates></LinearRing></outerBoundaryIs></Polygon>"
  }

  private fun kml(vararg layers: Triple<String, List<Tile>, Int>) =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>" +
      layers.joinToString("") { (name, tiles, zoom) ->
        "<Placemark><name>$name</name><ExtendedData><Data name=\"size\"><value>${tiles.size}</value></Data></ExtendedData>" +
          "<MultiGeometry>${polygons(tiles, zoom)}</MultiGeometry></Placemark>"
      } + "</Document></kml>"

  @Test
  fun `rasterise reads back exactly the tiles that were drawn`() {
    val tiles = grid("##.#", "#..#", "####")
    val analysis = Squadrats.analyse(kml(Triple("squadratinhos", tiles, z)))
    assertEquals(tiles.toSet(), analysis.layers.getValue(Layer.SQUADRATINHOS).visited)
  }

  @Test
  fun `findYards keeps tiles with four visited neighbours, grouped and biggest first`() {
    val visited = grid(
      "#####.###",
      "#####.###",
      "#####.###",
      "#####....",
    ).toSet()
    val yards = Squadrats.findYards(visited)
    assertEquals(listOf(6, 1), yards.map { it.size })
    assertEquals(grid(".....", ".###.", ".###.").toSet(), yards[0].toSet())
    assertEquals(listOf(Tile(x0 + 7, y0 + 1)), yards[1])
  }

  @Test
  fun `outline returns an outer ring and a hole, without corners on straight runs`() {
    val rings = Squadrats.outline(grid("###", "#.#", "###", x = 0, y = 0))
    assertEquals(2, rings.size)
    assertEquals(listOf(4, 4), rings.map { it.size })
    assertEquals(setOf(listOf(0, 0), listOf(3, 0), listOf(3, 3), listOf(0, 3)), rings[0].map { it.toList() }.toSet())
  }

  @Test
  fun `polygons attach each hole to the outer ring around it`() {
    // A ring with a hole, and a separate island to its right.
    val polys = Squadrats.polygons(grid("###..#", "#.#...", "###...", x = 0, y = 0))
    assertEquals(2, polys.size)
    val ring = polys.single { it.size == 2 }
    assertEquals(setOf(listOf(1, 1), listOf(2, 1), listOf(2, 2), listOf(1, 2)), ring[1].map { it.toList() }.toSet())
    assertEquals(1, polys.single { it.size == 1 }.size)
  }

  @Test
  fun `analyse matches the reported sizes for both layers`() {
    val tiles14 = grid("####", "####", "####", x = 8390, y = 5470)
    val tiles17 = grid("#####", "#####", "#####", "#####")
    val d = Squadrats.analyse(kml(Triple("squadrats", tiles14, 14), Triple("squadratinhos", tiles17, z)))
    assertEquals(d.reported["squadrats"], d.layers.getValue(Layer.SQUADRATS).visited.size)
    assertEquals(d.reported["squadratinhos"], d.layers.getValue(Layer.SQUADRATINHOS).visited.size)
    assertEquals(listOf(6), d.layers.getValue(Layer.SQUADRATINHOS).yards.map { it.size })
  }
}
