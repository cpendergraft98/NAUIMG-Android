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
import java.util.Random

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
    fun generateMetrics(): String{
        val random = Random()
        // This is the weirdest methodology for establishing a range of values to generate
        // a random number within that I have ever seen, but apparently this is how random.nextInt()
        // works??
        val uploadSpeed = random.nextInt((100 - 1) + 1) + 1 //Mbs
        val downloadSpeed = random.nextInt((100 - 1) + 1) + 1 //Mbs
        val jitter = random.nextInt((50 - 1) + 1) + 1 //ms
        val packetLoss = random.nextInt(100) //%
        val latency = random.nextInt((100 - 1) + 1) + 1 //ms

        return "Upload Speed = $uploadSpeed Mbs \n" +
                "Download Speed = $downloadSpeed Mbs \n" +
                "Jitter = $jitter ms \n" +
                "Packet Loss = $packetLoss % \n" +
                "Latency = $latency ms "
    }
}

// Define the WebAppInterface
interface WebAppInterface {
    @JavascriptInterface
    fun generateMetrics(): String
}