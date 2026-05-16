package com.mt.organizemessages.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.data.MessageRepository
import com.mt.organizemessages.SettingsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class ThreadViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MessageRepository = MessageRepository(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val settingsManager = SettingsManager(application)

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
        val finalMessage = if (settingsManager.isSignatureEnabled && settingsManager.signatureText.isNotBlank()) {
            "$text\n\n${settingsManager.signatureText}"
        } else {
            text
        }
        viewModelScope.launch(ioDispatcher) {
            repository.sendSmsMessage(address, finalMessage, subId)
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
