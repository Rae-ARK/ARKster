package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt as part of the staged OOP modular-monolith
 * refactor (Facade + Chain of Responsibility + Builder). Full plan, and
 * what's still open after this stage:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * This is the "ranked heuristics" piece the refactor doc calls out
 * specifically: "parseChapter's ranked heuristics ... already *are* a
 * chain; today they're inlined sequential checks instead of composed
 * handler objects. Formalizing this means the same chain can be reused for
 * EPUB's NCX/nav-title fallback matching ... instead of that plan writing
 * its own copy." Behavior is unchanged from ScannerImpl's private
 * parseChapter() - every comment below explaining *why* a rule exists
 * (bugs.md Bug 2) is carried over verbatim, not rewritten, since it's the
 * reasoning that would otherwise be lost, not the code shape.
 *
 * Structured as a processing chain (each handler enriches a shared
 * context and passes it to the next), not a try/fallback chain - the
 * steps are sequential transformations of one filename, not alternative
 * ways to parse it. See each Handler below.
 */

data class ParsedChapter(val number: Int?, val title: String, val sortTier: Int)

// Chapter sort tiers - see ChapterTitleParser.parse() and bugs.md Bug 2. Regular
// chapters (0) sort first by their own number/title, exactly as before this fix;
// bonus content (1) and closing content (2) always sort after every regular chapter
// in the same folder, in that order.
const val TIER_REGULAR = 0
const val TIER_BONUS = 1
const val TIER_CLOSING = 2

/**
 * One link in the chapter-filename parsing chain. Each handler receives the
 * context the previous handler left off at and returns an enriched copy for the
 * next one - never a final ParsedChapter directly, so a later handler (e.g. a
 * future EPUB NCX/nav-title fallback) can still see and adjust anything an
 * earlier one decided.
 */
internal fun interface ChapterParseHandler {
    fun handle(context: ChapterParseContext): ChapterParseContext
}

/**
 * Threaded through the chain below. `filename` is the original, untouched
 * input (kept for the final "title" fallback); `rest` is whatever's left to
 * parse after each handler consumes its own piece.
 */
internal data class ChapterParseContext(
    val filename: String,
    val rest: String,
    val marker: Int = TIER_REGULAR,
    val number: Int? = null,
    val title: String? = null
)

/**
 * Determines a chapter's (number, title, sortTier) from its filename. See
 * bugs.md Bug 2: a leading "~" marks bonus/side content (interlude, extra
 * chapter, side story, omake, ...) and a leading "!" marks closing/meta content
 * (afterword, author's note, ...) - both sort after every regular chapter in
 * their folder, closing always last. Markers are stripped before the title is
 * derived, so "!Author's Afterword.txt" displays as "Author's Afterword", not
 * "!Author's Afterword". An optional number right after the marker (e.g. "~2
 * Side Story.txt") still controls order within that tier.
 */
class ChapterTitleParser {

    companion object {
        // Marker prefixes an author can put directly on a filename to control tier,
        // independent of whatever word they use ("Interlude", "SS", "Omake", "番外",
        // ...) - see bugs.md Bug 2 for the full rationale.
        private const val BONUS_MARKER = '~'
        private const val CLOSING_MARKER = '!'

        // Best-effort fallback for files that predate the marker convention and don't
        // use it - keeps existing libraries from regressing to unordered rather than
        // requiring every author to immediately rename every file. Only consulted when
        // no marker prefix is present; once a file is renamed with ~ / ! that's
        // authoritative and these keywords no longer apply to it.
        private val LEGACY_CLOSING_KEYWORDS = listOf("afterword", "author's note", "authors note")
        private val LEGACY_BONUS_KEYWORDS = listOf("interlude", "side story", "omake", "extra chapter", "bonus chapter")

        // Heuristic: leading number + delimiter, e.g. "026 Not Again" or, once a
        // marker's been stripped, "2 Side Story".
        private val NUMBER_PATTERN = Regex("^\\s*(\\d+)\\s+(.+)$")
    }

    // Strips the ~/! marker prefix (if any) and records which tier it implies.
    private val markerHandler = ChapterParseHandler { context ->
        val (marker, rest) = when (context.rest.firstOrNull()) {
            BONUS_MARKER -> TIER_BONUS to context.rest.drop(1)
            CLOSING_MARKER -> TIER_CLOSING to context.rest.drop(1)
            else -> TIER_REGULAR to context.rest
        }
        context.copy(marker = marker, rest = rest)
    }

    // Pulls a leading "<number> <title>" pair out of what's left, falling back to
    // treating the whole remainder as the title (with _/- normalized to spaces) when
    // there's no leading number.
    private val numberPrefixHandler = ChapterParseHandler { context ->
        val match = NUMBER_PATTERN.matchEntire(context.rest.trim())
        if (match != null) {
            context.copy(
                number = match.groupValues[1].toIntOrNull(),
                title = match.groupValues[2].trim()
            )
        } else {
            context.copy(title = context.rest.replace(Regex("[_-]"), " ").trim())
        }
    }

    // A blank title (e.g. the whole filename was just a marker) falls back to the
    // original, unparsed filename rather than rendering as empty.
    private val blankTitleFallbackHandler = ChapterParseHandler { context ->
        context.copy(title = context.title?.ifBlank { context.filename })
    }

    // No marker on this file - fall back to keyword sniffing on the now-resolved
    // title so libraries that predate the marker convention don't regress to
    // unordered. A marker always wins outright when present.
    private val legacyKeywordTierHandler = ChapterParseHandler { context ->
        if (context.marker != TIER_REGULAR) return@ChapterParseHandler context
        val lower = context.title.orEmpty().lowercase()
        val tier = when {
            LEGACY_CLOSING_KEYWORDS.any { lower.contains(it) } -> TIER_CLOSING
            LEGACY_BONUS_KEYWORDS.any { lower.contains(it) } -> TIER_BONUS
            else -> TIER_REGULAR
        }
        context.copy(marker = tier)
    }

    private val chain = listOf(
        markerHandler,
        numberPrefixHandler,
        blankTitleFallbackHandler,
        legacyKeywordTierHandler
    )

    fun parse(filename: String): ParsedChapter {
        val withoutExt = filename.replaceFirst(Regex("\\.(txt|md)$", RegexOption.IGNORE_CASE), "")
        var context = ChapterParseContext(filename = filename, rest = withoutExt)
        for (handler in chain) {
            context = handler.handle(context)
        }
        return ParsedChapter(
            number = context.number,
            title = context.title ?: filename,
            sortTier = context.marker
        )
    }
}
