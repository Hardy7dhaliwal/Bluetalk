package com.example.bluetalk.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    private var _data = MutableLiveData<String>()
    val data: LiveData<String> = _data

    fun sendData(data: String) {
        _data.value = data
    }
}