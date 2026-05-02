package com.mt.organizemessages.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TagsDbHelper private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "tags.db", null, 2) {

    companion object {
        @Volatile private var instance: TagsDbHelper? = null

        fun getInstance(context: Context): TagsDbHelper =
            instance ?: synchronized(this) {
                instance ?: TagsDbHelper(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE message_tags (message_id TEXT PRIMARY KEY, tags TEXT)")
        db.execSQL("CREATE TABLE blocked_senders (address TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE archived_threads (thread_id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE message_colors (message_id TEXT PRIMARY KEY, color_hex TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE blocked_senders (address TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE archived_threads (thread_id INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TABLE message_colors (message_id TEXT PRIMARY KEY, color_hex TEXT)")
        }
    }
    fun getAllTagsMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        val cursor = readableDatabase.query("message_tags", arrayOf("message_id", "tags"), null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val tagsStr = it.getString(1) ?: ""
                if (tagsStr.isNotBlank()) map[id] = tagsStr.split(",").map { t -> t.trim() }
            }
        }
        return map
    }
    fun setTagsForMessage(messageId: String, tags: List<String>) {
        val values = android.content.ContentValues().apply {
            put("message_id", messageId)
            put("tags", tags.joinToString(","))
        }
        writableDatabase.insertWithOnConflict("message_tags", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun getBlockedSenders(): Set<String> {
        val set = mutableSetOf<String>()
        val cursor = readableDatabase.query("blocked_senders", arrayOf("address"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) set.add(it.getString(0)) }
        return set
    }
    fun setBlockedSender(address: String) {
        val values = android.content.ContentValues().apply { put("address", address) }
        writableDatabase.insertWithOnConflict("blocked_senders", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun removeBlockedSender(address: String) {
        writableDatabase.delete("blocked_senders", "address = ?", arrayOf(address))
    }
    fun getArchivedThreads(): Set<Long> {
        val set = mutableSetOf<Long>()
        val cursor = readableDatabase.query("archived_threads", arrayOf("thread_id"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) set.add(it.getLong(0)) }
        return set
    }
    fun setArchivedThread(threadId: Long) {
        val values = android.content.ContentValues().apply { put("thread_id", threadId) }
        writableDatabase.insertWithOnConflict("archived_threads", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun getAllMessageColorsMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cursor = readableDatabase.query("message_colors", arrayOf("message_id", "color_hex"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) map[it.getString(0)] = it.getString(1) }
        return map
    }
    fun setMessageColor(messageId: String, colorHex: String?) {
        if (colorHex == null) {
            writableDatabase.delete("message_colors", "message_id = ?", arrayOf(messageId))
        } else {
            val values = android.content.ContentValues().apply {
                put("message_id", messageId)
                put("color_hex", colorHex)
            }
            writableDatabase.insertWithOnConflict("message_colors", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
