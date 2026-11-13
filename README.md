# 🧩 HoiTruyenChu Patches

Morphe patches for **iTruyenChu** (`com.ngoctieucac.itruyenchu`), tested on version 2.1.0.

They fix text-to-speech reading (stops with the screen off, after a phone call, play button doing nothing)
fix Google login, and make the reading history sync with your account automatically.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/CozyCode890/HoiTruyenChu-patches

<a href="https://morphe.software/add-source?github=CozyCode890/HoiTruyenChu-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

| Patch | Description |
|---|---|
| TTS reliability | Re-creates the TTS engine when it dies, pauses during calls and resumes afterwards, retries failed lines, uses the correct Vietnamese voice. |
| Background reading | Keeps reading (and moving to the next chapter) with the screen off. |
| Fix Google login | "Đăng nhập với Google" failed with error 400: the server now expects the Firebase ID token (like the website) instead of the email this app version sends. |
| Auto sync reading history | Uploads the reading history after every chapter change and merges the history of your other devices when the app starts, without pressing "Đồng bộ". |

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
    stub/                              compile-only signatures of app classes (not bundled)
patches/src/main/kotlin/app/hoitruyenchu/
  patches/itruyenchu/
    shared/Constants.kt                compatibility + extension class names
    misc/extension/SharedExtensionPatch.kt
    audio/tts/                         TtsReliabilityPatch.kt + Fingerprints.kt
    audio/backgroundreading/           BackgroundReadingPatch.kt + Fingerprints.kt
    misc/sync/                         AutoSyncReadingHistoryPatch.kt + Fingerprints.kt
    misc/login/                        FixGoogleLoginPatch.kt + Fingerprints.kt
  util/PatchListGenerator.kt
```

## 🔍 Notes on the app

iTruyenChu is an Expo / React Native app; its logic is Hermes bytecode, so the patches hook the
native modules it uses (expo-speech, React Native timers, AsyncStorage) instead of app code.

- **Reading history**: a zustand store persisted in AsyncStorage (`RKStorage` database) under the key `read`,
  the login under `auth-storage`. The reader only updates the local store.
- **Why sync was unreliable**: the server only records a chapter when it is downloaded with the login token,
  which the app does for chapter 51 and later. Chapters 1–50 come from a public URL without the token.
  A full sync (`POST /users/sync-books`) only ran when pressing "Đồng bộ".
- **Google login**: the app signs in to Firebase, then sends `POST /auth/login-firebase {email}`.
  The server (and the website) now use `{idToken: firebaseUser.getIdToken()}`, so the app gets 400.
  The patch adds an interceptor to React Native's OkHttp client that keeps the Firebase ID token from
  `accounts:signInWithIdp` and sends it instead, then adds the profile to the answer if it has no `user`.
- **Auto sync patch**: uploads the history 3 s after it changes, and on app start uploads it and merges
  the server answer into the stored history before the app loads it (waits at most 3.5 s, offline-safe).

## 🛠️ Building locally

- Run `./gradlew buildAndroid` (needs a GitHub token with `read:packages`, see the
  [Morphe documentation](https://github.com/MorpheApp/morphe-documentation)).
- The built patches file is `patches/build/libs/patches-*.mpp`.
- Patch the APK with [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) or Morphe Manager.

Logs: `adb logcat -s HTC-TtsFix HTC-Sync HTC-Login`

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).
