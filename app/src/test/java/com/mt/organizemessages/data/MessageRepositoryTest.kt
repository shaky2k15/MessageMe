package com.mt.organizemessages.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageRepositoryTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: MessageRepository

    @Before
    fun setup() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        repository = MessageRepository(context)
    }

    @Test
    fun `fetchSms should parse cursor correctly`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getColumnIndexOrThrow(Telephony.Sms._ID) } returns 0
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID) } returns 1
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS) } returns 2
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.BODY) } returns 3
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.DATE) } returns 4
        every { cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE) } returns 5

        every { cursor.getLong(0) } returns 1L
        every { cursor.getLong(1) } returns 101L
        every { cursor.getString(2) } returns "5551234"
        every { cursor.getString(3) } returns "Hello"
        every { cursor.getLong(4) } returns 1000L
        every { cursor.getInt(5) } returns Telephony.Sms.MESSAGE_TYPE_SENT

        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns cursor

        val messages = repository.fetchSms(null)

        assertEquals(1, messages.size)
        assertEquals("sms_1", messages[0].id)
        assertEquals(101L, messages[0].threadId)
        assertEquals("5551234", messages[0].address)
        assertEquals("Hello", messages[0].body)
        assertEquals(true, messages[0].isSent)
    }

    @Test
    fun `fetchSms should handle null cursor`() {
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns null
        val messages = repository.fetchSms(null)
        assertEquals(0, messages.size)
    }
}
