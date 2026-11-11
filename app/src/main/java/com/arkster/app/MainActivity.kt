package com.arkster.app

/*
 * STAGE 3 MOVE -- 2026-11-11
 * This file is the actual Stage 3 deliverable: the refactor doc's target
 * package layout always described MainActivity.kt ending up "thin: NavHost
 * + splash gate, nothing else" (docs/arkster/implementation/
 * oop-modular-monolith-refactor.md -- scaffolding, not a permanent
 * fixture: see that doc's own header on what to do once it's gone).
 *
 * Everything that used to live here moved out:
 * - `Screen` (sealed class) -> core/navigation/Routes.kt, as route templates
 *   instead of entity-carrying objects (see that file's header for why).
 * - `MetadataSearchState` -> feature/noveldetail/NovelDetailViewModel.kt,
 *   since it was only ever driven by, and only ever shown over, that one
 *   screen.
 * - novels/chapters/arcs/recentlyRead/inProgressNovels/scanProgress/
 *   scanMessage and startScan() -> core/data/LibrarySession.kt (an
 *   Application-scoped singleton, not a ViewModel - see its header for why).
 * - Every other per-screen field and function (loadNovelDetails,
 *   loadAuthorPage, openChapter, saveChapterEdits, saveReadingProgress,
 *   fetchMetadataFor, applyMetadata) -> the matching feature/*/*ViewModel.kt.
 * - The inline `when (currentScreen.value)` block -> core/navigation/AppNavHost.kt.
 *
 * What's left is exactly what the target layout promised: the crash-screen
 * safety net (unchanged from Stage 2 and earlier - nothing about *that*
 * logic needed to change for this stage), the splash gate (not a navigable
 * destination, so it was never a Screen/Route case to begin with), and a
 * call into AppNavHost. scanner/prefsManager/contentRepo below are still
 * referenced here only for the Stage 2 crash-guard's first-access trick and
 * the theme/reader-prefs Flows the root composable reads directly - nothing
 * here owns their data anymore the way the old lateinit vars effectively did.
 *
 * STAGE 6 MOVE -- 2026-11-11: `db` dropped from that list - it's private on
 * App now, and this class's own `db get() = app.db` delegate is gone with
 * it. See the crash-guard block's own STAGE 6 MOVE comment for what forces
 * AppDatabase.create() in its place.
 */

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.arkster.app.core.datastore.Theme
import com.arkster.app.core.navigation.AppNavHost
import com.arkster.app.feature.splash.SplashScreen
import kotlinx.coroutines.launch

// Warm, sepia-toned reading theme - lower contrast than pure light/dark, meant to
// be easier on the eyes for long reading sessions (a common e-reader "paper" mode).
//
// NOTE: primaryContainer/onPrimaryContainer are explicitly set here too. Every other
// role above overrides lightColorScheme()'s default, but these two were left out -
// so anything using them (e.g. HomeScreen's "Browse All Novels" card) fell back to
// Material3's stock light-purple/dark-purple pair, which clashes with this warm/sepia
// palette and is what made that button look inconsistent with the rest of the theme.
private fun warmPaperColorScheme() = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFBF5),
    primaryContainer = Color(0xFFD9BE97),
    onPrimaryContainer = Color(0xFF3E2C1C),
    background = Color(0xFFF5ECD9),
    onBackground = Color(0xFF3E2C1C),
    surface = Color(0xFFF0E4CB),
    onSurface = Color(0xFF3E2C1C),
    surfaceVariant = Color(0xFFE6D7B8),
    onSurfaceVariant = Color(0xFF4E3B26)
)

private fun colorSchemeFor(theme: Theme) = when (theme) {
    Theme.LIGHT -> lightColorScheme()
    Theme.DARK -> darkColorScheme()
    Theme.WARM_PAPER -> warmPaperColorScheme()
    // Never actually reached - resolveTheme() below always substitutes SYSTEM_DEFAULT
    // for a concrete Light/Dark theme before colorSchemeFor is called. Kept as an
    // exhaustive branch (falls back to the same lightColorScheme() Theme.LIGHT uses)
    // rather than `else`, so adding a future Theme case fails to compile here instead
    // of silently falling through.
    Theme.SYSTEM_DEFAULT -> lightColorScheme()
}

// Resolves "follow system theme" down to a concrete Light/Dark theme based on the
// device's own day/night setting. System Default has no palette of its own - it
// borrows Dark at night and Light during the day - so every caller that needs an
// actual color scheme or reading-mode palette (colorSchemeFor above, ReaderScreen's
// appTheme) resolves through this first rather than switching on SYSTEM_DEFAULT
// itself. Pulled out as a plain, Context-free function (same reasoning as the
// sibling ARKarium reader's own resolveTheme) so it's usable outside a Composable
// and easy to unit test later even though this module has no test source set yet.
internal fun resolveTheme(theme: Theme, systemInDarkTheme: Boolean): Theme =
    if (theme == Theme.SYSTEM_DEFAULT) {
        if (systemInDarkTheme) Theme.DARK else Theme.LIGHT
    } else {
        theme
    }

class MainActivity : ComponentActivity() {

    // STAGE 2 MOVE -- 2026-11-11, still true after Stage 3: these five live on App
    // (Application), not here - see App.kt. `get()` so the crash-guard block in
    // onCreate below can still force first construction with a bare statement.
    //
    // STAGE 6 MOVE -- 2026-11-11: `db` itself is private on App as of this stage (see
    // App.kt), so this class can no longer hold a `get() = app.db` delegate at all -
    // removed outright rather than reworded, since nothing in this file used `db` for
    // anything other than the onCreate crash-guard statement below, which now forces
    // `novelRepository` instead (see that statement's own updated comment).
    private val app: App get() = application as App
    private val scanner get() = app.scanner
    private val prefsManager get() = app.prefsManager
    private val contentRepo get() = app.contentRepo

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { selectedUri ->
            contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // STAGE 3 MOVE -- 2026-11-11: this used to call MainActivity's own
            // startScan(), moved to LibrarySession.scan() (see that class's header).
            // Calling the singleton directly here rather than through whichever
            // screen's ViewModel happens to be alive, since picking a folder for the
            // first time can only happen from Home or Settings and both already read
            // this same shared instance - there's no ViewModel this naturally belongs
            // to that isn't just forwarding to here anyway.
            lifecycleScope.launch {
                prefsManager.setLibraryUri(selectedUri.toString())
                app.librarySession.scan(selectedUri)
            }
        }
    }

    // Shared fallback UI for anything that throws before/while the real screen renders.
    // Pulled out so BOTH the service-construction guard, the setContent guard, and the
    // saved-crash check below can show the same "here's exactly what broke" screen
    // instead of a silent process death - a stack trace on-screen beats needing
    // adb/logcat to debug a phone-only crash report.
    private fun renderCrashScreen(title: String, details: String) {
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    // Same BuildConfig.VERSION_NAME used in Settings' About line -
                    // worth having on a crash report screen specifically, since "what
                    // version were they on" is the first thing a bug report needs.
                    Text(
                        "ARKster v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                        item {
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    private fun renderCrashScreen(e: Throwable) {
        renderCrashScreen(
            "ARKster failed to start",
            (e::class.java.name + ": " + e.message) + "\n\n" +
                e.stackTrace.take(30).joinToString("\n") { "  at $it" }
        )
    }

    companion object {
        private const val CRASH_PREFS = "arkster_crash_info"
        private const val CRASH_KEY = "last_crash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety net of last resort. The try/catch blocks below only catch exceptions
        // thrown synchronously on THIS call stack - they cannot catch anything thrown
        // inside a lifecycleScope.launch { } coroutine body, since those run on their
        // own dispatcher and throw well after the launch{} call that started them has
        // already returned. An uncaught exception there crashes the process instantly
        // with nothing on screen and nothing catchable here - which is exactly what
        // "opens and immediately exits, no error screen" looks like. Installing a
        // global handler is the only way to observe it: it can't stop the crash (the
        // process still has to die), but it persists the trace first so the NEXT
        // launch can show it.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                getSharedPreferences(CRASH_PREFS, MODE_PRIVATE).edit()
                    .putString(
                        CRASH_KEY,
                        "ARKster v${BuildConfig.VERSION_NAME}\n\n" +
                            (throwable::class.java.name + ": " + throwable.message) + "\n\n" +
                            throwable.stackTrace.take(30).joinToString("\n") { "  at $it" }
                    )
                    .commit() // commit(), not apply() - must be on disk before the process dies
            } catch (_: Throwable) {
                // Never let the crash handler itself throw and mask the original crash.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        // If the previous launch crashed, show that trace now instead of trying to
        // start normally again (which would likely just crash the same way a second
        // time with nothing new learned).
        val crashPrefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val lastCrash = crashPrefs.getString(CRASH_KEY, null)
        if (lastCrash != null) {
            crashPrefs.edit().remove(CRASH_KEY).apply()
            renderCrashScreen("ARKster crashed last time it opened", lastCrash)
            return
        }

        // AppDatabase.create() (and the other service objects below) are the very first
        // things onCreate does, before setContent ever runs - see App.kt for why these
        // are `by lazy` there rather than constructed eagerly in Application.onCreate().
        // Referencing each one as a bare statement is deliberate: it's what forces the
        // lazy's first construction to happen right here, inside this try/catch,
        // instead of wherever the first real use further down happens to be.
        //
        // STAGE 6 MOVE -- 2026-11-11: `db` is private on App now, so it can't be
        // referenced from here directly any more - `novelRepository` takes its place.
        // novelRepository's own `by lazy { NovelRepository(db.novelDao()) }` still
        // reaches `db` first thing, so forcing novelRepository's construction here
        // forces AppDatabase.create() exactly as early as the bare `db` statement
        // used to, preserving the crash-guard behavior this block exists for.
        try {
            app.novelRepository
            scanner
            prefsManager
            contentRepo
        } catch (e: Throwable) {
            renderCrashScreen(e)
            return
        }

        try {
            renderMainContent()
        } catch (e: Throwable) {
            renderCrashScreen(e)
        }
    }

    private fun renderMainContent() {
        setContent {
            // Read once per composition and reused everywhere a resolved (non-
            // SYSTEM_DEFAULT) theme is needed below - the color scheme here and
            // ReaderScreen's appTheme param (passed down through AppNavHost) - so
            // both agree on the same light/dark resolution within a single frame.
            val systemInDarkTheme = isSystemInDarkTheme()
            val theme by prefsManager.theme.collectAsState(initial = Theme.LIGHT)
            val resolvedTheme = resolveTheme(theme, systemInDarkTheme)
            val colorScheme = colorSchemeFor(resolvedTheme)

            MaterialTheme(colorScheme = colorScheme) {
                val view = LocalView.current
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    // Splash always renders on its own solid-black canvas (see
                    // SplashScreen.kt) regardless of the selected reader theme, so force
                    // light/white status bar icons here rather than deriving them from
                    // colorScheme.background like the real content below does, and paint
                    // the status bar itself black to match that canvas.
                    SideEffect {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                        window.statusBarColor = Color.Black.toArgb()
                    }
                    SplashScreen(onFinished = { showSplash = false })
                    return@MaterialTheme
                }

                // The manifest's android:theme is a fixed system light theme, so its
                // statusBarColor default never tracks Light/Dark/Warm Paper - painting it
                // with the resolved background on every theme change makes the status bar
                // read as part of the app's surface instead of a leftover system chrome strip.
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        colorScheme.background.luminance() > 0.5f
                    window.statusBarColor = colorScheme.background.toArgb()
                }

                // Surface (not a plain Column + .background()) is required here: it's
                // what sets LocalContentColor for everything composed inside it, which a
                // plain .background() modifier does not - see git history on this file
                // for the Dark-theme-unreadable-text bug that caused.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background,
                    contentColor = colorScheme.onBackground
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val readerFontSize by prefsManager.readerFontSize.collectAsState(initial = 18f)
                        val readerLineHeight by prefsManager.readerLineHeight.collectAsState(initial = 1.8f)

                        AppNavHost(
                            app = app,
                            resolvedTheme = resolvedTheme,
                            readerFontSize = readerFontSize,
                            readerLineHeight = readerLineHeight,
                            onReaderFontSizeChange = { size ->
                                lifecycleScope.launch { prefsManager.setReaderFontSize(size) }
                            },
                            onReaderLineHeightChange = { height ->
                                lifecycleScope.launch { prefsManager.setReaderLineHeight(height) }
                            },
                            onPickFolder = { pickFolder.launch(null) }
                        )
                    }
                }
            }
        }
    }
}
