package me.jbusdriver.modern.data

import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.data.remote.JAVBusService
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository

@Singleton
class DefaultSettingsRepository @Inject constructor() : SettingsRepository
