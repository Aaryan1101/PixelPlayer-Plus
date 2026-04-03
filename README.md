# PixelPlayer+ 🎵

<p align="center">
  <img src="assets/icon.png" alt="App Icon" width="128"/>
</p>

<p align="center">
  <strong>Enhanced music player with online streaming capabilities</strong><br>
  Built with Jetpack Compose and Material Design 3
</p>

<p align="center">
  <img src="assets/screenshot1.jpg" alt="Screenshot 1" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot2.jpg" alt="Screenshot 2" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot3.jpg" alt="Screenshot 3" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot4.jpg" alt="Screenshot 4" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
    <a href="https://github.com/Aaryan1101/PixelPlayer-Plus/releases/latest">
        <img src="https://img.shields.io/github/v/release/Aaryan1101/PixelPlayer-Plus?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
    </a>
    <a href="https://github.com/Aaryan1101/PixelPlayer-Plus/releases">
        <img src="https://img.shields.io/github/downloads/Aaryan1101/PixelPlayer-Plus/total?logo=github&style=for-the-badge" alt="Total Downloads">
    </a>
    <img src="https://img.shields.io/badge/Android-10%2B-green?style=for-the-badge&logo=android" alt="Android 10+">
    <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin">
</p>

---

## 🌟 What's New in This Enhanced Version

This is an enhanced fork of the original PixelPlayer by [theovilardo](https://github.com/theovilardo), now maintained by [Aaryan](https://github.com/Aaryan1101) with powerful online music streaming capabilities and performance improvements:

### 🌐 **Online Music Integration**
- **YouTube Search & Stream** - Search and play millions of songs via NewPipe extractor
- **Piped API Support** - Privacy-friendly YouTube frontend integration
- **Enhanced Deezer Integration** - Improved artist images and metadata
- **Online Discovery** - Find new music without leaving the app

### ⚡ **Performance Optimizations**
- **Faster Startup** - Optimized initialization with staggered loading
- **Reduced Memory Usage** - Efficient image caching and processing
- **Smooth UI** - Eliminated frame drops during startup
- **Background Processing** - Non-blocking operations for better UX

### 🚀 **Recent Enhancements**
- **YouTube Stream Caching** - 25-minute TTL cache for faster YouTube streaming
- **Enhanced Search** - Debounced search with loading states and job management
- **Dynamic Color Theming** - Download components adapt to player theme colors
- **Improved Download Management** - Better state handling and cleanup
- **Code Quality Improvements** - Better error handling and resource management

---

## ‼️ DISCLAIMER
- This is an enhanced fork of the original PixelPlayer by [theovilardo](https://github.com/theovilardo)
- **Maintained by [Aaryan](https://github.com/Aaryan1101)** - Support for this version is provided by the fork maintainer
- Online features require internet connection and may have usage limitations

---

## ✨ Features

### 🎨 Modern UI/UX
- **Material You** - Dynamic color theming that adapts to your wallpaper
- **Smooth Animations** - Fluid transitions and micro-interactions
- **Customizable UI** - Adjustable corner radius and navigation bar settings
- **Dark/Light Theme** - Automatic or manual theme switching
- **Album Art Colors** - Dynamic color extraction from album artwork

### 🎵 Powerful Playback (Local + Online)
- **Media3 ExoPlayer** - Industry-leading audio engine with FFmpeg support
- **Background Playback** - Full media session integration
- **Queue Management** - Drag-and-drop reordering
- **Shuffle & Repeat** - All playback modes supported
- **Gapless Playback** - Seamless transitions between tracks
- **Custom Transitions** - Configure crossfades between songs
- **Online Streaming** - Play YouTube videos as audio
- **Mixed Playlists** - Combine local and online tracks

### 📚 Library Management
- **Multi-format Support** - MP3, FLAC, AAC, OGG, WAV, and more
- **Browse By** - Songs, Albums, Artists, Genres, Folders
- **Smart Artist Parsing** - Configurable delimiters for multi-artist tracks
- **Album Artist Grouping** - Proper album organization
- **Folder Filtering** - Choose which directories to scan

### 🔍 Discovery & Organization
- **Full-text Search** - Search across your entire library
- **YouTube Integration** - Search millions of songs online
- **Daily Mix** - AI-powered personalized playlist based on listening habits
- **Playlists** - Create and manage custom playlists with online content
- **Statistics** - Track your listening history and habits

### 🎤 Lyrics
- **Synchronized Lyrics** - LRC format via LRCLIB API
- **Lyrics Editing** - Modify or add lyrics to your tracks
- **Scrolling Display** - Follow along as you listen

### 🖼️ Artist Artwork
- **Enhanced Deezer Integration** - Improved artist images from Deezer API
- **Smart Caching** - Memory (LRU) + database caching for offline access
- **Fallback Icons** - Beautiful placeholders when images unavailable

### 🌐 Online Services
- **YouTube Search** - Find and stream any song on YouTube
- **Piped Integration** - Privacy-friendly YouTube streaming
- **NewPipe Extractor** - Reliable audio stream extraction
- **Online Metadata** - Automatic metadata fetching for online content

### 📲 Connectivity
- **Chromecast** - Stream to your TV or smart speakers
- **Android Auto** - Full Android Auto support for in-car playback (Soon)
- **Widgets** - Home screen control with Glance widgets

### ⚙️ Advanced Features
- **Tag Editor** - Edit metadata with TagLib (MP3, FLAC, M4A support)
- **AI Playlists** - Generate playlists with Gemini AI
- **Audio Waveforms** - Visual representation with Amplituda (Soon)
- **Performance Optimizations** - Faster startup and smoother operation

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | [Kotlin](https://kotlinlang.org/) 100% |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| **Design System** | [Material Design 3](https://m3.material.io/) |
| **Audio Engine** | [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3) + FFmpeg |
| **Architecture** | MVVM with StateFlow/SharedFlow |
| **DI** | [Hilt](https://dagger.dev/hilt/) |
| **Database** | [Room](https://developer.android.com/training/data-storage/room) |
| **Networking** | [Retrofit](https://square.github.io/retrofit/) + OkHttp |
| **Online Services** | [NewPipe](https://github.com/TeamNewPipe/NewPipe), [Piped](https://piped.video/) |
| **Image Loading** | [Coil](https://coil-kt.github.io/coil/) |
| **Async** | Kotlin Coroutines & Flow |
| **Background Tasks** | WorkManager |
| **Metadata** | [TagLib](https://github.com/nicholaus/taglib-android) |
| **Widgets** | [Glance](https://developer.android.com/jetpack/compose/glance) |

---

## 📱 Requirements

- **Android 11** (API 30) or higher
- **4GB RAM** recommended for smooth performance
- **Internet Connection** for online features
- **Storage Space** for caching online content

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug | 2024.2.1 or newer
- Android SDK 29+
- JDK 17 (recommended)

### Installation

1. **Clone repository**
   ```sh
   git clone https://github.com/Aaryan1101/PixelPlayer-Plus.git
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to cloned directory

3. **Sync and Build**
   - Wait for Gradle to sync dependencies
   - Build project (Build → Make Project)

4. **Configure API Keys** (if needed)
   - Add any required API keys to `local.properties`
   - Update API endpoints in network modules

5. **Run**
   - Connect a device or start an emulator
   - Click Run (▶️)

---

## ⬇️ Download

<p align="center">
  <a href="https://github.com/Aaryan1101/PixelPlayer-Plus/releases/latest">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60">
  </a>
</p>

---

## 📂 Project Structure

```
app/src/main/java/com/theveloper/pixelplay/
├── data/
│   ├── database/       # Room entities, DAOs, migrations
│   ├── model/          # Domain models (Song, Album, Artist, etc.)
│   ├── network/        # API services (LRCLIB, Deezer, YouTube, Piped)
│   │   ├── deezer/    # Deezer API integration
│   │   ├── youtube/   # YouTube/NewPipe extraction
│   │   ├── piped/     # Piped API integration
│   │   └── lyrics/    # LRCLIB lyrics service
│   ├── preferences/    # DataStore preferences
│   ├── repository/     # Data repositories
│   ├── service/        # MusicService, HTTP server
│   └── worker/         # WorkManager sync workers
├── di/                 # Hilt dependency injection modules
├── presentation/
│   ├── components/     # Reusable Compose components
│   ├── navigation/     # Navigation graph
│   ├── screens/        # Screen composables
│   └── viewmodel/      # ViewModels
├── ui/
│   ├── glancewidget/   # Home screen widgets
│   └── theme/          # Colors, typography, theming
└── utils/              # Extensions and utilities
```

## 🌐 Online Features

### YouTube Integration
- Search millions of songs
- Stream high-quality audio
- Extract metadata automatically
- Cache for offline playback

### Piped API
- Privacy-friendly YouTube access
- No tracking or ads
- Multiple quality options
- Reliable streaming

### Enhanced Metadata
- Automatic artist images
- Album artwork fetching
- Rich song information
- Cross-service metadata

---

## 📄 License

This project is licensed under MIT License - see [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Original PixelPlayer** by [theovilardo](https://github.com/theovilardo)
- **NewPipe** for YouTube extraction
- **Piped** for privacy-friendly streaming
- **Deezer** for artist images and metadata
- All contributors and testers

---

<p align="center">
  Enhanced with ❤️ by <a href="https://github.com/Aaryan1101">Aaryan</a>
  <br>
  <small>Forked from <a href="https://github.com/theovilardo/PixelPlayer">PixelPlayer by theovilardo</a></small>
</p>
