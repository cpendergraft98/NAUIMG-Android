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
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.*

// MainActivity class extends AppCompatActivity
class MainActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var gameSpinner: Spinner
    private lateinit var launchButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var viewModel: MainViewModel

    companion object {
        var latestLocation: Location? = null
    }

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create a location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateIntervalMillis(1000)
            setMaxUpdateDelayMillis(1000)
        }.build()

        // Define the location callback
        locationCallback = object : LocationCallback() {
            // Called when a new location update is received
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation
            }
        }

        // Check if location permission is granted, if not request permission
        checkLocationPermission()
    }

    // Method to check location permission
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), Constants.PERMISSIONS_REQUEST_LOCATION)
        } else {
            // Start location updates if permission is already granted
            startLocationUpdates()
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                startLocationUpdates()
            } else {
                // Permission denied, handle appropriately
                Log.e("MainActivity", "Location permission not granted.")
            }
        }
    }

    // Start location updates if permission is granted
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    // Stop location updates
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // Stop location updates when activity is paused
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    // Resume location updates when activity is resumed
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
}