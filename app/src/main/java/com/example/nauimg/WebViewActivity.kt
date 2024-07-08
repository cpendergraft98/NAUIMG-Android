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
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        // Set click listener for return button
        returnButton.setOnClickListener {
            // Create intent to return to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}