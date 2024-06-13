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
}