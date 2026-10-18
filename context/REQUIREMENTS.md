# Requirements

| Requirement | Prototype 1 target | Current status |
|---|---|---|
| Local device audio library | Request permission, scan MediaStore, list songs/albums/artists | Partial; source exists, not built/run |
| Playback | Play, pause, seek, next/previous, shuffle/repeat | Partial; source exists, not built/run |
| Background playback | Continue through Media3 service and Android media controls | Partial; source exists, platform behavior unverified |
| Discovery | Search by title/artist/album and show recently added | Partial; source exists, not built/run |
| Personal collections | Favorite songs and create/add/remove/delete playlists | Partial; local preference persistence source exists, not verified after restart |
| Usable interface | Dark/light appearance, empty/loading/error and permission flows, touch controls | Partial; source exists, visual/accessibility review not run |
| Spatial browsing and personalization | Native 3D Earth, varied album-art song orbits, camera rotate/pan/zoom/reset, active-track synchronization, reduced-motion handling and Compose fallback; horizontal orbit deck/list and persistent preferences | Partial; source updated; compile, renderer, touch, accessibility and device performance not verified |
| Listening controls | Sleep timer and clear recent-history action | Partial; source exists, timer behavior not device-verified |
| Local-first privacy | No account/network dependency; keep preferences app-private | Source configured; merged manifest/release build unverified |
| Streaming, lyrics, equalizer, cloud sync, subscriptions | Not part of prototype 1 | Missing/out of scope |

Status is not promoted to complete based only on UI or source presence. Record build/device evidence before updating these statuses.
