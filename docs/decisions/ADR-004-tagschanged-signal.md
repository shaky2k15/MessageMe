# ADR-004: Cross-ViewModel Metadata Change Signal (SharedFlow)

**Date:** 2026-05-03
**Status:** Accepted

## Context

When a user edits a tag or color on a message in `ThreadScreen` (`ThreadViewModel`), the tag/category drawer in `MainSmsScreen` (`InboxViewModel`) must refresh. These are two separate ViewModel instances with no direct reference to each other.

The `ContentObserver` on `content://mms-sms/conversations` only fires when the **system SMS database** changes. Custom metadata lives in our private `tags.db`, so editing a tag or color produces no system SMS event — the `ContentObserver` never fires and `InboxViewModel.allTags`/`allColors` stay stale.

## Decision

`MessageRepository.companion object` holds a consolidated `MutableSharedFlow<Unit>` named `metadataChanged`:

```kotlin
companion object {
    private val _metadataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val metadataChanged: SharedFlow<Unit> = _metadataChanged.asSharedFlow()
}
```

Every write method that modifies tag or color data emits on this flow:
```kotlin
fun setTagsForMessage(messageId: String, tags: List<String>) {
    db.setTagsForMessage(messageId, tags)
    _metadataChanged.tryEmit(Unit)
}
```

`InboxViewModel` collects this in its `init` block and refreshes the tag and color lists (not the full message list):
```kotlin
viewModelScope.launch {
    MessageRepository.metadataChanged.collect {
        _allTags.value = withContext(Dispatchers.IO) { repository.getAllTags() }
        _allColors.value = withContext(Dispatchers.IO) { repository.getAllColors() }
    }
}
```

## Consequences

- **Any new write method that modifies tag/color data must call `_metadataChanged.tryEmit(Unit)`**
- The pattern extends naturally: if a new screen needs to react to metadata changes, it collects `MessageRepository.metadataChanged`
- The companion object flow is process-scoped (no persistence needed — if the process dies, all ViewModels are recreated anyway)
- `extraBufferCapacity = 1` ensures the emit never drops if no collector is yet subscribed
