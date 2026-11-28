# 🧩 HoiTruyenChu Patches

Morphe patches for **iTruyenChu** (`com.ngoctieucac.itruyenchu`), tested on versions 3.0.0 and 2.1.0.

They fix text-to-speech reading (stops with the screen off, after a phone call, play button doing nothing,
speed change ignored until pause + play), fix Google login, show the real chapter titles instead of "Vô Đề Mục",
and make the reading history sync with your account automatically.

### Ready-made APK

Every [release](https://github.com/CozyCode890/HoiTruyenChu-patches/releases/latest) has the patched app attached
(`itruyenchu-<app version>-patched-<release>.apk`), built by CI with Morphe Manager's default key, so it installs
over an app patched with Morphe Manager.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/CozyCode890/HoiTruyenChu-patches

<a href="https://morphe.software/add-source?github=CozyCode890/HoiTruyenChu-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v2.2.4](https://github.com/CozyCode890/HoiTruyenChu-patches/releases/tag/v2.2.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 iTruyenChu&nbsp;&nbsp;•&nbsp;&nbsp;9 patches</summary>
<br>

**Package name:** `com.ngoctieucac.itruyenchu`

**🎯 Supported versions:**

| 3.0.0 | 2.1.0 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Auto sync reading history](#auto-sync-reading-history) | Automatically syncs your reading history on app start, after logging in, and when returning after more than 5 minutes, merging other devices' progress with book titles and saving free chapters 1–50 to your account. Also lets the "Đồng bộ" button work with an empty bookshelf and keeps deleted history books from returning. |  |
| [Background reading](#background-reading) | Keeps reading aloud and advancing to the next chapter when the screen is off or the app is in the background. |  |
| [Fix Google login](#fix-google-login) | Fixes "Đăng nhập với Google" failing with an error. Required for Google sign-in to work on this app version. |  |
| [Remember listening speed](#remember-listening-speed) | Remembers your chosen reading speed across app restarts instead of resetting to 1x, using one speed for all books. Replaces the speed menu with a YouTube-style slider and customizable quick-select buttons. |  |
| [Resume listening position](#resume-listening-position) | Returns to the last line you listened to when reopening a book from your bookshelf or "Đọc truyện". Can highlight the line, ask before resuming, or start reading automatically. |  |
| [Show chapter titles](#show-chapter-titles) | Shows real chapter titles instead of "Vô Đề Mục" in the chapter list, reader, and TTS by reading the title from each chapter's opening line. Titles are cached on your device and do not require logging in. |  |
| [TTS media controls](#tts-media-controls) | Shows live reading status and chapter progress (Dòng x/y) on the lock screen and media notification, with working play/pause and chapter skip buttons. Also fixes the voice bar's previous-chapter button. |  |
| [TTS reliability](#tts-reliability) | Fixes text-to-speech stopping after calls or failing to play, lets you pick a TTS engine and Vietnamese voice, applies speed changes immediately, and resumes from the exact word where reading stopped. Also reads "~" and "..." more naturally with a short pause. |  |
| [Tap a line to listen from it](#tap-a-line-to-listen-from-it) | Lets you tap any line in vertical reading mode to jump the voice reading there, pause, or start from that word. Scrolling manually pauses auto-follow and shows a "Về dòng đang đọc" button, with customizable tap and scroll behaviors in settings. |  |

</details>

<!-- PATCHES_END -->

## 📁 Project layout

Same layout as [MorpheApp/morphe-patches](https://github.com/MorpheApp/morphe-patches):

```
extensions/
  itruyenchu/                          extension merged into the app (extensions/itruyenchu.mpe)
    src/main/java/app/hoitruyenchu/extension/itruyenchu/patches/
      tts/TtsController.java           TTS engine lifecycle, call handling, background session
      sync/AutoSyncPatch.java          automatic reading history sync
      login/FixGoogleLoginPatch.java   OkHttp interceptor fixing the Google login request
      titles/ChapterTitlesPatch.java   real chapter titles (+ ChapterListInterceptor)
    stub/                              compile-only signatures of app classes (not bundled)
patches/src/main/kotlin/app/hoitruyenchu/
  patches/itruyenchu/
    shared/                            Constants.kt (compatibility, extension classes) + shared Fingerprints.kt (OkHttp, AsyncStorage)
    misc/extension/SharedExtensionPatch.kt
    audio/tts/                         TtsReliabilityPatch.kt + Fingerprints.kt
    audio/backgroundreading/           BackgroundReadingPatch.kt + Fingerprints.kt
    misc/sync/                         AutoSyncReadingHistoryPatch.kt
    misc/titles/                       ShowChapterTitlesPatch.kt
    misc/login/                        FixGoogleLoginPatch.kt
  util/PatchListGenerator.kt
```

## 🔍 Notes on the app

iTruyenChu is an Expo / React Native app; its logic is Hermes bytecode, so the patches hook the
native modules it uses (expo-speech, React Native timers, AsyncStorage) instead of app code.

- **Reading history**: a zustand store persisted in AsyncStorage (`RKStorage` database) under the key `read`,
  the login under `auth-storage`. The reader only updates the local store.
- **Why sync was unreliable**: the server records the progress itself whenever a chapter is downloaded with
  the login token (`GET /chapters/{slug}/content/{n}`). The app (and the website) only do that for chapter 51
  and later; chapters 1–50 come from a public URL (`/free/{slug}/chuong-{n}.txt`) and are never recorded.
  The recorded progress (`POST /users/sync-books-new` -> `{slug: chapter}`) was only pulled when pressing the
  sync button. (`POST /users/sync-books`, used by the history screen button, now answers HTTP 500: the patch
  sends that request to `sync-books-new`, records on the server the books read further on the device, and answers
  the merged history, oldest first, since the button re-adds the entries one by one in front.)
- **Google login**: the app signs in to Firebase, then sends `POST /auth/login-firebase {email}`.
  The server (and the website) now use `{idToken: firebaseUser.getIdToken()}`, so the app gets 400.
  The patch adds an interceptor to React Native's OkHttp client that keeps the Firebase ID token from
  `accounts:signInWithIdp` and sends it instead, then adds the profile to the answer if it has no `user`.
- **Auto sync patch**: when a free chapter is downloaded, downloads it once more with the token in the background
  so the server records it; on app start pulls the recorded progress and merges it into the stored history before
  the app loads it (waits at most 4.5 s, offline-safe). Entries that only have a slug and a chapter get their title
  and chapter count from the public `books/{slug}.json`, like the reader does.
- **"Vô Đề Mục"**: the chapter list (`/chapters/{slug}.json.gz` = `{"207": {"title": "Chương 207"}}`) has no real
  title for many books, so the app (and the website) strip "Chương 207" and show "Vô Đề Mục". The real title is the
  first `Chương N: ...` line of the chapter text, and the public preview (`/preview/{slug}/chuong-{n}-preview.txt`,
  gzip; `/free/...` for chapters 1–50) starts with it. The patch reads the first kilobyte of each (HTTP Range,
  no login, nothing recorded), caches the titles in `files/htc-chapter-titles/`, and fills them into the downloaded
  list and into the copy the app keeps in AsyncStorage (`s3:{url}`, refreshed hourly). Some chapters really have no title.
- **TTS speed**: the voice bar's reading chain (`speakLine` -> `onDone` -> `speakLine`) keeps the speed of the render
  in which play was pressed. The voice bar store is saved in AsyncStorage as `tts_voice_state`
  (`{"state":{"spead":..}}`, sic), so the patch takes the speed from there and speaks the current line again at it.

## 🛠️ Building locally

- Run `./gradlew buildAndroid` (needs a GitHub token with `read:packages`, see the
  [Morphe documentation](https://github.com/MorpheApp/morphe-documentation)).
- The built patches file is `patches/build/libs/patches-*.mpp`.
- Patch the APK with [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) or Morphe Manager.
- CI (`.github/workflows/release.yml`, job "Patched APK", script `.github/scripts/build_apk.sh`) patches the
  newest `app-<version>.apk` of the release [`app-source`](https://github.com/CozyCode890/HoiTruyenChu-patches/releases/tag/app-source)
  with each new release and attaches the APK. When the app updates, upload the new original APK there. Needs the
  secret `MORPHE_KEYSTORE_BASE64` (Morphe Manager's keystore, base64). To rebuild the APK of an existing release,
  run the workflow with `apk_tag`.

Logs: `adb logcat -s HTC-TtsFix HTC-Sync HTC-Login HTC-Titles`

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).
