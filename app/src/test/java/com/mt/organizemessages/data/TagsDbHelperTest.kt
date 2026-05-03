package com.mt.organizemessages.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagsDbHelperTest {

    private lateinit var dbHelper: TagsDbHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        TagsDbHelper.resetInstance()
        dbHelper = TagsDbHelper.getInstance(context)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        TagsDbHelper.resetInstance()
        context.deleteDatabase("tags.db")
        unmockkAll()
    }

    @Test
    fun testTagsOperations() {
        val messageId = "msg1"
        val tags = listOf("work", "urgent")
        
        dbHelper.setTagsForMessage(messageId, tags)
        val tagsMap = dbHelper.getAllTagsMap()
        
        assertEquals(tags, tagsMap[messageId])
    }

    @Test
    fun testBlockedSenders() {
        val address = "123456"
        dbHelper.setBlockedSender(address)
        assertTrue(dbHelper.getBlockedSenders().contains(address))
        
        dbHelper.removeBlockedSender(address)
        assertFalse(dbHelper.getBlockedSenders().contains(address))
    }

    @Test
    fun testArchivedThreads() {
        val threadId = 100L
        dbHelper.setArchivedThread(threadId)
        assertTrue(dbHelper.getArchivedThreads().contains(threadId))
    }

    @Test
    fun testMessageColors() {
        val messageId = "msg2"
        val color = "#FF5722"
        dbHelper.setMessageColor(messageId, color)
        val colorsMap = dbHelper.getAllMessageColorsMap()
        
        assertEquals(color, colorsMap[messageId])
    }

    @Test
    fun testOnCreate() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        dbHelper.onCreate(mockDb)
        verify { mockDb.execSQL(any()) }
    }

    @Test
    fun testOnUpgrade() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        dbHelper.onUpgrade(mockDb, 1, 2)
        verify { mockDb.execSQL(any()) }
    }
}
