package io.github.jochemjanssens.yardexplorer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import io.github.jochemjanssens.yardexplorer.ui.YardExplorerApp
import io.github.jochemjanssens.yardexplorer.ui.YardViewModel
import io.github.jochemjanssens.yardexplorer.ui.theme.YardExplorerTheme
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class MainActivity : ComponentActivity() {
  private val vm: YardViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    identifyToTileServer()
    if (savedInstanceState == null) openFrom(intent)
    setContent {
      YardExplorerTheme {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
        YardExplorerApp(vm, onPickFile = { picker.launch(arrayOf("application/vnd.google-earth.kml+xml", "*/*")) })
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    openFrom(intent)
  }

  /** "Open with Yard Explorer" from a file manager, or a KML shared to the app. */
  private fun openFrom(intent: Intent?) {
    val uri = when (intent?.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      else -> null
    } ?: return
    vm.open(uri)
  }

  /** OpenStreetMap's tile policy asks apps to identify themselves with their own User-Agent. */
  private fun identifyToTileServer() {
    MapLibre.getInstance(applicationContext) // must exist before its HTTP client is replaced
    HttpRequestUtil.setOkHttpClient(
      OkHttpClient.Builder().addNetworkInterceptor { chain ->
        chain.proceed(
          chain.request().newBuilder()
            .header("User-Agent", "YardExplorer/${BuildConfig.VERSION_NAME} (Android; +https://github.com/jochemjanssens/yard-explorer)")
            .build(),
        )
      }.build(),
    )
  }
}
