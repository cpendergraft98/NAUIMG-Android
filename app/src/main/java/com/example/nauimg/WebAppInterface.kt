package com.example.nauimg

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.Random
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import java.io.File

// WebAppInterface and related methods for communicating between Android and JS
class WebAppInterface(private val context: Context, private val firestore: FirebaseFirestore, private val sessionId: String, private var locationService: LocationService?) {

    fun updateLocationService(service: LocationService) {
        this.locationService = service
    }

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

            // Add sessionId to the JSON object
            jsonObject.put("sessionId", sessionId)

            // Use the Android ID to identify the device (for logging, though it's not part of the pathing)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            Log.d("WebAppInterface", "Android ID: $androidId")

            // Reference to the Check Data collection in Firestore for the current session
            val checkDataRef = firestore.collection("Movement Data")
                .document(sessionId!!)
                .collection("CheckData")

            Log.d("WebAppInterface", "Firestore Path: Movement Data/$sessionId/CheckData")

            // Convert JSON object to Map
            val dataMap = jsonToMap(jsonObject)

            // Add data to Firestore, let Firestore generate the document ID automatically
            checkDataRef.add(dataMap)
                .addOnSuccessListener { documentReference ->
                    Log.d("WebAppInterface", "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e: Exception ->
                    Log.w("WebAppInterface", "Error adding document", e)
                }
            // Write data locally for redundancy
            writeDataLocally("CheckData", dataMap)
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error in writeTwineData: ", e)
        }
    }

    @JavascriptInterface
    fun writeLikertData(data: String) {
        try {
            Log.d("WebAppInterface", "Received data: $data")

            val jsonObject = JSONObject(data)

            // Add sessionId to the JSON object
            jsonObject.put("sessionId", sessionId)

            // Use the Android ID to identify the device (for logging, though it's not part of the pathing)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            Log.d("WebAppInterface", "Android ID: $androidId")

            // Reference to the Likert collection in Firestore for the current session
            val checkDataRef = firestore.collection("Movement Data")
                .document(sessionId!!)
                .collection("LikertData")

            Log.d("WebAppInterface", "Firestore Path: Movement Data/$sessionId/LikertData")

            // Convert JSON object to Map
            val dataMap = jsonToMap(jsonObject)

            // Add data to Firestore, let Firestore generate the document ID automatically
            checkDataRef.add(dataMap)
                .addOnSuccessListener { documentReference ->
                    Log.d("WebAppInterface", "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e: Exception ->
                    Log.w("WebAppInterface", "Error adding document", e)
                }
            // Write data locally for redundancy
            writeDataLocally("LikertData", dataMap)
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error in writeTwineData: ", e)
        }
    }

    // Function to write data to local storage
    @JavascriptInterface
    fun writeDataLocally(dataType: String, data: Map<String, Any>) {
        try {
            val sessionDir = File(context.filesDir, "MovementData/$sessionId")
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

            Log.d("WebAppInterface", "Data written to local storage: $file")
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error writing data locally", e)
        }
    }

    // Same as above but takes in a JSON string instead of a Hash Map
    @JavascriptInterface
    fun writeDataLocallyJSON(dataType: String, jsonString: String) {
        try {
            // Convert the JSON string into a JSONObject
            val jsonObject = JSONObject(jsonString)

            val sessionDir = File(context.filesDir, "MovementData/$sessionId")
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
            jsonArray.put(jsonObject)

            // Write the updated array back to the file
            file.writeText(jsonArray.toString())

            Log.d("WebAppInterface", "Data written to local storage: $file")
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error writing data locally", e)
        }
    }

    // Used for the zombie game. Sets pois in android backend for use by vibration features
    @JavascriptInterface
    fun setPOIData(jsonPOIs: String) {
        val poiArray = JSONArray(jsonPOIs)
        val poiList = mutableListOf<LatLng>()
        for (i in 0 until poiArray.length()) {
            val poi = poiArray.getJSONObject(i)
            val latLng = LatLng(poi.getDouble("latitude"), poi.getDouble("longitude"))
            poiList.add(latLng)

            // Log each POI with its latitude and longitude
            Log.d("setPOIData", "POI $i: Latitude = ${latLng.latitude}, Longitude = ${latLng.longitude}")
        }
        LocationService.setPOIs(poiList)
        Log.d("setPOIData", "POI Data received and set. Total POIs: ${poiList.size}")
    }

    @JavascriptInterface
    fun POICheck() {
        Log.d("POICheck", "POICheck requested from JS.")
        locationService?.let {
            it.waitForLocationUpdate()
        } ?: Log.e("WebAppInterface", "LocationService is not initialized")
    }
    @JavascriptInterface
    fun requestHint() {
        Log.d("WebAppInterface", "Hint requested from JS.")
        locationService?.let {
            it.waitForHintLocationUpdate()
        } ?: Log.e("WebAppInterface", "LocationService is not initialized")
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