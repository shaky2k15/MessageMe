# Future Enhancement & Roadmap

This document serves as a guide for developers looking to take MessageMe to the next level of scalability and security.

## Priority 1: Multi-Module Architecture
- Transition from a single `:app` module to a feature-based modular structure:
  - `:feature:inbox` (SmsInboxScreen, InboxViewModel)
  - `:feature:chat` (ThreadScreen, ThreadViewModel)
  - `:feature:auth` (Identity/Google Auth logic)
  - `:core:data` (MessageRepository, TagsDbHelper)
  - `:core:ui` (Common components like ChatBubble)
- **Benefit**: Faster build times, strict compile-time dependency enforcement, and cleaner separation of concerns.

## Priority 2: Security & Identity
- **Credential Manager**: Replace the custom `Identity.authorize` flow with the unified Android **Credential Manager API** to support passkeys and a smoother biometric authentication experience.
- **Biometric Locking**: Add an option to lock the app or specific "Secret Tags" behind biometric authentication.

## Priority 3: Performance & Scale
- **Paging 3**: Implement paginated loading for the `MessageRepository` to support threads with 10,000+ messages.
- **R8/ProGuard**: Fully enable `isMinifyEnabled = true` and optimize the rules to reduce APK size and obfuscate core business logic.

## Priority 4: Advanced Features
- **Smart Replies**: Integrate local ML (MediaPipe or ML Kit) to suggest quick replies based on conversation context.
- **Scheduled Messages**: Add a "Send Later" feature using `WorkManager` for guaranteed delivery.
- **Rich Media**: Support for sending/receiving video and multiple image attachments.

## Priority 5: Reliability
- **Automated Regression Testing**: Use **Maestro** or **Compose UI Test** for end-to-end (E2E) flows like "Backup to Drive" and "Archive Thread".
- **Error Reporting**: Integrate Firebase Crashlytics to monitor field performance.

---
*Contributions are welcome! Follow the coding patterns defined in `docs/skill_guide.md`.*
