package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.jbusdriver.modern.data.UiPrefsStore
import javax.inject.Inject

@HiltViewModel
class UiPrefsViewModel @Inject constructor(
    val store: UiPrefsStore
) : ViewModel()
