package com.updater.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Serializable

data class DownloadTask(
    val id: String,            // MD5 of URL
    val url: String,           // Download URL
    val savePath: String,      // Local save path
    val title: String,         // Display title
    val totalBytes: Long,      // File total size
    var downloadedBytes: Long, // Downloaded size
    var status: Int,           // 0: PENDING, 1: DOWNLOADING, 2: PAUSED, 3: COMPLETED, 4: FAILED
    val fileMd5: String,       // Expected MD5
    var errorMsg: String? = null // Detailed error reason
) : Serializable {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_FAILED = 4
    }
}

class DownloadDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "updater_downloads.db"
        private const val DATABASE_VERSION = 2
        
        private const val TABLE_TASKS = "download_tasks"
        private const val COLUMN_ID = "id"
        private const val COLUMN_URL = "url"
        private const val COLUMN_SAVE_PATH = "save_path"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_TOTAL_BYTES = "total_bytes"
        private const val COLUMN_DOWNLOADED_BYTES = "downloaded_bytes"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_FILE_MD5 = "file_md5"
        private const val COLUMN_ERROR_MSG = "error_msg"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_TASKS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_URL TEXT,
                $COLUMN_SAVE_PATH TEXT,
                $COLUMN_TITLE TEXT,
                $COLUMN_TOTAL_BYTES INTEGER,
                $COLUMN_DOWNLOADED_BYTES INTEGER,
                $COLUMN_STATUS INTEGER,
                $COLUMN_FILE_MD5 TEXT,
                $COLUMN_ERROR_MSG TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COLUMN_ERROR_MSG TEXT")
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun insertOrUpdateTask(task: DownloadTask) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, task.id)
            put(COLUMN_URL, task.url)
            put(COLUMN_SAVE_PATH, task.savePath)
            put(COLUMN_TITLE, task.title)
            put(COLUMN_TOTAL_BYTES, task.totalBytes)
            put(COLUMN_DOWNLOADED_BYTES, task.downloadedBytes)
            put(COLUMN_STATUS, task.status)
            put(COLUMN_FILE_MD5, task.fileMd5)
            put(COLUMN_ERROR_MSG, task.errorMsg)
        }
        db.insertWithOnConflict(TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getTask(id: String): DownloadTask? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        
        var task: DownloadTask? = null
        if (cursor.moveToFirst()) {
            val errorMsgIndex = cursor.getColumnIndex(COLUMN_ERROR_MSG)
            val err = if (errorMsgIndex != -1) cursor.getString(errorMsgIndex) else null
            task = DownloadTask(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                savePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVE_PATH)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_BYTES)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOADED_BYTES)),
                status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                fileMd5 = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_MD5)),
                errorMsg = err
            )
        }
        cursor.close()
        return task
    }

    @Synchronized
    fun getAllTasks(): List<DownloadTask> {
        val tasks = ArrayList<DownloadTask>()
        val db = readableDatabase
        val cursor = db.query(TABLE_TASKS, null, null, null, null, null, null)
        
        while (cursor.moveToNext()) {
            val errorMsgIndex = cursor.getColumnIndex(COLUMN_ERROR_MSG)
            val err = if (errorMsgIndex != -1) cursor.getString(errorMsgIndex) else null
            val task = DownloadTask(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                savePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVE_PATH)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_BYTES)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOADED_BYTES)),
                status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                fileMd5 = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_MD5)),
                errorMsg = err
            )
            tasks.add(task)
        }
        cursor.close()
        return tasks
    }

    @Synchronized
    fun updateTaskProgress(id: String, downloadedBytes: Long, status: Int, errorMsg: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DOWNLOADED_BYTES, downloadedBytes)
            put(COLUMN_STATUS, status)
            if (errorMsg != null) {
                put(COLUMN_ERROR_MSG, errorMsg)
            }
        }
        db.update(TABLE_TASKS, values, "$COLUMN_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteTask(id: String) {
        val db = writableDatabase
        db.delete(TABLE_TASKS, "$COLUMN_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteTaskBySavePath(savePath: String) {
        val db = writableDatabase
        db.delete(TABLE_TASKS, "$COLUMN_SAVE_PATH = ?", arrayOf(savePath))
    }
}
