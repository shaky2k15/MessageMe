package com.mt.organizemessages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.ContactInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central data access layer for all SMS/MMS, contact, and metadata operations.
 *
 * **Threading contract:** All public methods perform blocking I/O and MUST be
 * called from [kotlinx.coroutines.Dispatchers.IO]. Calling them from the Main
 * thread will trigger StrictMode violations and risks ANRs on large datasets.
 *
 * **Access rule:** No code outside the `data/` package should import
 * [TagsDbHelper] directly. See ADR-001.
 */
class MessageRepository(private val context: Context) {

    companion object {
        /**
         * Emits [Unit] whenever tag data is written via [setTagsForMessage].
         * Observers (e.g. [com.mt.organizemessages.ui.InboxViewModel]) should
         * collect this to refresh tag-derived UI state without a full data reload.
         *
         * See ADR-004 for design rationale.
         */
        private val _tagsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val tagsChanged: SharedFlow<Unit> = _tagsChanged.asSharedFlow()
    }

    private val db get() = TagsDbHelper.getInstance(context)

    // ── Read: SMS/MMS ─────────────────────────────────────────────────────────

    /**
     * Fetches all SMS messages, optionally filtered to a single thread.
     *
     * @param targetThreadId If non-null, returns only messages from that thread.
     * @return List of [ChatMessage] sorted by the ContentProvider default order.
     */
    fun fetchSms(targetThreadId: Long?): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
            val selection = if (targetThreadId != null) "${Telephony.Sms.THREAD_ID} = ?" else null
            val selectionArgs = if (targetThreadId != null) arrayOf(targetThreadId.toString()) else null

            val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, null)
            cursor?.use {
                val idxId = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val idxThread = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val idxAddress = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val idxBody = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val idxDate = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val idxType = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val idStr = "sms_${it.getLong(idxId)}"
                    val threadId = it.getLong(idxThread)
                    val address = it.getString(idxAddress) ?: "Unknown"
                    val body = it.getString(idxBody) ?: ""
                    val date = it.getLong(idxDate)
                    val isSent = it.getInt(idxType) == Telephony.Sms.MESSAGE_TYPE_SENT
                    messages.add(ChatMessage(idStr, threadId, address, body, date, isSent, null))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return messages
    }

    /**
     * Fetches all MMS messages, optionally filtered to a single thread.
     * Resolves message body and first image attachment for each MMS.
     *
     * @param targetThreadId If non-null, returns only messages from that thread.
     */
    fun fetchMms(targetThreadId: Long?): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val uri = Telephony.Mms.CONTENT_URI
            val projection = arrayOf("_id", "thread_id", "date", "msg_box")
            val selection = if (targetThreadId != null) "thread_id = ?" else null
            val selectionArgs = if (targetThreadId != null) arrayOf(targetThreadId.toString()) else null

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow("_id")
                val threadIdx = it.getColumnIndexOrThrow("thread_id")
                val dateIdx = it.getColumnIndexOrThrow("date")
                val msgBoxIdx = it.getColumnIndexOrThrow("msg_box")

                while (it.moveToNext()) {
                    val mmsId = it.getString(idIdx)
                    val idStr = "mms_$mmsId"
                    val threadId = it.getLong(threadIdx)
                    val date = it.getLong(dateIdx) * 1000L
                    val isSent = it.getInt(msgBoxIdx) == Telephony.Mms.MESSAGE_BOX_SENT

                    var body = ""
                    var attachmentUri: String? = null

                    val partUri = Uri.parse("content://mms/part")
                    val partCursor = context.contentResolver.query(partUri, null, "mid = ?", arrayOf(mmsId), null)
                    partCursor?.use { pc ->
                        val ctIdx = pc.getColumnIndexOrThrow("ct")
                        val textIdx = pc.getColumnIndexOrThrow("text")
                        val partIdIdx = pc.getColumnIndexOrThrow("_id")
                        while (pc.moveToNext()) {
                            val contentType = pc.getString(ctIdx) ?: ""
                            if ("text/plain" == contentType) {
                                body = pc.getString(textIdx) ?: ""
                            } else if (contentType.startsWith("image/")) {
                                attachmentUri = "content://mms/part/${pc.getString(partIdIdx)}"
                            }
                        }
                    }

                    var address = "Unknown"
                    val addrCursor = context.contentResolver.query(Uri.parse("content://mms/$mmsId/addr"), null, null, null, null)
                    addrCursor?.use { ac ->
                        val typeIdx = ac.getColumnIndexOrThrow("type")
                        val addressIdx = ac.getColumnIndexOrThrow("address")
                        while (ac.moveToNext()) {
                            val type = ac.getInt(typeIdx)
                            if (isSent && type == 151) {
                                address = ac.getString(addressIdx) ?: "Unknown"; break
                            } else if (!isSent && type == 137) {
                                val rawAddr = ac.getString(addressIdx) ?: "Unknown"
                                if (rawAddr != "insert-address-token") { address = rawAddr; break }
                            }
                        }
                    }
                    messages.add(ChatMessage(idStr, threadId, address, body, date, isSent, attachmentUri))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return messages
    }

    /**
     * Fetches all SMS and MMS messages and merges tag and color metadata.
     *
     * @param groupedByThread If true (default), returns only the most recent message per thread.
     *                        Used for the Inbox view. If false, returns all messages,
     *                        used for Tag filtering, Metrics, and Backup.
     */
    fun fetchAllMessages(groupedByThread: Boolean = true): List<ChatMessage> {
        val allMsgs = (fetchSms(null) + fetchMms(null)).sortedByDescending { it.date }
        val tagsMap = db.getAllTagsMap()
        val colorsMap = db.getAllMessageColorsMap()
        allMsgs.forEach { it.tags = tagsMap[it.id] ?: emptyList(); it.colorHex = colorsMap[it.id] }
        return if (groupedByThread) allMsgs.distinctBy { it.threadId } else allMsgs
    }

    /**
     * Fetches the full message history for a single thread, sorted newest-first.
     * Used by [com.mt.organizemessages.ui.ThreadViewModel].
     *
     * @param threadId The thread to fetch.
     */
    fun fetchChatThread(threadId: Long): List<ChatMessage> {
        val allMsgs = (fetchSms(threadId) + fetchMms(threadId)).sortedByDescending { it.date }
        val tagsMap = db.getAllTagsMap()
        val colorsMap = db.getAllMessageColorsMap()
        allMsgs.forEach { it.tags = tagsMap[it.id] ?: emptyList(); it.colorHex = colorsMap[it.id] }
        return allMsgs
    }

    /**
     * Fetches the device contact list, sorted alphabetically by display name.
     * Requires READ_CONTACTS permission.
     */
    fun fetchContacts(): List<ContactInfo> {
        val contactsList = mutableListOf<ContactInfo>()
        try {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val cleanNumber = (it.getString(numberIndex) ?: "").replace(Regex("[^0-9+]"), "")
                    if (cleanNumber.isNotEmpty()) contactsList.add(ContactInfo(name, cleanNumber))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return contactsList.distinctBy { it.phoneNumber }
    }

    // ── Read: Tags / Metadata ────────────────────────────────────────────────

    /** Returns the set of phone numbers currently blocked by the user. */
    fun getBlockedSenders(): Set<String> = db.getBlockedSenders()

    /** Returns the set of thread IDs the user has archived. */
    fun getArchivedThreads(): Set<Long> = db.getArchivedThreads()

    /**
     * Returns a sorted, deduplicated list of all tags across all messages.
     * Used to populate the tag drawer in [com.mt.organizemessages.ui.InboxViewModel].
     */
    fun getAllTags(): List<String> =
        db.getAllTagsMap().values.flatten().distinct().filter { it.isNotBlank() }.sorted()

    /** Returns the list of tags associated with [messageId]. */
    fun getTagsForMessage(messageId: String): List<String> =
        db.getAllTagsMap()[messageId] ?: emptyList()

    /** Returns the hex color string for [messageId], or null if default. */
    fun getMessageColor(messageId: String): String? =
        db.getAllMessageColorsMap()[messageId]

    // ── Write: Tags / Metadata ───────────────────────────────────────────────

    /**
     * Saves tags for [messageId] and emits [tagsChanged] to notify observers
     * (e.g. [com.mt.organizemessages.ui.InboxViewModel]) to refresh the tag drawer.
     *
     * **Must be called on [kotlinx.coroutines.Dispatchers.IO].**
     */
    fun setTagsForMessage(messageId: String, tags: List<String>) {
        db.setTagsForMessage(messageId, tags)
        _tagsChanged.tryEmit(Unit)
    }

    /**
     * Sets or clears the bubble color for [messageId].
     *
     * @param colorHex A hex color string (e.g. "#D32F2F"), or null to reset to default.
     */
    fun setMessageColor(messageId: String, colorHex: String?) =
        db.setMessageColor(messageId, colorHex)

    /** Adds [address] to the blocked senders list. */
    fun setBlockedSender(address: String) = db.setBlockedSender(address)

    /** Removes [address] from the blocked senders list ("Not a Spam" action). */
    fun removeBlockedSender(address: String) = db.removeBlockedSender(address)

    /** Marks [threadId] as archived so it no longer appears in the inbox. */
    fun setArchivedThread(threadId: Long) = db.setArchivedThread(threadId)

    // ── Send / MMS ───────────────────────────────────────────────────────────

    /**
     * Sends an SMS message to [phoneNumber].
     *
     * Handles the API level difference: uses `getSystemService(SmsManager::class.java)`
     * on API 31+ and the deprecated `SmsManager.getDefault()` on API 30.
     * See ADR-005 and the [Build.VERSION_CODES] guard below.
     *
     * @param subId Optional subscription ID for dual-SIM devices. Pass null for default SIM.
     */
    fun sendSmsMessage(phoneNumber: String, message: String, subId: Int? = null) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (subId != null && subId != -1)
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                else
                    context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                if (subId != null && subId != -1)
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                else
                    SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts a simulated outgoing MMS record into the system MMS content provider.
     * This is used to record that an image was shared; actual MMS sending is handled
     * by the system when this app is the default SMS app.
     */
    fun simulateSendMms(threadId: Long, address: String, imageUri: Uri) {
        try {
            val values = ContentValues().apply {
                put("thread_id", threadId)
                put("date", System.currentTimeMillis() / 1000)
                put("msg_box", 2)
                put("read", 1)
            }
            val mmsUri = context.contentResolver.insert(Uri.parse("content://mms"), values)
            if (mmsUri != null) {
                val mmsId = mmsUri.lastPathSegment
                val partValues = ContentValues().apply {
                    put("mid", mmsId)
                    put("ct", "image/jpeg")
                    put("text", "Sent Image")
                }
                context.contentResolver.insert(Uri.parse("content://mms/part"), partValues)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
