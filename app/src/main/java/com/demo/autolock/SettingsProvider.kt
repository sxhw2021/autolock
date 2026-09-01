package com.demo.autolock

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cursor = MatrixCursor(COLUMNS)
        cursor.addRow(arrayOf(
            prefs.getBoolean(KEY_ENABLED, true).toString(),
            prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_MIN).toString()
        ))
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY.settings"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.demo.autolock.settings"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/settings")
        const val KEY_ENABLED = "enabled"
        const val KEY_TIMEOUT = "timeout_minutes"
        const val PREFS_NAME = "settings"
        const val DEFAULT_TIMEOUT_MIN = 5
        val COLUMNS = arrayOf(KEY_ENABLED, KEY_TIMEOUT)
    }
}
