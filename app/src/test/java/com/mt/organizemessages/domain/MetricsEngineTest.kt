package com.mt.organizemessages.domain

import com.mt.organizemessages.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsEngineTest {

    private val metricsEngine = MetricsEngine()

    @Test
    fun `generateMetricsHtml should calculate stats correctly`() {
        val messages = listOf(
            ChatMessage("1", 101, "Alice", "Hi", 0, false, tags = listOf("friends")),
            ChatMessage("2", 101, "Alice", "How are you?", 0, false, tags = listOf("friends", "important")),
            ChatMessage("3", 102, "Bob", "Work stuff", 0, true, tags = listOf("work"))
        )

        val html = metricsEngine.generateMetricsHtml(messages)

        assertTrue(html.contains("Total Messages:</b> 3"))
        assertTrue(html.contains("Unique Tags:</b> 3")) // friends, important, work
        assertTrue(html.contains("Frequent Sender:</b> Alice"))
        assertTrue(html.contains("friends</span> <span>2</span>"))
        assertTrue(html.contains("work</span> <span>1</span>"))
    }

    @Test
    fun `generateMetricsHtml should handle empty list gracefully`() {
        val html = metricsEngine.generateMetricsHtml(emptyList())
        assertTrue(html.contains("Total Messages:</b> 0"))
        assertTrue(html.contains("Frequent Sender:</b> N/A"))
    }
}
