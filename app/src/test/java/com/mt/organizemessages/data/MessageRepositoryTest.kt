package com.mt.organizemessages.data

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
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
    private val contentResolver: android.content.ContentResolver = mockk()
    private val db: TagsDbHelper = mockk(relaxed = true)

    @Before
    fun setup() {
        // Use a real Robolectric context for database operations
        val realContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        
        // Mock the context to return our mock content resolver for SMS/MMS/Contacts
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
    fun testFetchSms() {
        val cursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE))
        cursor.addRow(arrayOf<Any>(1L, 10L, "123456", "Hello", System.currentTimeMillis(), 1))
        
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val messages = repository.fetchSms(null)
        assertEquals(1, messages.size)
        assertEquals("Hello", messages[0].body)
    }

    @Test
    fun testMetadataOperations() {
        // Test Tags
        repository.setTagsForMessage("msg1", listOf("work", "urgent"))
        assertEquals(listOf("work", "urgent"), repository.getTagsForMessage("msg1"))
        assertTrue(repository.getAllTags().contains("work"))
        
        // Test Colors
        repository.setMessageColor("msg1", "#FF0000")
        assertEquals("#FF0000", repository.getMessageColor("msg1"))
        
        // Test Blocking
        repository.setBlockedSender("12345")
        assertTrue(repository.getBlockedSenders().contains("12345"))
        repository.removeBlockedSender("12345")
        assertFalse(repository.getBlockedSenders().contains("12345"))
        
        // Test Archiving
        repository.setArchivedThread(100L)
        assertTrue(repository.getArchivedThreads().contains(100L))
    }

    @Test
    fun testFetchContacts() {
        val cursor = MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER))
        cursor.addRow(arrayOf<Any>("Alice", "123-456-7890"))
        
        every { contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        
        val contacts = repository.fetchContacts()
        assertEquals(1, contacts.size)
        assertEquals("Alice", contacts[0].name)
    }
    
    @Test
    fun testFetchAllMessagesCombinesSmsAndMms() {
        // Mock SMS
        val smsCursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE))
        smsCursor.addRow(arrayOf<Any>(1L, 10L, "123", "SMS", System.currentTimeMillis(), 1))
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns smsCursor
        
        // Mock MMS
        val mmsCursor = MatrixCursor(arrayOf("_id", "thread_id", "date", "msg_box"))
        mmsCursor.addRow(arrayOf<Any>("1", 11L, System.currentTimeMillis() / 1000, 1))
        every { contentResolver.query(Telephony.Mms.CONTENT_URI, any(), any(), any(), any()) } returns mmsCursor
        
        // Mock MMS part
        val partCursor = MatrixCursor(arrayOf("_id", "mid", "ct", "text"))
        partCursor.addRow(arrayOf<Any>("101", "1", "text/plain", "MMS"))
        every { contentResolver.query(Uri.parse("content://mms/part"), any(), any(), any(), any()) } returns partCursor
        
        // Mock MMS addr
        val addrCursor = MatrixCursor(arrayOf("address", "type"))
        addrCursor.addRow(arrayOf<Any>("456", 137))
        every { contentResolver.query(Uri.parse("content://mms/1/addr"), any(), any(), any(), any()) } returns addrCursor
        
        val allMessages = repository.fetchAllMessages()
        assertEquals(2, allMessages.size)
    }

    @Test
    fun testSendSms() {
        val smsManager = mockk<android.telephony.SmsManager>(relaxed = true)
        every { context.getSystemService(android.telephony.SmsManager::class.java) } returns smsManager
        
        // Test with default subId
        repository.sendSmsMessage("123", "Hello")
        verify { smsManager.sendTextMessage("123", null, "Hello", null, null) }
        
        // Test with specific subId
        val subSmsManager = mockk<android.telephony.SmsManager>(relaxed = true)
        every { smsManager.createForSubscriptionId(1) } returns subSmsManager
        repository.sendSmsMessage("123", "Hello", 1)
        verify { subSmsManager.sendTextMessage("123", null, "Hello", null, null) }
    }

    @Test
    fun testSimulateSendMms() {
        val uri = mockk<Uri>()
        repository.simulateSendMms(100L, "456", uri)
        // Should not crash, verifying it doesn't fail
    }

    @Test
    fun testFetchAllMessagesEmpty() {
        every { contentResolver.query(Telephony.Sms.CONTENT_URI, any(), any(), any(), any()) } returns null
        every { contentResolver.query(Telephony.Mms.CONTENT_URI, any(), any(), any(), any()) } returns null
        
        val all = repository.fetchAllMessages()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testFetchSmsWithThreadId() {
        val cursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE))
        cursor.addRow(arrayOf<Any>(1L, 10L, "123", "Body", 0L, 1))
        every { contentResolver.query(any(), any(), match { it?.contains("thread_id = ?") == true }, any(), any()) } returns cursor
        
        val msgs = repository.fetchSms(10L)
        assertEquals(1, msgs.size)
        verify { 
            contentResolver.query(Telephony.Sms.CONTENT_URI, any(), match { it?.contains("thread_id = ?") == true }, arrayOf("10"), any()) 
        }
    }

    @Test
    fun testFetchMmsWithThreadId() {
        val cursor = MatrixCursor(arrayOf("_id", "thread_id", "date", "msg_box"))
        cursor.addRow(arrayOf<Any>("1", 10L, 0L, 1))
        every { 
            contentResolver.query(Telephony.Mms.CONTENT_URI, any(), match { it?.contains("thread_id = ?") == true }, any(), any()) 
        } returns cursor
        every { contentResolver.query(Uri.parse("content://mms/part"), any(), any(), any(), any()) } returns null
        every { contentResolver.query(Uri.parse("content://mms/1/addr"), any(), any(), any(), any()) } returns null
        
        val msgs = repository.fetchMms(10L)
        assertEquals(1, msgs.size)
    }

    @Test
    fun testGetMessageMetadata() {
        repository.setTagsForMessage("1", listOf("tag"))
        repository.setMessageColor("1", "#FFF")
        
        assertEquals(listOf("tag"), repository.getTagsForMessage("1"))
        assertEquals("#FFF", repository.getMessageColor("1"))
    }

    @Test
    fun testGetAllTags() {
        repository.setTagsForMessage("1", listOf("tagB", "tagA"))
        repository.setTagsForMessage("2", listOf("tagA", "tagC"))
        val allTags = repository.getAllTags()
        assertEquals(listOf("tagA", "tagB", "tagC"), allTags)
    }
}
