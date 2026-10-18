package com.afterglow.music.data

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAddedSeconds: Long,
) {
    val key: String get() = uri.toString()
    val safeTitle: String get() = title.ifBlank { "Unknown track" }
    val safeArtist: String get() = artist.ifBlank { "Unknown artist" }
    val safeAlbum: String get() = album.ifBlank { "Unknown album" }
}
