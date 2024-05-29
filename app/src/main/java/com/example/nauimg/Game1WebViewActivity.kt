package com.example.nauimg

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class Game1WebViewActivity : AppCompatActivity() {
    private  lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game1_web_view)

        webView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccess = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        webView.loadUrl("file:///android_asset/Game1.html")
    }

    fun btnReturnHandler(view: View){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}