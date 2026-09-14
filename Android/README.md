# MeloRemote for Android

MeloRemote is a native Jetpack Compose client for Music Assistant. The Android app mirrors the released iOS app's authentication, player control, library, queue, speaker, search, media-action, and settings workflows.

## Local development

Open this `Android` directory as the project in Android Studio. This laptop is configured with:

- Android Studio's bundled JDK 17
- Android SDK at `C:\Users\Zack\AppData\Local\Android\Sdk`
- Compile SDK 37 and target SDK 36
- ADB access to the connected Samsung test phone

`local.properties` is machine-specific and intentionally ignored by Git.

From PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat installDebug
```

The debug application ID is `com.versarepair.meloremote.debug`, so it can coexist with a future production install.

## Release build

Play App Signing should own the final app-signing key. Create and retain an upload key, then supply these as environment variables or Gradle properties:

```text
MELOREMOTE_UPLOAD_STORE_FILE
MELOREMOTE_UPLOAD_STORE_PASSWORD
MELOREMOTE_UPLOAD_KEY_ALIAS
MELOREMOTE_UPLOAD_KEY_PASSWORD
```

Verify and build the release bundle with:

```powershell
.\gradlew.bat verifyReleaseSigning bundleRelease
```

Never commit the keystore or signing secrets. The Play upload artifact is generated at `app/build/outputs/bundle/release/app-release.aab`.

## Before production submission

- Exercise both token and username/password sign-in against the production Music Assistant server.
- Test playback, seek, volume/mute, shuffle/repeat, queue mutations, search, playlists, favorites, and reconnect behavior on real hardware.
- Create the Play Console app using package ID `com.versarepair.meloremote` and enable Play App Signing.
- Complete the privacy policy, Data safety, content rating, target-audience, app-access, ads, and store-listing declarations.
- Upload an internal-testing AAB first, resolve the automated pre-launch report, then promote through closed testing to production.

Google Play materials are under `play/`: the 512 px listing icon, 1024 × 500 feature graphic, five phone screenshots, store listing copy, privacy-policy update, release-readiness notes, and release notes. The upload keystore and signing secrets remain intentionally outside the repository.
