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

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    companion object {
        var latestLocation: Location? = null
        // Can make this a JSONObject once we are set up with the server
        val locationData = JSONObject() // Initialize JSON array
        private const val CHANNEL_ID = "LocationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")

        createNotificationChannel()
        startForegroundService()

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create a location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateIntervalMillis(1000)
            setMaxUpdateDelayMillis(1000)
        }.build()

        // Define the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation
                Log.d("LocationService", "Location update received: $latestLocation")

                latestLocation?.let { location ->
                    val currentDate = Calendar.getInstance().time
                    // Update a JSON object with the necessary data
                    locationData.apply {
                        put("datetime", currentDate)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("origin", "android")
                        put("androidId", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
                    }
                }
                Log.d("LocationService", locationData.toString(4))
            }
        }
        // Start location updates
        startLocationUpdates()
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