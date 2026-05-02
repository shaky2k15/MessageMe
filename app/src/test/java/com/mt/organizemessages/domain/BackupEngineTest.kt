package com.mt.organizemessages.domain

import com.mt.organizemessages.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEngineTest {

    private val backupEngine = BackupEngine()

    @Test
    fun `generateBackupXml should create valid XML for single message`() {
        val messages = listOf(
            ChatMessage(
                id = "1",
                threadId = 101L,
                address = "5551234",
                body = "Hello World!",
                date = 1625097600000L, // 2021-07-01
                isSent = false,
                tags = listOf("work")
            )
        )

        val xml = backupEngine.generateBackupXml(messages)

        assertTrue(xml.contains("address='5551234'"))
        assertTrue(xml.contains("body='Hello World!'"))
        assertTrue(xml.contains("tags='work'"))
        assertTrue(xml.contains("<smses count='1'>"))
    }

    @Test
    fun `generateBackupXml should escape special characters`() {
        val messages = listOf(
            ChatMessage(
                id = "2",
                threadId = 102L,
                address = "Joe & Moe",
                body = "Quote: \"Hello\"",
                date = 1625097600000L,
                isSent = true
            )
        )

        val xml = backupEngine.generateBackupXml(messages)

        assertTrue(xml.contains("address='Joe &amp; Moe'"))
        assertTrue(xml.contains("body='Quote: &quot;Hello&quot;'"))
    }

    @Test
    fun `generateBackupXml should handle empty list`() {
        val xml = backupEngine.generateBackupXml(emptyList())
        assertTrue(xml.contains("<smses count='0'>"))
    }
}
