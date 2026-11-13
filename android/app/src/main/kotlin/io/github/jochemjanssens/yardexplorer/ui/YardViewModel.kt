package io.github.jochemjanssens.yardexplorer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jochemjanssens.yardexplorer.data.ExportStore
import io.github.jochemjanssens.yardexplorer.data.PlaceNames
import io.github.jochemjanssens.yardexplorer.model.Analysis
import io.github.jochemjanssens.yardexplorer.model.Layer
import io.github.jochemjanssens.yardexplorer.model.Squadrats
import io.github.jochemjanssens.yardexplorer.ui.map.MapData
import io.github.jochemjanssens.yardexplorer.ui.map.TOP
import io.github.jochemjanssens.yardexplorer.ui.map.buildMapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class YardUiState(
  val loading: Boolean = true,
  val analysis: Analysis? = null,
  val mapData: Map<Layer, MapData> = emptyMap(),
  val layer: Layer = Layer.SQUADRATINHOS,
  val minSize: Int = 5,
  val showVisited: Boolean = true,
  val selected: Int? = null,
  /** Place names by PlaceNames.key of a yard's label point. */
  val places: Map<String, String> = emptyMap(),
  val message: String? = null,
  /** Bumped whenever the map should frame the home area again. */
  val frameHome: Int = 0,
) {
  val map: MapData? get() = mapData[layer]
  val yards get() = analysis?.layers?.get(layer)?.yards.orEmpty()

  /** The top 20 always show; smaller yards only from the chosen size up. */
  fun listed(rank: Int) = rank < TOP || yards[rank].size >= minSize || rank == selected
}

class YardViewModel(app: Application) : AndroidViewModel(app) {
  private val store = ExportStore(app)
  private val placeNames = PlaceNames(app)
  private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

  private val _state = MutableStateFlow(YardUiState(minSize = prefs.getInt("minSize", 5)))
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val kml = withContext(Dispatchers.IO) { store.load() }
      if (kml != null) show(kml) else _state.update { it.copy(loading = false) }
    }
  }

  /** Opens a KML the user picked or shared, and keeps it for next time. */
  fun open(uri: Uri) {
    viewModelScope.launch {
      _state.update { it.copy(loading = true) }
      val kml = withContext(Dispatchers.IO) { runCatching { store.read(uri) }.getOrNull() }
      if (kml == null) {
        _state.update { it.copy(loading = false, message = "That file isn't a Squadrats KML export.") }
        return@launch
      }
      withContext(Dispatchers.IO) { store.save(kml) }
      show(kml)
    }
  }

  private suspend fun show(kml: String) {
    val (analysis, maps) = withContext(Dispatchers.Default) {
      val a = Squadrats.analyse(kml)
      a to a.layers.mapValues { buildMapData(it.value) }
    }
    if (analysis.layers.isEmpty()) {
      _state.update { it.copy(loading = false, message = "No squadrats found in that file.") }
      return
    }
    _state.update { it.copy(loading = false, analysis = analysis, mapData = maps, selected = null, frameHome = it.frameHome + 1) }
    lookUpPlaces()
  }

  fun setLayer(layer: Layer) {
    _state.update { it.copy(layer = layer, selected = null, frameHome = it.frameHome + 1) }
    lookUpPlaces()
  }

  fun setMinSize(size: Int) {
    prefs.edit().putInt("minSize", size).apply()
    _state.update { it.copy(minSize = size) }
    lookUpPlaces()
  }

  fun toggleVisited() = _state.update { it.copy(showVisited = !it.showVisited) }

  /** Selects a yard (or clears the selection when it's already selected). */
  fun select(rank: Int) = _state.update { it.copy(selected = if (it.selected == rank) null else rank) }

  fun dismissMessage() = _state.update { it.copy(message = null) }

  fun placeKey(rank: Int): String? = state.value.map?.labelPoints?.getOrNull(rank)?.let { placeNames.key(it.latitude, it.longitude) }

  private var placeJob: Job? = null

  /** Looks up names for the listed yards, biggest first, one at a time. */
  private fun lookUpPlaces() {
    placeJob?.cancel()
    placeJob = viewModelScope.launch {
      val s = state.value
      val points = s.map?.labelPoints ?: return@launch
      val found = mutableMapOf<String, String>()
      var lastFlush = System.currentTimeMillis()
      fun flush() {
        if (found.isEmpty()) return
        val batch = found.toMap()
        found.clear()
        _state.update { it.copy(places = it.places + batch) }
        lastFlush = System.currentTimeMillis()
      }
      for (rank in points.indices) {
        if (!s.listed(rank)) continue
        val p = points[rank]
        val key = placeNames.key(p.latitude, p.longitude)
        if (key in state.value.places) continue
        found[key] = placeNames.lookup(p.latitude, p.longitude) ?: continue
        // Cached names arrive together; new lookups at most about once a second.
        if (System.currentTimeMillis() - lastFlush > 1000) flush()
      }
      flush()
    }
  }
}
