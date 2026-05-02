package com.mt.organizemessages.data

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageRepositoryTest {

    private lateinit var repository: MessageRepository
    private val context: Context = mockk()
    private val contentResolver: android.content.ContentResolver = mockk()

    @Before
    fun setup() {
        every { context.contentResolver } returns contentResolver
        repository = MessageRepository(context)
    }

    @Test
    fun testFetchSms() {
        val cursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE))
        cursor.addRow(arrayOf(1L, 10L, "123456", "Hello", System.currentTimeMillis(), 1))
        
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val messages = repository.fetchSms(null)
        assertEquals(1, messages.size)
        assertEquals("Hello", messages[0].body)
    }

    @Test
    fun testFetchMms() {
        val mmsCursor = MatrixCursor(arrayOf("_id", "thread_id", "date", "msg_box"))
        mmsCursor.addRow(arrayOf("1", 10L, System.currentTimeMillis() / 1000, 1))
        
        every { contentResolver.query(Telephony.Mms.CONTENT_URI, any(), any(), any(), any()) } returns mmsCursor
        
        val partCursor = MatrixCursor(arrayOf("_id", "mid", "ct", "text"))
        partCursor.addRow(arrayOf("101", "1", "text/plain", "MMS text"))
        every { contentResolver.query(Uri.parse("content://mms/part"), any(), any(), any(), any()) } returns partCursor
        
        val addrCursor = MatrixCursor(arrayOf("address", "type"))
        addrCursor.addRow(arrayOf("654321", 137))
        every { contentResolver.query(Uri.parse("content://mms/1/addr"), any(), any(), any(), any()) } returns addrCursor
        
        val messages = repository.fetchMms(null)
        assertEquals(1, messages.size)
        assertEquals("MMS text", messages[0].body)
    }

    @Test
    fun testFetchContacts() {
        val cursor = MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER))
        cursor.addRow(arrayOf("Alice", "123-456-7890"))
        
        every { contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val contacts = repository.fetchContacts()
        assertEquals(1, contacts.size)
        assertEquals("Alice", contacts[0].name)
    }
}
