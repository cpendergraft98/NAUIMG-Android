package com.example.nauimg

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// MainViewModel class extends ViewModel, serving as the ViewModel for MainActivity.
class MainViewModel : ViewModel() {
    // MutableLiveData to hold the selected game, allowing observation of changes.
    // It's nullable to handle the initial state when no game is selected.
    val selectedGame = MutableLiveData<String?>()
}