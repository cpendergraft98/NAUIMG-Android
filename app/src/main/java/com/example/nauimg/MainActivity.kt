package com.example.nauimg

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent;
import android.content.pm.PackageManager
import android.view.View;
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter

class MainActivity : AppCompatActivity() {
    private lateinit var gameSpinner: Spinner
    private lateinit var launchButton: Button
    private var selectedGame: String? = null

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameSpinner = findViewById(R.id.gameSpinner)
        launchButton = findViewById(R.id.launchButton)

        // Load HTML files from assets folder
        val assetManager = assets
        val games = assetManager.list("")?.filter { it.endsWith(".html") } ?: emptyList()

        // Set up spinner with the list of games
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, games)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gameSpinner.adapter = adapter

        gameSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long){
                selectedGame = parent.getItemAtPosition(position) as String
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedGame = null
            }
        }

        launchButton.setOnClickListener() {
            selectedGame?.let {
                val intent = Intent(this, Game0WebViewActivity::class.java).apply{
                    putExtra("FILENAME", it)
                }
                startActivity(intent)
            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        }
    }

    /*
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
    */
}