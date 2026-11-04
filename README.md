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

1. Open [freetunes.pages.dev](https://freetunes.pages.dev) for every
   version with checksums, or use [Releases](https://github.com/ThatAerospaceandTechGuy/FreeTune-Android/releases/latest)
   and download `FreeTune-0.8.4.apk` directly.
2. Optionally check the APK against the SHA-256 listed in the release notes:
   `sha256sum FreeTune-0.8.4.apk`.
3. Transfer it to an Android phone (Android 8.0+).
4. Tap the APK and allow "install unknown apps" for your file manager/browser.
5. Open FreeTune, grant notification permission, and search for music.

Every version is published as its own release with its APK, notes and checksum, so
older builds stay available. Upgrading keeps your library, history and downloads.

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

## Website

The download page at <https://freetunes.pages.dev> is generated from
`dist/` and deployed to Cloudflare Pages:

```sh
node site/build.mjs
npx wrangler pages deploy site/public --project-name freetunes --branch main
```

See `site/README.md` for details.

## Features in this version

- Redesigned Home with a daily mix, quick-pick chips, "Jump back in" and liked songs
- YouTube search that starts automatically as you type (2+ characters, 650 ms debounce)
  with clear idle, loading, and no-results states
- Autoplay: when the queue ends, FreeTune keeps going with related songs. The next
  stream URL and the related-song list are prefetched while the current song plays, so
  songs change without the old loading pause (on by default, toggle in Settings or on
  the player screen)
- Autoplay survives removed, private or region-locked videos by skipping them instead
  of stopping, and continues from imported local files by searching for that song
- Resumes the queue from your last session, paused at the same position; one tap on
  play continues where you left off
- Like or unlike the current song straight from the media notification, next to
  previous and next controls
- Start radio from any song: it plays the song and fills the queue with related
  tracks immediately
- The Home daily mix is built from the artists you listen to and like, instead of a
  fixed search
- Private listening: songs played while it is on stay out of Recent and the mix
- Repeat off / all / one, plus a sleep timer that stops after 15-60 minutes or at
  the end of the current song
- The queue is one tap away: it lists every upcoming song, and tapping one jumps
  straight to it
- Lyrics load on their own: a `.lrc` file you pair or keep next to a download wins,
  otherwise FreeTune looks the song up on LRCLIB by artist, title and duration and
  caches the result. Timed lines highlight as the song plays
- Downloading a song also writes its lyrics as a matching `.lrc` beside the audio
  file, so a downloaded track keeps its lyrics without a connection
- Fetch missing in the Downloads tab fills in `.lrc` files for songs downloaded
  before lyrics were saved automatically, skipping the ones that already have them
- Previous and next in the media notification now move through the real queue
  instead of the single media item the player holds
- Play next and Add to queue from every song's menu, so a song can be queued without
  replacing what is playing
- Undo after skipping: a snackbar puts the previous song back at the position you
  left it
- A stream that stops before its advertised length resumes from where it stopped,
  re-resolving the URL and then trying a different audio stream. If it still cannot
  finish, playback stops with a message instead of quietly jumping to the next song
- A refused stream (HTTP 403) is retried with a freshly resolved URL before a song
  is skipped, so an expired stream link no longer looks like a broken track
- Downloads verify the received byte count against the advertised length, so a
  connection that delivers only part of a track is retried instead of being saved
  as complete
- Downloads verify the received byte count, so a dropped connection can no longer be
  saved as a complete song
- Playlists: create, add/remove songs, play all, shuffle, download all, delete
- Reorder songs inside a playlist: long-press a song and drag it into place
- Import songs that are already on the phone — the OS Music folder, Download, or another
  app's folder — into any playlist. They play offline and are never downloaded twice
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
- Network access is used for YouTube requests (and downloads from YouTube's CDN),
  plus lyrics lookups that send only artist, title and duration to lrclib.net
- `allowBackup` is disabled

## Legal

FreeTune is an unofficial client. Downloading audio from YouTube may violate YouTube's
Terms of Service and copyright law depending on the content and your jurisdiction.
Downloads are intended for personal use only. You are responsible for the content you
access and download. The app is not affiliated with YouTube or Google.

FreeTune is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation,
version 3 of the License, or (at your option) any later version.

It is distributed in the hope that it will be useful, but **without any warranty**,
without even the implied warranty of merchantability or fitness for a particular
purpose. See [LICENSE](LICENSE) for the full text.

Build outputs used to ship in `dist/`; they now live on the
[Releases](https://github.com/ThatAerospaceandTechGuy/FreeTune-Android/releases) page
and are not committed to git. `dist/SHA256.txt` keeps the checksums.

## Architecture

```
app/src/main/java/com/freetune/app/
├── data/
│   ├── db/          Room entities, DAOs, database (v3 + migrations)
│   ├── local/       MediaStore scanner for songs already on the device
│   ├── repo/        MusicRepository (library, likes, history, downloads, playlists)
│   ├── settings/    SettingsStore (SharedPreferences + StateFlow)
│   ├── session/     SessionStore (last queue + position, for resume)
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
- Autoplay needs a network connection to fetch related songs. Links are resolved ahead of
  time, but with no connection the queue still stops at the end of downloaded tracks.
- Autoplay from an imported file has no YouTube id to look up, so it falls back to a
  search for that title and artist.
- A restored session comes back paused at the saved position and only loads the audio
  when you tap play.
- The like button appears in the expanded media notification; the collapsed view keeps
  previous, play/pause and next.
- Online lyrics come from LRCLIB, which is free and needs no account. A song it
  does not know stays without lyrics until you add a `.lrc` file; instrumentals are
  labelled as such. Lookups need a connection, and results are cached per song.
- Downloads made before 0.8.3 have no saved `.lrc`. The Downloads tab's Fetch
  missing action writes them without downloading the audio again.
- A `.lrc` file always takes precedence over the online copy. Removing a paired
  file falls back to the cached or online lyrics for that song.
- Importing on-device songs asks for the audio permission (READ_MEDIA_AUDIO on Android 13+).
  Files are read through MediaStore and are never moved, copied or deleted.
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
