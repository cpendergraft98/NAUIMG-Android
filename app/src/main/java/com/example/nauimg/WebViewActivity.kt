package com.example.nauimg

import android.content.Intent
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Random
import android.widget.Button
import android.annotation.SuppressLint

// WebViewActivity class extends AppCompatActivity
class WebViewActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var webView: WebView
    private lateinit var returnButton: Button

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        // Find views by their IDs
        webView = findViewById(R.id.webView)
        returnButton = findViewById(R.id.returnButton)

        // Configure WebView settings
        val webSettings = webView.settings
        @SuppressLint("SetJavaScriptEnabled") // The warning for JavaScriptEnabled is a nonissue
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccess = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.mediaPlaybackRequiresUserGesture = false

        // Set WebChromeClient for handling geolocation permissions
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, true, false)
            }
        }

        // Load HTML file specified in the intent
        val filename = intent.getStringExtra("FILENAME")
        if (filename != null) {
            webView.webViewClient = WebViewClient()
            webView.loadUrl("file:///android_asset/$filename")
        }

        // Inject Javascript interfaces into WebView
        webView.addJavascriptInterface(this, "AndroidBridge")

        // Set click listener for return button
        returnButton.setOnClickListener {
            // Create intent to return to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    // Generate random metrics and return as JSON string
    @JavascriptInterface
    fun generateMetrics(): String {
        val random = Random()

        val uploadSpeed = random.nextInt((100 - 1) + 1) + 1 // Mbps
        val downloadSpeed = random.nextInt((100 - 1) + 1) + 1 // Mbps
        val jitter = random.nextInt((50 - 1) + 1) + 1 // ms
        val packetLoss = random.nextInt(100) // %
        val latency = random.nextInt((100 - 1) + 1) + 1 // ms

        val metricJSON = JSONObject()
        metricJSON.put("uploadSpeed", uploadSpeed)
        metricJSON.put("downloadSpeed", downloadSpeed)
        metricJSON.put("jitter", jitter)
        metricJSON.put("packetLoss", packetLoss)
        metricJSON.put("latency", latency)

        return metricJSON.toString()
    }

    // Get latest location and return as JSON string
    @JavascriptInterface
    fun getLocationJSON(): String {
        val locationJSON = JSONObject()
        MainActivity.latestLocation?.let {
            locationJSON.put("latitude", it.latitude)
            locationJSON.put("longitude", it.longitude)
        } ?: run {
            locationJSON.put("latitude", null)
            locationJSON.put("longitude", null)
        }
        return locationJSON.toString()
    }
}