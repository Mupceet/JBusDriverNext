package me.jbusdriver.modern.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JBusManagerTest {
    @Test
    fun managerListIsNotPubliclyMutable() {
        val publicMethodNames = JBusManager::class.java.methods.map { it.name }.toSet()

        assertFalse(publicMethodNames.contains("getManager"))
        assertTrue(publicMethodNames.contains("getCurrentActivity"))
        assertTrue(publicMethodNames.contains("getActiveActivityCount"))
    }
}
