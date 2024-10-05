package com.example.nauimg

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.IBinder
import android.content.ServiceConnection
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NameEntryActivity : AppCompatActivity() {

    private lateinit var nameEditText: EditText
    private lateinit var submitButton: Button
    private var locationService: LocationService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection
    {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?)
        {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_name_entry)

        nameEditText = findViewById(R.id.nameEditText)
        submitButton = findViewById(R.id.submitButton)

        // Bind to the LocationService
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        submitButton.setOnClickListener {
            val playerName = nameEditText.text.toString().trim()

            if (playerName.isNotEmpty()) {
                // Send the player name to LocationService if bound
                if(isBound)
                {
                    locationService?.setPlayerName(playerName)
                }

                // Create intent to pass the player name to SpeedTestClone
                val intent = Intent(this, SpeedTestClone::class.java).apply {
                    putExtra("PLAYER_NAME", playerName)
                    putExtra("SESSION_ID", intent.getStringExtra("SESSION_ID"))
                }
                startActivity(intent)
                finish() // Optionally finish the NameEntryActivity
            } else {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy()
    {
        super.onDestroy()
        // Unbind from the service when activity is destroyed
        if(isBound)
        {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}