package com.example.nauimg

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LikertFormActivity : AppCompatActivity() {

    private var sessionId: String? = null
    private lateinit var firestore: FirebaseFirestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_likert_form)

        // Get references to the UI components
        val radioGroup1 = findViewById<RadioGroup>(R.id.radioGroup1)
        val radioGroup2 = findViewById<RadioGroup>(R.id.radioGroup2)
        val resultText = findViewById<TextView>(R.id.resultText)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val btnReturn = findViewById<Button>(R.id.btnReturn)
        sessionId = intent.getStringExtra("SESSION_ID")
        firestore = FirebaseFirestore.getInstance()

        // Handle submit button click
        submitButton.setOnClickListener {
            // Get selected values from the RadioGroups
            val envConnection = getSelectedRadioValue(radioGroup1)
            val playerConnection = getSelectedRadioValue(radioGroup2)

            // Check if both values were selected
            if (envConnection != null && playerConnection != null) {
                // Display the result
                val result = "You rated your connection to the environment as: $envConnection.\n" +
                        "You rated your connection to your fellow players as: $playerConnection."
                resultText.text = result

                writeLikertDataToFirestore(envConnection, playerConnection)
            } else {
                // Show a message if not all options were selected
                Toast.makeText(this, "Please answer both questions before submitting.", Toast.LENGTH_SHORT).show()
            }
        }

        btnReturn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // Helper function to get the selected radio button value
    private fun getSelectedRadioValue(radioGroup: RadioGroup): Int? {
        // Get the ID of the selected radio button
        val selectedId = radioGroup.checkedRadioButtonId

        return if (selectedId != -1) {
            // Find the selected RadioButton by ID
            val selectedRadioButton = findViewById<RadioButton>(selectedId)
            // Get the text from the selected RadioButton and convert it to an Int
            selectedRadioButton.text.toString().toInt()
        } else {
            null // No option selected
        }
    }

    // Function to write the Likert scale data to Firestore
    private fun writeLikertDataToFirestore(envConnection: Int, playerConnection: Int) {
        if (sessionId.isNullOrEmpty()) {
            Log.w("LikertForm", "Session ID is not set. Skipping Firestore write.")
            return
        }

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Create the Likert data to write to Firestore
        val likertData = hashMapOf(
            "game" to "Speedtester",
            "androidId" to (androidId ?: "Unknown"),
            "envconnection" to envConnection,
            "plrconnection" to playerConnection
        )

        // Reference to the LikertData collection
        val likertDataRef = firestore.collection("Movement  Data")
            .document(sessionId!!)
            .collection("LikertData")

        // Add the data to Firestore
        likertDataRef.add(likertData)
            .addOnSuccessListener { documentReference ->
                Log.d("LikertForm", "Likert data added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("LikertForm", "Error adding Likert data", e)
            }

        //writeDataLocally("LikertData", likertData)
    }

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