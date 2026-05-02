# ADR-003: AndroidViewModel for All Screen State

**Date:** 2026-05-03
**Status:** Accepted

## Context

The original `MainSmsScreen` and `ThreadScreen` stored all state in `remember { mutableStateOf(...) }`. This means:
- On device rotation, all messages are re-fetched from scratch (visible flash)
- On Android 14+, background apps are killed aggressively — state is lost when user returns
- `ContentObserver` registered in `DisposableEffect` leaks if the composable leaves composition during a slow recomposition

## Decision

All persistent screen state lives in `AndroidViewModel` subclasses:
- `InboxViewModel` — inbox messages, blocked senders, archived threads, tag list
- `ThreadViewModel` — messages for the open thread

`AndroidViewModel` (not `ViewModel`) is used because these classes need `Application` context to create `MessageRepository` and register `ContentObserver`.

**Correct pattern:**
```kotlin
val inboxViewModel: InboxViewModel = viewModel()
val messages by inboxViewModel.messages.collectAsState()
```

**Forbidden pattern:**
```kotlin
// ❌ State that survives rotation should not be in remember
var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
```

`ContentObserver` registration lives in `ViewModel.init` and cleanup in `ViewModel.onCleared()` — not in `DisposableEffect`.

## Consequences

- Rotation no longer re-fetches data
- Process death awareness is correct (ViewModel is recreated, triggers `loadData()` fresh from DB)
- A new screen that needs data from `MessageRepository` must get its own ViewModel
- Ephemeral UI state (e.g. dialog open/closed, text field input) may still use `remember {}`
