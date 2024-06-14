package com.example.nauimg

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter

// MainActivity class extends AppCompatActivity
class MainActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var gameSpinner: Spinner
    private lateinit var launchButton: Button
    private var selectedGame: String? = null

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views by their IDs
        gameSpinner = findViewById(R.id.gameSpinner)
        launchButton = findViewById(R.id.launchButton)

        // Load HTML files from assets folder
        val assetManager = assets
        val games = assetManager.list("")?.filter { it.endsWith(".html") } ?: emptyList()

        // Set up spinner with the list of games
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, games)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gameSpinner.adapter = adapter

        // Set item selected listener for spinner
        gameSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Called when item in spinner is selected
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long){
                selectedGame = parent.getItemAtPosition(position) as String
            }

            // Called when no item is selected in spinner
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedGame = null
            }
        }

        // Set click listener for launch button
        launchButton.setOnClickListener {
            selectedGame?.let {
                // Create intent to start WebViewActivity with selected game file
                val intent = Intent(this, WebViewActivity::class.java).apply{
                    putExtra("FILENAME", it)
                }
                startActivity(intent)
            }
        }

        // Request location permission if not granted
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf
                (Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        }
    }
}