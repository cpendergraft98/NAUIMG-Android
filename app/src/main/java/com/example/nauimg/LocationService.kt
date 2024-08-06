package com.example.nauimg

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import com.google.firebase.firestore.FirebaseFirestore

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var firestore: FirebaseFirestore
    private var sessionId: String? = null
    private lateinit var androidId: String

    companion object {
        var latestLocation: Location? = null
        val locationData = JSONObject() // Initialize JSON object
        private const val CHANNEL_ID = "LocationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        createNotificationChannel()
        startForegroundService()

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create a location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500).apply {
            setMinUpdateIntervalMillis(500)
            setMaxUpdateDelayMillis(500)
        }.build()

        // Define the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation
                Log.d("LocationService", "Location update received: $latestLocation")

                latestLocation?.let { location ->
                    val currentDate = Calendar.getInstance().time

                    // JSON Object so Twine game has access to data
                    locationData.apply {
                        put("datetime", currentDate)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("origin", "android")
                        put("androidId", androidId)
                    }

                    // Hash Map so Firebase has access to data
                    val locationDataHM = hashMapOf(
                        "datetime" to currentDate,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "origin" to "android",
                        "androidId" to androidId
                    )

                    /*
                     The reason we have a JSON object and a Hash Map is because in order to
                     communicated data to Twine we need a JSON object and to communicate data
                     to Firebase we need a Hash Map
                    */

                    // Append to Firestore document
                    appendLocationToFirestore(locationDataHM)
                }
                Log.d("LocationService", locationData.toString(4))
            }
        }
        // Start location updates
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("sessionId")
        Log.d("LocationService", "Received session ID: $sessionId")
        return START_STICKY
    }

    private fun appendLocationToFirestore(locationData: Map<String, Any>) {
        // Check if sessionId is set
        if (sessionId.isNullOrEmpty()) {
            Log.w("LocationService", "Session ID is not set. Skipping Firestore write.")
            return
        }

        val locationUpdatesRef = firestore.collection("Movement Data").document(sessionId!!)
            .collection(androidId).document("Location Data").collection("Data")

        // Add a new document with a generated ID
        locationUpdatesRef.add(locationData)
            .addOnSuccessListener { documentReference ->
                Log.d("LocationService", "Location data added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Error adding location data", e)
            }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Service")
            .setContentText("Collecting location data")
            .setSmallIcon(R.mipmap.ic_launcher)  // Update this to an existing drawable
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            Log.d("LocationService", "Location updates started")
        } else {
            Log.e("LocationService", "Location permission not granted")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Location updates stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d("LocationService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}