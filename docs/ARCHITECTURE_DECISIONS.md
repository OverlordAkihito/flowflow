# Architecture Decisions

## Current prototype architecture

Prototype 1 is a local-first Android application. The Compose UI reads observable state from `MusicViewModel`. The view model uses `MusicRepository` for MediaStore discovery and app-private preference storage, and uses a Media3 `MediaController` to send playback commands. `PlaybackService` owns the ExoPlayer and MediaSession so audio can continue outside the activity. The manifest declares only audio-library, notification, and playback-service permissions; it does not request network access.

## Decisions

### A-001 — Native Android with Kotlin and Compose

- **Status:** Selected for prototype 1.
- **Reason:** The user explicitly requested an Android app. A native project gives direct access to Android media permissions, MediaStore, foreground playback, and system media controls.
- **Consequence:** Requires Android Studio/JDK/SDK or a Gradle Android toolchain to build and run.

### A-002 — Local device library only

- **Status:** Selected for prototype 1.
- **Reason:** No streaming, account, cloud, or provider requirements were provided. A local-only foundation avoids inventing external services and keeps the first prototype usable offline.
- **Consequence:** Audio discovery is permission-gated. Streaming, sync, downloads, lyrics, and cloud backup remain out of scope.

### A-003 — Media3 session service for playback

- **Status:** Selected in source; runtime behavior unverified.
- **Reason:** Android recommends a media session service for background playback and system/controller integration.
- **Evidence:** `PlaybackService.kt`, app manifest, Media3 dependencies in `app/build.gradle.kts`.

### A-004 — SharedPreferences for small local collections and preferences

- **Status:** Selected for prototype 1; scale limits remain.
- **Reason:** Favorites, playlist membership, appearance, accent, library presentation, and sort choice are small app-owned values and need no account or backend.
- **Consequence:** If collection volume, backup, migration, or richer metadata grows, evaluate Room/DataStore and an explicit migration path.

### A-005 — Spatial browsing with an explicit list alternative

- **Status:** Implemented in source; visual and usability behavior still needs device review.
- **Reason:** The user requested an unusual, space-inspired interface that avoids a default vertical-library experience.
- **Consequence:** The orbit deck is the default library presentation, while a persistent list option remains available for fast scanning and accessibility preference.

### A-006 — Local sleep timer

- **Status:** Implemented in source; background timing behavior needs device verification.
- **Reason:** A timer that pauses local playback adds a useful player control without requiring a provider or network service.
- **Consequence:** The timer is owned by the app process and is not persisted across process death.

### A-007 — Earth-centered home orbit

- **Status:** Implemented in source with native OpenGL ES 2.0; compilation and phone review are pending.
- **Reason:** The user supplied an Earth-with-crossing-orbits visual and asked for song icons/names to orbit the planet, with Next advancing to the next song.
- **Decision:** Render a shaded, rotating procedural Earth, atmospheric rim, star field, and varied orbital planes in a native OpenGL ES 2.0 `GLSurfaceView`. Build local album-art/title billboards off the render thread and show a 24-track window around the active selection. Keep the active track synchronized with the Media3-backed Compose UI and preserve the existing Compose orbit as the renderer fallback.
- **Consequence:** Drag rotates, pinch or pointer-wheel zooms, two-finger movement pans, and camera controls reset/adjust the view. Tapping a visible satellite starts that song in the full library queue; changing tracks advances the visible sector. Reduced-motion settings stop continuous scene motion. Build, rendering, touch behavior, and device performance still need verification.

## Open decisions

- Minimum and target Android versions after device/user compatibility review.
- Whether to add editable queue ordering, gapless/crossfade, equalizer, lyrics, external folders, or Android Auto.
- Whether playlists and preferences should participate in Android backup.
- Whether release signing, package identity, and Play distribution are required.

No production credentials, provider, server, database, account system, or payment integration has been selected.
