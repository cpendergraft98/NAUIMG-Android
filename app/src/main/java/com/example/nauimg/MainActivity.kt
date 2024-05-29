package com.example.nauimg

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent;
import android.content.pm.PackageManager
import android.view.View;
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        }
    }

    // Instead of having multiple webView activities, can I dynamically load a different game in one
    // webview? Have them select what game they want to play from a dropdown, press a single button to launch it
    // and have that filename passed into the Webview activity? Use the constants.kt file?

    fun btnGame0Handler(view: View){
        val intent = Intent(this, Game0WebViewActivity::class.java)
        startActivity(intent)
    }

    fun btnGame1Handler(view: View){
        val intent = Intent(this, Game1WebViewActivity::class.java)
        startActivity(intent)
    }

    fun btnGame2Handler(view: View){
        val intent = Intent(this, Game2WebViewActivity::class.java)
        startActivity(intent)
    }
}