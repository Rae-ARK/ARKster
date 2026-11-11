# What is ARKster

ARKster is a privacy-first, offline-first local library and reader for
Android. It turns a folder of novels sitting on-device into something that
browses and reads like a private Royal Road: a Home feed, a Fiction page,
arcs, author pages, and a native chapter reader — with no account, no
server, and no file copying.

## Principles

- **Offline-first and privacy-respecting.** No accounts by default; network
  metadata lookups are opt-in and cached locally.
- **SAF-first storage.** The app asks for one root library folder via
  Android's Storage Access Framework and persists the `Uri` permission; it
  never copies the user's files.
- **The folder is the source of truth.** Room is the indexed, queryable
  representation built from it — not a second copy of the data.
- **Format complexity stays in the data layer.** The UI consumes a
  normalized `Chapter`/`BookMetadata` model regardless of whether the
  underlying source is a folder of `.txt` files, an EPUB, or (later) a PDF.

## Scope: what ARKster is (and isn't)

ARKster is the general-purpose reader in the ARK family:

| App | Platform | Who it's for | License |
|---|---|---|---|
| **ARKster** (this repo) | Android | Anyone with a folder of novels | GPLv3 |
| [ARKium](https://github.com/Rae-ARK/arkium) | Desktop (C, GTK 4/libadwaita) | Anyone with a folder of novels | GPLv3 |
| [ARKarium](https://github.com/Rae-ARK/arkarium) | Android | Readers of Rae ARK's own novels | Proprietary |

- **ARKarium** (separate, proprietary) owns author→reader relay sync,
  new-chapter notifications, per-chapter read-event stats, and
  character-aware TTS. It forked ARKster's scanner, sort-tier, and
  author-page logic, so bugfixes to those are worth back-porting there (see
  [`scanner-invariants.md`](scanner-invariants.md)) — but new
  author-distribution features are not ARKster's concern. For a
  field-by-field comparison of the two apps' folder conventions and data
  models, see
  [ARKium's Android reference doc](https://github.com/Rae-ARK/arkium/blob/main/docs/foundation/ANDROID_REFERENCE.md).
- **ARKium** (desktop sibling, same folder model) owns any future
  desktop-as-library / phone-as-subset backup sync. Nothing in ARKster
  assumes that sync exists yet — it doesn't, as of this doc.

ARKster itself stays local-only and account-free per the Principles above:
one root folder, indexed on-device, no network dependency for core
reading.

## Current status

**v0.3**, per `app/build.gradle.kts`'s `versionName` — the single source of
truth the app itself reads back via `BuildConfig.VERSION_NAME`. v0.3 is a
bugfix-only release on top of v0.2's feature set (the fixes now captured as
rules in [`scanner-invariants.md`](scanner-invariants.md)) — no new
user-facing feature has shipped since v0.2. Library scan, novel indexing,
arcs, author pages, a Royal Road-style chapter reader, and persistent URI
storage are all shipped; see [`architecture.md`](architecture.md) for the
full feature set, [`implementation/README.md`](../implementation/README.md)
for what's being built next, and
[`proposal/README.md`](../proposal/README.md) for what's documented but
untouched.

**Verified:** 2026-11-11 @ (this patch)
