# Project Overview

Afterglow is a native Android music player. Prototype 1 is scoped to music files already available on the device, with local browsing, search, favorites, playlists, and background playback. The app is designed to work offline and does not include an account or external media provider.

The workspace contains the Kotlin/Compose/Media3 Afterglow project. Home now uses a native OpenGL ES 2.0 scene with a rotating procedural Earth, atmospheric rim, varied 3D song orbits, local album-art billboards, and touch camera navigation. Compose owns player controls and a lightweight orbit fallback. The library retains its orbit-deck and list modes. The new renderer and its Android integration remain uncompiled and device-unverified.
