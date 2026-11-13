package be.jochemjanssens.yardexplorer.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Keeps the last opened Squadrats export in the app's private storage, so the map is ready next time. */
class ExportStore(private val context: Context) {
  private val file = File(context.filesDir, "latest.kml")

  fun load(): String? = file.takeIf { it.exists() }?.readText()

  /** Reads a KML the user picked or shared with the app; returns null if it isn't one. */
  fun read(uri: Uri): String? {
    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return null
    return text.takeIf { "<kml" in it.take(500) }
  }

  fun save(kml: String) = file.writeText(kml)
}
