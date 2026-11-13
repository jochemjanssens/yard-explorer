package io.github.jochemjanssens.yardexplorer.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Place names for yards, from Android's built-in geocoder, cached on the device by coordinate
 * (rounded to ~100 m) so each yard is looked up once.
 */
class PlaceNames(context: Context) {
  private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)
  private val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

  fun key(lat: Double, lon: Double) = "%.3f,%.3f".format(Locale.ROOT, lat, lon)

  fun cached(key: String): String? = prefs.getString(key, null)

  /** Looks up a name, or returns null when the geocoder is unavailable or has no answer. */
  suspend fun lookup(lat: Double, lon: Double): String? {
    val k = key(lat, lon)
    cached(k)?.let { return it }
    val address = geocoder?.let { first(it, lat, lon) } ?: return null
    val name = address.locality ?: address.subLocality ?: address.subAdminArea ?: address.featureName ?: return null
    prefs.edit().putString(k, name).apply()
    return name
  }

  private suspend fun first(g: Geocoder, lat: Double, lon: Double): Address? = try {
    if (Build.VERSION.SDK_INT >= 33) {
      suspendCancellableCoroutine { cont ->
        g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
          override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
          override fun onError(errorMessage: String?) = cont.resume(null)
        })
      }
    } else {
      @Suppress("DEPRECATION")
      withContext(Dispatchers.IO) { g.getFromLocation(lat, lon, 1)?.firstOrNull() }
    }
  } catch (e: Exception) {
    null // offline or the service is unavailable: the row keeps its plain name
  }
}
