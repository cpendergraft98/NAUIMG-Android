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
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import com.google.firebase.firestore.FirebaseFirestore
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*
import android.os.Handler
import android.os.Looper

class LocationService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private lateinit var vibrator: Vibrator

    private var azimuth: Float = 0f // Azimuth in degrees
    private var direction: String = "Unknown" // latest direction value

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
        private val pois = mutableListOf<LatLng>()
        fun setPOIs(poiList: List<LatLng>) {
            pois.clear()
            pois.addAll(poiList)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")

        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize SensorManager and sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: throw IllegalStateException("Accelerometer not available")

        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: throw IllegalStateException("Magnetometer not available")

        // Register sensor listeners
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)


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
            setMinUpdateDistanceMeters(0f)
        }.build()

        // Define Location Callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation

                latestLocation?.let { location ->
                    val currentDate = Calendar.getInstance().time

                    // JSON Object so Twine game has access to data
                    locationData.apply {
                        put("datetime", currentDate)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("azimuth", azimuth) // Include latest azimuth
                        put("direction", direction) // Include latest direction
                        put("origin", "android")
                        put("androidId", androidId)
                    }

                    // Hash Map so Firebase has access to data
                    val locationDataHM = hashMapOf(
                        "datetime" to currentDate,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "azimuth" to azimuth, // Include latest azimuth
                        "direction" to direction, // Include latest direction
                        "origin" to "android",
                        "androidId" to androidId
                    )

                    // Append to Firestore document
                    appendLocationToFirestore(locationDataHM)

                    // Use the smoothed location for POI detection
                    if (pois.isNotEmpty()) {
                        var closestPoi: LatLng? = null
                        var minDistance = Double.MAX_VALUE

                        // Find the closest POI using the smoothed location
                        for (poi in LocationService.pois) {
                            // Calculate the distance between the user's current location and the POI
                            val distance = haversine(location.latitude, location.longitude, poi.latitude, poi.longitude)

                            // Log the POI location and distance to the user
                            Log.d("VibrationService", "POI location: Latitude = ${poi.latitude}, Longitude = ${poi.longitude}")
                            Log.d("VibrationService", "Distance to POI: $distance meters")

                            if (distance < minDistance) {
                                minDistance = distance
                                closestPoi = poi
                            }
                        }

                        closestPoi?.let { poi ->
                            if (minDistance <= 1.2) { // Adjust your detection radius accordingly
                                Log.d("NotificationTest", "User is within 1.2 meters of POI, sending notification.")
                                notifyUserOfPOI() // Notify user
                                pois.remove(poi) // Remove the POI from the list
                            } else {
                                adjustVibrationPulse(minDistance) // Adjust pulse frequency based on distance
                            }
                        }
                    }

                    if (LocationService.pois.isEmpty()) {
                        Log.d("LocationService", "All POIs found. Stopping vibration.")
                        stopVibration()
                    }
                }
                Log.d("LocationService", locationData.toString(4))
            }
        }
        // Start location updates
        startLocationUpdates()
    }

    private val recentLocations = mutableListOf<Location>()

    private fun smoothLocation(newLocation: Location): Location {
        recentLocations.add(newLocation)
        if (recentLocations.size > 5) recentLocations.removeAt(0) // Keep only the last 5 locations

        val averageLat = recentLocations.map { it.latitude }.average()
        val averageLon = recentLocations.map { it.longitude }.average()

        return Location("").apply {
            latitude = averageLat
            longitude = averageLon
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                Log.d("LocationService", "Accelerometer data: ${event.values.contentToString()}")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                Log.d("LocationService", "Magnetometer data: ${event.values.contentToString()}")
            }
        }

        val rotationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            Log.d("LocationService", "Azimuth in degrees before rounding: $azimuth")

            azimuth = (azimuth + 360) % 360 // Convert to 0-360 range
            Log.d("LocationService", "Azimuth after converting to 0-360 range: $azimuth")

            // Convert azimuth to cardinal direction
            direction = when (azimuth.roundToInt()) {
                in 0..44 -> "N"
                in 45..134 -> "E"
                in 135..224 -> "S"
                in 225..314 -> "W"
                in 315..360 -> "N"
                else -> "Unknown"
            }

            Log.d("LocationService", "Rotation matrix calculated successfully.")
            Log.d("LocationService", "Azimuth: $azimuth°, Direction: $direction")
        } else {
            Log.e("LocationService", "Failed to calculate rotation matrix.")
        }
    }

    private fun stopVibration() {
        handler?.removeCallbacksAndMessages(null)
        vibrator.cancel()
        Log.d("VibrationService", "Vibration handler stopped and vibration canceled.")
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

    private var currentRange: IntRange? = null
    private var handler: Handler? = null
    private var isVibrating = false

    private fun adjustVibrationPulse(distance: Double) {
        val maxDistance = 50.0

        // Define distance ranges and corresponding delays
        val delayRanges = mapOf(
            0..10 to 100L, // 11-20 meters: 400ms delay
            11..30 to 400L,// 21-30 meters: 600ms delay
            31..40 to 700L,   // 31-40 meters: 800ms delay
            41..maxDistance.toInt() to 1000L  // 41-50 meters: 1000ms delay
        )

        // Determine which range the current distance falls into
        val newRange = delayRanges.keys.firstOrNull { range -> distance.toInt() in range }

        // Only update the vibration pattern if the user has crossed into a new range
        if (newRange != currentRange) {
            currentRange = newRange
            val pulseInterval = delayRanges[newRange]
            pulseInterval?.let {
                // Cancel any existing vibration
                handler?.removeCallbacksAndMessages(null)
                isVibrating = false

                // Start the new vibration pattern
                startVibration(it)
                Log.d("VibrationService", "Vibrating with pulse interval: $it ms (Range: $currentRange)")
            }
        } else {
            Log.d("VibrationService", "Remaining in the same range, no update to pulse interval.")
        }
    }

    private fun startVibration(pulseInterval: Long) {
        handler = Handler(Looper.getMainLooper())
        val vibrationRunnable = object : Runnable {
            override fun run() {
                if (isVibrating) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val vibrationEffect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                        vibrator.vibrate(vibrationEffect)
                    } else {
                        vibrator.vibrate(200)
                    }
                    handler?.postDelayed(this, pulseInterval)
                }
            }
        }

        isVibrating = true
        handler?.post(vibrationRunnable)
    }

    // Haversine formula to compute the distance between two geographic coordinates
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double{
        val earthRadius = 6371e3 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun notifyUserOfPOI() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "poi_channel_id"
        val channelName = "POI Notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_background) // Replace with your app's icon
            .setContentTitle("Point of Interest Found!")
            .setContentText("You have reached a point of interest.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(1, notificationBuilder.build())
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
        sensorManager.unregisterListener(this) // Unregister sensor listeners
        handler?.removeCallbacksAndMessages(null)
        cancelVibration()
        stopLocationUpdates()
        Log.d("LocationService", "Service destroyed")
    }

    private fun cancelVibration() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
        Log.d("VibrationService", "Vibration canceled")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle changes in sensor accuracy, not needed currently
    }
}