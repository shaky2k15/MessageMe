package com.mt.organizemessages.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.data.MessageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for [com.mt.organizemessages.MainSmsScreen].
 *
 * Owns all inbox state as [kotlinx.coroutines.flow.StateFlow]s. All data
 * fetching runs on [ioDispatcher] via [viewModelScope].
 *
 * The [ContentObserver] is registered once in [init] and cleaned up in
 * [onCleared] — it must never be registered from a Composable.
 * See ADR-003.
 */
class InboxViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MessageRepository = MessageRepository(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    /** Live inbox messages — one entry per thread, newest first. */
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Phone numbers blocked by the user. */
    private val _blockedSenders = MutableStateFlow<Set<String>>(emptySet())
    val blockedSenders: StateFlow<Set<String>> = _blockedSenders.asStateFlow()

    /** Thread IDs archived by the user. */
    private val _archivedThreads = MutableStateFlow<Set<Long>>(emptySet())
    val archivedThreads: StateFlow<Set<Long>> = _archivedThreads.asStateFlow()

    /** All unique tags across all messages, sorted alphabetically. */
    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    // ContentObserver registered once, lives with the ViewModel
    private val contentObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadData()
        }
    }

    init {
        loadData()
        application.contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/conversations"), true, contentObserver
        )
        // Refresh allTags whenever a tag is saved anywhere (e.g. from ThreadScreen)
        viewModelScope.launch {
            MessageRepository.tagsChanged.collect {
                _allTags.value = withContext(ioDispatcher) { repository.getAllTags() }
            }
        }
    }

    /** Reloads all inbox state from [MessageRepository] on [ioDispatcher]. */
    fun loadData() {
        viewModelScope.launch(ioDispatcher) {
            val blocked = repository.getBlockedSenders()
            val archived = repository.getArchivedThreads()
            val msgs = repository.fetchAllMessages()
            val tags = repository.getAllTags()
            _blockedSenders.value = blocked
            _archivedThreads.value = archived
            _messages.value = msgs
            _allTags.value = tags
        }
    }

    /**
     * Archives [threadId] so it no longer appears in the inbox.
     * Persists via [MessageRepository.setArchivedThread] on [ioDispatcher].
     */
    fun archiveThread(threadId: Long) {
        viewModelScope.launch(ioDispatcher) {
            repository.setArchivedThread(threadId)
            _archivedThreads.value = repository.getArchivedThreads()
        }
    }

    /**
     * Adds [address] to the blocked senders list and refreshes all inbox state.
     * Persists via [MessageRepository.setBlockedSender] on [ioDispatcher].
     */
    fun blockSender(address: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.setBlockedSender(address)
            _blockedSenders.value = repository.getBlockedSenders()
            loadData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
    }
}
