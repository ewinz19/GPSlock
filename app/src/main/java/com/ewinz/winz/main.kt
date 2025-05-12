package com.ewinz.winz

import com.ewinz.winz.databinding.ActivityMainBinding


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.widget.EditText
import android.os.Build
import android.net.Uri
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader


import android.content.*
import android.widget.*



import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle

import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: Button
    private lateinit var tvLocation: TextView
    private lateinit var intervalInput: EditText
    private lateinit var distanceInput: EditText
    private var isTracking = false
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_XTRA_PERMISSION = 1002
    private var pendingXtraAction: (() -> Unit)? = null

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("lat", 0.0)
            val lon = intent?.getDoubleExtra("lon", 0.0)
            val acc = intent?.getFloatExtra("acc", 0f)
            val time = intent?.getStringExtra("time")

            val intervalInfo = intent?.getStringExtra("intervalInfo") ?: ""
            val distanceInfo = intent?.getStringExtra("distanceInfo") ?: ""
            val satelliteCount = intent?.getIntExtra("satelliteCount", 0) ?: 0

           tvStatus.text = "Lokasi terkunci"
            tvLocation.text = """
                Lat: $lat
                Lon: $lon
                Akurasi: $acc m
                Waktu: $time
                $intervalInfo
                $distanceInfo
                Satelit: $satelliteCount
            """.trimIndent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init drawer
        toggle = ActionBarDrawerToggle(this, binding.drawerLayout, R.string.open, R.string.close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(binding.navView)
        }

        binding.navView.setNavigationItemSelectedListener {
            true
        }

        // Elemen utama di luar drawer
        btnStartStop = binding.btnStartStop
        tvLocation = binding.tvLocation
        tvStatus = binding.tvStatus

        // Tombol tracking
        btnStartStop.setOnClickListener {
            if (isTracking) stopTracking() else startTracking()
        }

        // Elemen di dalam drawer
        val header = binding.navView.getHeaderView(0)
        intervalInput = header.findViewById(R.id.intervalInput)
        distanceInput = header.findViewById(R.id.distanceInput)

        val button1 = header.findViewById<Button>(R.id.button1)
        val button2 = header.findViewById<Button>(R.id.button2)
        val button3 = header.findViewById<Button>(R.id.button3)
        val button4 = header.findViewById<Button>(R.id.button4)
        val button5 = header.findViewById<Button>(R.id.button5)



        
        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val savedInterval = prefs.getLong("interval_sec", 3L)
        val savedDistance = prefs.getFloat("distance", 0f)
        intervalInput.setText(savedInterval.toString())
        distanceInput.setText(savedDistance.toString())

        button1.setOnClickListener {
            lifecycleScope.launch {
                val success = SystemFileDownloader.downloadXtraFile(this@MainActivity)
                runOnUiThread {
                    if (success) {

                        Toast.makeText(this@MainActivity, "Succes Download Xtra-Gps", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal. Cek error/cek internet.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        button2.setOnClickListener {
                                    val xtraInjector = XtraInjector(this@MainActivity)
                        xtraInjector.injectCustomXtra()
                        xtraInjector.logXtraMetadata()
                        
Toast.makeText(this@MainActivity, "start. inject XTRA.", Toast.LENGTH_SHORT).show()
        }
        
        button3.setOnClickListener {
            requestXtraPermission()

        }
        button4.setOnClickListener {
            LocationModeHelper.showCurrentMode(this)
        }

       button5.setOnClickListener {
            LocationModeHelper.openLocationSettings(this)
        }



        registerReceiver(locationReceiver, IntentFilter("GPS_LOCATION_UPDATE"))
    }

    override fun onDestroy() {
        unregisterReceiver(locationReceiver)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        isTracking = prefs.getBoolean("isTracking", false)
        updateUiForTracking(isTracking)
    }


/*     #######     */


    private fun startTracking() {
    when {
        // 1. Cek jika GPS tidak aktif
        !isGpsEnabled() -> {
            showGpsDisabledAlert()
        }

        // 2. Cek permission
        !checkPermissions() -> {
            requestPermissions()
        }

        // 3. Semua kondisi terpenuhi
        else -> {
            try {
                // Ambil input (dalam detik)
                val intervalSec = intervalInput.text.toString().toLongOrNull() ?: 3L
                val distance = distanceInput.text.toString().toFloatOrNull() ?: 0f

                // Simpan ke SharedPreferences
                getSharedPreferences("tracking_prefs", MODE_PRIVATE).edit().apply {
                    putLong("interval_sec", intervalSec)
                    putFloat("distance", distance)
                    putBoolean("isTracking", true)
                    apply()
                }

                val intervalMs = intervalSec * 1000  // Konversi ke milidetik untuk LocationManager

                // Kirim ke service
                val serviceIntent = Intent(this, GpsTrackingService::class.java).apply {
                    putExtra("interval", intervalMs)
                    putExtra("distance", distance)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                updateUiForTracking(true)

            } catch (e: Exception) {
                handleTrackingError(e)
            }
        }
    }
}


private fun isGpsEnabled(): Boolean {
    val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}

private fun showGpsDisabledAlert() {
    AlertDialog.Builder(this)
        .setTitle("GPS Tidak Aktif")
        .setMessage("Aktifkan GPS untuk melanjutkan tracking")
        .setPositiveButton("Buka Pengaturan") { _, _ ->
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        .setNegativeButton("Batal", null)
        .show()
}

private fun updateUiForTracking(isTracking: Boolean) {
    this.isTracking = isTracking
    btnStartStop.text = if (isTracking) "STOP" else "START"
    tvStatus.text = if (isTracking) "Mencari sinyal GPS..." else "Tracking dihentikan"
}

private fun handleTrackingError(e: Exception) {
    Log.e("Tracking", "Gagal memulai tracking", e)
    updateUiForTracking(false)
    Toast.makeText(
        this, 
        "Gagal memulai tracking: ${e.localizedMessage}", 
        Toast.LENGTH_LONG
    ).show()
}



    private fun stopTracking() {
        stopService(Intent(this, GpsTrackingService::class.java))
        isTracking = false
        btnStartStop.text = "START"
        tvStatus.text = "Tracking dihentikan"
        tvLocation.text = ""
    }

private fun withXtraPermission(action: () -> Unit) {
    if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        action()
    } else {
        pendingXtraAction = action
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS),
            REQUEST_XTRA_PERMISSION
        )
    }
}

private fun checkPermissions(): Boolean {
    val fineLocationGranted = ContextCompat.checkSelfPermission(
        this, 
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    
    // Background location hanya diperlukan untuk Android 10 (Q) ke atas
    val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Untuk versi di bawah Android 10, dianggap granted
    }
    
    return fineLocationGranted && backgroundLocationGranted
}

private fun requestPermissions() {
    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // Hanya request background location jika Android 10+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    
    ActivityCompat.requestPermissions(
        this,
        permissionsToRequest.toTypedArray(),
        LOCATION_PERMISSION_REQUEST_CODE
    )
}


override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    when (requestCode) {
        LOCATION_PERMISSION_REQUEST_CODE -> {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startTracking()
            } else {
                showPermissionDeniedMessage()
            }
        }

        REQUEST_XTRA_PERMISSION -> {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingXtraAction?.invoke()
                pendingXtraAction = null
            } else {
                Toast.makeText(this, "Izin XTRA ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun showPermissionDeniedMessage() {
    AlertDialog.Builder(this)
        .setTitle("Izin Diperlukan")
        .setMessage("Aplikasi membutuhkan izin lokasi untuk berfungsi dengan baik")
        .setPositiveButton("Buka Pengaturan") { _, _ ->
            openAppSettings()
        }
        .setNegativeButton("Tutup", null)
        .show()
}

private fun openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

private fun requestXtraPermission() {
    withXtraPermission {
        executeDeleteAidingData()
        executePsdsInjection()
        
    }
}



    
  

private fun executeDeleteAidingData() {
    try {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.sendExtraCommand(
            LocationManager.GPS_PROVIDER,
            "delete_aiding_data",
            null
        )
        Toast.makeText(this, "XTRA delete_aiding_data sent", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "Gagal kirim XTRA delete_aiding_data: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun executePsdsInjection() {
    try {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.sendExtraCommand(
            LocationManager.GPS_PROVIDER,
            "force_psds_injection",
            null
        )
        Toast.makeText(this, "XTRA force_psds_injection sent", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "Gagal kirim XTRA force_psds_injection: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

}
