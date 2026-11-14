# FrameForge — Android video editor starter (v0.1)

A **native Android (Kotlin + Jetpack Compose)** non-destructive video editor starter. This is functioning application source code, **not** a complete CapCut/DaVinci competitor, and **not** a prebuilt or device-tested APK.

## Included features

- Android document picker: import multiple videos without storage permissions; persistable read grants
- ExoPlayer preview for a sequence of clips, play/pause, seek on the full timeline
- Thumbnail timeline; tap to select a clip
- Non-destructive in/out trimming and clip splitting at playhead
- Clip reordering and deletion
- Mute original clip audio and apply grayscale shader effect (preview and export)
- 30-step undo/redo within the current editing session
- A single autosaved project draft, surviving app restarts
- Media3 Transformer MP4 export, progress, cancel, and save to `Movies/FrameForge` via MediaStore
- Dark Compose UI and basic responsive scrolling

## Build instructions (Windows)

1. Install **Android Studio** and the Android SDK Platform **36**. Install a JDK 17 or newer (Android Studio's bundled JDK is fine).
2. Extract this ZIP.
3. Run this once in PowerShell from the extracted directory:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\setup-wrapper.ps1
   ```

   This downloads the **official Gradle 8.13 wrapper JAR** and verifies its SHA-256 against Gradle's published checksum. A wrapper binary is not bundled in the ZIP. A network connection is needed for the initial Gradle/dependency downloads.

4. Open the **FrameForgeAndroid** folder in Android Studio (File > Open), then **Sync Project with Gradle Files**.
5. Connect an Android 10+ phone using USB debugging, or start an emulator. Click **Run**.
6. To build a test APK: **Build > Build APK(s)**; output should appear at `app/build/outputs/apk/debug/app-debug.apk`.

On macOS/Linux with an existing Gradle installed, run `gradle wrapper --gradle-version 8.13` to create the wrapper JAR, then `./gradlew :app:assembleDebug`.

**Version pinning:** AGP 8.13.2, Gradle 8.13, Kotlin 2.2.20, Compose BOM 2026.09.00, AndroidX Media3 1.11.1. All Media3 modules intentionally use the same version.

## Using the editor

1. Choose **Import videos**.
2. Tap clips along the timeline to select them, or drag the main seekbar to scrub.
3. Trim a clip with the range slider; changes are committed when you release it.
4. Put the playhead inside a clip and tap **Split** to create two clips.
5. Reorder with arrows. Toggle **Mute audio** and **B&W filter** per clip.
6. Tap **Export**. Keep the app open while exporting. The MP4 appears under your phone's Movies/FrameForge collection.

**Source files are untouched.** Drafts only contain source document URIs and editing instructions. If a source is deleted, moved, revoked, or incompatible, that clip will no longer work.

## What is not included (important)

- No full multitrack audio mixing, overlays/text, keyframed graphics, transitions, LUTs, background removal, or speed ramps yet.
- No multi-project library, cloud sync, proxies, hardware profiling, or DRM support.
- No hard export resolution/bitrate/FPS chooser; export follows Media3 and the device's supported formats.
- No foreground service. Export can stop if the Activity is destroyed or the system reclaims its resources.
- No automated device testing. This environment does not have the Android SDK/Gradle/phone, so the application has **not** been compiled or run here. Android Studio/device testing is required, and integration issues may require fixes.
- Files with unsupported codecs may not preview or export. Playback and encoding performance depend on the device.
- Timeline thumbnails are small, on-demand previews; a large project should eventually use disk caching.

## Proposed architecture for production

- **UI:** Kotlin / Jetpack Compose; responsive landscape/tablet panels
- **Editing model:** versioned schema storing tracks, keyframes, effects, media URIs, undo history
- **Preview engine:** Media3 / hardware decoders / GPU effects. Proxy and frame caches for 4K content
- **Export:** Media3 Transformer composition, persistent foreground service, cancellation/resume
- **Storage:** Room/SQLite multi-project library plus thumbnails/cache, robust atomic autosaving
- **Performance:** native C++ where profiling shows a need. Avoid re-encoding on every edit

## Next milestones

1. Separate video/audio tracks, import background music and live waveform visualization.
2. Subtitle/text overlays, transition UI, clip dragging and pinch-to-zoom timeline.
3. Resolution/aspect ratio presets, bitrate/FPS controls, background exports and notifications.
4. GPU effects library (LUT/HSL/curves), keyframes, transitions, speed changes.
5. Crash recovery, robust source relinking, hardware test matrix, encoder fallback and profiling.

## Licenses

The authored project files in this starter are MIT-licensed (see `LICENSE`). AndroidX / Media3 / Compose / other third-party dependencies keep their own licenses; review obligations before commercial release.

## Technical references

- Media3 editor guide: https://developer.android.com/media/implement/editing-app
- Media3 Transformer getting started: https://developer.android.com/media/media3/transformer/getting-started
- Compose setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Gradle wrapper checksums: https://gradle.org/release-checksums/
