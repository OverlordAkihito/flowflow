# Architecture

Current source is a Kotlin Android app with Jetpack Compose screens and a native OpenGL ES 2.0 `GLSurfaceView` for the Home music universe. The renderer draws a procedural lit Earth, atmosphere, star field, 3D orbit paths, and a background-built atlas of local album-art/title satellites. Compose remains responsible for navigation, camera controls, selected-track details, and playback UI. `MainActivity` connects a Media3 `MediaController` to `PlaybackService`; the service owns ExoPlayer and a MediaSession. `MusicViewModel` exposes playback/library state and routes commands; `MusicRepository` queries MediaStore and stores favorites, playlists, recent keys, and appearance preferences in app-private SharedPreferences.

Appearance, accent selection, library presentation, sorting, favorite/playlist data, and listening history are stored in app-private SharedPreferences. The sleep timer runs in the app's ViewModel and pauses the active Media3 player when it expires; it is not restored after process death.

The Earth scene shows up to 24 tracks around the active selection at once, with the visible sector following playback through the full library. Drag rotates the camera, pinch or mouse-wheel zooms, two-finger movement pans, and camera buttons reset/adjust zoom. System reduced-motion settings freeze scene animation. OpenGL ES or shader failure falls back to the Compose orbit. These paths have not yet been compiled or device-verified.

See [the repository audit](../docs/REPOSITORY_AUDIT.md) for evidence and validation gaps, and [architecture decisions](../docs/ARCHITECTURE_DECISIONS.md) for choices and open items.
