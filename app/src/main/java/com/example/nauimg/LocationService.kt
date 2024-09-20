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
import android.os.Binder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*
import android.os.Handler
import android.os.Looper
import android.os.HandlerThread
import android.os.Process
import android.webkit.WebView

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
    private var selectedGame: String? = null
    private lateinit var androidId: String

    private lateinit var handlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    // Binder given to clients
    private val binder = LocalBinder()

    // Class used for client binder
    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private var poiCheckCallback: ((Boolean) -> Unit)? = null
    private var hintCallback: ((String) -> Unit)? = null

    private var webView: WebView? = null

    // Method to set the WebView Instance
    fun setWebView(webView: WebView){
        this.webView = webView
    }

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

        // Initialize the HandlerThread and Handler for background processing
        handlerThread = HandlerThread("LocationServiceThread", Process.THREAD_PRIORITY_BACKGROUND)
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)

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
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateIntervalMillis(1000)
            setMaxUpdateDelayMillis(1000)
            setMinUpdateDistanceMeters(0f)
        }.build()

        // Define Location Callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                latestLocation = locationResult.lastLocation

                // Log the user's current location
                Log.d("LocationService", "Location update received: $latestLocation")

                // Handle location updates on background thread
                handleLocationUpdate(locationResult)
            }
        }
        // Start location updates on the background thread
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

    private fun handleLocationUpdate(locationResult: LocationResult) {
        latestLocation?.let { location ->
            val currentDate = Calendar.getInstance().time

            // JSON Object so Twine game has access to data
            locationData.apply {
                put("datetime", currentDate)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("azimuth", azimuth) // Include latest azimuth
                put("direction", direction) // Include latest direction
                put("session", sessionId ?: "Unknown")
                put("androidId", androidId)
                put("game", selectedGame ?: "Unknown")
            }

            // Hash Map so Firebase has access to data
            val locationDataHM = hashMapOf(
                "datetime" to currentDate,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "azimuth" to azimuth, // Include latest azimuth
                "direction" to direction, // Include latest direction
                "session" to (sessionId ?: "Unknown"),
                "androidId" to androidId,
                "game" to (selectedGame ?: "Unknown")
            )

            // Check if the game is valid (not "Unknown" or null) before writing to Firestore
            if (selectedGame.isNullOrEmpty() || selectedGame == "Unknown") {
                Log.d("LocationService", "Skipping Firestore write: no game selected or game is 'Unknown'")
            } else {
                // Append the raw location data to Firestore
                appendLocationToFirestore(locationDataHM)
            }

            // If there are POIs, find the closest one and adjust the vibration accordingly
            if (pois.isNotEmpty()) {
                var closestPoi: LatLng? = null
                var minDistance = Double.MAX_VALUE

                // Iterate over the POIs to find the closest one
                for (poi in pois) {
                    val distance = haversine(location.latitude, location.longitude, poi.latitude, poi.longitude)

                    // Log the POI location and distance to the user
                    Log.d("VibrationService", "POI location: Latitude = ${poi.latitude}, Longitude = ${poi.longitude}")
                    Log.d("VibrationService", "Distance to POI: $distance meters")

                    if (distance < minDistance) {
                        minDistance = distance
                        closestPoi = poi
                    }
                }

                // Adjust the vibration based on the closest POI
                closestPoi?.let { poi ->
                    adjustVibrationPulse(minDistance)
                }
            }

            // Handle the hint generation via callback
            hintCallback?.let { callback ->
                val hint = generateHint()
                callback(hint)
                hintCallback = null  // Reset callback after it's used
            }

            // Handle the manual POI check via callback
            poiCheckCallback?.let { callback ->
                val isAtPOI = checkIfWithinPOI(latestLocation)
                callback(isAtPOI)
                poiCheckCallback = null  // Reset callback after it's used
            }

            // Stop the vibration if there are no more POIs left
            if (pois.isEmpty()) {
                Log.d("LocationService", "All POIs found. Stopping vibration.")
                stopVibration()
            }
        }
        Log.d("LocationService", locationData.toString(4))
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                //Log.d("LocationService", "Accelerometer data: ${event.values.contentToString()}")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                //Log.d("LocationService", "Magnetometer data: ${event.values.contentToString()}")
            }
        }

        val rotationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            //Log.d("LocationService", "Azimuth in degrees before rounding: $azimuth")

            azimuth = (azimuth + 360) % 360 // Convert to 0-360 range
            //Log.d("LocationService", "Azimuth after converting to 0-360 range: $azimuth")

            // Convert azimuth to cardinal direction
            direction = when (azimuth.roundToInt()) {
                in 0..44 -> "N"
                in 45..134 -> "E"
                in 135..224 -> "S"
                in 225..314 -> "W"
                in 315..360 -> "N"
                else -> "Unknown"
            }

            //Log.d("LocationService", "Rotation matrix calculated successfully.")
            //Log.d("LocationService", "Azimuth: $azimuth°, Direction: $direction")
        } else {
            Log.e("LocationService", "Failed to calculate rotation matrix.")
        }
    }

    fun waitForLocationUpdate() {
        Log.d("LocationService", "Waiting for the next location update...")
        poiCheckCallback = { isAtPOI ->
            val script = "receivePOICheck($isAtPOI);"
            webView?.let { view ->
                Handler(Looper.getMainLooper()).post {
                    view.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun checkIfWithinPOI(location: Location?): Boolean {
        location?.let {
            val iterator = pois.iterator()
            while (iterator.hasNext()) {
                val poi = iterator.next()
                val distance = haversine(it.latitude, it.longitude, poi.latitude, poi.longitude)
                if (distance <= 7.0) {
                    iterator.remove()  // Remove the POI from the list
                    stopVibration()    // Reset the vibration after collecting a POI
                    return true
                }
            }
        }
        return false
    }

    fun waitForHintLocationUpdate() {
        Log.d("LocationService", "Waiting for the next location update for hint generation...")
        hintCallback = {
            val hint = generateHint()
            webView?.let { view ->
                Handler(Looper.getMainLooper()).post {
                    view.evaluateJavascript("receiveHint('$hint');", null)
                }
            }
        }
    }

    private fun generateHint(): String {
        // Example logic for generating a hint based on current orientation and nearest POI
        var hint = "No hint available"
        latestLocation?.let { location ->
            val nearestPoi = pois.minByOrNull { haversine(location.latitude, location.longitude, it.latitude, it.longitude) }
            nearestPoi?.let { poi ->
                val bearingToPoi = bearing(location.latitude, location.longitude, poi.latitude, poi.longitude)
                val relativeBearing = (bearingToPoi - azimuth + 360) % 360

                hint = when {
                    relativeBearing < 45 || relativeBearing > 315 -> "is somewhere in front of you!"
                    relativeBearing in 45.0..135.0 -> "is somewhere to your right!"
                    relativeBearing in 135.0..225.0 -> "is somewhere behind you!"
                    relativeBearing in 225.0..315.0 -> "is somewhere to your left!"
                    else -> "Unable to determine direction, try moving around."
                }
            }
        }
        return hint
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun stopVibration() {
        handler?.removeCallbacksAndMessages(null)
        isVibrating = false
        vibrator.cancel()
        Log.d("VibrationService", "Vibration handler stopped and vibration canceled.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("sessionId")
        selectedGame = intent?.getStringExtra("selectedGame") // Get the selected game from the intent
        Log.d("LocationService", "Received session ID: $sessionId and game: $selectedGame")
        return START_STICKY
    }

    private fun appendLocationToFirestore(locationData: Map<String, Any>) {
        // Check if sessionId is set
        if (sessionId.isNullOrEmpty()) {
            Log.w("LocationService", "Session ID is not set. Skipping Firestore write.")
            return
        }

        val locationUpdatesRef = firestore.collection("Movement Data").document(sessionId!!)
            .collection("LocationData") // Storing the location data in the new structure

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
        val maxDistance = 30.0

        // Define distance ranges and corresponding delays
        val delayRanges = mapOf(
            0..7 to 250L, // 0-7 meters: 250ms delay
            8..15 to 500L,// 8-15 meters: 500ms delay
            16..23 to 1000L,   // 16-23 meters: 1000ms delay
            24..maxDistance.toInt() to 2000L  // 24-30 meters: 2000ms delay
        )

        // Determine which range the current distance falls into
        val newRange = delayRanges.keys.firstOrNull { range -> distance.toInt() in range }

        // If no POIs are in range, stop vibration
        if (newRange == null) {
            stopVibration()
            Log.d("VibrationService", "No POIs within max distance. Stopping vibration.")
            return
        }

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
            // Request location updates using the HandlerThread's looper
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, handlerThread.looper)
            Log.d("LocationService", "Location updates started on background thread")
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
        stopVibration()
        stopLocationUpdates()
        handlerThread.quitSafely()
        Log.d("LocationService", "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopVibration()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle changes in sensor accuracy, not needed currently
    }
}