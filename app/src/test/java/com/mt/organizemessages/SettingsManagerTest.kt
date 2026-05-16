package com.mt.organizemessages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun `isTaggingEnabled defaults to true and can be changed`() {
        assertTrue(settingsManager.isTaggingEnabled)
        settingsManager.isTaggingEnabled = false
        assertFalse(settingsManager.isTaggingEnabled)
    }

    @Test
    fun `isMetricsEnabled defaults to true and can be changed`() {
        assertTrue(settingsManager.isMetricsEnabled)
        settingsManager.isMetricsEnabled = false
        assertFalse(settingsManager.isMetricsEnabled)
    }

    @Test
    fun `isSignatureEnabled defaults to false and can be changed`() {
        assertFalse(settingsManager.isSignatureEnabled)
        settingsManager.isSignatureEnabled = true
        assertTrue(settingsManager.isSignatureEnabled)
    }

    @Test
    fun `signatureText defaults to empty and can be changed`() {
        assertEquals("", settingsManager.signatureText)
        settingsManager.signatureText = "My Sig"
        assertEquals("My Sig", settingsManager.signatureText)
    }
}
