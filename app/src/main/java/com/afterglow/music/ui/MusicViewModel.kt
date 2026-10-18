package com.afterglow.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.afterglow.music.data.MusicRepository
import com.afterglow.music.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { HOME, LIBRARY, SEARCH, PLAYLISTS, SETTINGS }
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FAVORITES }
enum class LibraryPresentation { ORBIT, LIST }
enum class LibrarySort { TITLE, ARTIST, RECENT }
enum class AccentTheme { AURORA, ION, PULSAR }

data class MusicUiState(
    val screen: AppScreen = AppScreen.HOME,
    val libraryTab: LibraryTab = LibraryTab.SONGS,
    val tracks: List<Track> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val recentKeys: List<String> = emptyList(),
    val playlists: Map<String, List<String>> = emptyMap(),
    val selectedPlaylist: String? = null,
    val query: String = "",
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentKey: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val darkTheme: Boolean = true,
    val libraryPresentation: LibraryPresentation = LibraryPresentation.ORBIT,
    val librarySort: LibrarySort = LibrarySort.TITLE,
    val accentTheme: AccentTheme = AccentTheme.AURORA,
    val sleepTimerSeconds: Long? = null,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private val _state = MutableStateFlow(
        MusicUiState(
            favorites = repository.favorites(), recentKeys = repository.recent(), playlists = repository.playlists(), darkTheme = repository.isDark(),
            libraryPresentation = enumValueOrDefault(repository.libraryPresentation(), LibraryPresentation.ORBIT),
            librarySort = enumValueOrDefault(repository.librarySort(), LibrarySort.TITLE),
            accentTheme = enumValueOrDefault(repository.accentTheme(), AccentTheme.AURORA),
        ),
    )
    val state: StateFlow<MusicUiState> = _state.asStateFlow()
    private var player: MediaController? = null
    private var sleepTimerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayerState()
    }

    init {
        viewModelScope.launch {
            while (true) {
                syncPlayerState()
                delay(500)
            }
        }
    }

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted, error = null, tracks = if (granted) it.tracks else emptyList()) }
        if (granted) refreshLibrary()
    }

    fun refreshLibrary() {
        if (!_state.value.hasPermission) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { withContext(Dispatchers.IO) { repository.scan() } }
                .onSuccess { songs ->
                    val available = songs.mapTo(hashSetOf()) { it.key }
                    val favorites = _state.value.favorites.intersect(available)
                    val playlists = _state.value.playlists.mapValues { (_, keys) -> keys.filter { it in available } }
                    val recent = repository.recent().filter { it in available }
                    repository.saveFavorites(favorites)
                    repository.savePlaylists(playlists)
                    repository.saveRecent(recent)
                    _state.update { it.copy(tracks = songs, favorites = favorites, playlists = playlists, recentKeys = recent, loading = false) }
                }
                .onFailure { error ->
                    val message = if (error is SecurityException) "Music access was removed. Allow access in Settings to scan again." else "Your music could not be scanned. Please try again."
                    _state.update { it.copy(loading = false, error = message) }
                }
        }
    }

    fun bindPlayer(controller: MediaController?) {
        player?.removeListener(playerListener)
        player = controller
        controller?.addListener(playerListener)
        syncPlayerState()
    }

    fun navigate(screen: AppScreen) { _state.update { it.copy(screen = screen, selectedPlaylist = null) } }
    fun selectLibraryTab(tab: LibraryTab) { _state.update { it.copy(libraryTab = tab) } }
    fun setQuery(query: String) { _state.update { it.copy(query = query) } }
    fun openPlaylist(name: String?) { _state.update { it.copy(selectedPlaylist = name) } }
    fun toggleTheme() {
        val next = !_state.value.darkTheme
        repository.saveDark(next)
        _state.update { it.copy(darkTheme = next) }
    }

    fun setLibraryPresentation(value: LibraryPresentation) {
        repository.saveLibraryPresentation(value.name)
        _state.update { it.copy(libraryPresentation = value) }
    }

    fun setLibrarySort(value: LibrarySort) {
        repository.saveLibrarySort(value.name)
        _state.update { it.copy(librarySort = value) }
    }

    fun setAccentTheme(value: AccentTheme) {
        repository.saveAccentTheme(value.name)
        _state.update { it.copy(accentTheme = value) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null) {
            _state.update { it.copy(sleepTimerSeconds = null) }
            return
        }
        val seconds = minutes.coerceIn(1, 240) * 60L
        _state.update { it.copy(sleepTimerSeconds = seconds) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _state.update { it.copy(sleepTimerSeconds = remaining) }
            }
            player?.pause()
            _state.update { it.copy(sleepTimerSeconds = null) }
            sleepTimerJob = null
        }
    }

    fun clearRecentlyPlayed() {
        repository.saveRecent(emptyList())
        _state.update { it.copy(recentKeys = emptyList()) }
    }

    fun toggleFavorite(track: Track) {
        val next = _state.value.favorites.toMutableSet().apply { if (!add(track.key)) remove(track.key) }
        repository.saveFavorites(next)
        _state.update { it.copy(favorites = next) }
    }

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || _state.value.playlists.keys.any { it.equals(clean, ignoreCase = true) }) return false
        val next = _state.value.playlists + (clean to emptyList())
        repository.savePlaylists(next)
        _state.update { it.copy(playlists = next, selectedPlaylist = clean) }
        return true
    }

    fun addToPlaylist(name: String, track: Track) {
        val next = _state.value.playlists.toMutableMap()
        next[name] = (next[name].orEmpty() + track.key).distinct()
        repository.savePlaylists(next)
        _state.update { it.copy(playlists = next) }
    }

    fun removeFromPlaylist(name: String, track: Track) {
        val next = _state.value.playlists.toMutableMap()
        next[name] = next[name].orEmpty().filterNot { it == track.key }
        repository.savePlaylists(next)
        _state.update { it.copy(playlists = next) }
    }

    fun deletePlaylist(name: String) {
        val next = _state.value.playlists - name
        repository.savePlaylists(next)
        _state.update { it.copy(playlists = next, selectedPlaylist = null) }
    }

    fun play(track: Track, queue: List<Track> = _state.value.tracks) {
        val activePlayer = player ?: return
        val safeQueue = queue.ifEmpty { listOf(track) }
        val index = safeQueue.indexOfFirst { it.key == track.key }.coerceAtLeast(0)
        val items = safeQueue.map { item ->
            MediaItem.Builder()
                .setMediaId(item.key)
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.safeTitle)
                        .setArtist(item.safeArtist)
                        .setAlbumTitle(item.safeAlbum)
                        .setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/${item.albumId}"))
                        .build(),
                )
                .build()
        }
        activePlayer.setMediaItems(items, index, 0)
        activePlayer.prepare()
        activePlayer.play()
        val recent = (listOf(track.key) + repository.recent().filterNot { it == track.key }).take(50)
        repository.saveRecent(recent)
        _state.update { it.copy(recentKeys = recent) }
        syncPlayerState()
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun next() {
        player?.let { active ->
            if (active.hasNextMediaItem()) {
                active.seekToNextMediaItem()
            } else if (active.mediaItemCount > 1 && active.repeatMode == Player.REPEAT_MODE_OFF) {
                active.seekToDefaultPosition(0)
                active.play()
            } else {
                active.seekToNextMediaItem()
            }
        }
    }

    fun previous() {
        player?.let { active ->
            if (active.currentPosition > 3_000) {
                active.seekTo(0)
            } else if (active.hasPreviousMediaItem()) {
                active.seekToPreviousMediaItem()
            } else if (active.mediaItemCount > 1 && active.repeatMode == Player.REPEAT_MODE_OFF) {
                active.seekToDefaultPosition(active.mediaItemCount - 1)
            } else {
                active.seekToPreviousMediaItem()
            }
        }
    }
    fun seekTo(position: Long) { player?.seekTo(position) }
    fun toggleShuffle() { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; syncPlayerState() } }
    fun cycleRepeat() {
        player?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            syncPlayerState()
        }
    }

    private fun syncPlayerState() {
        val active = player ?: return
        _state.update {
            it.copy(
                isPlaying = active.isPlaying,
                currentKey = active.currentMediaItem?.mediaId,
                positionMs = active.currentPosition.coerceAtLeast(0),
                durationMs = active.duration.takeIf { value -> value > 0 } ?: 0,
                shuffled = active.shuffleModeEnabled,
                repeatMode = active.repeatMode,
            )
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
