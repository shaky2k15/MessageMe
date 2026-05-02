package com.mt.organizemessages.domain

import com.mt.organizemessages.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class BackupEngine {

    fun generateBackupXml(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        sb.append("<smses count='${messages.size}'>\n")
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy h:mm:ss a", Locale.US)
        
        messages.forEach { msg ->
            val type = if (msg.isSent) "2" else "1"
            val tagsAttr = if (msg.tags.isNotEmpty()) " tags='${msg.tags.joinToString(",")}'" else ""
            val colorAttr = if (msg.colorHex != null) " color='${msg.colorHex}'" else ""
            
            sb.append("  <sms protocol='0' address='${escapeXml(msg.address)}' date='${msg.date}' type='$type' subject='null' body='${escapeXml(msg.body)}' toa='null' sc_toa='null' service_center='null' read='1' status='-1' locked='0' readable_date='${dateFormat.format(Date(msg.date))}' contact_name='${escapeXml(msg.address)}' $tagsAttr $colorAttr />\n")
        }
        sb.append("</smses>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
