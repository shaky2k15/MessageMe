package com.mt.organizemessages.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.data.MessageRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val application: Application = mockk(relaxed = true)
    private val repository: MessageRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var viewModel: InboxViewModel
    private val tagsChangedFlow = MutableSharedFlow<Unit>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(MessageRepository.Companion)
        every { MessageRepository.tagsChanged } returns tagsChangedFlow
        
        coEvery { repository.getBlockedSenders() } returns setOf("123")
        coEvery { repository.getArchivedThreads() } returns setOf(456L)
        coEvery { repository.fetchAllMessages() } returns listOf(mockk())
        coEvery { repository.getAllTags() } returns listOf("tag1")
        
        viewModel = InboxViewModel(application, repository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadData updates state flows`() = runTest {
        viewModel.loadData()
        
        assertEquals(setOf("123"), viewModel.blockedSenders.value)
        assertEquals(setOf(456L), viewModel.archivedThreads.value)
        assertEquals(1, viewModel.messages.value.size)
        assertEquals(listOf("tag1"), viewModel.allTags.value)
    }

    @Test
    fun `archiveThread updates state`() = runTest {
        coEvery { repository.setArchivedThread(any()) } just Runs
        coEvery { repository.getArchivedThreads() } returns setOf(789L)
        
        viewModel.archiveThread(789L)
        
        coVerify { repository.setArchivedThread(789L) }
        assertEquals(setOf(789L), viewModel.archivedThreads.value)
    }

    @Test
    fun `blockSender updates state and reloads data`() = runTest {
        coEvery { repository.setBlockedSender(any()) } just Runs
        coEvery { repository.getBlockedSenders() } returns setOf("999")
        
        viewModel.blockSender("999")
        
        coVerify { repository.setBlockedSender("999") }
        assertEquals(setOf("999"), viewModel.blockedSenders.value)
    }
}
