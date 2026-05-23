package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.jbusdriver.modern.data.LabSettingsStore
import javax.inject.Inject

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore
) : ViewModel()
