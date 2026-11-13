package be.jochemjanssens.yardexplorer.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One accepted GPS position of a route. */
class RoutePoint(val lat: Double, val lon: Double, val timeMs: Long)

/**
 * A route being recorded: the accepted GPS positions, the distance, and every tile crossed, split into tiles that
 * were already in the export (visited) and tiles that are new. Plain Kotlin, so it can be unit-tested.
 * Not thread-safe: feed it from one thread (the app uses the main thread).
 */
class RouteTrack(private val visitedOf: (Layer) -> Set<Tile>) {
  val points = ArrayList<RoutePoint>()
  var distanceM = 0.0
    private set

  /** Every tile crossed, per layer, in the order they were crossed. */
  val crossed: Map<Layer, LinkedHashSet<Tile>> = Layer.entries.associateWith { LinkedHashSet() }

  /** The crossed tiles that weren't in the export, per layer, in crossing order. */
  val newTiles: Map<Layer, LinkedHashSet<Tile>> = Layer.entries.associateWith { LinkedHashSet() }

  val startedAtMs: Long get() = points.firstOrNull()?.timeMs ?: 0L
  val lastAtMs: Long get() = points.lastOrNull()?.timeMs ?: 0L

  /** Adds a GPS fix. Returns false (and ignores it) if it's too imprecise, too close to the last one, or a jump. */
  fun add(lat: Double, lon: Double, accuracyM: Float, timeMs: Long): Boolean {
    if (accuracyM > MAX_ACCURACY_M) return false
    val prev = points.lastOrNull()
    var step = 0.0
    if (prev != null) {
      step = metres(prev.lat, prev.lon, lat, lon)
      if (step < MIN_STEP_M) return false
      val seconds = (timeMs - prev.timeMs) / 1000.0
      if (seconds > 0 && step / seconds > MAX_SPEED_MS) return false // GPS jumped
    }
    points.add(RoutePoint(lat, lon, timeMs))
    distanceM += step
    for (layer in Layer.entries) {
      val tiles = if (prev == null) listOf(Squadrats.tileAt(lat, lon, layer.zoom))
      else Squadrats.tilesBetween(prev.lat, prev.lon, lat, lon, layer.zoom)
      val seen = crossed.getValue(layer)
      val visited = visitedOf(layer)
      for (t in tiles) if (seen.add(t) && t !in visited) newTiles.getValue(layer).add(t)
    }
    return true
  }

  companion object {
    const val MAX_ACCURACY_M = 50f
    const val MIN_STEP_M = 4.0
    const val MAX_SPEED_MS = 45.0 // 162 km/h: faster than any ride, so it must be a GPS jump

    /** Great-circle distance in metres. */
    fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val r = PI / 180
      val h = sin((lat2 - lat1) * r / 2).let { it * it } +
        cos(lat1 * r) * cos(lat2 * r) * sin((lon2 - lon1) * r / 2).let { it * it }
      return 2 * 6_371_000.0 * asin(sqrt(h))
    }
  }
}
