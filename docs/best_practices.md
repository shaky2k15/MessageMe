# Best Practices for Agentic Android Development

This guide outlines the best practices followed during the development of **MessageMe**, which can be applied to any similar project requiring high test coverage and robust architecture.

## 1. Architectural Patterns
- **Decouple Business Logic from UI**: Avoid putting logic (database queries, network calls, data parsing) inside the `Activity` or `Fragment`. Use the **Repository Pattern** and **Engine Pattern** (pure Kotlin classes) to make code testable.
- **Shadow Database for Metadata**: When working with system providers (like SMS), maintain a separate SQLite database for application-specific metadata (tags, colors, custom flags) to ensure interoperability.
- **Dependency Injection**: Use `remember` and `LaunchedEffect` in Compose to inject repositories, or use Hilt/Dagger for larger projects.

## 2. Testing & Coverage
- **Robolectric for Framework Testing**: Use Robolectric to run tests that require Android components (Context, ContentResolver) on the JVM without needing an emulator.
- **Mocking with MockK**: Use MockK to isolate the unit under test. Mock system services like `SmsManager` or `SubscriptionManager`.
- **Kover for Metrics**: Use `kotlinx-kover` for accurate coverage reporting. Aim for 95%+ coverage on all `data` and `domain` layers.

## 3. Agentic Workflow Steps
1.  **Feature Discovery**: Define clear requirements and research Android version-specific limitations early.
2.  **Implementation Planning**: Always create an `implementation_plan.md` first. This prevents "coding into a corner" and identifies architectural bottlenecks.
3.  **Iterative Refactoring**: Don't be afraid to refactor a monolithic file early. It's easier to refactor at the start of a feature than at the end.
4.  **Automated Verification**: Build CI/CD pipelines early to verify that new changes don't break existing coverage.

## 4. UI/UX Best Practices
- **Material3 Design System**: Use standard Material3 components and color schemes for a premium, native feel.
- **Permission Handling**: Always use a clean, user-friendly permission request flow before accessing sensitive data like SMS or Contacts.
- **Responsive Layouts**: Use `LocalConfiguration` to adjust UI components (like Drawer width) based on screen size.
