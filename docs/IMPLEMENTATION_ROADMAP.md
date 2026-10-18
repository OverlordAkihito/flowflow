# Prototype Roadmap

## Prototype 1 — Local listening foundation

**Current status:** prototype source includes a native OpenGL ES 2.0 Earth-orbit music universe plus spatial library and preference controls. Compilation and Android runtime validation remain pending; the latest build check stops before project configuration because this environment cannot establish Gradle's loopback connection.

- [x] Create native Android project layout and product documentation.
- [x] Add permission-gated MediaStore scan and local metadata model.
- [x] Add Compose home, library, search, playlists, settings, and player UI.
- [x] Add Media3 playback service/session and controller-based playback commands.
- [x] Add local favorites, playlist membership, and theme preference persistence.
- [x] Add constellation navigation, horizontal orbit-deck library, and list alternative.
- [x] Add persistent accent palette, library sort/presentation controls, sleep timer, and recent-history clearing.
- [x] Replace the Home orbit drawing with a native 3D Earth, varied satellite trajectories, local artwork atlas, playback-aware selection, camera gestures/controls, reduced-motion support, and a Compose fallback.
- [ ] Compile/sync with Android Studio, resolve any build errors, and install on a device/emulator.
- [ ] Verify GLES shader/surface rendering, orbit picking, drag/pinch/pan/zoom/reset gestures, and renderer fallback on a phone and emulator.
- [ ] Verify runtime permission grant/deny/revoke and re-scan paths.
- [ ] Verify playback continuation, system media controls, audio focus/noisy output, queue transitions, and repeat/shuffle.
- [ ] Review small/large screen layout, TalkBack labels, contrast, and light mode.
- [ ] Add automated tests for playlist/favorite rules and playback state mapping after a successful build.

## Prototype 1.1 — Only after device feedback

Prioritize from observed issues: queue editing, richer filters, album artwork performance/cache, sleep timer reliability across process death, and persistence migration. Do not add streaming or external accounts without a separate product and provider decision.

## Release preparation

Before distribution, set the package/signing identity, verify target SDK behavior and merged permissions, run release build checks, audit license/attribution requirements, and review privacy disclosures. No release or publishing action has been performed.
