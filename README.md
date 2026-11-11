# ARKster — Novel Library & Reader (Android-first)

ARKster is a privacy-first, offline-first local library and reader designed to make a messy folder of novels feel like a private Royal Road site on your phone.

Key points:
- Android-first native app (Kotlin + Jetpack Compose).
- SAF-based: the app asks for a single root library folder and indexes content without copying user files.
- Start with `txt`/`md` support; EPUB and PDF are planned in later milestones.
- No accounts, no syncing, metadata downloads are opt-in and cached locally.

## The ARK ecosystem

| App | Platform | Who it's for | License |
|---|---|---|---|
| **ARKster** (this repo) | Android | Anyone with a folder of novels | GPLv3 |
| [ARKium](https://github.com/Rae-ARK/arkium) | Desktop (C, GTK 4/libadwaita) | Anyone with a folder of novels | GPLv3 |
| [ARKarium](https://github.com/Rae-ARK/arkarium) | Android | Readers of Rae ARK's own novels | Proprietary |

ARKster's role is the **general-purpose** one: a local, offline reader for *your
own* folder of novels, with no author-distribution or sync features — that's a
deliberate scope limit, not a missing feature. ARKarium is a separate,
proprietary app built for one author's readers; its own docs say its scanner,
sort-tier, and author-page logic were carried over from ARKster, but it then
adds things that are specifically out of scope here — relay sync from a fixed
distribution point, new-chapter notifications, per-chapter read-event stats,
and character-aware TTS. ARKium is the desktop sibling: same general-purpose
scope and folder conventions as ARKster, just a different platform. A
desktop-as-full-library / phone-as-curated-subset sync between ARKium and
ARKster is a *planned* direction for ARKium, not a current feature of either
app. For a detailed, field-by-field comparison of ARKster's and ARKarium's
folder conventions and data models (current as of a dated snapshot), see
[ARKium's Android reference doc](https://github.com/Rae-ARK/arkium/blob/main/docs/foundation/ANDROID_REFERENCE.md).

## Current status
**v0.3** — see `app/build.gradle.kts`'s `versionName`, the single source of truth the
app itself reads back via `BuildConfig.VERSION_NAME` (shown in Settings and on the
crash screens). v0.3 is a bugfix-only release on top of v0.2's feature set — no new
user-facing feature has shipped since v0.2. Library scan, novel indexing, arcs,
author pages, a Royal Road-style chapter reader with Reader Preferences, and
persistent URI storage are all in place; incremental/diff-based rescan also
reconciles deleted novels correctly (see
[`docs/arkster/foundational/scanner-invariants.md`](docs/arkster/foundational/scanner-invariants.md)).

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


## Documentation
Docs live in [`docs/arkster/`](docs/arkster/README.md), organised by
lifecycle — start at
[`docs/arkster/foundational/what-is-arkster.md`](docs/arkster/foundational/what-is-arkster.md):
- [`foundational/`](docs/arkster/foundational/README.md) — identity, architecture,
  scanner invariants (the hard-won bugfix rules), the author/reader model, and UI design.
- [`implementation/`](docs/arkster/implementation/README.md) — work in progress (currently EPUB support).
- [`proposal/`](docs/arkster/proposal/README.md) — documented but untouched ideas and open bug reports.
- [App README](app/README.md) — feature summary and build notes.
- [Scanner POC notes](spec/SCANNER_POC.md) — original heuristics and progressive scan UX notes.

## Architecture highlights
- **Room + DataStore**: Indexed library state + lightweight prefs.
- **BookSource abstraction**: the scanner reads folders directly today, not through this
  interface yet — making `FolderSource` real, alongside `EpubSource`, is staged in
  [`docs/arkster/implementation/oop-modular-monolith-refactor.md`](docs/arkster/implementation/oop-modular-monolith-refactor.md)
  and [`docs/arkster/implementation/epub-support.md`](docs/arkster/implementation/epub-support.md).
  PdfSource is a documented idea, not yet started (see
  [`docs/arkster/proposal/README.md`](docs/arkster/proposal/README.md)).
- **ChapterContentRepository**: Unified interface for reading chapter text across sources.
- **Graceful degradation**: Auto-generated covers, best-effort title parsing, user override editor.

## License
GPLv3