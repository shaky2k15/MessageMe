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

class ThreadViewModel(
    application: Application,
    private val repository: MessageRepository = MessageRepository(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var currentThreadId: Long = -1L

    private val contentObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (currentThreadId != -1L) loadThread(currentThreadId)
        }
    }

    init {
        getApplication<Application>().contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/conversations"), true, contentObserver
        )
    }

    fun loadThread(threadId: Long) {
        currentThreadId = threadId
        viewModelScope.launch(ioDispatcher) {
            _messages.value = repository.fetchChatThread(threadId)
        }
    }

    fun sendMessage(address: String, text: String, subId: Int? = null) {
        viewModelScope.launch(ioDispatcher) {
            repository.sendSmsMessage(address, text, subId)
        }
    }

    fun sendMms(threadId: Long, address: String, imageUri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            repository.simulateSendMms(threadId, address, imageUri)
        }
    }

    fun setTagsForMessage(messageId: String, tags: List<String>) {
        viewModelScope.launch(ioDispatcher) {
            repository.setTagsForMessage(messageId, tags)
            if (currentThreadId != -1L) _messages.value = repository.fetchChatThread(currentThreadId)
        }
    }

    fun setMessageColor(messageId: String, colorHex: String?) {
        viewModelScope.launch(ioDispatcher) {
            repository.setMessageColor(messageId, colorHex)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
    }
}
