package com.example.nauimg

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.*
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.Settings

class SpeedTestClone : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDownload: TextView
    private lateinit var tvUpload: TextView
    private lateinit var tvLatency: TextView
    private lateinit var tvPacketLoss: TextView
    private lateinit var tvJitter: TextView
    private lateinit var btnMain: Button
    private lateinit var btnReturn: Button

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
        btnReturn = findViewById(R.id.btnReturn)

        firestore = FirebaseFirestore.getInstance()
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        sessionId = intent.getStringExtra("SESSION_ID")

        btnMain.setOnClickListener {
            if (btnMain.text == "Test") {
                tvTitle.text = "Measuring..."
                btnMain.text = "Measuring..."

                // Simulate measuring time
                Handler(Looper.getMainLooper()).postDelayed({
                    val metrics = generateMetrics()
                    val metricJSON = JSONObject(metrics)

                    tvTitle.text = "Test Results"
                    btnMain.text = "Finish"

                    tvDownload.text = metricJSON.getString("downloadSpeed")
                    tvUpload.text = metricJSON.getString("uploadSpeed")
                    tvLatency.text = "Latency (ms)\n${metricJSON.getString("latency")}"
                    tvPacketLoss.text = "Packet Loss (%)\n${metricJSON.getString("packetLoss")}"
                    tvJitter.text = "Jitter (ms)\n${metricJSON.getString("jitter")}"

                    writeCheckDataToFirestore()

                }, 3000) // 3 seconds delay to simulate measurement time
            } else {
                tvTitle.text = "Ready to Test"
                btnMain.text = "Test"

                tvDownload.text = "-"
                tvUpload.text = "-"
                tvLatency.text = "Latency (ms)\n-"
                tvPacketLoss.text = "Packet Loss (%)\n-"
                tvJitter.text = "Jitter (ms)\n-"
            }
        }

        btnReturn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
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

        val currentDate = Calendar.getInstance().time

        val checkData = hashMapOf(
            "datetime" to currentDate,
            "latitude" to latestLocation.latitude,
            "longitude" to latestLocation.longitude,
            "origin" to "control",
            "androidId" to androidId
        )

        val checkDataRef = firestore.collection("Movement Data").document(sessionId!!)
            .collection(androidId).document("Check Data").collection("Data")

        checkDataRef.add(checkData)
            .addOnSuccessListener { documentReference ->
                Log.d("SpeedTestClone", "Check data added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("SpeedTestClone", "Error adding check data", e)
            }
    }
}