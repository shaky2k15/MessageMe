# Future Enhancement & Roadmap

This document serves as a guide for developers looking to take MessageMe to the next level.

## Priority 1: Modularization
- Split `MainActivity.kt` into features:
  - `:feature:inbox`
  - `:feature:chat`
  - `:feature:backup`
  - `:feature:metrics`
  - `:core:database`

## Priority 2: Performance
- **Paging 3:** Implement paginated loading for the Inbox and Chat threads to support thousands of messages without UI lag.
- **Background Sync:** Use `WorkManager` for scheduled Google Drive backups.

## Priority 3: Advanced Features
- **Smart Replies:** Integrate local ML (MediaPipe or ML Kit) to suggest quick replies.
- **Scheduled Messages:** Add a "Send Later" feature using `AlarmManager`.
- **Search Enhancements:** Add filtering in search (e.g., search within a specific tag or date range).
- **Material You:** Fully implement dynamic color support based on the user's wallpaper.

## Priority 4: Reliability
- **Unit Tests:** Add tests for `TagsDbHelper` and the XML Backup engine.
- **Error Reporting:** Integrate Firebase Crashlytics to monitor field performance.

---
*Contributions are welcome! Follow the coding patterns defined in `docs/skill_guide.md`.*
