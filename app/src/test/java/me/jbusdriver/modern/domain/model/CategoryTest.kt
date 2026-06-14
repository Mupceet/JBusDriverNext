package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryTest {
    @Test
    fun categoriesWithSameFieldsHaveSameHashCode() {
        val first = Category("A", pid = -1, tree = "1/", order = 10, id = 7)
        val second = Category("A", pid = -1, tree = "1/", order = 10, id = 7)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun categoriesWithSameIdButDifferentContentAreNotEqual() {
        val first = Category("A", pid = -1, tree = "1/", order = 10, id = 7)
        val second = Category("B", pid = 3, tree = "3/7/", order = 20, id = 7)

        assertNotEquals(first, second)
    }
}
