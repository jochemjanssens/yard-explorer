package be.jochemjanssens.yardexplorer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import be.jochemjanssens.yardexplorer.route.RouteRecorder
import be.jochemjanssens.yardexplorer.route.RouteService
import be.jochemjanssens.yardexplorer.route.RouteStatus
import be.jochemjanssens.yardexplorer.route.RouteUi
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.jochemjanssens.yardexplorer.model.Layer
import be.jochemjanssens.yardexplorer.ui.map.REGION_KM
import be.jochemjanssens.yardexplorer.ui.map.TOP
import be.jochemjanssens.yardexplorer.ui.map.YardMap
import be.jochemjanssens.yardexplorer.ui.theme.Ink
import be.jochemjanssens.yardexplorer.ui.theme.LocalYardColors
import be.jochemjanssens.yardexplorer.ui.theme.RankColors
import be.jochemjanssens.yardexplorer.ui.theme.Signal
import be.jochemjanssens.yardexplorer.ui.theme.YardType
import be.jochemjanssens.yardexplorer.ui.theme.rankColor
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private fun fmt(n: Int) = NumberFormat.getIntegerInstance(Locale.US).format(n)

@Composable
fun YardExplorerApp(vm: YardViewModel, onPickFile: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  when {
    state.analysis != null -> MapScreen(state, vm, onPickFile)
    state.loading -> Loading() // a saved or just-picked export; don't flash the landing page
    else -> Landing(message = state.message, onPickFile = onPickFile, onDismiss = vm::dismissMessage)
  }
}

@Composable
private fun Loading() {
  val c = LocalYardColors.current
  Column(
    Modifier.fillMaxSize().background(c.bg).gridded(c.line.copy(alpha = 0.35f), 24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Logo()
    Spacer(Modifier.height(16.dp))
    Label("Loading your map…")
  }
}

// ---------- Landing: what the app does and the three steps to load your map ----------

@Composable
private fun Landing(message: String?, onPickFile: () -> Unit, onDismiss: () -> Unit) {
  val c = LocalYardColors.current
  val uri = LocalUriHandler.current
  Column(
    Modifier
      .fillMaxSize()
      .background(c.bg)
      .gridded(c.line.copy(alpha = 0.35f), 24.dp)
      .systemBarsPadding() // scroll below the status bar, not under it
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Logo()
      Spacer(Modifier.width(10.dp))
      Text("Yard Explorer", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
    }
    Spacer(Modifier.height(36.dp))
    Label("Yards · Yardinhos")
    Spacer(Modifier.height(10.dp))
    Text("Every tile", fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = c.ink)
    Text(
      "counts.",
      Modifier.padding(top = 4.dp).hardShadow(c.shade, 4.dp).background(Signal).padding(horizontal = 6.dp),
      fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = Ink,
    )
    Spacer(Modifier.height(18.dp))
    Text(
      "See every Yard and Yardinho you've built, not just the biggest: ranked, named and mapped, wherever you've ridden.",
      style = YardType.body.copy(fontSize = 17.sp, lineHeight = 24.sp), color = c.muted,
    )

    Spacer(Modifier.height(32.dp))
    Label("How to load your map")
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().hardShadow(c.shade, 4.dp).background(c.panel).border(1.5.dp, c.ink, RectangleShape)) {
      Step(1, RankColors[0], "Open your Squadrats map", "Sign in at squadrats.com with your usual account and go to your map.") {
        TileButton("Open squadrats.com ↗", { uri.openUri("https://squadrats.com") })
      }
      StepDivider()
      Step(2, RankColors[1], "Download the KML file", buildString {
        append("In the panel on the right of the map, download the KML export. You get a file like ")
        append("squadrats-2026-10-29.kml, usually in your Downloads folder.")
      })
      StepDivider()
      Step(3, Signal, "Open it here", "Choose the file below, or open the .kml from your Downloads and pick Yard Explorer.") {
        TileButton("Choose KML file", onPickFile, signal = true)
      }
    }
    if (message != null) {
      Spacer(Modifier.height(14.dp))
      Text(message, Modifier.clickable(onClick = onDismiss), style = YardType.label.copy(fontSize = 12.sp), color = Color(0xFFE4002B))
    }
    Spacer(Modifier.height(10.dp))
    Label("After new rides, download a fresh KML and open it again.")
    Spacer(Modifier.height(28.dp))
    Text(
      "Your export stays on this phone. Place names come from Android's geocoder, and your location, if you turn it on, " +
        "is only shown on the map. Not affiliated with Squadrats.",
      style = YardType.label.copy(fontSize = 11.sp, letterSpacing = 0.sp, lineHeight = 17.sp), color = c.muted,
    )
    Spacer(Modifier.height(10.dp))
    Label(
      "Privacy policy ↗",
      Modifier.clickable(role = Role.Button) { uri.openUri("https://jochemjanssens.github.io/yard-explorer/privacy.html") }
        .padding(vertical = 6.dp),
      color = c.ink,
    )
  }
}

@Composable
private fun Step(n: Int, color: Color, title: String, text: String, action: (@Composable () -> Unit)? = null) {
  val c = LocalYardColors.current
  Column(Modifier.fillMaxWidth().padding(20.dp)) {
    Chip("$n", color, Modifier.hardShadow(Ink, 3.dp), size = 40.dp, fontSize = 18)
    Spacer(Modifier.height(12.dp))
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
    Spacer(Modifier.height(6.dp))
    Text(text, style = YardType.body.copy(lineHeight = 21.sp), color = c.muted)
    if (action != null) { Spacer(Modifier.height(14.dp)); action() }
  }
}

@Composable
private fun StepDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalYardColors.current.line))

// ---------- The map with the standings in a bottom sheet ----------

/** The collapsed sheet: handle, title row and the stats. */
private val PEEK = 122.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(state: YardUiState, vm: YardViewModel, onPickFile: () -> Unit) {
  val c = LocalYardColors.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheet = rememberBottomSheetScaffoldState()
  val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val peek = PEEK + navBar
  val layerData = state.analysis!!.layers.getValue(state.layer)
  val map = state.map!!

  // Location: off until the button is pressed; positions stay on the phone.
  var tracking by rememberSaveable { mutableStateOf(false) }
  var here by remember { mutableStateOf<Location?>(null) }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) tracking = true
    else scope.launch { sheet.snackbarHostState.showSnackbar("Location access was denied. Allow it in the app's settings to use this.") }
  }
  LocationUpdates(tracking) { here = it }
  LaunchedEffect(tracking) { if (!tracking) here = null }

  // Route recording: a foreground service feeds RouteRecorder, so it keeps going with the screen off.
  val route by RouteRecorder.ui.collectAsStateWithLifecycle()
  var confirmStop by remember { mutableStateOf(false) }
  var followKey by remember { mutableIntStateOf(0) }
  fun startRoute() {
    RouteService.start(context)
    tracking = true // show the position dot while recording
    followKey++
  }
  val routePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) startRoute()
    else scope.launch { sheet.snackbarHostState.showSnackbar("Recording a route needs precise location. Allow it in the app's settings.") }
  }
  val onRecordClick = {
    when (route.status) {
      RouteStatus.RECORDING -> confirmStop = true
      else -> {
        val missing = missingRoutePermissions(context)
        if (missing.isEmpty()) startRoute() else routePermissions.launch(missing)
      }
    }
  }
  if (confirmStop) {
    val newTiles = route.track?.newTiles?.get(state.layer)?.size ?: 0
    AlertDialog(
      onDismissRequest = { confirmStop = false },
      shape = RectangleShape,
      containerColor = c.panel,
      title = { Text("Stop this route?", fontWeight = FontWeight.SemiBold) },
      text = { Text("You've crossed $newTiles new ${state.layer.tileName}${if (newTiles == 1) "" else "s"} so far.", color = c.muted) },
      confirmButton = { TileButton("Stop route", { RouteService.stop(context); confirmStop = false }, signal = true) },
      dismissButton = { TileButton("Keep going", { confirmStop = false }) },
    )
  }

  LaunchedEffect(state.message) {
    val m = state.message ?: return@LaunchedEffect
    sheet.snackbarHostState.showSnackbar(m)
    vm.dismissMessage()
  }

  BottomSheetScaffold(
    scaffoldState = sheet,
    sheetPeekHeight = peek,
    sheetShape = RectangleShape,
    sheetContainerColor = c.panel,
    sheetContentColor = c.ink,
    sheetShadowElevation = 0.dp,
    sheetTonalElevation = 0.dp,
    sheetDragHandle = { SheetHandle() },
    snackbarHost = { SnackbarHost(it, Modifier.padding(bottom = peek)) },
    sheetContent = {
      Sheet(state, vm, onPickFile, onYardPicked = { scope.launch { sheet.bottomSheetState.partialExpand() } })
    },
  ) {
    Box(Modifier.fillMaxSize()) {
      YardMap(
        map = map,
        zoom = state.layer.zoom,
        visited = layerData.visited,
        showVisited = state.showVisited,
        minSize = state.minSize,
        selected = state.selected,
        frameHome = state.frameHome,
        here = here,
        route = route,
        followKey = followKey,
        topPadding = statusBar,
        bottomPadding = peek,
        onYardClick = vm::select,
        modifier = Modifier.fillMaxSize(),
      )
      Column(
        Modifier.align(Alignment.TopEnd).padding(top = statusBar + 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        LocateButton(
          on = tracking && here != null,
          waiting = tracking && here == null,
          onClick = {
            when {
              tracking && route.status == RouteStatus.RECORDING -> followKey++ // re-centre instead of switching off mid-route
              tracking -> tracking = false
              hasLocationPermission(context) -> tracking = true
              else -> permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
          },
        )
        RecordButton(recording = route.status == RouteStatus.RECORDING, onClick = { onRecordClick() })
      }
      if (route.status != RouteStatus.IDLE) {
        RouteHud(
          route = route,
          layer = state.layer,
          onStop = { confirmStop = true },
          onClose = RouteRecorder::dismiss,
          modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = peek + 14.dp),
        )
      }
    }
  }
}

/** The record button under the locate button: a red ring, or a red square while recording. */
@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val red = Color(0xFFE4002B)
  Box(
    modifier
      .size(44.dp)
      .hardShadow(Ink, 3.dp)
      .background(Color(0xFFFBFBF8))
      .border(1.5.dp, Ink, RectangleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics {
        contentDescription = if (recording) "Stop recording route" else "Record a route"
        stateDescription = if (recording) "Recording" else "Off"
      },
    contentAlignment = Alignment.Center,
  ) {
    if (recording) Box(Modifier.size(16.dp).background(red))
    else Box(Modifier.size(20.dp).border(2.5.dp, red, CircleShape), contentAlignment = Alignment.Center) {
      Box(Modifier.size(8.dp).background(red, CircleShape))
    }
  }
}

private fun formatDuration(seconds: Long): String {
  val h = seconds / 3600
  val m = seconds % 3600 / 60
  val sec = seconds % 60
  return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

private fun formatDistance(metres: Double) =
  if (metres < 1000) "${metres.toInt()} m" else String.format(Locale.US, "%.1f km", metres / 1000)

/** The status card above the sheet while a route is recording (and after it ends): time, distance, new tiles. */
@Composable
private fun RouteHud(route: RouteUi, layer: Layer, onStop: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
  val c = LocalYardColors.current
  val track = route.track ?: return
  val recording = route.status == RouteStatus.RECORDING
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(recording) {
    while (recording) { now = System.currentTimeMillis(); delay(1000) }
  }
  val seconds = (((if (recording) now else route.endedAtMs) - route.startedAtMs) / 1000).coerceAtLeast(0)
  val other = Layer.entries.first { it != layer }
  val newHere = track.newTiles.getValue(layer).size
  val newOther = track.newTiles.getValue(other).size
  val crossed = track.crossed.getValue(layer).size

  Column(modifier.fillMaxWidth().hardShadow(c.shade).background(c.panel).border(1.5.dp, c.ink, RectangleShape)) {
    Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(10.dp).background(if (recording) Color(0xFFE4002B) else c.muted, CircleShape))
      Spacer(Modifier.width(8.dp))
      Label(if (recording) "Recording route" else "Route done", color = c.ink)
      Spacer(Modifier.weight(1f))
      TileButton(if (recording) "Stop" else "Close", if (recording) onStop else onClose, signal = !recording,
        modifier = Modifier.padding(end = 3.dp, bottom = 3.dp))
    }
    Row(Modifier.fillMaxWidth()) {
      HudCell("Time", formatDuration(seconds), Modifier.weight(1f))
      Box(Modifier.width(1.dp).height(60.dp).background(c.line))
      HudCell("Distance", formatDistance(track.distanceM), Modifier.weight(1f))
      HudCell("New tiles", "+$newHere", Modifier.weight(1f).background(Signal), Ink)
    }
    Text(
      "${layer.tileName}s: $crossed crossed, $newHere new · ${other.tileName}s: +$newOther new",
      Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      style = YardType.label.copy(fontSize = 10.5.sp, letterSpacing = 0.sp), color = c.muted, maxLines = 1,
    )
  }
}

/** The permissions still to ask for before a route can start: precise location, and notifications on Android 13+. */
private fun missingRoutePermissions(context: Context): Array<String> = buildList {
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
  }
  if (Build.VERSION.SDK_INT >= 33 &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
  ) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun SheetHandle() {
  val c = LocalYardColors.current
  Box(
    Modifier
      .fillMaxWidth()
      .drawBehind { drawRect(c.ink, Offset.Zero, Size(size.width, 1.5.dp.toPx())) }
      .padding(top = 10.dp, bottom = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.width(44.dp).height(5.dp).background(c.ink.copy(alpha = 0.35f)))
  }
}

@Composable
private fun LocateButton(on: Boolean, waiting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(44.dp)
      .hardShadow(Ink, 3.dp)
      .background(if (on) Signal else Color(0xFFFBFBF8))
      .border(1.5.dp, Ink, RectangleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics {
        contentDescription = "My location"
        stateDescription = if (on || waiting) "On" else "Off"
      },
    contentAlignment = Alignment.Center,
  ) {
    LocateIcon(if (waiting) Ink.copy(alpha = 0.4f) else Ink)
  }
}

private fun hasLocationPermission(context: Context) = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
  .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Streams GPS (and network) positions while [active]. */
@SuppressLint("MissingPermission") // only started after the permission is granted
@Composable
private fun LocationUpdates(active: Boolean, onLocation: (Location) -> Unit) {
  val context = LocalContext.current
  DisposableEffect(active) {
    if (!active || !hasLocationPermission(context)) return@DisposableEffect onDispose { }
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { onLocation(it) }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter(lm::isProviderEnabled)
    // A last-known position can be hours old or in another city; only use it if it's recent.
    providers.mapNotNull { lm.getLastKnownLocation(it) }
      .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 2 * 60 * 1_000_000_000L }
      .maxByOrNull { it.elapsedRealtimeNanos }?.let(onLocation)
    for (p in providers) lm.requestLocationUpdates(p, 2000L, 2f, listener, Looper.getMainLooper())
    onDispose { lm.removeUpdates(listener) }
  }
}

// ---------- Sheet: stats, layer switch and the standings ----------

private val SIZES = listOf(1 to "All", 3 to "3+", 5 to "5+", 10 to "10+")

@Composable
private fun Sheet(state: YardUiState, vm: YardViewModel, onPickFile: () -> Unit, onYardPicked: () -> Unit) {
  val c = LocalYardColors.current
  val yards = state.yards
  val name = state.layer.yardName
  val listed = yards.indices.filter(state::listed)
  val hidden = yards.size - listed.size
  val reported = state.analysis!!.reported[if (state.layer == Layer.SQUADRATINHOS) "yardinho" else "yard"]

  LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f).background(c.panel)) {
    item {
      Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Logo()
          Spacer(Modifier.width(10.dp))
          Text("Yard Explorer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f))
          Label("Open KML", Modifier.clickable(onClick = onPickFile).padding(6.dp), color = c.ink)
        }
        Spacer(Modifier.height(12.dp))
        Hud(state)
        Spacer(Modifier.height(10.dp))
        Text(
          "${fmt(yards.size)} ${name.lowercase()}s · top $TOP in colour · Away = more than ${REGION_KM} km from your best" +
            if (reported != null && reported == yards.firstOrNull()?.size) " · matches Squadrats ✓" else "",
          style = YardType.label.copy(fontSize = 11.sp, letterSpacing = 0.sp, lineHeight = 16.sp), color = c.muted,
        )
        Spacer(Modifier.height(12.dp))
        Segmented(
          options = Layer.entries.map { it to "${it.yardName}s" },
          selected = state.layer,
          onSelect = vm::setLayer,
        )
      }
      Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink))
      Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(Signal).border(1.5.dp, Ink, RectangleShape))
        Spacer(Modifier.width(9.dp))
        Text("STANDINGS", style = YardType.number.copy(fontSize = 12.sp, letterSpacing = 1.2.sp))
      }
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Toggle("Visited tiles", state.showVisited, vm::toggleVisited)
        Spacer(Modifier.weight(1f))
        for ((size, label) in SIZES) SmallSegment(label, state.minSize == size) { vm.setMinSize(size) }
      }
      Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Label("#", Modifier.width(40.dp))
        Label(name, Modifier.weight(1f))
        Label("Gap", Modifier.width(56.dp))
        Label("Tiles", Modifier.width(48.dp))
      }
    }
    items(listed, key = { it }) { rank ->
      val key = vm.placeKey(rank)
      StandingRow(
        rank = rank,
        tiles = yards[rank].size,
        leader = yards.first().size,
        title = key?.let(state.places::get) ?: name,
        away = state.map?.home?.contains(rank) == false,
        selected = state.selected == rank,
        onClick = { vm.select(rank); onYardPicked() },
      )
    }
    if (hidden > 0) item {
      Text(
        "${fmt(hidden)} smaller ${name.lowercase()}s hidden · change the size filter to see them",
        Modifier.padding(16.dp), style = YardType.label.copy(fontSize = 11.sp, letterSpacing = 0.sp), color = c.muted,
      )
    }
    item { Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)) }
  }
}

@Composable
private fun Hud(state: YardUiState) {
  val c = LocalYardColors.current
  val analysis = state.analysis!!
  val best = state.yards.firstOrNull()?.size ?: 0
  Row(Modifier.fillMaxWidth().hardShadow(c.shade).background(c.panel).border(1.5.dp, c.ink, RectangleShape)) {
    HudCell("Squadrats", fmt(analysis.layers[Layer.SQUADRATS]?.visited?.size ?: 0), Modifier.weight(1f))
    Box(Modifier.width(1.dp).height(60.dp).background(c.line))
    HudCell("Squadratinhos", fmt(analysis.layers[Layer.SQUADRATINHOS]?.visited?.size ?: 0), Modifier.weight(1.15f))
    HudCell("Best ${state.layer.yardName.lowercase()}", fmt(best), Modifier.weight(1f).background(Signal), Ink)
  }
}

@Composable
private fun HudCell(label: String, value: String, modifier: Modifier, color: Color = LocalYardColors.current.ink) =
  Column(modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
    Text(label.uppercase(), style = YardType.label.copy(fontSize = 9.5.sp, letterSpacing = 0.4.sp), color = color.copy(alpha = 0.7f), maxLines = 1)
    Spacer(Modifier.height(6.dp))
    Text(value, style = YardType.number.copy(fontSize = 22.sp, letterSpacing = (-0.5).sp), color = color, maxLines = 1)
  }

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
  val c = LocalYardColors.current
  Row(Modifier.fillMaxWidth().background(c.panel).border(1.5.dp, c.ink, RectangleShape)) {
    options.forEachIndexed { i, (value, label) ->
      if (i > 0) Box(Modifier.width(1.5.dp).height(38.dp).background(c.ink))
      val on = value == selected
      Box(
        Modifier.weight(1f).height(38.dp).background(if (on) c.ink else Color.Transparent)
          .clickable(role = Role.Tab) { onSelect(value) }
          .semantics { stateDescription = if (on) "Selected" else "Not selected" },
        contentAlignment = Alignment.Center,
      ) {
        Text(label.uppercase(), style = YardType.number.copy(fontSize = 12.sp, letterSpacing = 1.sp), color = if (on) c.panel else c.muted)
      }
    }
  }
}

@Composable
private fun SmallSegment(text: String, on: Boolean, onClick: () -> Unit) {
  val c = LocalYardColors.current
  Box(
    Modifier.background(if (on) c.ink else Color.Transparent).border(1.dp, if (on) c.ink else c.line, RectangleShape)
      .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
  ) {
    Text(text, style = YardType.number.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = if (on) c.panel else c.muted)
  }
}

@Composable
private fun Toggle(text: String, on: Boolean, onClick: () -> Unit) {
  val c = LocalYardColors.current
  Row(
    Modifier.clickable(role = Role.Checkbox, onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(14.dp).background(if (on) c.signalText else Color.Transparent).border(1.5.dp, c.ink, RectangleShape),
    )
    Spacer(Modifier.width(7.dp))
    Text(text, style = YardType.label.copy(fontSize = 11.5.sp, letterSpacing = 0.sp), color = c.muted)
  }
}

@Composable
private fun StandingRow(rank: Int, tiles: Int, leader: Int, title: String, away: Boolean, selected: Boolean, onClick: () -> Unit) {
  val c = LocalYardColors.current
  Row(
    Modifier
      .fillMaxWidth()
      .background(if (selected) c.hover else Color.Transparent)
      .drawBehind {
        drawRect(c.line, Offset.Zero, Size(size.width, 1.dp.toPx()))
        if (selected) drawRect(Signal, Offset.Zero, Size(4.dp.toPx(), size.height))
      }
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(40.dp)) { if (rank < TOP) Chip("${rank + 1}", rankColor(rank)) else PlainChip("${rank + 1}") }
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      if (rank == 0) Tag("Leader", lead = true)
      else if (away) Tag("Away")
    }
    Text(if (rank == 0) "—" else "+${fmt(leader - tiles)}", Modifier.width(56.dp), style = YardType.label.copy(fontSize = 11.5.sp, letterSpacing = 0.sp), color = c.muted)
    Text(fmt(tiles), Modifier.width(48.dp), style = YardType.number.copy(fontSize = 15.sp))
  }
}

@Composable
private fun Tag(text: String, lead: Boolean = false) {
  val c = LocalYardColors.current
  Text(
    buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.uppercase()) } },
    Modifier.padding(start = 8.dp).background(if (lead) Signal else Color.Transparent)
      .border(1.dp, if (lead) Ink else c.line, RectangleShape).padding(horizontal = 5.dp, vertical = 2.dp),
    style = YardType.label.copy(fontSize = 9.5.sp), color = if (lead) Ink else c.muted,
  )
}
