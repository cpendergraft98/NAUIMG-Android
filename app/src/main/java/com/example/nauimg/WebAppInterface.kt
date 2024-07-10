package com.example.nauimg

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import java.io.File

// WebAppInterface and related methods for communicating between Androind and JS
class WebAppInterface(private val context: Context) {

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

    @JavascriptInterface
    fun writeTwineData(data: String){
        // Parse the received JSON string into a JSONObject
        val jsonObject = JSONObject(data)

        // Path to the file
        val file = File(context.filesDir, "check_data.json")

        val jsonArray: JSONArray

        // Check if the file exists
        if(file.exists()){
            // Read the existing file
            val existingData = file.readText()
            // Parse the existing file content into a JSONArray
            jsonArray = JSONArray(existingData)
        }else{
            // Create a new JSONArray
            jsonArray = JSONArray()
        }

        // Add the new data to the array
        jsonArray.put(jsonObject)

        // Write the updated array back to the file
        file.writeText(jsonArray.toString())

    }

}