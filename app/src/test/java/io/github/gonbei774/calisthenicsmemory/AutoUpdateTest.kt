package io.github.gonbei774.calisthenicsmemory

import io.github.gonbei774.calisthenicsmemory.viewmodel.extractAutoUpdate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoUpdateTest {

    @Test
    fun testExtractAutoUpdate() {
        val text = """
            Here is your updated context:
            {
              "type": "auto_update",
              "updatedContext": {
                "version": 22,
                "app": "Calisthenics Memory",
                "groups": [],
                "exercises": [],
                "records": []
              }
            }
            I have removed the exercise as requested.
        """.trimIndent()

        val extracted = extractAutoUpdate(text)
        assertNotNull(extracted)
        assert(extracted!!.contains("\"type\": \"auto_update\""))
    }

    @Test
    fun testExtractAutoUpdateNoMatch() {
        val text = "Just a normal message."
        val extracted = extractAutoUpdate(text)
        assertNull(extracted)
    }
}
