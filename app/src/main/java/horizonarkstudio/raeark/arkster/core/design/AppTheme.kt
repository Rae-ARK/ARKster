package horizonarkstudio.raeark.arkster.core.design

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

// Lifted from ARKarium's ui/theme/AppTheme.kt (itself extracted from MainActivity.kt
// as part of that project's MainActivity slim-down) and adapted for ARKster: same
// calibration approach and structure, but with a different brand seed - see below.
// Pure color-scheme lookup - no Compose state, no Context - so it's usable on its own
// without pulling in the Activity.

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import horizonarkstudio.raeark.arkster.core.datastore.Theme

// Light and Dark below are calibrated with Google's own Material Color Utilities
// algorithm (the HCT color space + SchemeTonalSpot - the same math lightColorScheme()/
// darkColorScheme()'s *own* Material-You baseline is built from), run against a real
// seed color instead of relying on those two functions' stock seed, which is a fixed
// violet (#6750A4). Every role is derived from that seed rather than hand-picked, so
// nothing here clashes or silently falls back to that stock violet for a role nobody
// got around to overriding.
//
// ARKarium's version of this file seeds Light/Dark from a deep teal. ARKster is a
// different app with a different audience - a private, local "your own Royal Road"
// shelf rather than ARKarium's author-to-reader sync client - so this isn't reusing
// that teal identity verbatim. The seed here is a warm gold (#71601B), picked to stay
// clear of every hue already doing a job elsewhere in this app's palette: not green
// (an earlier pass through this file tried that and it read too close to a second
// "nature" identity for a reading-shelf app), not Material's stock purple (the thing
// this whole pass exists to get away from), not the muted sepia-brown warm-paper mode
// already owns, not the blue tertiary already carries over from ARKarium, and not the
// brick-red error color. Tertiary keeps that same blue accent (it was never the
// teal/purple/green problem this reseed is about), and every other role is re-derived
// from the new gold seed the same way ARKarium derives its own roles from teal.
fun lightColorSchemeApp() = lightColorScheme(
    primary = Color(0xFF71601B),
    onPrimary = Color(0xFFFFFCEA),
    primaryContainer = Color(0xFFF7E9A9),
    onPrimaryContainer = Color(0xFF634D00),
    secondary = Color(0xFF67634B),
    onSecondary = Color(0xFFFFFCEA),
    secondaryContainer = Color(0xFFEBE6CD),
    onSecondaryContainer = Color(0xFF59543D),
    tertiary = Color(0xFF406280),
    onTertiary = Color(0xFFF6F9FF),
    tertiaryContainer = Color(0xFFB8DBFE),
    onTertiaryContainer = Color(0xFF2B4E6B),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF36342A),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF36342A),
    surfaceVariant = Color(0xFFE6E4DA),
    onSurfaceVariant = Color(0xFF626057),
    outline = Color(0xFF7E7B72),
    outlineVariant = Color(0xFFB5B2A9),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF100F0A),
    inverseOnSurface = Color(0xFF9E9C99),
    inversePrimary = Color(0xFFFDEEAE),
    surfaceTint = Color(0xFF71601B)
)

fun darkColorSchemeApp() = darkColorScheme(
    primary = Color(0xFFD6CC9C),
    onPrimary = Color(0xFF4D400D),
    primaryContainer = Color(0xFF605526),
    onPrimaryContainer = Color(0xFFF3E8B8),
    secondary = Color(0xFFCFCAB1),
    onSecondary = Color(0xFF47422C),
    secondaryContainer = Color(0xFF433F27),
    onSecondaryContainer = Color(0xFFC8C3AA),
    tertiary = Color(0xFFD3E8FF),
    onTertiary = Color(0xFF345674),
    tertiaryContainer = Color(0xFFB8DBFE),
    onTertiaryContainer = Color(0xFF2B4E6B),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    background = Color(0xFF100F0A),
    onBackground = Color(0xFFE9E7DD),
    surface = Color(0xFF100F0A),
    onSurface = Color(0xFFE9E7DD),
    surfaceVariant = Color(0xFF29281E),
    onSurfaceVariant = Color(0xFFAEABA2),
    outline = Color(0xFF79776D),
    outlineVariant = Color(0xFF4B4940),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFFAF9F6),
    inverseOnSurface = Color(0xFF575651),
    inversePrimary = Color(0xFF6D6334),
    surfaceTint = Color(0xFFD6CC9C)
)

// Warm, sepia-toned reading theme - lower contrast than pure light/dark, meant to be
// easier on the eyes for long reading sessions (a common e-reader "paper" mode).
// Carried over from ARKarium verbatim: it's a muted amber/brown palette, nowhere near
// green, purple, or the brighter gold Light/Dark use above, so there's no substitution
// to make here - just the same fix. ARKster's previous warm-paper scheme (formerly
// inline in MainActivity.kt) only overrode primary/onPrimary/primaryContainer/
// onPrimaryContainer/background/onBackground/surface/onSurface/surfaceVariant/
// onSurfaceVariant; secondary/tertiary/outline/outlineVariant were left to
// lightColorScheme()'s stock violet defaults, which clashed with the rest of this
// sepia palette - the exact bug ARKarium's own comment on this function describes and
// fixes. This version carries that fix over.
fun warmPaperColorScheme() = lightColorScheme(
    primary = Color(0xFF845425),
    onPrimary = Color(0xFFFFF7F4),
    primaryContainer = Color(0xFFFDBC83),
    onPrimaryContainer = Color(0xFF62380A),
    secondary = Color(0xFF745A44),
    onSecondary = Color(0xFFFFF7F4),
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF664D37),
    tertiary = Color(0xFF63612B),
    onTertiary = Color(0xFFFFFBCC),
    tertiaryContainer = Color(0xFFFEFAB4),
    onTertiaryContainer = Color(0xFF625F29),
    error = Color(0xFFA73B21),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFD795A),
    onErrorContainer = Color(0xFF6E1400),
    background = Color(0xFFF5ECD9),
    onBackground = Color(0xFF3E2C1C),
    surface = Color(0xFFF0E4CB),
    onSurface = Color(0xFF3E2C1C),
    surfaceVariant = Color(0xFFE6D7B8),
    onSurfaceVariant = Color(0xFF4E3B26),
    outline = Color(0xFF736A53),
    outlineVariant = Color(0xFFD0C5AF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF130D09),
    inverseOnSurface = Color(0xFFA69B93),
    inversePrimary = Color(0xFFBD8551),
    surfaceTint = Color(0xFF845425)
)

// ARKster's Theme enum since gained a SYSTEM_DEFAULT case (see PreferencesManager.kt
// and MainActivity.resolveTheme) - this file predates that and the comment above used
// to say there was no such case to handle here. There's still no palette of System
// Default's own: resolveTheme() always substitutes a concrete Light/Dark theme before
// a caller needs an actual color scheme, so this branch is never actually reached.
// Kept as an exhaustive branch (falling back to the same lightColorSchemeApp() the
// LIGHT case uses) rather than `else`, so a future Theme case fails to compile here
// instead of silently falling through - same reasoning as MainActivity's own
// colorSchemeFor.
fun colorSchemeFor(theme: Theme) = when (theme) {
    Theme.LIGHT -> lightColorSchemeApp()
    Theme.DARK -> darkColorSchemeApp()
    Theme.WARM_PAPER -> warmPaperColorScheme()
    Theme.SYSTEM_DEFAULT -> lightColorSchemeApp()
}
