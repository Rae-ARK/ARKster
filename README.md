# ARKster — Novel Library & Reader (Android-first)

ARKster is a privacy-first, offline-first local library and reader designed to make a messy folder of novels feel like a private Royal Road site on your phone.

Key points:
- Android-first native app (Kotlin + Jetpack Compose).
- SAF-based: the app asks for a single root library folder and indexes content without copying user files.
- Start with `txt`/`md` support; EPUB and PDF are planned in later milestones.
- No accounts, no syncing, metadata downloads are opt-in and cached locally.

## Current status
**v0.1 MVP complete** — Library scan, novel indexing, chapter reader, and persistent URI storage are ready.

## Quick start
1. Clone this repository and open the `/app` folder in Android Studio.
2. Sync Gradle and build the debug APK.
3. Install on an Android device (API 24+).
4. Select a folder containing novel subfolders with `.txt`/`.md` chapter files.
5. Browse, search, and read.

## Building from command line
```bash
./gradlew assembleDebug   # Build debug APK
./gradlew assembleRelease # Build release APK (requires signing config)
```

APK outputs: `app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`

## GitHub Actions CI
Push to `main` or `develop` branches to trigger automatic APK builds. Artifacts are available on the Actions tab.

## Documentation
- [Android design & architecture](docs/ARKster_ANDROID_DESIGN.md) — design overview, storage model, BookSource abstraction
- [v0.1 MVP app README](app/README.md) — feature summary and build notes
- [v0.2 Roadmap](docs/V0.2_ROADMAP.md) — arcs, pagination prefs, manual edits, incremental rescan
- [Scanner POC notes](spec/SCANNER_POC.md) — heuristics and progressive scan UX

## Architecture highlights
- **Room + DataStore**: Indexed library state + lightweight prefs.
- **BookSource abstraction**: FolderSource (v0.1), then EpubSource and PdfSource (v0.3+).
- **ChapterContentRepository**: Unified interface for reading chapter text across sources.
- **Graceful degradation**: Auto-generated covers, best-effort title parsing, user override editor.

## License
Unspecified (add a LICENSE file if publishing).

