# FreeTune

A free, ad-free, fully local YouTube music player for Android.

- Streams audio from YouTube with no ads
- Downloads songs to the device for offline playback
- Reliable background playback with media notification and lock-screen controls
- Playlists with their own download folders
- Optional floating playback bar over other apps
- Local library: playlists, liked songs, recent history, downloads
- No account, no analytics, no ads, no cloud, no tracking
- Sideloaded APK only

## Install

1. Download `FreeTune-0.3.0.apk` from `dist/`.
2. Transfer it to an Android phone (Android 8.0+).
3. Tap the APK and allow "install unknown apps" for your file manager/browser.
4. Open FreeTune, grant notification permission, and search for music.

Upgrading from 0.1.0 or 0.2.0 keeps your library, history and downloads.

## Build from source

Requirements:

- JDK 21
- Android SDK with platform 35 and build-tools 35.0.0
- Internet access (Gradle downloads dependencies)

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Release build (uses `keystore.properties` + `freetune-release.jks` if present):

```powershell
.\gradlew.bat :app:assembleRelease
```

## Features in this version

- Redesigned Home with a daily mix, quick-pick chips, "Jump back in" and liked songs
- YouTube search that starts automatically as you type (2+ characters, 650 ms debounce)
  with clear idle, loading, and no-results states
- Autoplay: when the queue ends, FreeTune keeps going with related songs (on by default,
  toggle in Settings or on the player screen)
- Playlists: create, add/remove songs, play all, shuffle, download all, delete
- Play or shuffle a whole playlist straight from a Library playlist card
- Full-playlist downloads with quality choice, a "skip already downloaded" option,
  and re-download support for the entire playlist
- Each playlist downloads into its own folder under `Music/FreeTune/<playlist>/`
- Playback via Media3/ExoPlayer with a real foreground media service
- Media notification, lock-screen and Bluetooth controls
- Optional floating controls bar (previous / play-pause / next / close) over other apps
- Audio-only downloads with quality selection (best / balanced / data saver)
- Resumable downloads (HTTP Range), Wi-Fi-only constraint, download progress and retry
- Liked songs, recent history, downloaded library, per-playlist folders
- Privacy details live in Settings, not on Home
- Manual "erase all data"
- Works without Google Play Services

## Privacy

- No analytics, ads, crash reporting or telemetry SDKs
- No account or sign-in
- All library, history, playlists and downloads are stored on the device
- Network access is only used for YouTube requests (and downloads from YouTube's CDN)
- `allowBackup` is disabled

## Legal

FreeTune is an unofficial client. Downloading audio from YouTube may violate YouTube's
Terms of Service and copyright law depending on the content and your jurisdiction.
Downloads are intended for personal use only. You are responsible for the content you
access and download. The app is not affiliated with YouTube or Google.

This project is licensed under GPL-3.0.

## Architecture

```
app/src/main/java/com/freetune/app/
├── data/
│   ├── db/          Room entities, DAOs, database (v2 + migration)
│   ├── repo/        MusicRepository (library, likes, history, downloads, playlists)
│   ├── settings/    SettingsStore (SharedPreferences + StateFlow)
│   ├── yt/          NewPipeExtractor wrapper + OkHttp downloader
│   └── download/    WorkManager worker, queue, status store
├── playback/        ExoPlayer manager, MediaSessionService, overlay service
└── ui/              Compose screens, theme, view model, components
```

## Download folders

Downloads are saved to the app-specific external Music directory:

```
/sdcard/Android/data/com.freetune.app/files/Music/FreeTune/
```

Songs downloaded from a playlist are placed in a subfolder named after the playlist.
Because this is app-specific storage, other music players may not index the files;
they are fully playable inside FreeTune.

## Known limitations

- Floating controls need the "display over other apps" permission and are off by default.
- Autoplay needs a network connection to fetch related songs; offline playback still works
  for downloaded tracks, but the queue stops at the end when autoplay can't reach YouTube.
- Downloads are stored in the app-specific Music folder, so other music players
  may not index them. They are fully playable inside FreeTune.
- Embedded ID3/MP4 tags are not written yet; metadata is stored in the local database.
- YouTube extraction uses NewPipeExtractor's best-effort mode. YouTube changes can
  temporarily break search or stream extraction until the extractor dependency is updated.
- No cross-device sync, accounts, chat, or cloud features by design.

## Updating the extractor

If search or playback stops working, bump `newpipe` in
`gradle/libs.versions.toml` to the latest release, then rebuild:

```powershell
.\gradlew.bat :app:assembleDebug
```
