package com.mt.organizemessages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.mt.organizemessages.ChatMessage
import com.mt.organizemessages.ContactInfo
import com.mt.organizemessages.TagsDbHelper

class MessageRepository(private val context: Context) {

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
                    val partSelection = "mid = ?"
                    val partArgs = arrayOf(mmsId)
                    val partCursor = context.contentResolver.query(partUri, null, partSelection, partArgs, null)
                    partCursor?.use { pc ->
                        val ctIdx = pc.getColumnIndexOrThrow("ct")
                        val textIdx = pc.getColumnIndexOrThrow("text")
                        val partIdIdx = pc.getColumnIndexOrThrow("_id")
                        
                        while (pc.moveToNext()) {
                            val contentType = pc.getString(ctIdx) ?: ""
                            if ("text/plain" == contentType) {
                                body = pc.getString(textIdx) ?: ""
                            } else if (contentType.startsWith("image/")) {
                                val partId = pc.getString(partIdIdx)
                                attachmentUri = "content://mms/part/$partId"
                            }
                        }
                    }
                    
                    var address = "Unknown"
                    val addrUri = Uri.parse("content://mms/$mmsId/addr")
                    val addrCursor = context.contentResolver.query(addrUri, null, null, null, null)
                    addrCursor?.use { ac ->
                        val typeIdx = ac.getColumnIndexOrThrow("type")
                        val addressIdx = ac.getColumnIndexOrThrow("address")
                        while (ac.moveToNext()) {
                            val type = ac.getInt(typeIdx)
                            if (isSent && type == 151) {
                                address = ac.getString(addressIdx) ?: "Unknown"
                                break
                            } else if (!isSent && type == 137) {
                                val rawAddr = ac.getString(addressIdx) ?: "Unknown"
                                if (rawAddr != "insert-address-token") {
                                    address = rawAddr
                                    break
                                }
                            }
                        }
                    }
                    
                    messages.add(ChatMessage(idStr, threadId, address, body, date, isSent, attachmentUri))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return messages
    }

    fun fetchAllMessages(): List<ChatMessage> {
        val sms = fetchSms(null)
        val mms = fetchMms(null)
        val allMsgs = (sms + mms).sortedByDescending { it.date }
        
        val tagsDb = TagsDbHelper(context)
        val tagsMap = tagsDb.getAllTagsMap()
        val colorsMap = tagsDb.getAllMessageColorsMap()
        
        allMsgs.forEach { 
            it.tags = tagsMap[it.id] ?: emptyList() 
            it.colorHex = colorsMap[it.id]
        }
        
        return allMsgs.distinctBy { it.threadId }
    }

    fun fetchChatThread(threadId: Long): List<ChatMessage> {
        val sms = fetchSms(threadId)
        val mms = fetchMms(threadId)
        val allMsgs = (sms + mms).sortedByDescending { it.date }
        
        val tagsDb = TagsDbHelper(context)
        val tagsMap = tagsDb.getAllTagsMap()
        val colorsMap = tagsDb.getAllMessageColorsMap()
        
        allMsgs.forEach { 
            it.tags = tagsMap[it.id] ?: emptyList() 
            it.colorHex = colorsMap[it.id]
        }
        
        return allMsgs
    }

    fun fetchContacts(): List<ContactInfo> {
        val contactsList = mutableListOf<ContactInfo>()
        try {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val number = it.getString(numberIndex) ?: ""
                    val cleanNumber = number.replace(Regex("[^0-9+]"), "")
                    if (cleanNumber.isNotEmpty()) contactsList.add(ContactInfo(name, cleanNumber))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return contactsList.distinctBy { it.phoneNumber }
    }

    fun sendSmsMessage(phoneNumber: String, message: String, subId: Int? = null) {
        try {
            val smsManager = if (subId != null && subId != -1) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                context.getSystemService(SmsManager::class.java)
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun simulateSendMms(threadId: Long, address: String, imageUri: Uri) {
        try {
            val values = ContentValues()
            values.put("thread_id", threadId)
            values.put("date", System.currentTimeMillis() / 1000)
            values.put("msg_box", 2)
            values.put("read", 1)
            val mmsUri = context.contentResolver.insert(Uri.parse("content://mms"), values)
            if (mmsUri != null) {
                val mmsId = mmsUri.lastPathSegment
                val partValues = ContentValues()
                partValues.put("mid", mmsId)
                partValues.put("ct", "image/jpeg")
                partValues.put("text", "Sent Image")
                context.contentResolver.insert(Uri.parse("content://mms/part"), partValues)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
