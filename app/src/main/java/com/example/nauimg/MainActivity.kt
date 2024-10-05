package com.example.nauimg

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import android.widget.EditText
import android.widget.TextView
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
    private lateinit var updateSessionButton: Button
    private lateinit var sessionTextView: TextView
    private lateinit var viewModel: MainViewModel
    private lateinit var firestore: FirebaseFirestore
    private var sessionId: String? = null
    private lateinit var prefs: SharedPreferences
    private var selectedGame: String? = null

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firestore and SharedPreferences
        firestore = FirebaseFirestore.getInstance()
        FirebaseFirestore.setLoggingEnabled(true) // Enable Firestore logging
        prefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)

        // Find views by their IDs
        gameRecyclerView = findViewById(R.id.gameRecyclerView)
        launchButton = findViewById(R.id.launchButton)
        updateSessionButton = findViewById(R.id.updateSessionButton)
        sessionTextView = findViewById(R.id.sessionTextView)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Load HTML files from assets folder
        val assetManager = assets
        val gameLabels = mapOf(
            "ScavengerHunt.html" to "Scavenger Hunt",
            "Zombie Apocalypse.html" to "Zombie Apocalypse",
            "Soul Seeker.html" to "Soul Seeker"
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

        // Check if a session ID is already stored
        sessionId = prefs.getString("sessionId", null)
        if(sessionId.isNullOrEmpty())
        {
            promptForSessionId() // Prompt if no session ID is stored
        } else {
            sessionTextView.text = "Current Session ID: $sessionId"
            startLocationService() // Start the LocationService if Session ID is available.
        }

        // Set click listener for launch button
        launchButton.setOnClickListener {
            if (sessionId != null) {
                selectedGame?.let { gameFileName ->
                    if (gameFileName == "Speedtest") {
                        // Launch NameEntryActivity
                        val intent = Intent(this, NameEntryActivity::class.java).apply {
                            putExtra("SESSION_ID", sessionId)
                            putExtra("selectedGame", gameFileName)
                        }
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, WebViewActivity::class.java).apply {
                            putExtra("FILENAME", gameFileName)
                            putExtra("SESSION_ID", sessionId)
                        }
                        startActivity(intent)
                    }

                    // Pass the selected game name to the LocationService via the intent
                    val locationServiceIntent = Intent(this, LocationService::class.java).apply {
                        putExtra("sessionId", sessionId)
                        putExtra("selectedGame", gameFileName) // Pass the selected game to LocationService
                    }
                    startService(locationServiceIntent)
                }
            } else {
                Toast.makeText(this, "Please create a session ID first.", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if location permission is granted, if not request permission
        checkLocationPermission()
        updateSessionButton.setOnClickListener {
            promptForSessionId()
        }
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
                    saveSessionId(sessionId!!)
                    sessionTextView.text = "Current Session ID: $sessionId"
                    startLocationService() // Start the LocationService if session ID is available
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun saveSessionId(sessionId: String) {
        val editor = prefs.edit()
        editor.putString("sessionId", sessionId)
        editor.apply()
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
        val locationServiceIntent = Intent(this, LocationService::class.java)
        locationServiceIntent.putExtra("sessionId", sessionId)
        startService(locationServiceIntent)
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