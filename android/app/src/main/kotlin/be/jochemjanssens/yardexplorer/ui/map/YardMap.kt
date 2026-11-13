package be.jochemjanssens.yardexplorer.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import be.jochemjanssens.yardexplorer.model.Layer
import be.jochemjanssens.yardexplorer.model.Squadrats
import be.jochemjanssens.yardexplorer.route.RouteStatus
import be.jochemjanssens.yardexplorer.route.RouteUi
import be.jochemjanssens.yardexplorer.model.Tile
import be.jochemjanssens.yardexplorer.ui.theme.Ink
import be.jochemjanssens.yardexplorer.ui.theme.Signal
import be.jochemjanssens.yardexplorer.ui.theme.Steel
import be.jochemjanssens.yardexplorer.ui.theme.hex
import be.jochemjanssens.yardexplorer.ui.theme.rankColor
import be.jochemjanssens.yardexplorer.ui.theme.textOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/** OpenStreetMap's standard raster tiles: the same map as the website. */
private const val OSM_STYLE = """{
  "version": 8,
  "sources": { "osm": { "type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"], "tileSize": 256,
    "maxzoom": 19, "attribution": "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors" } },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}"""

private val EMPTY = FeatureCollection.fromFeatures(emptyList<Feature>())

/**
 * The map: OpenStreetMap with the visited area, every yard (top 20 coloured with a numbered badge),
 * and, while location is on, the dot and the tile you're in. MapLibre's own gestures behave like Google
 * Maps: pinch, double-tap, double-tap-and-drag and two-finger tap.
 */
@Composable
fun YardMap(
  map: MapData,
  zoom: Int,
  visited: Set<Tile>,
  showVisited: Boolean,
  minSize: Int,
  selected: Int?,
  frameHome: Int,
  here: Location?,
  route: RouteUi,
  /** Bumped to start following the position again (after the locate button, or a new route). */
  followKey: Int,
  topPadding: Dp,
  bottomPadding: Dp,
  onYardClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val density = LocalDensity.current
  val mapView = remember {
    MapLibre.getInstance(context)
    MapView(context).apply { onCreate(null) }
  }
  var libre by remember { mutableStateOf<MapLibreMap?>(null) }
  var style by remember { mutableStateOf<Style?>(null) }
  val clicks by rememberUpdatedState(onYardClick)

  // Forward the screen's lifecycle to the MapView.
  DisposableEffect(lifecycle, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
      if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
      mapView.onDestroy()
    }
  }

  LaunchedEffect(mapView) {
    mapView.getMapAsync { m ->
      m.uiSettings.apply {
        isRotateGesturesEnabled = false // north stays up, like the tile grid
        isTiltGesturesEnabled = false
        isCompassEnabled = false
        isLogoEnabled = false
        attributionGravity = Gravity.TOP or Gravity.START
      }
      m.setStyle(Style.Builder().fromJson(OSM_STYLE)) { s ->
        setUpStyle(s, context)
        style = s
      }
      m.addOnMapClickListener { point ->
        val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(point), "yard-fill").firstOrNull()
        hit?.getNumberProperty("rank")?.toInt()?.let(clicks)
        hit != null
      }
      libre = m
    }
  }

  // The OpenStreetMap credit sits top-left, clear of the status bar.
  LaunchedEffect(libre, topPadding) {
    val m = libre ?: return@LaunchedEffect
    val top = with(density) { topPadding.roundToPx() }
    val side = with(density) { Dp(12f).roundToPx() }
    m.uiSettings.setAttributionMargins(side, top, side, side)
  }

  // Data for this layer.
  LaunchedEffect(style, map) {
    val s = style ?: return@LaunchedEffect
    s.getSourceAs<GeoJsonSource>("visited")?.setGeoJson(map.visited)
    s.getSourceAs<GeoJsonSource>("yards")?.setGeoJson(map.yards)
    s.getSourceAs<GeoJsonSource>("badges")?.setGeoJson(map.badges)
  }

  LaunchedEffect(style, showVisited) {
    val s = style ?: return@LaunchedEffect
    val v = visibility(if (showVisited) Property.VISIBLE else Property.NONE)
    s.getLayer("visited-fill")?.setProperties(v)
    s.getLayer("visited-line")?.setProperties(v)
  }

  // Size filter and selection: the selected yard stands out, the rest fade back.
  LaunchedEffect(style, minSize, selected) {
    val s = style ?: return@LaunchedEffect
    val shown = any(eq(get("top"), true), gte(get("size"), minSize), eq(get("rank"), selected ?: -1))
    val isSel = eq(get("rank"), selected ?: -1)
    val isTop = eq(get("top"), true)
    (s.getLayer("yard-fill") as? FillLayer)?.apply {
      setFilter(shown)
      setProperties(fillOpacity(
        if (selected == null) switchCase(isTop, literal(0.25f), literal(0.15f))
        else switchCase(isSel, literal(0.4f), isTop, literal(0.08f), literal(0.06f)),
      ))
    }
    (s.getLayer("yard-line") as? LineLayer)?.apply {
      setFilter(shown)
      setProperties(
        lineWidth(switchCase(isSel, literal(3.5f), isTop, literal(2.5f), literal(1.5f))),
        lineOpacity(if (selected == null) literal(1f) else switchCase(isSel, literal(1f), literal(0.45f))),
      )
    }
    (s.getLayer("yard-casing") as? LineLayer)?.setProperties(
      lineWidth(switchCase(isSel, literal(6f), literal(5f))),
      lineOpacity(if (selected == null) literal(0.8f) else switchCase(isSel, literal(0.8f), literal(0.35f))),
    )
    (s.getLayer("badges") as? SymbolLayer)?.setProperties(
      iconOpacity(if (selected == null) literal(1f) else switchCase(isSel, literal(1f), literal(0.5f))),
    )
  }

  // Open on home: the biggest few yards near home, clear of the sheet.
  LaunchedEffect(libre, frameHome, map) {
    val m = libre ?: return@LaunchedEffect
    val bounds = map.homeView ?: return@LaunchedEffect
    m.moveCamera(fit(bounds, density, topPadding, bottomPadding))
  }

  LaunchedEffect(libre, selected) {
    val m = libre ?: return@LaunchedEffect
    val bounds = map.yardBounds.getOrNull(selected ?: return@LaunchedEffect) ?: return@LaunchedEffect
    m.animateCamera(fit(bounds, density, topPadding, bottomPadding, extra = 48f), 600)
  }

  // While recording, the map follows your position, until you move the map yourself (the locate button resumes).
  var follow by remember { mutableStateOf(true) }
  LaunchedEffect(followKey) { follow = true }
  LaunchedEffect(libre) {
    libre?.addOnCameraMoveStartedListener { reason ->
      if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) follow = false
    }
  }
  LaunchedEffect(here, route.status, follow) {
    val m = libre ?: return@LaunchedEffect
    val position = here ?: return@LaunchedEffect
    if (route.status == RouteStatus.RECORDING && follow) {
      m.easeCamera(CameraUpdateFactory.newLatLng(LatLng(position.latitude, position.longitude)), 700)
    }
  }

  // When a route ends, zoom out to show all of it, clear of the status card.
  LaunchedEffect(libre, route.status) {
    val m = libre ?: return@LaunchedEffect
    val track = route.track ?: return@LaunchedEffect
    if (route.status != RouteStatus.FINISHED || track.points.size < 2) return@LaunchedEffect
    val bounds = LatLngBounds.from(
      track.points.maxOf { it.lat }, track.points.maxOf { it.lon }, track.points.minOf { it.lat }, track.points.minOf { it.lon },
    )
    m.animateCamera(fit(bounds, density, topPadding, bottomPadding + 150.dp, extra = 40f), 900)
  }

  // The route being recorded: its line, and the new tiles crossed so far (for the layer shown). Rebuilt a moment after
  // the last GPS fix, off the main thread, from copies taken here (the track changes on the main thread).
  LaunchedEffect(style, route.tick, route.status, zoom) {
    val s = style ?: return@LaunchedEffect
    val track = route.track
    if (track == null) {
      s.getSourceAs<GeoJsonSource>("route-line")?.setGeoJson(EMPTY_GEOJSON)
      s.getSourceAs<GeoJsonSource>("route-new")?.setGeoJson(EMPTY_GEOJSON)
      return@LaunchedEffect
    }
    delay(250)
    val layer = Layer.entries.first { it.zoom == zoom }
    val lonLat = DoubleArray(track.points.size * 2).also { out ->
      track.points.forEachIndexed { i, p -> out[2 * i] = p.lon; out[2 * i + 1] = p.lat }
    }
    val newTiles = track.newTiles.getValue(layer).toList()
    val (lineJson, tilesJson) = withContext(Dispatchers.Default) { routeLineJson(lonLat) to tilesJson(newTiles, zoom) }
    s.getSourceAs<GeoJsonSource>("route-line")?.setGeoJson(lineJson)
    s.getSourceAs<GeoJsonSource>("route-new")?.setGeoJson(tilesJson)
  }

  // Your location: MapLibre's dot, plus the tile you're in (green and dashed when it's new).
  var hadFix by remember { mutableStateOf(false) }
  LaunchedEffect(style, here, zoom, visited) {
    val s = style ?: return@LaunchedEffect
    val m = libre ?: return@LaunchedEffect
    val location = m.locationComponent
    val source = s.getSourceAs<GeoJsonSource>("here")
    if (here == null) {
      if (location.isLocationComponentActivated) location.isLocationComponentEnabled = false
      source?.setGeoJson(EMPTY)
      hadFix = false
      return@LaunchedEffect
    }
    showLocation(m, s, context, here)
    val tile = Tile(
      Squadrats.lonToTileX(here.longitude, zoom).toInt(),
      Squadrats.latToTileY(here.latitude, zoom).toInt(),
    )
    source?.setGeoJson(Feature.fromGeometry(multiPolygon(listOf(tile), zoom)).apply {
      addBooleanProperty("new", tile !in visited)
    })
    if (!hadFix) {
      hadFix = true
      m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(here.latitude, here.longitude), maxOf(m.cameraPosition.zoom, 15.0)), 600)
    }
  }

  AndroidView(factory = { mapView }, modifier = modifier)
}

/** Fits [bounds] on screen, clear of the status bar at the top and the sheet at the bottom. */
private fun fit(bounds: LatLngBounds, density: Density, top: Dp, bottom: Dp, extra: Float = 20f) = with(density) {
  val e = Dp(extra).roundToPx()
  CameraUpdateFactory.newLatLngBounds(bounds, e, top.roundToPx() + e, e, bottom.roundToPx() + e)
}

@SuppressLint("MissingPermission") // only called with a location fix, which needs the permission
private fun showLocation(m: MapLibreMap, s: Style, context: Context, here: Location) {
  val location = m.locationComponent
  if (!location.isLocationComponentActivated) {
    val options = LocationComponentOptions.builder(context)
      .foregroundTintColor(Signal.toArgb())
      .backgroundTintColor(Ink.toArgb())
      .accuracyColor(Signal.toArgb())
      .accuracyAlpha(0.15f)
      .pulseEnabled(true)
      .pulseColor(Signal.toArgb())
      .build()
    location.activateLocationComponent(
      LocationComponentActivationOptions.builder(context, s)
        .locationComponentOptions(options)
        .useDefaultLocationEngine(false) // the app feeds positions itself
        .build(),
    )
  }
  location.isLocationComponentEnabled = true
  location.cameraMode = CameraMode.NONE // follow along without fighting the viewer
  location.renderMode = RenderMode.NORMAL
  location.forceLocationUpdate(here)
}

private fun setUpStyle(s: Style, context: Context) {
  val density = context.resources.displayMetrics.density
  for (rank in 0 until TOP) s.addImage("rank-$rank", badge(rank, density))
  for (id in listOf("visited", "yards", "badges", "here", "route-new", "route-line")) s.addSource(GeoJsonSource(id, EMPTY))

  val ink = Ink.hex()
  s.addLayer(FillLayer("visited-fill", "visited").withProperties(fillColor(ink), fillOpacity(0.1f)))
  s.addLayer(LineLayer("visited-line", "visited").withProperties(lineColor(ink), lineWidth(0.75f), lineOpacity(0.2f)))
  s.addLayer(FillLayer("yard-fill", "yards").withProperties(fillColor(get("color")), fillOpacity(0.25f)))
  s.addLayer(
    LineLayer("yard-casing", "yards").withFilter(eq(get("top"), true))
      .withProperties(lineColor(ink), lineWidth(5f), lineOpacity(0.8f)),
  )
  s.addLayer(
    LineLayer("yard-line", "yards").withProperties(
      lineColor(switchCase(eq(get("top"), true), get("color"), literal(Steel.hex()))),
      lineWidth(2.5f),
    ),
  )
  // The route being recorded: new tiles crossed so far (green, with a dark edge), and the ridden line on top.
  s.addLayer(FillLayer("route-new-fill", "route-new").withProperties(fillColor(Signal.hex()), fillOpacity(0.45f)))
  s.addLayer(LineLayer("route-new-casing", "route-new").withProperties(lineColor(ink), lineWidth(5f), lineOpacity(0.8f)))
  s.addLayer(LineLayer("route-new-line", "route-new").withProperties(lineColor(Signal.hex()), lineWidth(2.5f)))
  s.addLayer(
    LineLayer("route-casing", "route-line").withProperties(
      lineColor("#FFFFFF"), lineWidth(8f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
    ),
  )
  s.addLayer(
    LineLayer("route-line", "route-line").withProperties(
      lineColor(ink), lineWidth(4f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
    ),
  )
  s.addLayer(
    FillLayer("here-fill", "here").withFilter(eq(get("new"), true))
      .withProperties(fillColor(Signal.hex()), fillOpacity(0.25f)),
  )
  // Dashed edge around a new tile, a solid one around a visited tile.
  s.addLayer(
    LineLayer("here-line-new", "here").withFilter(eq(get("new"), true))
      .withProperties(lineColor(ink), lineWidth(2f), lineDasharray(arrayOf(3f, 2f))),
  )
  s.addLayer(
    LineLayer("here-line", "here").withFilter(eq(get("new"), false)).withProperties(lineColor(ink), lineWidth(2f)),
  )
  s.addLayer(
    SymbolLayer("badges", "badges").withProperties(iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true)),
  )
}

/** A rank badge: a square tile in the yard's colour with its number and a hard shadow, like on the website. */
private fun badge(rank: Int, density: Float): Bitmap {
  val size = 26 * density
  val shadow = 2 * density
  val stroke = 1.5f * density
  val bmp = Bitmap.createBitmap((size + shadow).toInt(), (size + shadow).toInt(), Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  paint.color = Ink.toArgb()
  canvas.drawRect(shadow, shadow, size + shadow, size + shadow, paint)
  paint.color = rankColor(rank).toArgb()
  canvas.drawRect(0f, 0f, size, size, paint)
  paint.style = Paint.Style.STROKE
  paint.strokeWidth = stroke
  paint.color = Ink.toArgb()
  canvas.drawRect(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2, paint)
  paint.style = Paint.Style.FILL
  paint.color = textOn(rankColor(rank)).toArgb()
  paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
  paint.textSize = 12 * density
  paint.textAlign = Paint.Align.CENTER
  val y = size / 2 - (paint.descent() + paint.ascent()) / 2
  canvas.drawText("${rank + 1}", size / 2, y, paint)
  return bmp
}
