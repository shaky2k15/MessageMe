package com.mt.organizemessages

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mt.organizemessages.ui.theme.MessageMeTheme
import com.mt.organizemessages.ui.InboxViewModel
import com.mt.organizemessages.ui.ThreadViewModel
import com.mt.organizemessages.data.MessageRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.app.Application
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `InboxItem displays correctly`() {
        val msg = ChatMessage(
            id = "1", threadId = 1L, address = "5554", 
            body = "Test Body", date = System.currentTimeMillis(), 
            isSent = false, tags = listOf("work")
        )
        
        composeTestRule.setContent {
            MessageMeTheme {
                InboxItem(
                    msg = msg,
                    onClick = {},
                    onArchive = {},
                    onBlock = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("5554").assertExists()
        composeTestRule.onNodeWithText("Test Body").assertExists()
        composeTestRule.onNodeWithText("#work").assertExists()
    }

    @Test
    fun `ChatBubble displays correctly`() {
        val msg = ChatMessage(
            id = "2", threadId = 1L, address = "5554", 
            body = "Bubble Message", date = System.currentTimeMillis(), 
            isSent = true, tags = emptyList()
        )
        
        composeTestRule.setContent {
            MessageMeTheme {
                ChatBubble(
                    msg = msg,
                    isTaggingEnabled = true,
                    onTagClick = {},
                    onTagsSaved = { _, _ -> },
                    onColorSaved = { _, _ -> }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Bubble Message").assertExists()
    }

    @Test
    fun `MainSmsScreen displays inbox title and empty state`() {
        val mockViewModel = mockk<InboxViewModel>(relaxed = true)
        
        every { mockViewModel.messages } returns MutableStateFlow(emptyList<ChatMessage>()).asStateFlow()
        every { mockViewModel.blockedSenders } returns MutableStateFlow(emptySet<String>()).asStateFlow()
        every { mockViewModel.archivedThreads } returns MutableStateFlow(emptySet<Long>()).asStateFlow()
        every { mockViewModel.allTags } returns MutableStateFlow(emptyList<String>()).asStateFlow()
        every { mockViewModel.allColors } returns MutableStateFlow(emptyList<String>()).asStateFlow()

        composeTestRule.setContent {
            MessageMeTheme {
                MainSmsScreen(
                    isMetricsEnabled = true,
                    isSignatureEnabled = true,
                    onNavigateToNewMessage = {},
                    onNavigateToMetrics = {},
                    onNavigateToSpam = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onNavigateToSignature = {},
                    onNavigateToThread = { _, _ -> },
                    onNavigateToTagFilter = {},
                    onNavigateToColorFilter = {},
                    inboxViewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Inbox").assertExists()
        composeTestRule.onNodeWithText("Your inbox is empty.").assertExists()
        composeTestRule.onNodeWithContentDescription("New Message").assertExists()
    }

    @Test
    fun `ThreadScreen displays address and messages`() {
        val mockViewModel = mockk<ThreadViewModel>(relaxed = true)
        val msgs = listOf(
            ChatMessage("1", 1L, "123456", "Thread Message", System.currentTimeMillis(), false)
        )
        
        every { mockViewModel.messages } returns MutableStateFlow(msgs).asStateFlow()

        composeTestRule.setContent {
            MessageMeTheme {
                ThreadScreen(
                    threadId = 1L,
                    address = "123456",
                    isTaggingEnabled = true,
                    onNavigateBack = {},
                    onTagClick = {},
                    threadViewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("123456").assertExists()
        composeTestRule.onNodeWithText("Thread Message").assertExists()
    }

    @Test
    fun `NewMessageScreen displays UI components`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)
        val mockRepo = mockk<MessageRepository>(relaxed = true)
        
        composeTestRule.setContent {
            MessageMeTheme {
                NewMessageScreen(
                    settingsManager = settingsManager,
                    onNavigateBack = {},
                    messageRepo = mockRepo
                )
            }
        }

        composeTestRule.onNodeWithText("New Message").assertExists()
        composeTestRule.onNodeWithText("To: Name or Number").assertExists()
        composeTestRule.onNodeWithText("Send").assertExists()
    }

    @Test
    fun `SettingsScreen displays switches`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)
        
        composeTestRule.setContent {
            MessageMeTheme {
                SettingsScreen(
                    settingsManager = settingsManager,
                    onSettingsChanged = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithText("Enable Tagging").assertExists()
    }

    @Test
    fun `MainSmsScreen search updates text`() {
        val mockViewModel = mockk<InboxViewModel>(relaxed = true)
        every { mockViewModel.messages } returns MutableStateFlow(emptyList<ChatMessage>()).asStateFlow()
        every { mockViewModel.blockedSenders } returns MutableStateFlow(emptySet<String>()).asStateFlow()
        every { mockViewModel.archivedThreads } returns MutableStateFlow(emptySet<Long>()).asStateFlow()
        every { mockViewModel.allTags } returns MutableStateFlow(emptyList<String>()).asStateFlow()
        every { mockViewModel.allColors } returns MutableStateFlow(emptyList<String>()).asStateFlow()

        composeTestRule.setContent {
            MessageMeTheme {
                MainSmsScreen(
                    isMetricsEnabled = false, isSignatureEnabled = false,
                    onNavigateToNewMessage = {}, onNavigateToMetrics = {}, onNavigateToSpam = {},
                    onNavigateToSettings = {}, onNavigateToAbout = {}, onNavigateToSignature = {},
                    onNavigateToThread = { _, _ -> }, onNavigateToTagFilter = {},
                    onNavigateToColorFilter = {}, inboxViewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Search messages...").performTextInput("Hello")
        composeTestRule.onNodeWithText("Hello").assertExists()
    }

    @Test
    fun `MainSmsScreen drawer opens and shows items`() {
        val mockViewModel = mockk<InboxViewModel>(relaxed = true)
        every { mockViewModel.messages } returns MutableStateFlow(emptyList<ChatMessage>()).asStateFlow()
        every { mockViewModel.blockedSenders } returns MutableStateFlow(emptySet<String>()).asStateFlow()
        every { mockViewModel.archivedThreads } returns MutableStateFlow(emptySet<Long>()).asStateFlow()
        every { mockViewModel.allTags } returns MutableStateFlow(listOf("tag1")).asStateFlow()
        every { mockViewModel.allColors } returns MutableStateFlow(listOf("#FF0000")).asStateFlow()

        composeTestRule.setContent {
            MessageMeTheme {
                MainSmsScreen(
                    isMetricsEnabled = false, isSignatureEnabled = true,
                    onNavigateToNewMessage = {}, onNavigateToMetrics = {}, onNavigateToSpam = {},
                    onNavigateToSettings = {}, onNavigateToAbout = {}, onNavigateToSignature = {},
                    onNavigateToThread = { _, _ -> }, onNavigateToTagFilter = {},
                    onNavigateToColorFilter = {}, inboxViewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithText("tag1").assertExists()
        composeTestRule.onNodeWithText("Category").assertExists()
        composeTestRule.onNodeWithText("#FF0000").assertDoesNotExist()
    }

    @Test
    fun `ThreadScreen send button interaction`() {
        val mockViewModel = mockk<ThreadViewModel>(relaxed = true)
        every { mockViewModel.messages } returns MutableStateFlow(emptyList<ChatMessage>()).asStateFlow()

        composeTestRule.setContent {
            MessageMeTheme {
                ThreadScreen(
                    threadId = 1L, address = "123", isTaggingEnabled = true,
                    onNavigateBack = {}, onTagClick = {}, threadViewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Text message").performTextInput("New reply")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        
        verify { mockViewModel.sendMessage("123", "New reply") }
    }

    @Test
    fun `NewMessageScreen send button interaction`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)
        val mockRepo = mockk<MessageRepository>(relaxed = true)

        composeTestRule.setContent {
            MessageMeTheme {
                NewMessageScreen(
                    settingsManager = settingsManager,
                    onNavigateBack = {},
                    messageRepo = mockRepo
                )
            }
        }

        composeTestRule.onNodeWithText("To: Name or Number").performTextInput("5554")
        composeTestRule.onNodeWithText("Message").performTextInput("Test message")
        composeTestRule.onNodeWithText("Send").performClick()

        verify { mockRepo.sendSmsMessage("5554", "Test message", any()) }
    }

    @Test
    fun `AboutScreen displays app info`() {
        composeTestRule.setContent {
            MessageMeTheme {
                AboutScreen(onNavigateBack = {})
            }
        }
        composeTestRule.onNodeWithText("About").assertExists()
        composeTestRule.onNodeWithText("Version 1.0.0").assertExists()
    }

    @Test
    fun `SignatureSettingsScreen displays UI`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)
        composeTestRule.setContent {
            MessageMeTheme {
                SignatureSettingsScreen(settingsManager = settingsManager, onNavigateBack = {})
            }
        }
        composeTestRule.onNodeWithText("Signature Details").assertExists()
        composeTestRule.onNodeWithText("Save Signature").assertExists()
    }

    @Test
    fun `TagFilterScreen displays UI`() {
        composeTestRule.setContent {
            MessageMeTheme {
                TagFilterScreen(tag = "work", onNavigateToThread = { _, _ -> }, onNavigateBack = {})
            }
        }
        composeTestRule.onNodeWithText("Tag: work").assertExists()
    }

    @Test
    fun `ColorFilterScreen displays UI`() {
        composeTestRule.setContent {
            MessageMeTheme {
                ColorFilterScreen(colorHex = "#FF0000", onNavigateToThread = { _, _ -> }, onNavigateBack = {})
            }
        }
        composeTestRule.onNodeWithText("Color: #FF0000", substring = true).assertExists()
    }

    @Test
    fun `SpamFolderScreen displays UI`() {
        composeTestRule.setContent {
            MessageMeTheme {
                SpamFolderScreen(onNavigateToThread = { _, _ -> }, onNavigateBack = {})
            }
        }
        composeTestRule.onNodeWithText("Spam & Blocked").assertExists()
    }

    @Test
    fun `MetricsBrowserScreen displays UI`() {
        composeTestRule.setContent {
            MessageMeTheme {
                MetricsBrowserScreen(modifier = androidx.compose.ui.Modifier, onBack = {})
            }
        }
        composeTestRule.onNodeWithText("Metrics").assertExists()
    }

}
