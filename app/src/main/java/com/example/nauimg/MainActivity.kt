package com.example.nauimg

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

// MainActivity class extends AppCompatActivity
class MainActivity : AppCompatActivity() {
    // Initialize variables
    private lateinit var gameRecyclerView: RecyclerView
    private lateinit var launchButton: Button
    private lateinit var viewModel: MainViewModel
    private lateinit var firestore: FirebaseFirestore
    private var sessionId: String? = null
    private var selectedGame: String? = null

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()
        FirebaseFirestore.setLoggingEnabled(true) // Enable Firestore logging

        // Store Android ID for research purposes. Ignore warning
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Find views by their IDs
        gameRecyclerView = findViewById(R.id.gameRecyclerView)
        launchButton = findViewById(R.id.launchButton)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Load HTML files from assets folder
        val assetManager = assets
        val gameLabels = mapOf(
            "ScavengerHunt.html" to "Scavenger Hunt",
            "Zombie Apocalypse.html" to "Zombie Apocalypse"
        )

        val games = assetManager.list("")?.filter { it.endsWith(".html") }?.map { fileName ->
            Game(fileName, gameLabels[fileName] ?: fileName)
        }?.toMutableList() ?: mutableListOf()

        // Add Speedtest activity to the list of games
        games.add(Game("Speedtest", "Speed Tester"))



        // Set up RecyclerView with the list of games
        val adapter = GameListAdapter(games) { game ->
            selectedGame = game.fileName
        }

        gameRecyclerView.layoutManager = LinearLayoutManager(this)
        gameRecyclerView.adapter = adapter


        // Set click listener for launch button
        launchButton.setOnClickListener {
            if (sessionId != null) {
                selectedGame?.let { gameFileName ->
                    val intent = if (gameFileName == "Speedtest") {
                        Intent(this, SpeedTestClone::class.java).apply {
                            putExtra("SESSION_ID", sessionId)
                        }
                    } else {
                        Intent(this, WebViewActivity::class.java).apply {
                            putExtra("FILENAME", gameFileName)
                            putExtra("SESSION_ID", sessionId)
                        }
                    }
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "Please create a session ID first.", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if location permission is granted, if not request permission
        checkLocationPermission()
        promptForSessionId()
    }

    private fun promptForSessionId() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Session ID")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("OK", null) // Pass null to override automatic dismissal

        val dialog = builder.create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val enteredId = input.text.toString().trim()
                if (enteredId.isEmpty()) {
                    Toast.makeText(this, "Session ID cannot be empty. Please enter a valid ID.", Toast.LENGTH_SHORT).show()
                } else {
                    sessionId = enteredId
                    createSession(sessionId!!)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun createSession(sessionId: String) {
        Log.d("MainActivity", "Creating session with ID: $sessionId")
        val sessionRef = firestore.collection("Movement Data").document(sessionId)
        val deviceIds = listOf("eceae5a981c05fbe", "0371849f3bd4068e", "8f0d163fb6bc0b2f","a315f9b4e6403e14")
        for (deviceId in deviceIds) {
            val deviceRef = sessionRef.collection("Devices").document(deviceId)
            val initialData: Map<String, Any> = hashMapOf("initialized" to true)
            deviceRef.set(initialData)
                .addOnSuccessListener {
                    Log.d("MainActivity", "DocumentSnapshot added with ID: $deviceId")
                }
                .addOnFailureListener { e: Exception ->
                    Log.e("MainActivity", "Error adding document", e)
                }

            // Create Check Data and Location Data documents
            val checkDataDoc: Map<String, Any> = hashMapOf()
            val locationDataDoc: Map<String, Any> = hashMapOf()
            deviceRef.collection("Data").document("Check Data").set(checkDataDoc)
                .addOnSuccessListener {
                    Log.d("MainActivity", "Check Data document created for $deviceId")
                }
                .addOnFailureListener { e: Exception ->
                    Log.e("MainActivity", "Error creating Check Data document", e)
                }

            deviceRef.collection("Data").document("Location Data").set(locationDataDoc)
                .addOnSuccessListener {
                    Log.d("MainActivity", "Location Data document created for $deviceId")
                }
                .addOnFailureListener { e: Exception ->
                    Log.e("MainActivity", "Error creating Location Data document", e)
                }
        }

        // Set the sessionId in the LocationService
        val locationServiceIntent = Intent(this, LocationService::class.java)
        locationServiceIntent.putExtra("sessionId", sessionId)
        startService(locationServiceIntent)
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