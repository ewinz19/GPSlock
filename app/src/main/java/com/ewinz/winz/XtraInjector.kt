package com.ewinz.winz

import android.location.LocationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import java.io.File

class XtraInjector(private val context: Context) {

    private val xtraFile = File(context.filesDir, "winz_xtra.bin")

    fun injectCustomXtra() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

Log.d("XTRA_INJECT", "context.filesDir = ${context.filesDir.absolutePath}")

Log.d("XTRA_INJECT", "File exists: ${xtraFile.exists()} readable: ${xtraFile.canRead()}")

        

        if (!xtraFile.exists() || !xtraFile.canRead()) {
            Log.e("XTRA_INJECT", "File XTRA tidak ditemukan atau tidak dapat dibaca!")
            return
        }

        try {
            val xtraData = xtraFile.readBytes()
            val extras = Bundle().apply {
                putByteArray("data", xtraData)
                putLong("expiration", System.currentTimeMillis() + 3600000)
            }

            val success = locationManager.sendExtraCommand(
                LocationManager.GPS_PROVIDER,
                "force_psds_injection",
                extras



            )

            if (success) {
                Log.d("XTRA_INJECT", "Injeksi XTRA berhasil!")
              
          //      locationManager.sendExtraCommand("gps", "force_time_injection", null)
            } else {
                Log.e("XTRA_INJECT", "Gagal eksekusi perintah injeksi")
            }

        } catch (e: SecurityException) {
            Log.e("XTRA_INJECT", "Izin ditolak", e)
        } catch (e: Exception) {
            Log.e("XTRA_INJECT", "Error: ${e.message}", e)
        }
    }

    fun logXtraMetadata() {
        Log.d("XTRA_INJECT", """
            ===== XTRA File Info =====
            Path: ${xtraFile.absolutePath}
            Size: ${xtraFile.length()} bytes
            Modified: ${xtraFile.lastModified()}
        """.trimIndent())
    }
}