# Security and Privacy

- Request audio-library access only when the user chooses to grant it; handle denial and revocation.
- The manifest does not declare `INTERNET`; prototype 1 has no account, analytics, streaming, or cloud provider.
- Keep preferences in app-private storage. Do not copy or upload the user's music.
- Playback service is exported for Android media-session interoperability; verify its behavior and merged manifest when built.
- Verify permission, notification, and media-session behavior on Android versions supported by the final build. These checks have not yet run.
