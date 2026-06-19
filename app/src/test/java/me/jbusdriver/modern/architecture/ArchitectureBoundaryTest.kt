package me.jbusdriver.modern.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `forum domain models do not depend on Gson serialization`() {
        val source = File("src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt").readText()

        assertFalse(
            "Domain models should not import Gson; keep ContentBlock adapters in core serialization.",
            source.contains("com.google.gson")
        )
    }
}
