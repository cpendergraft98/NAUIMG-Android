package com.example.nauimg

import android.content.Intent
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.google.firebase.firestore.FirebaseFirestore

// WebViewActivity class extends AppCompatActivity
class WebViewActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var webView: WebView
    private lateinit var returnButton: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sessionId: String
    private var locationService: LocationService? = null
    private var isBound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isBound = true

            // Pass the WebView instance to the LocationService
            locationService?.setWebView(webView)

            // Add the Javascript Interface after the locationService is guaranteed to be initialized
            webView.addJavascriptInterface(WebAppInterface(this@WebViewActivity, firestore, sessionId, locationService!!), "AndroidBridge")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        firestore = FirebaseFirestore.getInstance()
        sessionId = intent.getStringExtra("SESSION_ID") ?: "" // Retrieve the session ID passed from MainActivity


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


        // Set click listener for return button
        returnButton.setOnClickListener {
            // Create intent to return to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if(isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}