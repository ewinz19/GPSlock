package com.ewinz.winz

import com.ewinz.winz.databinding.ActivityMainBinding

import kotlin.math.abs
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class GpsTrackingService : Service() {
    private var intervalMs: Long = 3000L
    private var distance: Float = 0f
    private var isSearching = true
    private var gnssFix = false
    private var satelliteCount = 0

    private var lastSatelliteCount = -1
    private var lastGnssFix = false
    private var lastNotificationContent: String? = null

    private lateinit var locationManager: LocationManager
    private val CHANNEL_ID = "gps_tracking_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var locationListener: LocationListener
    private lateinit var gnssStatusCallback: GnssStatus.Callback

 
        


    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        registerGnssStatus()
    }

override fun onBind(intent: Intent?): IBinder? {
    return null
}


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMs = intent?.getLongExtra("interval", 3000L) ?: 3000L
        distance = intent?.getFloatExtra("distance", 0f) ?: 0f
        isSearching = true

        startForeground(NOTIFICATION_ID, createNotification(getNotificationContent(true)))
        startGpsUpdates()
        return START_STICKY
    }

    private fun startGpsUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
  
    
   
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                processLocation(location)
            }

            override fun onProviderDisabled(provider: String) {
                updateSearchStatus(true)
                updateNotificationIfChanged(getNotificationContent(true))
            }

            override fun onProviderEnabled(provider: String) {
                updateSearchStatus(true)
                updateNotificationIfChanged(getNotificationContent(true))
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }


        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                distance,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("GPS-Lock", "GPS fallback failed", e)
        }
    }

    private fun processLocation(location: Location) {
        val wasSearching = isSearching
        isSearching = location.accuracy > 10f

        if (wasSearching != isSearching) {
            updateNotificationIfChanged(getNotificationContent(isSearching, location))
        }

        saveLastLocation(location)
        sendLocationToUI(location)
    }


    private fun getNotificationContent(searching: Boolean, location: Location? = null): String {
        val gnssStatus = if (gnssFix) "GNSS: ✅ Fix" else "GNSS: ❌ No Fix"
        val satInfo = "Satelit: $satelliteCount"

        return if (searching) {
            "🔍 Mencari..📡.\n$gnssStatus | $satInfo\nInterval: ${intervalMs}ms | Jarak: ${distance}m"
        } else {
            location?.let {
                "🔒GPS Terkunci✅\n$gnssStatus | $satInfo\nLat: ${"%.6f".format(it.latitude)}\nLon: ${"%.6f".format(it.longitude)}\nAcc: ${"%.1f".format(it.accuracy)}m"
            } ?: run {
                val lastLoc = getLastKnownLocation()
                "📡GPS Terkunci🧭\n$gnssStatus | $satInfo\nLat: ${"%.6f".format(lastLoc?.latitude ?: 0.0)}\nLon: ${"%.6f".format(lastLoc?.longitude ?: 0.0)}\nAcc: ${"%.1f".format(lastLoc?.accuracy ?: 0f)}m"
            }
        }
    }


/*
private fun getNotificationContent(searching: Boolean, location: Location? = null): String {
    val gnssStatus = if (gnssFix) "GNSS: ✅ Fix" else "GNSS: ❌ No Fix"
    val satInfo = "Satelit: $satelliteCount"

    return if (gnssFix && !searching) {
        // GPS dianggap benar-benar terkunci
        location?.let {
            "🔒 GPS Terkunci✅\n$gnssStatus | $satInfo\nLat: ${"%.6f".format(it.latitude)}\nLon: ${"%.6f".format(it.longitude)}\nAcc: ${"%.1f".format(it.accuracy)}m"
        } ?: run {
            val lastLoc = getLastKnownLocation()
            "🔒 GPS Terkunci (Cache)\n$gnssStatus | $satInfo\nLat: ${"%.6f".format(lastLoc?.latitude ?: 0.0)}\nLon: ${"%.6f".format(lastLoc?.longitude ?: 0.0)}\nAcc: ${"%.1f".format(lastLoc?.accuracy ?: 0f)}m"
        }
    } else {
        // Masih mencari, atau fix belum tersedia
        "🔍 Mencari...\n$gnssStatus | $satInfo\nInterval: ${intervalMs}ms | Jarak: ${distance}m"
    }
}

*/



    private fun registerGnssStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onStarted() {
                    gnssFix = false
                    updateNotificationIfChanged(getNotificationContent(true))
                }

                override fun onStopped() {
                    gnssFix = false
                    satelliteCount = 0
                    updateNotificationIfChanged(getNotificationContent(true))
                }

                override fun onFirstFix(ttffMillis: Int) {
                    gnssFix = true
                    updateNotificationIfChanged(getNotificationContent(false))
                }

                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var used = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) used++
                    }

                    val significantChange = abs(used - lastSatelliteCount) >= 2
                    satelliteCount = used

                    if (significantChange || gnssFix != lastGnssFix) {
                        lastSatelliteCount = used
                        lastGnssFix = gnssFix
                        updateNotificationIfChanged(getNotificationContent(isSearching))
                        
                }
            }
}
            locationManager.registerGnssStatusCallback(
                gnssStatusCallback,
                Handler(Looper.getMainLooper())
            )
        }
    }

    private fun updateSearchStatus(searching: Boolean) {
        isSearching = searching
    }

    private fun saveLastLocation(location: Location) {
        val prefs = getSharedPreferences("GpsPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_location", "${location.latitude},${location.longitude}")
            .putFloat("last_accuracy", location.accuracy)
            .apply()
    }

    private fun getLastKnownLocation(): Location? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else {
            val prefs = getSharedPreferences("GpsPrefs", Context.MODE_PRIVATE)
            prefs.getString("last_location", null)?.let {
                val parts = it.split(",")
                Location("cached").apply {
                    latitude = parts[0].toDouble()
                    longitude = parts[1].toDouble()
                    accuracy = prefs.getFloat("last_accuracy", 0f)
                }
            }
        }
    }

    private fun sendLocationToUI(location: Location) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val intent = Intent("GPS_LOCATION_UPDATE").apply {
            putExtra("lat", location.latitude)
            putExtra("lon", location.longitude)
            putExtra("acc", location.accuracy)
            putExtra("time", time)
            putExtra("intervalInfo", "Interval: ${intervalMs}ms")
            putExtra("distanceInfo", "Jarak: ${distance}m")
            putExtra("isSearching", isSearching)
            putExtra("satelliteCount", satelliteCount)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(content: String) {
        val notif = createNotification(content)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notif)
    }

    private fun updateNotificationIfChanged(content: String) {
        if (content != lastNotificationContent) {
            lastNotificationContent = content
            updateNotification(content)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isSearching) "Mencari GPS" else "GPS Terkunci")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Lock Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "GPS Lock Status" }

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    private fun stopTracking() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e("GpsTrackingService", "removeUpdates error", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            } catch (e: Exception) {
                Log.e("GpsTrackingService", "unregisterGnssStatusCallback error", e)
            }
        }
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    stopForeground(Service.STOP_FOREGROUND_REMOVE)
} else {
    stopForeground(true)
}
        
        stopSelf()
    }
}