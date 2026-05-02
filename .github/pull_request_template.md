## Description
<!-- What does this PR do? Why? -->

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / tech debt
- [ ] Dependency update
- [ ] Documentation

---

## Architecture Checklist

### Data Layer
- [ ] No `TagsDbHelper(context)` or `TagsDbHelper.getInstance()` called outside `data/` package
- [ ] No `MessageRepository(context)` created inside a `@Composable` — use `viewModel()` instead
- [ ] All `contentResolver.query()`, `readableDatabase`, `writableDatabase` calls are wrapped in `withContext(Dispatchers.IO)` or run from a ViewModel on `Dispatchers.IO`

### State Management
- [ ] New UI state lives in an `AndroidViewModel`, not in `remember { mutableStateOf(...) }`
- [ ] StateFlows are exposed as read-only `StateFlow` (not `MutableStateFlow`) from the ViewModel

### Signals & Reactivity
- [ ] If tags were written, `MessageRepository._tagsChanged.tryEmit(Unit)` is called (see `MessageRepository.setTagsForMessage` for example)
- [ ] If a new cross-ViewModel signal was added, it is documented in `docs/decisions/ADR-004-tagschanged-signal.md`

### Android Compatibility
- [ ] Any new Android API usage was checked against `minSdk = 30` (Android 11)
- [ ] No `SmsManager.getDefault()` used without an `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)` guard
- [ ] No `GoogleSignIn` / `GoogleSignInAccount` — use `Identity.getAuthorizationClient` (see ADR-005)

---

## Testing Checklist
- [ ] New `MessageRepository` public methods have a unit test in `MessageRepositoryTest`
- [ ] New ViewModel state transitions have a unit test
- [ ] `./gradlew testDebugUnitTest` passes locally

---

## Reviewer Notes
<!-- Anything the reviewer should pay extra attention to? -->
