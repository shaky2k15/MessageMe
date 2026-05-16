package com.mt.organizemessages

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLogicTest {

    @Test
    fun `AppScreen objects should be correct`() {
        assertEquals(AppScreen.Inbox, AppScreen.Inbox)
        assertEquals(AppScreen.NewMessage, AppScreen.NewMessage)
        
        val thread = AppScreen.Thread(1L, "addr")
        assertEquals(1L, thread.threadId)
        assertEquals("addr", thread.address)

        val tagFilter = AppScreen.TagFilter("tag")
        assertEquals("tag", tagFilter.tag)

        val colorFilter = AppScreen.ColorFilter("#FFF")
        assertEquals("#FFF", colorFilter.colorHex)
        
        assertEquals(AppScreen.MetricsBrowser, AppScreen.MetricsBrowser)
        assertEquals(AppScreen.SpamFolder, AppScreen.SpamFolder)
        assertEquals(AppScreen.Settings, AppScreen.Settings)
        assertEquals(AppScreen.About, AppScreen.About)
        assertEquals(AppScreen.SignatureSettings, AppScreen.SignatureSettings)
    }
}
