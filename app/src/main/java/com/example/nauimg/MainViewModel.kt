package com.example.nauimg

import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val selectedGame = MutableLiveData<String?>()
    val latestLocation = MutableLiveData<Location?>()
}