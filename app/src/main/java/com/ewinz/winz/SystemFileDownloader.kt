package com.ewinz.winz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SystemFileDownloader {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; SM-G960F Build/RQ1A.210105.003) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"

    // Daftar server XTRA (urutan prioritas)
    private val xtraUrls = listOf(
        "http://xtrapath1.izatcloud.net/xtra2.bin",
        "https://gllto.glpals.com/7day/v5/latest/lto2.dat",     
        "https://gllto1.glpals.com/7day/v4/latest/lto2.dat",      
        "http://xtrapath2.izatcloud.net/xtra2.bin",
        "http://xtrapath3.izatcloud.net/xtra2.bin",
        "http://xtra1.gpsonextra.net/xtra.bin",
        "http://xtra3.gpsonextra.net/xtra2.bin",
        "http://xtra2.gpsonextra.net/xtra2.bin",
        "https://gllto1.glpals.com/7day/v5/latest/lto2.dat",
        "https://gllto2.glpals.com/7day/v5/latest/lto2.dat",
        "https://gllto.glpals.com/7day/v4/latest/lto2.dat",
        "https://gllto2.glpals.com/7day/v4/latest/lto2.dat",
        "https://gllto1.glpals.com/7day/v3/latest/lto2.dat",
        "https://gllto2.glpals.com/7day/v3/latest/lto2.dat"
    )

    suspend fun downloadXtraFile(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) {
            Log.w("Download", "No network")
            return@withContext false
        }

        val targetFile = File(context.filesDir, "winz_xtra.bin")
        val tempFile = File(context.cacheDir, "winz_xtra_temp.bin")

        for (urlStr in xtraUrls) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "keep-alive")
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempFile.length() > 0) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        targetFile.setReadable(true, false)
                        Log.i("Download", "Saved to ${targetFile.absolutePath}")
                        return@withContext true
                    }
                } else {
                    Log.w("Download", "Server failed: $urlStr -> ${connection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("Download", "Error: $urlStr -> ${e.message}")
            }
        }

        return@withContext false
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(network) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}