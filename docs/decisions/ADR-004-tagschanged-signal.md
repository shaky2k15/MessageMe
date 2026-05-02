# ADR-004: Cross-ViewModel Tag Change Signal (SharedFlow)

**Date:** 2026-05-03
**Status:** Accepted

## Context

When a user edits a tag on a message in `ThreadScreen` (`ThreadViewModel`), the tag drawer in `MainSmsScreen` (`InboxViewModel`) must refresh. These are two separate ViewModel instances with no direct reference to each other.

The `ContentObserver` on `content://mms-sms/conversations` only fires when the **system SMS database** changes. Tag data lives in our private `tags.db`, so editing a tag produces no system SMS event — the `ContentObserver` never fires and `InboxViewModel.allTags` stays stale.

## Decision

`MessageRepository.companion object` holds a `MutableSharedFlow<Unit>`:

```kotlin
companion object {
    private val _tagsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tagsChanged: SharedFlow<Unit> = _tagsChanged.asSharedFlow()
}
```

Every write method that modifies tag data emits on this flow:
```kotlin
fun setTagsForMessage(messageId: String, tags: List<String>) {
    db.setTagsForMessage(messageId, tags)
    _tagsChanged.tryEmit(Unit)
}
```

`InboxViewModel` collects this in its `init` block and refreshes only `allTags` (not the full message list):
```kotlin
viewModelScope.launch {
    MessageRepository.tagsChanged.collect {
        _allTags.value = withContext(Dispatchers.IO) { repository.getAllTags() }
    }
}
```

## Consequences

- **Any new write method that modifies tag/color data must call `_tagsChanged.tryEmit(Unit)`**
- The pattern extends naturally: if a new screen needs to react to tag changes, it collects `MessageRepository.tagsChanged`
- The companion object flow is process-scoped (no persistence needed — if the process dies, all ViewModels are recreated anyway)
- `extraBufferCapacity = 1` ensures the emit never drops if no collector is yet subscribed
