package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertFalse
import org.junit.Test

class ILinkContractTest {
    @Test
    fun linkContractDoesNotExposeMutableCollectionCategory() {
        val methodNames = ILink::class.java.methods.map { it.name }.toSet()

        assertFalse(methodNames.contains("getCategoryId"))
        assertFalse(methodNames.contains("setCategoryId"))
    }
}
