package com.my.firstapp

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

internal const val AUTO_BACKUP_FOLDER = "Download/今天吃什么/"
private const val AUTO_BACKUP_PREFIX = "今天吃什么备份_"

internal fun autoBackupFileName(day: String) = "$AUTO_BACKUP_PREFIX$day.json"

internal fun backupIdsToDelete(rows: List<Pair<Long, String>>, keep: Int = 7): List<Long> =
    rows
        .filter { (_, name) -> name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(".json") }
        .sortedByDescending { it.second }
        .drop(keep)
        .map { it.first }

class AutoBackupStore(private val resolver: ContentResolver) {
    fun backup(day: String, json: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = autoBackupFileName(day)
        if (!exists(collection, name)) {
            write(collection, name, json)
        }
        prune(collection)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exists(collection: Uri, name: String): Boolean =
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(AUTO_BACKUP_FOLDER, name),
            null,
        )?.use { it.moveToFirst() } == true

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun write(collection: Uri, name: String, json: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, AUTO_BACKUP_FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val item = resolver.insert(collection, values) ?: error("Cannot create backup")
        try {
            resolver.openOutputStream(item, "w")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open backup")
            resolver.update(
                item,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun prune(collection: Uri) {
        val rows = mutableListOf<Pair<Long, String>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(AUTO_BACKUP_FOLDER),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                rows += cursor.getLong(idCol) to cursor.getString(nameCol)
            }
        }
        backupIdsToDelete(rows).forEach { id ->
            resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
        }
    }
}
