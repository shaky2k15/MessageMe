# Best Practices for Agentic Android Development

This guide outlines the best practices followed during the modernization of **MessageMe**, specifically designed for high-scalability and agentic maintainability.

## 1. Architectural Patterns
- **Repository Pattern**: All data access (Telephony, Contacts, Metadata) must be abstracted by `MessageRepository`. Never access the database or content resolver directly from a Composable or ViewModel.
- **ViewModel State Management**: Screens must observe read-only `StateFlow`s from an `AndroidViewModel`. Use `viewModelScope` to ensure I/O operations are cancelled when the screen is closed.
- **Thread-Safe Singletons**: Shared resources like `SQLiteOpenHelper` (`TagsDbHelper`) must be singletons to prevent connection leaks and database lock contention.
- **Reactive Cross-Screen Signals**: Use `SharedFlow` for signaling events between unrelated ViewModels (e.g., refreshing the inbox when a tag is updated in a chat thread). See [ADR-004](decisions/ADR-004-tagschanged-signal.md).

## 2. Governance & Enforcement
- **Lint as Architect**: Use custom Android Lint rules to enforce package boundaries. If a UI class tries to access a Data class it shouldn't, the build must fail.
- **Static Analysis (Detekt)**: Enforce strict limits on file size, function complexity, and forbidden API calls (e.g., blocking deprecated Google Sign-In).
- **Mandatory CI Gates**: Never merge a PR without a green build, successful linting, and 95%+ code coverage.

## 3. Testing & Coverage
- **Robolectric for Framework Testing**: Use Robolectric for JVM-based tests requiring `ContentResolver` or `Context`.
- **Mocking with MockK**: Isolate logic by mocking system services like `SmsManager`.
- **JaCoCo for Metrics**: Use **JaCoCo** for accurate coverage reporting. Aim for 98%+ coverage on all `data` and `ui/viewmodel` layers.


## 4. Agentic Workflow Steps
1.  **Architecture Decision Records (ADRs)**: Always document the *why* behind a pattern change before implementing it. This provides the AI and future developers with a source of truth.
2.  **Planning Mode**: For complex migrations (like Auth or threading), use Planning Mode to generate a technical contract before writing code.
3.  **Governance Audits**: Periodically ask the AI to run a "Scalability Audit" to identify new technical debt or deprecated API usage.

## 5. UI/UX Best Practices
- **Material3 Design System**: Use standard Material3 tokens for a premium, native feel.
- **Thread-Safe UI**: Ensure no I/O is performed in `LaunchedEffect` without an explicit `withContext(Dispatchers.IO)` wrapper.
