package com.example.nauimg

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Random
import com.google.android.gms.location.*
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.util.Log
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationRequest

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var latestLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        webView = findViewById(R.id.webView)

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply{
            setMinUpdateIntervalMillis(5000)
            setMaxUpdateDelayMillis(5000)
        }.build()

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation
            }
        }

        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        } else {
            startLocationUpdates()
        }

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccess = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String, callback: GeolocationPermissions.Callback){
                callback.invoke(origin, true, false)
            }
        }

        val filename = intent.getStringExtra("FILENAME")
        if (filename != null) {
            webView.webViewClient = WebViewClient()
            webView.loadUrl("file:///android_asset/$filename")
        }

        // Inject the interface into WebView
        webView.addJavascriptInterface(this, "AndroidBridge")
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, ContextCompat.getMainExecutor(this), locationCallback)
        } else {
            Log.e("WebViewActivity", "Location permission not granted.")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        }
    }

    private fun stopLocationUpdates(){
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    // Ignore warning that view is not used, btnReturnHandler does not work without it and I do not
    // know why
    fun btnReturnHandler(view: View){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    @JavascriptInterface
    fun generateMetrics(): String{
        val random = Random()

        val uploadSpeed = random.nextInt((100 - 1) + 1) + 1 //Mbs
        val downloadSpeed = random.nextInt((100 - 1) + 1) + 1 //Mbs
        val jitter = random.nextInt((50 - 1) + 1) + 1 //ms
        val packetLoss = random.nextInt(100) //%
        val latency = random.nextInt((100 - 1) + 1) + 1 //ms

        val metricJSON = JSONObject()
        metricJSON.put("uploadSpeed", uploadSpeed)
        metricJSON.put("downloadSpeed", downloadSpeed)
        metricJSON.put("jitter", jitter)
        metricJSON.put("packetLoss", packetLoss)
        metricJSON.put("latency", latency)

        return metricJSON.toString()
    }

    @JavascriptInterface
    fun getLocationJSON(): String {
        val locationJSON = JSONObject()
        latestLocation?.let {
            locationJSON.put("latitude", it.latitude)
            locationJSON.put("longitude", it.longitude)
        } ?: run {
            locationJSON.put("latitude", null)
            locationJSON.put("longitude", null)
        }
        return locationJSON.toString()
    }
}