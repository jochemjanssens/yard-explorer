package be.jochemjanssens.yardexplorer.route

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import be.jochemjanssens.yardexplorer.MainActivity
import be.jochemjanssens.yardexplorer.R
import be.jochemjanssens.yardexplorer.model.Layer
import java.util.Locale

/**
 * Keeps recording GPS positions into [RouteRecorder] while the app is in the background or the screen is off.
 * Android requires a visible notification for that; it shows the distance and the new tiles so far.
 */
class RouteService : Service() {
  private lateinit var locationManager: LocationManager
  private var lastNotificationMs = 0L

  private val listener = LocationListener { location ->
    RouteRecorder.onFix(location)
    if (System.currentTimeMillis() - lastNotificationMs > 8_000) updateNotification()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    val channel = NotificationChannel(CHANNEL, "Route recording", NotificationManager.IMPORTANCE_LOW)
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  }

  @SuppressLint("MissingPermission") // started only after the location permission was granted
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    try {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
    } catch (e: SecurityException) {
      RouteRecorder.stop()
      stopSelf()
    }
    return START_NOT_STICKY // if Android kills the app, the route isn't silently resumed without anyone watching
  }

  override fun onDestroy() {
    locationManager.removeUpdates(listener)
    super.onDestroy()
  }

  private fun updateNotification() {
    lastNotificationMs = System.currentTimeMillis()
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
  }

  private fun notification(): Notification {
    val track = RouteRecorder.ui.value.track
    val km = (track?.distanceM ?: 0.0) / 1000
    val inhos = track?.newTiles?.get(Layer.SQUADRATINHOS)?.size ?: 0
    val squadrats = track?.newTiles?.get(Layer.SQUADRATS)?.size ?: 0
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("Recording route")
      .setContentText(String.format(Locale.US, "%.1f km · %d new squadratinhos · %d new squadrats", km, inhos, squadrats))
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .build()
  }

  companion object {
    private const val CHANNEL = "route"
    private const val NOTIFICATION_ID = 1

    /** Starts recording a new route. Call while the app is visible, with the location permission granted. */
    fun start(context: Context) {
      RouteRecorder.start()
      ContextCompat.startForegroundService(context, Intent(context, RouteService::class.java))
    }

    fun stop(context: Context) {
      RouteRecorder.stop()
      context.stopService(Intent(context, RouteService::class.java))
    }
  }
}
