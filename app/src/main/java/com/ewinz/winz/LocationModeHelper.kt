package com.ewinz.winz

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast

object LocationModeHelper {

    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }

    fun isGpsOnlyEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
               !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun isHighAccuracyEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun showCurrentMode(context: Context) {
        val mode = when {
            isHighAccuracyEnabled(context) -> "High Accuracy"
            isGpsOnlyEnabled(context) -> "GPS Only"
            else -> "Unknown or Battery Saving Mode"
        }
        Toast.makeText(context, "Mode Lokasi: $mode", Toast.LENGTH_SHORT).show()
    }
}
