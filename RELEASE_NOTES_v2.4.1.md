# PixelPlayer Plus v2.4.1

PixelPlayer now includes a dedicated Linux desktop application alongside the Android player.

## PixelPlayer Desktop

- New black and purple three-panel desktop interface built with Compose Desktop
- Local library scanning plus online search and playback through NewPipe Extractor `v0.26.5`
- Persistent liked songs and mixed local/online playlists
- High-resolution YouTube artwork with automatic fallbacks
- Related-song radio, listening history, recommendations, queue, shuffle, and repeat
- GNOME/KDE now-playing integration through PixelPlayer's own MPRIS service
- Working keyboard Play/Pause, Previous, and Next media controls
- Background playback when the window is closed, with tray controls where supported
- Single-instance launching: reopening PixelPlayer raises the existing window
- Desktop notifications and a dedicated Now Playing / Up Next panel

The Linux archive is portable and includes its Java runtime. `mpv` must be installed on the system.

## Android

- Updated NewPipe Extractor from `v0.26.1` to `v0.26.5`
- Android minimum supported API is now 28

## Downloads

- `PixelPlayer-Android-v2.4.1.apk` — Android application
- `PixelPlayer-Desktop-v2.4.1-linux-x64.tar.gz` — Linux x86-64 desktop application
- `SHA256SUMS.txt` — checksums generated from the published assets

The Android APK is v2-signed with the repository's existing debug certificate. It is intended for
direct installation and updates that use the same signing certificate, not Play Store publication.

## Desktop installation

Extract the desktop archive and run:

```bash
pixelplayer-desktop/bin/pixelplayer-desktop
```
