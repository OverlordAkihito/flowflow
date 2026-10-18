# Afterglow Project Instructions

## Product and source of truth

- This repository is a native Android music player. Prototype 1 is local-device audio playback; do not substitute unrelated product requirements from the supplied social-app keeper kit.
- Treat the user's current request as the product scope. Treat repository source/build configuration as evidence of current implementation and context documents as navigational aids.
- Inspect current files and the relevant Android APIs before changing behavior. Do not assume a feature works because a screen or button exists.
- Do not add account, streaming, analytics, payment, cloud, or network capabilities without explicit product/provider direction.

## Implementation

- Use Kotlin, Compose, AndroidX Media3, and the existing feature-oriented folders unless concrete evidence supports a change.
- Request only permissions needed for the selected local feature; explain permission use in the UI and handle denial/revocation.
- Keep audio playback in the media session service. Keep UI state and MediaStore/preferences work out of composable business logic where practical.
- Preserve local data and avoid destructive migrations. Confirm playlist deletion and distinguish it from deleting audio files.
- Keep touch targets, contrast, empty/loading/error states, and content descriptions usable across supported screen sizes.

## Validation and handoff

- Run safe, relevant build/lint/tests when the Android toolchain exists. Report exact commands and results; do not describe source-only behavior as device-verified.
- Before handoff run `python scripts/context_sync.py` and `python scripts/context_validate.py`, update `context/WORK_STATE.md`, `context/SESSION_HANDOFF.md`, and append a concise `context/SESSION_LOG.md` entry for meaningful work.
- Never commit secrets, private media, or personal records. Do not claim release or publication occurred unless it did.
