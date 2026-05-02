package com.mt.organizemessages.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.data.MessageRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    private val application: Application = mockk(relaxed = true)
    private val repository: MessageRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var viewModel: ThreadViewModel
    private val contentResolver: android.content.ContentResolver = mockk(relaxed = true)

    @Before
    fun setup() {
        every { application.contentResolver } returns contentResolver
        Dispatchers.setMain(testDispatcher)
        viewModel = ThreadViewModel(application, repository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadThread updates messages`() = runTest {
        val mockMessages = listOf(mockk<ChatMessage>())
        coEvery { repository.fetchChatThread(123L) } returns mockMessages
        
        viewModel.loadThread(123L)
        
        assertEquals(mockMessages, viewModel.messages.value)
    }

    @Test
    fun `sendMessage calls repository`() = runTest {
        coEvery { repository.sendSmsMessage(any(), any(), any()) } just Runs
        
        viewModel.sendMessage("123", "Hello", 1)
        
        coVerify { repository.sendSmsMessage("123", "Hello", 1) }
    }

    @Test
    fun `setTagsForMessage updates state`() = runTest {
        coEvery { repository.setTagsForMessage(any(), any()) } just Runs
        coEvery { repository.fetchChatThread(123L) } returns emptyList()
        
        viewModel.loadThread(123L) 
        viewModel.setTagsForMessage("msg1", listOf("tag1"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { repository.setTagsForMessage("msg1", listOf("tag1")) }
        coVerify(exactly = 2) { repository.fetchChatThread(123L) }
    }

    @Test
    fun `setMessageColor calls repository`() = runTest {
        coEvery { repository.setMessageColor(any(), any()) } just Runs
        
        viewModel.setMessageColor("msg1", "#FF0000")
        
        coVerify { repository.setMessageColor("msg1", "#FF0000") }
    }

    @Test
    fun `sendMms calls repository`() = runTest {
        val uri = mockk<Uri>()
        coEvery { repository.simulateSendMms(any(), any(), any()) } just Runs
        
        viewModel.sendMms(123L, "address", uri)
        
        coVerify { repository.simulateSendMms(123L, "address", uri) }
    }
}
