package com.example.nauimg

import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.*
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat

class SpeedTestClone : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDownload: TextView
    private lateinit var tvUpload: TextView
    private lateinit var tvLatency: TextView
    private lateinit var tvPacketLoss: TextView
    private lateinit var tvJitter: TextView
    private lateinit var btnMain: Button
    private lateinit var btnForm: Button
    private lateinit var vibrator: Vibrator

    private lateinit var firestore: FirebaseFirestore
    private var sessionId: String? = null
    private lateinit var androidId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_test_clone)

        tvTitle = findViewById(R.id.tvTitle)
        tvDownload = findViewById(R.id.tvDownload)
        tvUpload = findViewById(R.id.tvUpload)
        tvLatency = findViewById(R.id.tvLatency)
        tvPacketLoss = findViewById(R.id.tvPacketLoss)
        tvJitter = findViewById(R.id.tvJitter)
        btnMain = findViewById(R.id.btnMain)
        btnForm = findViewById(R.id.btnForm)

        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        firestore = FirebaseFirestore.getInstance()
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        sessionId = intent.getStringExtra("SESSION_ID")

        btnMain.setOnClickListener {
            when (btnMain.text.toString()) {
                "Test" -> {
                    tvTitle.text = "Measuring..."
                    btnMain.text = "Measuring..."
                    btnMain.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.purple_500))
                    btnMain.setTextColor(ContextCompat.getColor(this, R.color.button_text_color)) // Ensure text color is white

                    // Simulate measuring time
                    Handler(Looper.getMainLooper()).postDelayed({
                        val metrics = generateMetrics()
                        val metricJSON = JSONObject(metrics)

                        tvTitle.text = "Test Results"
                        btnMain.text = "Take another measurement"
                        btnMain.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_500))
                        btnMain.setTextColor(ContextCompat.getColor(this, R.color.button_text_color)) // Ensure text color is white

                        tvDownload.text = metricJSON.getString("downloadSpeed")
                        tvUpload.text = metricJSON.getString("uploadSpeed")
                        tvLatency.text = "Latency (ms)\n${metricJSON.getString("latency")}"
                        tvPacketLoss.text = "Packet Loss (%)\n${metricJSON.getString("packetLoss")}"
                        tvJitter.text = "Jitter (ms)\n${metricJSON.getString("jitter")}"

                        writeCheckDataToFirestore()

                        // Trigger a brief vibration (e.g., 500 milliseconds)
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }

                    }, 3000) // 3 seconds delay to simulate measurement time
                }
                "Take another measurement" -> {
                    tvTitle.text = "Measuring..."
                    btnMain.text = "Measuring..."
                    btnMain.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.purple_500))
                    btnMain.setTextColor(ContextCompat.getColor(this, R.color.button_text_color)) // Ensure text color is white

                    // Simulate another measurement
                    Handler(Looper.getMainLooper()).postDelayed({
                        val metrics = generateMetrics()
                        val metricJSON = JSONObject(metrics)

                        tvTitle.text = "Test Results"
                        btnMain.text = "Take another measurement"
                        btnMain.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_500))
                        btnMain.setTextColor(ContextCompat.getColor(this, R.color.button_text_color)) // Ensure text color is white

                        tvDownload.text = metricJSON.getString("downloadSpeed")
                        tvUpload.text = metricJSON.getString("uploadSpeed")
                        tvLatency.text = "Latency (ms)\n${metricJSON.getString("latency")}"
                        tvPacketLoss.text = "Packet Loss (%)\n${metricJSON.getString("packetLoss")}"
                        tvJitter.text = "Jitter (ms)\n${metricJSON.getString("jitter")}"

                        writeCheckDataToFirestore()

                        // Trigger a brief vibration (e.g., 500 milliseconds)
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }

                    }, 3000) // 3 seconds delay to simulate measurement time
                }
            }
        }

        btnForm.setOnClickListener {
            val intent = Intent(this, LikertFormActivity::class.java)
            intent.putExtra("SESSION_ID", sessionId) // Pass the session ID
            startActivity(intent) // Start the activity
            finish() // Optionally finish the current activity
        }
    }
    private fun generateMetrics(): String {
        val random = Random()

        val uploadSpeed = random.nextInt((100 - 1) + 1) + 1 // Mbps
        val downloadSpeed = random.nextInt((100 - 1) + 1) + 1 // Mbps
        val jitter = random.nextInt((50 - 1) + 1) + 1 // ms
        val packetLoss = random.nextInt(100) // %
        val latency = random.nextInt((100 - 1) + 1) + 1 // ms

        val metricJSON = JSONObject()
        metricJSON.put("uploadSpeed", uploadSpeed)
        metricJSON.put("downloadSpeed", downloadSpeed)
        metricJSON.put("jitter", jitter)
        metricJSON.put("packetLoss", packetLoss)
        metricJSON.put("latency", latency)

        return metricJSON.toString()
    }

    private fun writeCheckDataToFirestore() {
        if (sessionId.isNullOrEmpty()) {
            Log.w("SpeedTestClone", "Session ID is not set. Skipping Firestore write.")
            return
        }

        val latestLocation = LocationService.latestLocation
        if (latestLocation == null) {
            Log.w("SpeedTestClone", "Latest location is not available. Skipping Firestore write.")
            return
        }

        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val currentDate = isoDateFormat.format(Calendar.getInstance().time)

        val checkData = hashMapOf(
            "datetime" to currentDate,
            "latitude" to latestLocation.latitude,
            "longitude" to latestLocation.longitude,
            "game" to "Speedtest",
            "session" to (sessionId ?: "Unknown"),
            "androidId" to androidId
        )

        val checkDataRef = firestore.collection("Movement Data").document(sessionId!!)
            .collection("CheckData")

        checkDataRef.add(checkData)
            .addOnSuccessListener { documentReference ->
                Log.d("SpeedTestClone", "Check data added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("SpeedTestClone", "Error adding check data", e)
            }

        writeDataLocally("CheckData", checkData)
    }

    // Function to write data to local storage
    private fun writeDataLocally(dataType: String, data: Map<String, Any>) {
        try {
            val sessionDir = File(applicationContext.filesDir, "MovementData/$sessionId")
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }

            // Write data to a JSON file in the appropriate directory
            val file = File(sessionDir, "$dataType.json")
            val jsonArray = if (file.exists()) {
                JSONArray(file.readText()) // Read existing data
            } else {
                JSONArray() // Start new array if no file exists
            }

            // Append new data to the JSON array
            jsonArray.put(JSONObject(data))

            // Write the updated array back to the file
            file.writeText(jsonArray.toString())

            Log.d("LocationService", "Data written to local storage: $file")
        } catch (e: Exception) {
            Log.e("LocationService", "Error writing data locally", e)
        }
    }
}