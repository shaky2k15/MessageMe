package com.mt.organizemessages

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTest {

    @Test
    fun `ChatMessage should retain values`() {
        val msg = ChatMessage(
            id = "1",
            threadId = 10L,
            address = "addr",
            body = "body",
            date = 1000L,
            isSent = true,
            tags = listOf("tag1"),
            colorHex = "#FFFFFF"
        )
        
        assertEquals("1", msg.id)
        assertEquals(10L, msg.threadId)
        assertEquals("addr", msg.address)
        assertEquals("body", msg.body)
        assertEquals(1000L, msg.date)
        assertEquals(true, msg.isSent)
        assertEquals(listOf("tag1"), msg.tags)
        assertEquals("#FFFFFF", msg.colorHex)
    }

    @Test
    fun `ContactInfo should retain values`() {
        val contact = ContactInfo("Alice", "123")
        assertEquals("Alice", contact.name)
        assertEquals("123", contact.phoneNumber)
    }
}
