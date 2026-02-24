package com.lotterytool.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.repository.CheckVersionRepository
import com.lotterytool.data.repository.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckVersionViewModel @Inject constructor(
    private val repository: CheckVersionRepository
) : ViewModel() {

    // UI 观察的状态
    var updateState by mutableStateOf<UpdateResult>(UpdateResult.NoUpdate)
        private set

    fun checkForUpdates() {
        viewModelScope.launch {
            updateState = repository.checkUpdate()
        }
    }

    fun dismissDialog() {
        updateState = UpdateResult.NoUpdate
    }
}