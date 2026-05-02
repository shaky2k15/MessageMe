package com.mt.organizemessages

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsManagerTest {

    private val context: Context = mockk()
    private val sharedPrefs: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk()
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        every { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } just Runs
        
        settingsManager = SettingsManager(context)
    }

    @Test
    fun testTaggingToggle() {
        every { sharedPrefs.getBoolean("enable_tagging", true) } returns true
        assertTrue(settingsManager.isTaggingEnabled)
        
        settingsManager.isTaggingEnabled = false
        verify { editor.putBoolean("enable_tagging", false) }
    }

    @Test
    fun testMetricsToggle() {
        every { sharedPrefs.getBoolean("enable_metrics", true) } returns true
        assertTrue(settingsManager.isMetricsEnabled)
        
        settingsManager.isMetricsEnabled = false
        verify { editor.putBoolean("enable_metrics", false) }
    }
}
