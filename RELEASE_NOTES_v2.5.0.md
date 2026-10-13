# PixelPlayer Plus v2.5.0

This update brings PixelPlayer+'s Android and Linux desktop experiences under the same purple
identity, improves online music discovery, and fixes several persistence and lifecycle problems.

## Important Android installation notice

The Android package name is now `io.github.aaryan1101.pixelplayerplus`.

If you have an older PixelPlayer build installed under the previous package name, please uninstall
it before installing v2.5.0. Android considers the new package a separate app, so listening history,
favourites, playlists, and settings from the old package cannot be migrated automatically.

We are sorry for the history and settings loss caused by this one-time package-name change. Future
PixelPlayer+ updates will continue using the new package name.

## Android

- Added online discovery categories for trending, Punjabi, Bollywood, Hindi, chill, workout, and
  other useful searches
- Replaced the old home mix banner with online discovery categories
- Changed search categories to fetch online music rather than grouping offline files by unreliable
  genre metadata
- Fixed online songs so tapping the heart button persists them in Library > Favourites
- Fixed the welcome screen reopening on every launch; it now appears only after a fresh install
- Persist setup completion before entering the main app
- Fixed the cramped "Welcome to" setup heading
- Removed the Beta information button because it linked to the upstream project instead of
  PixelPlayer+
- Improved startup work, artwork loading, playback state collection, recent history, and database
  persistence
- Updated the Android launcher icon and theme to the new purple PixelPlayer+ identity

## Linux desktop

- Updated the application, package, and tray icon to the purple PixelPlayer+ identity
- Added a native tray menu that opens on click and provides useful player controls
- Added a real Quit action so PixelPlayer can be closed completely instead of remaining in the
  background
- Added notification preferences and other essential settings
- Refined the desktop home and discovery experience
- Improved background playback, single-instance behavior, media controls, and desktop integration

## Discord music bot

- Added the optional PixelPlayer Discord music-bot source using slash commands and Lavalink
- Added per-server queues, playback controls, configurable volume, and idle disconnect
- Added recommendation-based autoplay that continues after a song or `/skip`
- Added safe example configuration files; real bot and Lavalink credentials remain excluded

## Downloads

- `PixelPlayer-Android-v2.5.0.apk` - Android application
- `PixelPlayer-Desktop-v2.5.0-linux-x64.tar.gz` - portable Linux x86-64 application
- `SHA256SUMS.txt` - checksums for both downloads

The Linux build includes its Java runtime and requires `mpv` on the host system.

The Android APK is v2-signed with the repository's existing debug certificate. It supports direct
installation and future updates signed with that same certificate, but it is not Play Store ready.
