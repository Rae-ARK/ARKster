package com.arkster.app.data

// Thrown by AppDatabase.create() instead of Room's own IllegalStateException when the
// unhandled version Room hit is a *downgrade* (the running app's migration list only
// goes up to some version lower than what's already on disk) - e.g. an older APK
// sideloaded over data a newer one already wrote. Kept as an IllegalStateException
// subtype so anything already catching IllegalStateException around
// AppDatabase.create() still catches it; MainActivity.onCreate catches this subtype
// first to show a plain message instead of a stack trace.
//
// Lives in its own file on purpose: Kotlin binds an annotation to the very next
// declaration, so declaring this between @Database and AppDatabase would make Room
// try to treat it as the database class.
class DatabaseDowngradeException(message: String) : IllegalStateException(message)
