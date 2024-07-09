package com.example.nauimg

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.*
import org.json.JSONObject
import android.provider.Settings

// MainActivity class extends AppCompatActivity
class MainActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var gameSpinner: Spinner
    private lateinit var launchButton: Button
    private lateinit var viewModel: MainViewModel

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Store Android ID for research purposes
        // Generating an ID serverside may be better...
        // This will work for now, ignore warning
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Find views by their IDs
        gameSpinner = findViewById(R.id.gameSpinner)
        launchButton = findViewById(R.id.launchButton)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Load HTML files from assets folder
        val assetManager = assets
        val games = assetManager.list("")?.filter { it.endsWith(".html") } ?: emptyList()

        // Set up spinner with the list of games
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, games)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gameSpinner.adapter = adapter

        // Set an item selected listener for the gameSpinner spinner.
        gameSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // This method is called when an item in the spinner is selected.
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Update the selectedGame LiveData in the viewModel with the selected item's value.
                viewModel.selectedGame.value = parent.getItemAtPosition(position) as String
            }

            // This method is called when no item is selected in the spinner.
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Set the selectedGame LiveData in the viewModel to null when no item is selected.
                viewModel.selectedGame.value = null
            }
        }

        // Set click listener for launch button
        launchButton.setOnClickListener {
            viewModel.selectedGame.value?.let { game ->
                val intent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra("FILENAME", game)
                }
                startActivity(intent)
            }
        }

        // Observe changes in the selectedGame LiveData in the viewModel.
        viewModel.selectedGame.observe(this) { game: String? ->
            // Find the position of the selected game in the games list.
            val position = games.indexOf(game)

            // Check if the game is found in the list.
            if (position >= 0) {
                // Set the selection of the gameSpinner to the position of the selected game.
                gameSpinner.setSelection(position)
            }
        }

        // Check if location permission is granted, if not request permission
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        } else {
            // Start the LocationService if permission is already granted
            startLocationService()
        }
    }

    private fun startLocationService() {
        Log.d("MainActivity", "Starting LocationService...")
        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the LocationService
                Log.d("MainActivity", "Permission granted, starting LocationService...")
                startLocationService()
            } else {
                // Permission denied, handle appropriately
                Log.e("MainActivity", "Location permission not granted.")
            }
        }
    }
}