package com.example.nauimg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent;
import android.view.View;

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun btnGame0Handler(view: View){
        val intent = Intent(this, Game0WebViewActivity::class.java)
        startActivity(intent)
    }
}