# PixelPlayer Desktop

PixelPlayer Desktop is a separate Compose Desktop application for Linux. It keeps the Android
application untouched while sharing the PixelPlayer visual identity.

## Included

- Local music-folder scanning and metadata reading
- Online music search and playback through NewPipe Extractor `v0.26.5`
- High-resolution online artwork with max-resolution YouTube fallbacks
- Related-song radio that continuously replenishes the queue without following search order
- Persistent listening history with recently played, favorite-artist, mood, focus, pop, and trending Home shelves
- Search, library, liked songs, playlists, and queue views
- Persistent local and online favorites, mixed playlists, folders, volume, and last selected song
- Play, pause, previous, next, seek, volume, shuffle, and repeat controls
- mpv-backed playback with an isolated IPC process
- PixelPlayer-owned MPRIS service for GNOME/KDE now-playing, play/pause, previous, and next controls
- Desktop notifications when a song starts
- Close-to-background playback with tray controls and explicit quit
- Responsive three-panel desktop layout with a dedicated Now Playing and Up Next rail

## Requirements

- Linux x86-64
- `mpv` available on `PATH`

The portable release bundles its own Java runtime, so a separate Java installation is not needed.

## Run from source

Because this repository's wrapper has Windows line endings on this checkout, use:

```bash
env -u ANDROID_SDK_ROOT java -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain --no-configuration-cache :desktopApp:run
```

## Portable release

Extract `PixelPlayer-Desktop-0.4.5-linux-x64.tar.gz`, then run:

```bash
pixelplayer-desktop/bin/pixelplayer-desktop
```

The first launch scans `~/Music` when that directory exists. Use **Add music folder** in the app
to select another library location.
