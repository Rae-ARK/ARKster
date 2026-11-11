package horizonarkstudio.raeark.arkster.scanner

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * The refactor doc's architecture.md correction already describes today's
 * resolve-once-at-scan-then-read-from-Room behavior as "a caching Proxy in
 * front of the real source (zip bytes, SAF query)" - this class is that
 * description made real and named, following the exact Real
 * Subject/Proxy/shared-interface shape in
 * docs/kotlin-design-patterns/src/main/kotlin/Proxy.kt (RealImage/
 * ProxyImage, both implementing Image; ProxyImage lazily constructs and
 * reuses its RealImage instead of reloading on every display() call).
 *
 * Unlike that example (which caches a whole constructed object), this Proxy
 * caches the *result* of a pure resolution call, keyed by the folder's own
 * URI - CoverResolver.findCoverUri has no state to construct once and reuse,
 * just a SAF `listFiles()` + regex pass worth not repeating for a folder
 * already resolved this process's lifetime. Each novel/arc folder is only
 * ever resolved once per scan pass today (ScannerImpl never calls this twice
 * for the same folder in one pass - see scanRoot's and
 * scanChaptersForNovel's single call sites), so this doesn't change
 * observable behavior yet; it exists so a future second caller (a manual
 * "refresh cover" action, a retry path, or EpubSource's planned
 * cover-extraction-to-cache step reusing this same seam) gets memoization
 * for free instead of silently redoing the SAF work, and so the Proxy named
 * in the design-decisions section is an actual class instead of only a
 * comment pointing at Room.
 *
 * ConcurrentHashMap, not a plain MutableMap, since ScannerImpl (and whatever
 * future caller shares this instance) runs scan work on Dispatchers.IO and
 * nothing here guarantees single-threaded access the way a ViewModel's own
 * state would.
 */

import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.ConcurrentHashMap

class CachingCoverResolverProxy(
    private val real: CoverSource = CoverResolver()
) : CoverSource {

    // ConcurrentHashMap can't store a null value directly, and "no cover found" is a
    // legitimate, cacheable resolution (not an absence of one) - so each entry wraps
    // the resolved String? in a one-field holder instead of using it as the map's
    // value type directly.
    private class CachedResult(val uri: String?)

    private val cache = ConcurrentHashMap<String, CachedResult>()

    override fun findCoverUri(folder: DocumentFile): String? =
        cache.computeIfAbsent(folder.uri.toString()) { CachedResult(real.findCoverUri(folder)) }.uri
}
