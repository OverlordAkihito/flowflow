package com.afterglow.music.data

import android.app.Application
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(application: Application) {
    private val resolver = application.contentResolver
    private val prefs = application.getSharedPreferences("afterglow-library", 0)

    fun scan(): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val result = mutableListOf<Track>()
        resolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = cursor.getLong(albumIdCol),
                    durationMs = cursor.getLong(durationCol),
                    dateAddedSeconds = cursor.getLong(addedCol),
                )
            }
        }
        return result
    }

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
    fun saveFavorites(keys: Set<String>) { prefs.edit().putStringSet("favorites", keys).apply() }
    fun recent(): List<String> = JSONArray(prefs.getString("recent", "[]")).toStringList()
    fun saveRecent(keys: List<String>) { prefs.edit().putString("recent", JSONArray(keys.take(50)).toString()).apply() }
    fun playlists(): Map<String, List<String>> {
        val json = runCatching { JSONObject(prefs.getString("playlists", "{}")) }.getOrDefault(JSONObject())
        return buildMap {
            json.keys().forEach { name -> put(name, runCatching { json.getJSONArray(name).toStringList() }.getOrDefault(emptyList())) }
        }
    }
    fun savePlaylists(value: Map<String, List<String>>) {
        val json = JSONObject()
        value.forEach { (name, keys) -> json.put(name, JSONArray(keys)) }
        prefs.edit().putString("playlists", json.toString()).apply()
    }
    fun isDark(): Boolean = prefs.getBoolean("dark-theme", true)
    fun saveDark(dark: Boolean) { prefs.edit().putBoolean("dark-theme", dark).apply() }
    fun libraryPresentation(): String = prefs.getString("library-presentation", "ORBIT") ?: "ORBIT"
    fun saveLibraryPresentation(value: String) { prefs.edit().putString("library-presentation", value).apply() }
    fun librarySort(): String = prefs.getString("library-sort", "TITLE") ?: "TITLE"
    fun saveLibrarySort(value: String) { prefs.edit().putString("library-sort", value).apply() }
    fun accentTheme(): String = prefs.getString("accent-theme", "AURORA") ?: "AURORA"
    fun saveAccentTheme(value: String) { prefs.edit().putString("accent-theme", value).apply() }

    private fun JSONArray.toStringList() = List(length()) { index -> getString(index) }
}
