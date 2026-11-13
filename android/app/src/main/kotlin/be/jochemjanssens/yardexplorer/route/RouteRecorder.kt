package be.jochemjanssens.yardexplorer.route

import android.location.Location
import be.jochemjanssens.yardexplorer.model.Layer
import be.jochemjanssens.yardexplorer.model.RouteTrack
import be.jochemjanssens.yardexplorer.model.Tile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RouteStatus { IDLE, RECORDING, FINISHED }

/**
 * What the screen shows. [track] is mutable and changes in place on every GPS fix; [tick] changes with it, so
 * observers know to look again. Everything runs on the main thread.
 */
data class RouteUi(
  val status: RouteStatus = RouteStatus.IDLE,
  val track: RouteTrack? = null,
  val tick: Int = 0,
  val startedAtMs: Long = 0,
  val endedAtMs: Long = 0,
)

/** The route being recorded. A process-wide singleton, so the foreground service and the screen share it. */
object RouteRecorder {
  private val _ui = MutableStateFlow(RouteUi())
  val ui: StateFlow<RouteUi> = _ui.asStateFlow()

  /** The tiles in the loaded export, which decide what counts as a new tile. Set when an export is opened. */
  @Volatile
  var visited: Map<Layer, Set<Tile>> = emptyMap()

  fun start() {
    val track = RouteTrack { layer -> visited[layer] ?: emptySet() }
    _ui.value = RouteUi(RouteStatus.RECORDING, track, tick = 0, startedAtMs = System.currentTimeMillis())
  }

  fun onFix(location: Location) {
    val current = _ui.value
    if (current.status != RouteStatus.RECORDING) return
    val track = current.track ?: return
    val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
    if (track.add(location.latitude, location.longitude, accuracy, location.time)) {
      _ui.value = current.copy(tick = current.tick + 1)
    }
  }

  fun stop() {
    val current = _ui.value
    if (current.status == RouteStatus.RECORDING) {
      _ui.value = current.copy(status = RouteStatus.FINISHED, endedAtMs = System.currentTimeMillis(), tick = current.tick + 1)
    }
  }

  /** Clears a finished route from the screen. */
  fun dismiss() {
    if (_ui.value.status == RouteStatus.FINISHED) _ui.value = RouteUi()
  }
}
