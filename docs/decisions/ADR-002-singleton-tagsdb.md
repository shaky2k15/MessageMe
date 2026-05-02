# ADR-002: TagsDbHelper as a Singleton

**Date:** 2026-05-03
**Status:** Accepted

## Context

`TagsDbHelper` extends `SQLiteOpenHelper`. Every `SQLiteOpenHelper` instance opens a new database connection. The original code called `TagsDbHelper(context)` dozens of times across composable functions, creating a new connection on every recomposition. This causes:
- SQLite `SQLITE_BUSY` lock contention warnings in Logcat
- ~5ms overhead per connection open on older devices
- Unpredictable behavior when two instances write simultaneously

## Decision

`TagsDbHelper` has a **private constructor** and exposes a single `getInstance(context)` method using a thread-safe double-checked locking singleton:

```kotlin
companion object {
    @Volatile private var instance: TagsDbHelper? = null

    fun getInstance(context: Context): TagsDbHelper =
        instance ?: synchronized(this) {
            instance ?: TagsDbHelper(context.applicationContext).also { instance = it }
        }
}
```

The `applicationContext` (not `Activity` context) is used to prevent memory leaks.

## Consequences

- There is exactly one `SQLiteOpenHelper` per process — one connection pool
- `TagsDbHelper(context)` no longer compiles (private constructor)
- The singleton is cleared automatically when the process dies (no manual cleanup needed)
- All access goes through `MessageRepository`, which holds the singleton reference via `private val db get() = TagsDbHelper.getInstance(context)`
