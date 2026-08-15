# ARKster — Novel Library & Reader (Android-first)

ARKster is a privacy-first, offline-first local library and reader designed to make a messy folder of novels feel like a private Royal Road site on your phone.

Key points:
- Android-first native app (Kotlin + Jetpack Compose).
- SAF-based: the app asks for a single root library folder and indexes content without copying user files.
- Start with `txt`/`md` support; EPUB and PDF are planned in later milestones.
- No accounts, no syncing, metadata downloads are opt-in and cached locally.

## Current status
**v0.2** — see `app/build.gradle.kts`'s `versionName`, the single source of truth the
app itself reads back via `BuildConfig.VERSION_NAME` (shown in Settings and on the
crash screens). Library scan, novel indexing, arcs, author pages, a Royal
Road-style chapter reader with Reader Preferences, and persistent URI storage are
all in place; incremental/diff-based rescan now also reconciles deleted novels
correctly (see `bugs.md`).

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
- [App README](app/README.md) — feature summary and build notes
- [Scanner POC notes](spec/SCANNER_POC.md) — heuristics and progressive scan UX
- [Bug reports & fixes](bugs.md) — root-caused bugs and how each was patched, in order
- `docs/done and dealth with/` — completed roadmaps and staged plans, kept for history:
  [v0.2 Roadmap](docs/done%20and%20dealth%20with/V0.2_ROADMAP.md),
  [v0.2 completion summary](docs/done%20and%20dealth%20with/V0.2_COMPLETION_SUMMARY.md),
  [UI overhaul summary](docs/done%20and%20dealth%20with/UI_OVERHAUL_SUMMARY.md),
  [Author page & chapter page redesign](docs/done%20and%20dealth%20with/AUTHOR_PAGE_AND_CHAPTER_REDESIGN.md)

## Architecture highlights
- **Room + DataStore**: Indexed library state + lightweight prefs.
- **BookSource abstraction**: FolderSource (shipped), then EpubSource and PdfSource (later milestones - see `docs/EPUB_SUPPORT.md`).
- **ChapterContentRepository**: Unified interface for reading chapter text across sources.
- **Graceful degradation**: Auto-generated covers, best-effort title parsing, user override editor.

## License
GPLv3

