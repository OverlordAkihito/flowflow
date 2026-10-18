# Decisions

- Prototype 1 is native Android because the user requested an Android app.
- Prototype 1 plays local device audio only; no external service or provider is assumed.
- Media3 session service owns background playback; Compose communicates through MediaController.
- Favorites/playlists/settings use app-private SharedPreferences for this first prototype. Revisit storage if richer data or migrations become necessary.
- The default navigation is spatial and horizontal; a list mode remains available for quick scanning.
- A local sleep timer pauses the active player and is not persisted across process death.

See [architecture decisions](../docs/ARCHITECTURE_DECISIONS.md) for rationale and open decisions.
