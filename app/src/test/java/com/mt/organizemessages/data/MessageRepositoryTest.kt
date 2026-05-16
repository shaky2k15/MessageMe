package com.mt.organizemessages.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageRepositoryTest {

    private lateinit var repository: MessageRepository
    private lateinit var context: Context
    private val contentResolver: ContentResolver = mockk()

    @Before
    fun setup() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        context = spyk(realContext)
        every { context.contentResolver } returns contentResolver
        
        TagsDbHelper.resetInstance()
        repository = MessageRepository(context)
    }

    @After
    fun tearDown() {
        TagsDbHelper.resetInstance()
        unmockkAll()
    }

    @Test
    fun `fetchSms returns messages correctly`() {
        val cursor = MatrixCursor(arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, 
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE
        ))
        cursor.addRow(arrayOf<Any?>(1L, 10L, "5554", "Hello", 1000L, 2)) // 2 = SENT
        
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val result = repository.fetchSms(null)
        assertEquals(1, result.size)
        assertEquals("sms_1", result[0].id)
        assertEquals(10L, result[0].threadId)
        assertEquals("Hello", result[0].body)
        assertTrue(result[0].isSent)
    }

    @Test
    fun `fetchMms returns messages with parts and address`() {
        // MMS Cursor
        val mmsCursor = MatrixCursor(arrayOf("_id", "thread_id", "date", "msg_box"))
        mmsCursor.addRow(arrayOf<Any?>("1", 20L, 1000L, 2)) // 2 = SENT
        every { contentResolver.query(Telephony.Mms.CONTENT_URI, any(), any(), any(), any()) } returns mmsCursor

        // Parts Cursor (Text and Image)
        val partCursor = MatrixCursor(arrayOf("_id", "ct", "text"))
        partCursor.addRow(arrayOf<Any?>("101", "text/plain", "MMS Body"))
        partCursor.addRow(arrayOf<Any?>("102", "image/jpeg", null))
        every { contentResolver.query(Uri.parse("content://mms/part"), any(), "mid = ?", arrayOf("1"), any()) } returns partCursor

        // Address Cursor
        val addrCursor = MatrixCursor(arrayOf("address", "type"))
        addrCursor.addRow(arrayOf<Any?>("5555", 151)) // 151 = Recipient
        every { contentResolver.query(Uri.parse("content://mms/1/addr"), any(), any(), any(), any()) } returns addrCursor

        val result = repository.fetchMms(null)
        assertEquals(1, result.size)
        assertEquals("mms_1", result[0].id)
        assertEquals("MMS Body", result[0].body)
        assertEquals("content://mms/part/102", result[0].attachmentUri)
        assertEquals("5555", result[0].address)
    }

    @Test
    fun `fetchContacts filters and cleans numbers`() {
        val cursor = MatrixCursor(arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, 
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ))
        cursor.addRow(arrayOf<Any?>("Alice", "123-456-7890"))
        cursor.addRow(arrayOf<Any?>("Bob", " (555) 123-4567 "))
        
        every { contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val result = repository.fetchContacts()
        assertEquals(2, result.size)
        assertEquals("1234567890", result[0].phoneNumber)
        assertEquals("5551234567", result[1].phoneNumber)
    }

    @Test
    fun `fetchAllMessages distincts by threadId`() {
        val cursor = MatrixCursor(arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, 
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE
        ))
        cursor.addRow(arrayOf<Any?>(1L, 10L, "A", "Msg 1", 1000L, 1))
        cursor.addRow(arrayOf<Any?>(2L, 10L, "A", "Msg 2", 2000L, 1)) // Same thread, newer
        
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        every { contentResolver.query(Telephony.Mms.CONTENT_URI, any(), any(), any(), any()) } returns null
        
        val result = repository.fetchAllMessages(groupedByThread = true)
        assertEquals(1, result.size)
        assertEquals("sms_2", result[0].id) // Newest one wins
    }

    @Test
    fun `sendSmsMessage handles API versions correctly`() {
        val mockSmsManager = mockk<SmsManager>(relaxed = true)
        
        // Mock getSystemService for API 31+
        every { context.getSystemService(SmsManager::class.java) } returns mockSmsManager
        
        // Test normal send
        repository.sendSmsMessage("123", "Body")
        verify { mockSmsManager.sendTextMessage("123", null, "Body", null, null) }
        
        // Test with subId
        val subSmsManager = mockk<SmsManager>(relaxed = true)
        every { mockSmsManager.createForSubscriptionId(1) } returns subSmsManager
        repository.sendSmsMessage("123", "Body", 1)
        verify { subSmsManager.sendTextMessage("123", null, "Body", null, null) }
    }

    @Test
    fun `metadata methods return correct data from db`() {
        repository.setTagsForMessage("m1", listOf("t1"))
        repository.setMessageColor("m1", "#F00")
        
        assertEquals(listOf("t1"), repository.getTagsForMessage("m1"))
        assertEquals("#F00", repository.getMessageColor("m1"))
        assertEquals(listOf("t1"), repository.getAllTags())
        assertEquals(listOf("#F00"), repository.getAllColors())
    }
}
