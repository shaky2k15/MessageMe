# ADR-001: Repository Pattern for Data Access

**Date:** 2026-05-03
**Status:** Accepted

## Context

The original codebase accessed `TagsDbHelper` and `ContentResolver` directly from Composable functions and `DisposableEffect` blocks. This caused:
- SQLite connections being opened on the Main thread (ANR risk)
- Multiple `TagsDbHelper` instances competing for the same database file
- No single place to add caching, logging, or mocking for tests

## Decision

All data access — both reads (SMS/MMS/contacts) and writes (tags, colors, blocked senders) — must go through `MessageRepository`. No code outside the `data/` package should import `TagsDbHelper` directly.

**Correct pattern:**
```kotlin
// In a ViewModel
val messages = withContext(Dispatchers.IO) { repository.fetchAllMessages() }
```

**Forbidden pattern:**
```kotlin
// ❌ Never do this in a Composable or ViewModel
val db = TagsDbHelper(context)
val tags = db.getAllTagsMap()
```

## Consequences

- All new data operations must be added as methods on `MessageRepository`
- `TagsDbHelper` constructor is private — only `TagsDbHelper.getInstance()` is accessible
- Lint rule `TagsDbHelperDirectAccessDetector` enforces this at compile time
- Unit tests for data logic go in `MessageRepositoryTest`, not UI tests
