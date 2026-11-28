package me.jbusdriver.modern.ui.forum.account

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 让组合层（Navigation 的浏览器路由）能拿到 [ForumAccountSignals] 单例的宿主。 */
@HiltViewModel
class ForumAccountSignalsViewModel @Inject constructor(
    val signals: me.jbusdriver.modern.data.repository.ForumAccountSignals
) : ViewModel()
