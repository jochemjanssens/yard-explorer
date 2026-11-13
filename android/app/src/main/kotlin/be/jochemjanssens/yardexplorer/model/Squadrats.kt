package be.jochemjanssens.yardexplorer.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

// Kotlin port of the website's squadrats.js: parses a Squadrats KML export and finds every Yard / Yardinho.
// Iteration orders match the JavaScript version, so both rank equal-sized yards the same way.

/** A map tile by its x/y index at some zoom level. */
data class Tile(val x: Int, val y: Int)

enum class Layer(val zoom: Int, val placemark: String, val yardName: String, val tileName: String) {
  SQUADRATINHOS(17, "squadratinhos", "Yardinho", "squadratinho"),
  SQUADRATS(14, "squadrats", "Yard", "squadrat"),
}

class LayerData(val layer: Layer, val visited: Set<Tile>, val yards: List<List<Tile>>)

/** Everything in one export: tiles and yards per layer, and the sizes Squadrats itself reports. */
class Analysis(val layers: Map<Layer, LayerData>, val reported: Map<String, Int>)

/** A ring of tile corners; [x, y] pairs. */
typealias Ring = List<IntArray>

/** An outer ring followed by its holes. */
typealias PolygonRings = List<Ring>

object Squadrats {
  private fun tiles(z: Int) = (1 shl z).toDouble()

  fun lonToTileX(lon: Double, z: Int) = (lon + 180) / 360 * tiles(z)

  fun latToTileY(lat: Double, z: Int): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * tiles(z)
  }

  fun tileToLon(x: Double, z: Int) = x / tiles(z) * 360 - 180

  fun tileToLat(y: Double, z: Int) = Math.toDegrees(atan(sinh(PI - 2 * PI * y / tiles(z))))

  class Placemark(val size: Int?, val rings: List<List<DoubleArray>>) // each point is [lon, lat]

  // Plain string scanning rather than regexes: Android's regex engine is very slow on lazy patterns over
  // a ~350 KB export (seconds instead of milliseconds).
  private fun between(text: String, open: String, close: String, from: Int = 0, until: Int = text.length): String? {
    val a = text.indexOf(open, from).takeIf { it in 0 until until } ?: return null
    val b = text.indexOf(close, a + open.length).takeIf { it in 0..until } ?: return null
    return text.substring(a + open.length, b)
  }

  private fun parseCoordinates(text: String): List<DoubleArray> =
    text.split(' ', '\n', '\r', '\t').filter(String::isNotEmpty).map { p ->
      val c = p.indexOf(',')
      val c2 = p.indexOf(',', c + 1).let { if (it < 0) p.length else it }
      doubleArrayOf(p.substring(0, c).toDouble(), p.substring(c + 1, c2).toDouble())
    }

  fun parsePlacemarks(kml: String): Map<String, Placemark> {
    val out = LinkedHashMap<String, Placemark>()
    var i = 0
    while (true) {
      val start = kml.indexOf("<Placemark>", i).takeIf { it >= 0 } ?: break
      val end = kml.indexOf("</Placemark>", start).takeIf { it >= 0 } ?: break
      i = end + "</Placemark>".length
      val name = between(kml, "<name>", "</name>", start, end) ?: continue
      val size = between(kml, "<Data name=\"size\"><value>", "</value>", start, end)?.trim()?.toIntOrNull()
      val rings = mutableListOf<List<DoubleArray>>()
      var j = start
      while (true) {
        val cs = kml.indexOf("<coordinates>", j).takeIf { it in 0 until end } ?: break
        val ce = kml.indexOf("</coordinates>", cs)
        rings.add(parseCoordinates(kml.substring(cs + "<coordinates>".length, ce)))
        j = ce + "</coordinates>".length
      }
      out[name] = Placemark(size, rings)
    }
    return out
  }

  /**
   * Turns polygon rings (outer + holes) into tiles with the even-odd rule. Squadrats polygons are unions
   * of tiles, so every vertex sits on a tile corner.
   */
  fun rasterise(rings: List<List<DoubleArray>>, z: Int): Set<Tile> {
    val edgesByRow = LinkedHashMap<Int, MutableList<Int>>()
    for (ring in rings) {
      val pts = ring.map { (lon, lat) -> intArrayOf(lonToTileX(lon, z).roundToInt(), latToTileY(lat, z).roundToInt()) }
      for (i in 0 until pts.size - 1) {
        val (x1, y1) = pts[i]
        val (x2, y2) = pts[i + 1]
        if (x1 != x2 || y1 == y2) continue // only vertical edges cross a row
        for (y in min(y1, y2) until max(y1, y2)) edgesByRow.getOrPut(y) { mutableListOf() }.add(x1)
      }
    }
    val tiles = LinkedHashSet<Tile>()
    for ((y, xs) in edgesByRow) {
      xs.sort()
      var i = 0
      while (i + 1 < xs.size) {
        for (x in xs[i] until xs[i + 1]) tiles.add(Tile(x, y))
        i += 2
      }
    }
    return tiles
  }

  private fun neighbours(t: Tile) = listOf(Tile(t.x + 1, t.y), Tile(t.x - 1, t.y), Tile(t.x, t.y + 1), Tile(t.x, t.y - 1))

  /** A yard tile is a visited tile whose four neighbours are all visited. Yards are the 4-connected clusters, biggest first. */
  fun findYards(visited: Set<Tile>): List<List<Tile>> {
    val eligible = LinkedHashSet<Tile>()
    for (t in visited) if (neighbours(t).all(visited::contains)) eligible.add(t)
    val seen = HashSet<Tile>()
    val yards = mutableListOf<List<Tile>>()
    for (start in eligible) {
      if (!seen.add(start)) continue
      val tiles = mutableListOf<Tile>()
      val stack = ArrayDeque(listOf(start))
      while (stack.isNotEmpty()) {
        val t = stack.removeLast()
        tiles.add(t)
        for (n in neighbours(t)) if (n in eligible && seen.add(n)) stack.addLast(n)
      }
      yards.add(tiles)
    }
    return yards.sortedByDescending { it.size } // stable, like Array.prototype.sort
  }

  /**
   * Outline rings around a set of tiles, in tile-corner coordinates. Boundary edges run clockwise around
   * each tile (y grows downwards), so outer rings are clockwise and holes counter-clockwise.
   */
  fun outline(tiles: Collection<Tile>): List<Ring> {
    val set = tiles as? Set<Tile> ?: tiles.toHashSet()
    val next = LinkedHashMap<Long, MutableList<IntArray>>()
    fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    fun edge(x1: Int, y1: Int, x2: Int, y2: Int) = next.getOrPut(key(x1, y1)) { mutableListOf() }.add(intArrayOf(x2, y2))
    for (t in set) {
      val (x, y) = t
      if (Tile(x, y - 1) !in set) edge(x, y, x + 1, y)
      if (Tile(x + 1, y) !in set) edge(x + 1, y, x + 1, y + 1)
      if (Tile(x, y + 1) !in set) edge(x + 1, y + 1, x, y + 1)
      if (Tile(x - 1, y) !in set) edge(x, y + 1, x, y)
    }
    val rings = mutableListOf<Ring>()
    for ((startKey, outs) in next) {
      while (outs.isNotEmpty()) {
        val start = intArrayOf((startKey shr 32).toInt(), startKey.toInt())
        val ring = mutableListOf(start)
        var cur = outs.removeAt(outs.size - 1)
        while (key(cur[0], cur[1]) != startKey) {
          ring.add(cur)
          val outsHere = next.getValue(key(cur[0], cur[1]))
          cur = outsHere.removeAt(outsHere.size - 1)
        }
        // Drop corners on straight runs so shapes stay light.
        rings.add(ring.filterIndexed { i, p ->
          val a = ring[(i - 1 + ring.size) % ring.size]
          val b = ring[(i + 1) % ring.size]
          (b[0] - a[0]) * (p[1] - a[1]) != (b[1] - a[1]) * (p[0] - a[0])
        })
      }
    }
    return rings
  }

  /** Twice the signed area; positive for clockwise rings in y-down tile coordinates. */
  private fun area2(ring: Ring): Long {
    var s = 0L
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[(i + 1) % ring.size]
      s += a[0].toLong() * b[1] - b[0].toLong() * a[1]
    }
    return s
  }

  private fun contains(ring: Ring, px: Double, py: Double): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
      val (xi, yi) = ring[i]
      val (xj, yj) = ring[j]
      if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi).toDouble() + xi) inside = !inside
      j = i
    }
    return inside
  }

  /**
   * The outline grouped into polygons for map libraries that need them (GeoJSON MultiPolygon): every outer
   * ring with the holes inside it. A hole belongs to the smallest outer ring that contains a tile just
   * beside its first edge (a tile of the set itself).
   */
  fun polygons(tiles: Collection<Tile>): List<PolygonRings> {
    class Outer(val ring: Ring, val area: Long) {
      val minX = ring.minOf { it[0] }
      val maxX = ring.maxOf { it[0] }
      val minY = ring.minOf { it[1] }
      val maxY = ring.maxOf { it[1] }
      val holes = mutableListOf<Ring>()
      fun mightContain(x: Double, y: Double) = x > minX && x < maxX && y > minY && y < maxY
    }
    val outers = mutableListOf<Outer>()
    val holes = mutableListOf<Ring>()
    for (ring in outline(tiles)) {
      val area = area2(ring)
      if (area > 0) outers.add(Outer(ring, area)) else if (area < 0) holes.add(ring)
    }
    outers.sortBy { it.area } // smallest first, so the first match is the ring right around a hole
    for (hole in holes) {
      // The set's tile on the right-hand side of the hole's first edge (y down), at its centre.
      val a = hole[0]
      val b = hole[1]
      val dx = Integer.signum(b[0] - a[0])
      val dy = Integer.signum(b[1] - a[1])
      val tx = a[0] + (if (dx == 1) 0.5 else if (dx == -1) -0.5 else if (dy == 1) -0.5 else 0.5)
      val ty = a[1] + (if (dy == 1) 0.5 else if (dy == -1) -0.5 else if (dx == 1) 0.5 else -0.5)
      // A cheap bounding-box test first; the full point-in-ring test only for the few rings that pass it.
      val owner = outers.firstOrNull { it.mightContain(tx, ty) && contains(it.ring, tx, ty) } ?: continue
      owner.holes.add(hole)
    }
    return outers.map { listOf(it.ring) + it.holes }
  }

  /** The tile a position is in. */
  fun tileAt(lat: Double, lon: Double, z: Int) = Tile(floor(lonToTileX(lon, z)).toInt(), floor(latToTileY(lat, z)).toInt())

  /**
   * Every tile a straight stretch between two positions passes through, in order, including the first and the
   * last. A fast rider can cover several tiles between two GPS fixes, so crossings are traced along the line
   * rather than taken from the fixes themselves. (Web Mercator is linear in tile space, so a straight line in
   * tile coordinates is right for the short stretches between fixes.)
   */
  fun tilesBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double, z: Int): List<Tile> {
    val x1 = lonToTileX(lon1, z)
    val y1 = latToTileY(lat1, z)
    val x2 = lonToTileX(lon2, z)
    val y2 = latToTileY(lat2, z)
    var tx = floor(x1).toInt()
    var ty = floor(y1).toInt()
    val endX = floor(x2).toInt()
    val endY = floor(y2).toInt()
    val tiles = mutableListOf(Tile(tx, ty))
    val dx = x2 - x1
    val dy = y2 - y1
    val stepX = if (dx > 0) 1 else -1
    val stepY = if (dy > 0) 1 else -1
    val inf = Double.POSITIVE_INFINITY
    var nextX = if (dx > 0) (tx + 1 - x1) / dx else if (dx < 0) (x1 - tx) / -dx else inf
    var nextY = if (dy > 0) (ty + 1 - y1) / dy else if (dy < 0) (y1 - ty) / -dy else inf
    val perX = if (dx != 0.0) 1 / abs(dx) else inf
    val perY = if (dy != 0.0) 1 / abs(dy) else inf
    var guard = abs(endX - tx) + abs(endY - ty) + 2 // never loop forever on a rounding quirk
    while ((tx != endX || ty != endY) && guard-- > 0) {
      if (nextX < nextY) { tx += stepX; nextX += perX } else { ty += stepY; nextY += perY }
      tiles.add(Tile(tx, ty))
    }
    return tiles
  }

  fun analyse(kml: String): Analysis {
    val placemarks = parsePlacemarks(kml)
    val layers = LinkedHashMap<Layer, LayerData>()
    for (layer in Layer.entries) {
      val pm = placemarks[layer.placemark] ?: continue
      val visited = rasterise(pm.rings, layer.zoom)
      layers[layer] = LayerData(layer, visited, findYards(visited))
    }
    val reported = placemarks.mapNotNull { (name, pm) -> pm.size?.let { name to it } }.toMap()
    return Analysis(layers, reported)
  }

  /** The yard tile closest to the yard's centre: where its label goes, and what its place name is looked up for. */
  fun labelTile(tiles: List<Tile>): Tile {
    val cx = tiles.sumOf { it.x }.toDouble() / tiles.size
    val cy = tiles.sumOf { it.y }.toDouble() / tiles.size
    return tiles.minBy { abs(it.x - cx) * abs(it.x - cx) + abs(it.y - cy) * abs(it.y - cy) }
  }
}
