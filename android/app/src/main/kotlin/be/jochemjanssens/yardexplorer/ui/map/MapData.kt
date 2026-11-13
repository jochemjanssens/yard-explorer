package be.jochemjanssens.yardexplorer.ui.map

import be.jochemjanssens.yardexplorer.model.LayerData
import be.jochemjanssens.yardexplorer.model.Squadrats
import be.jochemjanssens.yardexplorer.model.Tile
import be.jochemjanssens.yardexplorer.ui.theme.hex
import be.jochemjanssens.yardexplorer.ui.theme.rankColor
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How many yards get their own colour and badge. */
const val TOP = 20

/** Yards further than this from the best one are tagged "Away"; the map opens on the ones within it. */
const val REGION_KM = 37.5

/** The map opens framed on this many of the biggest yards near home. */
private const val FOCUS_YARDS = 6

/** Everything the map draws for one layer, prepared once per export. */
class MapData(
  /** GeoJSON, serialised up front so the main thread doesn't have to. */
  val visited: String,
  /** One feature per yard, with properties rank, size, color and top. */
  val yards: String,
  /** A point per top-20 yard where its rank badge goes, with properties rank and icon. */
  val badges: String,
  val yardBounds: List<LatLngBounds>,
  val labelPoints: List<LatLng>,
  /** Ranks of the yards within REGION_KM of the best one. */
  val home: Set<Int>,
  /** The biggest few yards near home: what the map opens on. */
  val homeView: LatLngBounds?,
)

fun buildMapData(data: LayerData): MapData {
  val z = data.layer.zoom
  val yards = data.yards
  val bounds = yards.map { boundsOf(it, z) }
  val labels = yards.map { tiles -> Squadrats.labelTile(tiles).let { LatLng(lat(it.y + 0.5, z), lon(it.x + 0.5, z)) } }
  val homeCenter = bounds.firstOrNull()?.center
  val home = yards.indices.filter { homeCenter != null && km(homeCenter, bounds[it].center) <= REGION_KM }.toSet()
  val nearby = home.sorted().take(FOCUS_YARDS)
  val homeView = nearby.map { bounds[it] }.reduceOrNull { a, b -> a.union(b) }

  // GeoJSON written directly as text: building geometry objects and serialising them took seconds on a phone.
  val colors = yards.indices.map { rankColor(it).hex() }
  val yardsJson = featureCollection(yards.size) { i ->
    feature(multiPolygonJson(yards[i], z), """"rank":$i,"size":${yards[i].size},"color":"${colors[i]}","top":${i < TOP}""")
  }
  val badgesJson = featureCollection(minOf(TOP, labels.size)) { i ->
    feature("""{"type":"Point","coordinates":[${labels[i].longitude},${labels[i].latitude}]}""", """"rank":$i,"icon":"rank-$i"""")
  }
  return MapData(
    visited = featureCollection(1) { feature(multiPolygonJson(data.visited, z), "") },
    yards = yardsJson,
    badges = badgesJson,
    yardBounds = bounds,
    labelPoints = labels,
    home = home,
    homeView = homeView,
  )
}

private fun lat(y: Double, z: Int) = Squadrats.tileToLat(y, z)
private fun lon(x: Double, z: Int) = Squadrats.tileToLon(x, z)

fun tileBounds(t: Tile, z: Int): LatLngBounds =
  LatLngBounds.from(lat(t.y.toDouble(), z), lon(t.x + 1.0, z), lat(t.y + 1.0, z), lon(t.x.toDouble(), z))

private fun boundsOf(tiles: List<Tile>, z: Int): LatLngBounds = LatLngBounds.from(
  lat(tiles.minOf { it.y }.toDouble(), z), lon(tiles.maxOf { it.x } + 1.0, z),
  lat(tiles.maxOf { it.y } + 1.0, z), lon(tiles.minOf { it.x }.toDouble(), z),
)

private fun featureCollection(count: Int, feature: (Int) -> String) = buildString {
  append("""{"type":"FeatureCollection","features":[""")
  for (i in 0 until count) { if (i > 0) append(','); append(feature(i)) }
  append("]}")
}

private fun feature(geometry: String, properties: String) = """{"type":"Feature","geometry":$geometry,"properties":{$properties}}"""

/**
 * Appends a coordinate with 7 decimals (about 1 cm) using integer arithmetic: much faster on Android than
 * formatting a Double, and there are tens of thousands of them.
 */
internal fun StringBuilder.appendCoord(v: Double): StringBuilder {
  var n = Math.round(v * 1e7)
  if (n < 0) { append('-'); n = -n }
  append(n / 10_000_000).append('.')
  val frac = (n % 10_000_000).toString()
  repeat(7 - frac.length) { append('0') }
  return append(frac)
}

const val EMPTY_GEOJSON = """{"type":"FeatureCollection","features":[]}"""

/** A route as GeoJSON; [lonLat] is [lon0, lat0, lon1, lat1, …]. Empty until there are two points. */
fun routeLineJson(lonLat: DoubleArray): String = if (lonLat.size < 4) EMPTY_GEOJSON else buildString {
  append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
  for (i in 0 until lonLat.size / 2) {
    if (i > 0) append(',')
    append('[').appendCoord(lonLat[2 * i]).append(',').appendCoord(lonLat[2 * i + 1]).append(']')
  }
  append("]}}]}")
}

/** A set of tiles as one GeoJSON feature (merged outlines), or an empty collection. */
fun tilesJson(tiles: Collection<Tile>, z: Int): String =
  if (tiles.isEmpty()) EMPTY_GEOJSON else featureCollection(1) { feature(multiPolygonJson(tiles, z), "") }

/** Tiles as GeoJSON MultiPolygon text: merged outlines, holes attached to their outer ring, rings closed. */
fun multiPolygonJson(tiles: Collection<Tile>, z: Int): String = buildString {
  // Corners share a few hundred distinct x and y values; convert each to degrees once.
  val lons = HashMap<Int, Double>()
  val lats = HashMap<Int, Double>()
  append("""{"type":"MultiPolygon","coordinates":[""")
  Squadrats.polygons(tiles).forEachIndexed { pi, polygon ->
    if (pi > 0) append(',')
    append('[')
    polygon.forEachIndexed { ri, ring ->
      if (ri > 0) append(',')
      append('[')
      for (k in 0..ring.size) { // the first corner again at the end closes the ring
        val (x, y) = ring[k % ring.size]
        if (k > 0) append(',')
        append('[').appendCoord(lons.getOrPut(x) { lon(x.toDouble(), z) }).append(',')
          .appendCoord(lats.getOrPut(y) { lat(y.toDouble(), z) }).append(']')
      }
      append(']')
    }
    append(']')
  }
  append("]}")
}

/** Tiles as a GeoJSON MultiPolygon object (for small, frequently changing shapes like the tile you're in). */
fun multiPolygon(tiles: Collection<Tile>, z: Int): MultiPolygon = MultiPolygon.fromLngLats(
  Squadrats.polygons(tiles).map { polygon ->
    polygon.map { ring ->
      (ring + listOf(ring.first())).map { (x, y) -> Point.fromLngLat(lon(x.toDouble(), z), lat(y.toDouble(), z)) }
    }
  },
)

/** Great-circle distance in km. */
fun km(a: LatLng, b: LatLng): Double {
  val r = Math.PI / 180
  val h = sin((b.latitude - a.latitude) * r / 2).let { it * it } +
    cos(a.latitude * r) * cos(b.latitude * r) * sin((b.longitude - a.longitude) * r / 2).let { it * it }
  return 2 * 6371 * asin(sqrt(h))
}
