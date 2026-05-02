package com.mt.organizemessages.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        dbHelper = TagsDbHelper(context)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase("tags.db")
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
        val sender = "1234567890"
        
        dbHelper.setBlockedSender(sender)
        assertTrue(dbHelper.getBlockedSenders().contains(sender))
        
        dbHelper.removeBlockedSender(sender)
        assertFalse(dbHelper.getBlockedSenders().contains(sender))
    }

    @Test
    fun testArchivedThreads() {
        val threadId = 123L
        
        dbHelper.setArchivedThread(threadId)
        assertTrue(dbHelper.getArchivedThreads().contains(threadId))
    }

    @Test
    fun testMessageColors() {
        val messageId = "msg1"
        val color = "#FF0000"
        
        dbHelper.setMessageColor(messageId, color)
        val colorsMap = dbHelper.getAllMessageColorsMap()
        
        assertEquals(color, colorsMap[messageId])
    }
}
