package com.mt.organizemessages.ui

import android.app.Application
import android.net.Uri
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.fetchChatThread(any()) } returns emptyList()
        viewModel = ThreadViewModel(application, repository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadThread updates messages`() = runTest {
        val msgs = listOf(mockk<com.mt.organizemessages.ChatMessage>())
        coEvery { repository.fetchChatThread(123L) } returns msgs
        
        viewModel.loadThread(123L)
        
        assertEquals(msgs, viewModel.messages.value)
    }

    @Test
    fun `sendMessage calls repository`() = runTest {
        coEvery { repository.sendSmsMessage(any(), any(), any()) } just Runs
        
        viewModel.sendMessage("123", "Hello")
        
        coVerify { repository.sendSmsMessage("123", match { it.startsWith("Hello") }, any()) }
    }

    @Test
    fun `sendMms calls repository`() = runTest {
        val uri = mockk<Uri>()
        coEvery { repository.simulateSendMms(any(), any(), any()) } just Runs
        
        viewModel.sendMms(123L, "address", uri)
        
        coVerify { repository.simulateSendMms(123L, "address", uri) }
    }

    @Test
    fun `setTagsForMessage calls repository and reloads thread`() = runTest {
        coEvery { repository.setTagsForMessage(any(), any()) } just Runs
        val msgs = listOf(mockk<com.mt.organizemessages.ChatMessage>())
        coEvery { repository.fetchChatThread(123L) } returns msgs
        
        viewModel.loadThread(123L)
        viewModel.setTagsForMessage("msgId", listOf("tag1"))
        
        coVerify { repository.setTagsForMessage("msgId", listOf("tag1")) }
        assertEquals(msgs, viewModel.messages.value)
    }

    @Test
    fun `setMessageColor calls repository`() = runTest {
        coEvery { repository.setMessageColor(any(), any()) } just Runs
        
        viewModel.setMessageColor("msgId", "#FF0000")
        
        coVerify { repository.setMessageColor("msgId", "#FF0000") }
    }
}
