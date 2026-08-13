ARKster Android app (v0.1 MVP)
======================================

This is a minimal Android Compose implementation of the ARKster MVP.

Features (v0.1)
- SAF-based folder selection and persistent permission handling
- Progressive folder scanning that discovers novels and chapters
- Room-based indexing of novels and chapters
- Library screen showing discovered novels
- Novel detail screen listing chapters from a selected novel
- Reader screen displaying chapter text with scrolling
- Text encoding fallback (UTF-8 → ISO-8859-1)
- Persistent storage of selected library URI (restores on app restart)

How to build and run
1. Open this folder in Android Studio.
2. Sync Gradle (File → Sync Now).
3. Build and run on an Android device (API 24+).
4. Tap "Select library folder" to pick a folder containing novel subfolders.
5. Wait for scan to complete, then tap a novel to see chapters, and tap a chapter to read.

Known limitations
- No manual chapter reordering or title editing yet (v0.2).
- No arcs support yet (v0.2).
- EPUB/PDF support deferred to v0.3/v0.4.
- No themes or font customization UI yet.

Architecture notes
- `PreferencesManager` handles persistent preference storage (DataStore).
- `ScannerImpl` enumerates folders and parses chapter filenames using ranked heuristics.
- `ChapterContentRepository` abstracts text file reading with encoding fallback.
- Navigation is managed via sealed class `Screen` enum in `MainActivity`.

