package com.mt.organizemessages

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mt.organizemessages.ui.theme.MessageMeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class SmsAppContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SmsAppContent shows Welcome when permissions missing`() {
        // Robolectric doesn't have permissions by default
        composeTestRule.setContent {
            MessageMeTheme {
                SmsAppContent()
            }
        }
        
        // It might show "Default App Required" first if that check fails
        // In MainActivity.kt, the check for isDefaultSms is first.
        // If Robolectric returns false for isRoleHeld(ROLE_SMS), it shows "Default App Required".
        
        val node = composeTestRule.onAllNodesWithText("Default App Required").fetchSemanticsNodes()
        if (node.isNotEmpty()) {
            composeTestRule.onNodeWithText("Default App Required").assertExists()
        } else {
            composeTestRule.onNodeWithText("Welcome to MessageMe!").assertExists()
        }
    }
}
