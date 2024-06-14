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
import com.google.android.gms.location.*

// MainActivity class extends AppCompatActivity
class MainActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var gameSpinner: Spinner
    private lateinit var launchButton: Button
    private var selectedGame: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

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
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
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
                val intent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra("FILENAME", it)
                }
                startActivity(intent)
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