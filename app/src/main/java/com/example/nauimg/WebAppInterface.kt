package com.example.nauimg

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.Random
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray

// WebAppInterface and related methods for communicating between Androind and JS
class WebAppInterface(private val context: Context, private val firestore: FirebaseFirestore, private val sessionId: String) {

    // Generate random metrics and return as JSON string
    @JavascriptInterface
    fun generateMetrics(): String {
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

    @JavascriptInterface
    fun getLocationJSON(): String {
        // Return the current state of the JSON array as a formatted string
        return LocationService.locationData.toString(4)
    }

    private var checkCounter = 0

    init {
        resetCheckCounter()
    }

    @JavascriptInterface
    fun writeTwineData(data: String) {
        try {
            Log.d("WebAppInterface", "Received data: $data")

            val jsonObject = JSONObject(data)

            // Use the Android ID to identify the device
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            Log.d("WebAppInterface", "Android ID: $androidId")

            // Reference to the device's Check Data subcollection in Firestore
            val checkDataRef = firestore.collection("Movement Data").document(sessionId)
                .collection(androidId).document("Check Data").collection("Data")

            Log.d("WebAppInterface", "Firestore Path: Movement Data/$sessionId/$androidId/Check Data/CheckDataSub")

            // Convert JSON object to Map
            val dataMap = jsonToMap(jsonObject)

            // Generate custom document ID
            val checkId = generateCheckId()

            // Add data to Firestore with custom document ID
            checkDataRef.document(checkId).set(dataMap)
                .addOnSuccessListener {
                    Log.d("WebAppInterface", "DocumentSnapshot added with ID: $checkId")
                }
                .addOnFailureListener { e: Exception ->
                    Log.w("WebAppInterface", "Error adding document", e)
                }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error in writeTwineData: ", e)
        }
    }

    private fun generateCheckId(): String {
        checkCounter += 1
        return "Check $checkCounter"
    }

    private fun resetCheckCounter() {
        checkCounter = 0
    }

    private fun jsonToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            var value = jsonObject.get(key)
            if (value is JSONObject) {
                value = jsonToMap(value)
            } else if (value is JSONArray) {
                value = jsonToList(value)
            }
            map[key] = value
        }
        return map
    }

    private fun jsonToList(jsonArray: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until jsonArray.length()) {
            var value = jsonArray.get(i)
            if (value is JSONObject) {
                value = jsonToMap(value)
            } else if (value is JSONArray) {
                value = jsonToList(value)
            }
            list.add(value)
        }
        return list
    }
}