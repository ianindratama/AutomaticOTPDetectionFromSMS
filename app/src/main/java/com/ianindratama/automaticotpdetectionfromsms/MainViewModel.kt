package com.ianindratama.automaticotpdetectionfromsms

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _userPhoneNumber = MutableStateFlow("")
    val userPhoneNumber: StateFlow<String> = _userPhoneNumber

    fun updateUserPhoneNumber(newValue: String) {
        _userPhoneNumber.value = newValue
    }
}
