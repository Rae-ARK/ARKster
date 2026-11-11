package horizonarkstudio.raeark.arkster.feature.settings

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

// One heading + body pair rendered by LegalDocumentScreen. Kept as plain data
// (no markdown/HTML) since the content below is short and static - a real
// markup renderer would be overkill for a handful of paragraphs. `icon` is
// purely cosmetic (drives the icon badge next to the heading in the card
// renderer) and defaults to a generic doc glyph so adding a section without
// picking an icon still compiles and still looks intentional.
//
// `tldr` is new: a single plain-language sentence rendered above `body` in
// the card (see LegalDocumentScreen's LegalSectionCard) - the "say it like a
// human, then say it properly" pattern real privacy-summary redesigns use so
// a reader gets the gist without parsing formal phrasing first. It is purely
// a reading aid and carries no legal weight of its own: `body` remains the
// actual policy/terms text and is what governs. Defaults to "" (renders
// nothing extra) so this is optional per section.
//
// `icon`/`tldr` and the card/quick-facts presentation in LegalDocumentScreen
// are lifted from ARKarium's equivalent files - same reading-aid idea, same
// data shape. The actual policy/terms text below is untouched: it's scoped
// to what ARKster's code does (see MainActivity/ScannerImpl/
// GoogleBooksMetadataProvider/PreferencesManager), which is a genuinely
// different, narrower thing than what ARKarium's own policy describes (a
// local-only reader with one opt-in metadata lookup, versus ARKarium's
// direct author-to-reader sync client) - the two apps' content is not
// interchangeable even though the presentation now is.
data class LegalSection(
    val heading: String,
    val body: String,
    val icon: ImageVector = Icons.Filled.Description,
    val tldr: String = ""
)

// Content here is deliberately scoped to what ARKster's code actually does
// (see MainActivity/ScannerImpl/GoogleBooksMetadataProvider/PreferencesManager)
// rather than generic boilerplate - e.g. the only network call this app ever
// makes is the user-triggered Google Books metadata lookup, so that's the only
// thing called out under "Network access" below. Keep this file in sync if
// that behavior changes (a new network call, a new local data store, etc.).
object LegalContent {

    // Document-level "quick facts" rendered as a row of chips above the
    // section cards (see LegalDocumentScreen) - the tl;dr of the whole
    // document in four words-or-fewer each, the same job the good/bad icon
    // summary does on sites like ToS;DR, just inline instead of linking out
    // to one. Purely a reading aid; the section cards below remain the
    // actual policy.
    val privacyQuickFacts = listOf(
        "No accounts or sign-in",
        "No ads or analytics",
        "Opt-in metadata lookup only",
        "Everything stays on your device"
    )

    val termsQuickFacts = listOf(
        "Open source, GPLv3",
        "No purchases or subscriptions",
        "Provided as-is",
        "Actively evolving"
    )

    val privacyPolicy = listOf(
        LegalSection(
            "Overview",
            "ARKster is an offline-first, privacy-first app. There are no user " +
                "accounts, no sign-in, no analytics, no crash-reporting services, and " +
                "no ads. Nothing about your usage is collected or transmitted unless " +
                "this policy says otherwise below.",
            icon = Icons.Filled.PrivacyTip,
            tldr = "No accounts, no ads, no analytics - this app isn't watching you."
        ),
        LegalSection(
            "Your library folder",
            "ARKster reads the single folder you choose via Android's Storage " +
                "Access Framework (SAF). It reads chapter files to index and display " +
                "them; it never copies, uploads, or otherwise sends the contents of " +
                "your library anywhere. All indexing happens entirely on your device.",
            icon = Icons.Filled.Folder,
            tldr = "ARKster reads your folder to show your novels - it never sends your files anywhere."
        ),
        LegalSection(
            "Data stored on your device",
            "Novel, arc, chapter, and author details discovered while scanning are " +
                "stored locally in an on-device database, along with your reading " +
                "progress and app preferences (theme, selected library folder, and " +
                "similar settings). None of this leaves your device as part of normal " +
                "app use.",
            icon = Icons.Filled.Storage,
            tldr = "Everything it remembers - your library, progress, settings - stays in a database on your phone."
        ),
        LegalSection(
            "Network access (opt-in, per novel)",
            "The only network request ARKster ever makes is triggered by you, " +
                "manually, when you tap \"Fetch info\" on a novel. That sends the " +
                "novel's title to the Google Books API to look up a matching cover, " +
                "description, and genre tags. Nothing else about your library or " +
                "device is sent with that request, and it only happens when you ask " +
                "for it. Google's handling of that request is governed by their own " +
                "privacy policy, not this one.",
            icon = Icons.Filled.Wifi,
            tldr = "Tap \"Fetch info\" and it asks Google Books for a cover and blurb - only if you ask it to."
        ),
        LegalSection(
            "Crash reports",
            "If ARKster crashes, a trace is saved locally on your device so it can " +
                "be shown to you on the next launch to help with debugging. This " +
                "trace stays on your device and is never sent anywhere automatically.",
            icon = Icons.Filled.BugReport,
            tldr = "Crash traces stay on your device, shown to you on the next launch - never sent anywhere."
        ),
        LegalSection(
            "Third parties",
            "ARKster does not sell, share, or otherwise disclose your data to third " +
                "parties, because outside of the opt-in metadata lookup above, it " +
                "does not collect any data to share in the first place.",
            icon = Icons.Filled.Groups,
            tldr = "Nothing to share - outside that one opt-in lookup, nothing is collected in the first place."
        ),
        LegalSection(
            "Changes to this policy",
            "ARKster is open-source and under active development. This policy may " +
                "be updated alongside the app; the project's GitHub repository is " +
                "the source of truth for the current version.",
            icon = Icons.Filled.Update,
            tldr = "ARKster is still evolving - the GitHub repo is always the source of truth."
        )
    )

    val termsAndConditions = listOf(
        LegalSection(
            "Acceptance of terms",
            "By installing or using ARKster, you agree to these terms. If you don't " +
                "agree with them, please don't use the app.",
            icon = Icons.Filled.Gavel,
            tldr = "Using ARKster means you agree to the terms below."
        ),
        LegalSection(
            "What ARKster is",
            "ARKster is a local library organizer and reader for novel files (txt/md, " +
                "with more formats planned) that already exist on your device. It does " +
                "not provide, host, distribute, or endorse any written content itself - " +
                "it only indexes and displays files from the folder you point it at.",
            icon = Icons.Filled.Info,
            tldr = "A local library organizer and reader - it only shows files already on your device."
        ),
        LegalSection(
            "Your responsibility for content",
            "You are solely responsible for the legality of the files you choose to " +
                "store on your device and open with ARKster. The app doesn't inspect, " +
                "moderate, or take any position on the source or legality of your files.",
            icon = Icons.Filled.MenuBook,
            tldr = "Whatever you put in your folder is on you - ARKster doesn't check it."
        ),
        LegalSection(
            "License",
            "ARKster is licensed under the GNU General Public License v3.0 (GPLv3). " +
                "The full license text ships with the project's source and is available " +
                "on the GitHub repository.",
            icon = Icons.Filled.Description,
            tldr = "Free and open-source under the GPLv3 - the source ships with the project."
        ),
        LegalSection(
            "No warranty",
            "ARKster is provided \"as is\", without warranty of any kind, express or " +
                "implied, to the maximum extent permitted by applicable law - consistent " +
                "with the GPLv3 license it's distributed under. Use it at your own risk.",
            icon = Icons.Filled.VerifiedUser,
            tldr = "Provided as-is - use it at your own risk, like most open-source software."
        ),
        LegalSection(
            "No accounts, no purchases",
            "ARKster has no accounts, subscriptions, or in-app purchases. There is " +
                "nothing to manage beyond the app's own local settings.",
            icon = Icons.Filled.Block,
            tldr = "Nothing to buy, nothing to log into - just local settings."
        ),
        LegalSection(
            "Changes to the app",
            "ARKster is actively developed and features, screens, and behavior may " +
                "change between versions. The project's GitHub repository tracks the " +
                "current state and history of those changes.",
            icon = Icons.Filled.Update,
            tldr = "Still actively developed - features and screens can change between versions."
        )
    )
}
