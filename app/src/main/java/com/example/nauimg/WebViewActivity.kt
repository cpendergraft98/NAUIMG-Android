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

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        webView = findViewById(R.id.webView)

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccess = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback){
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                webView.reload()
            }
        }
    }

    fun btnReturnHandler(view: View){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    @JavascriptInterface
    fun getRandomNumber(): Int{
        return (1..100).random()
    }
}

// Define the WebAppInterface
interface WebAppInterface {
    @JavascriptInterface
    fun getRandomNumber(): Int
}