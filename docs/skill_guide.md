# MessageMe Skill Guide for AI Agents

This document provides context and "skills" for AI coding assistants to manage and extend the MessageMe project.

## Project Essence
MessageMe is a feature-rich Android SMS/MMS client focused on power-user features: metadata tagging, parallel persistence (SQLite), cloud backup (Google Drive), and multi-SIM management.

## Core Architecture Knowledge
- **Main Logic:** Contained almost entirely in `MainActivity.kt` (for now).
- **Data Persistence:**
  - **System Provider:** Standard SMS/MMS storage.
  - **Private DB (`tags.db`):** Managed by `TagsDbHelper`. Stores tags, blocklists, archives, and per-message colors.
- **State Management:** Uses Jetpack Compose state hoisting in `SmsAppContent`.

## Available Skills (Standard Patterns)

### 1. Extending Message Metadata
To add a new attribute (e.g., "Priority" or "Reminder"):
1.  Update `ChatMessage` data class.
2.  Update `TagsDbHelper` to create a new table/column.
3.  Implement CRUD methods in `TagsDbHelper`.
4.  Update `MainSmsScreen` or `ThreadScreen` to display/edit the new metadata.

### 2. Modifying Cloud Backup
The backup engine uses `Google Drive REST API`.
- **Interoperability:** Ensure custom metadata is serialized as XML attributes (e.g., `tags="work,urgent"`) to maintain compatibility with "SMS Backup & Restore".

### 3. Handling Multi-SIM
- Use `SubscriptionManager` to retrieve active subscriptions.
- Use `SmsManager.getSmsManagerForSubscriptionId(subId)` for outgoing messages.
- Permission required: `READ_PHONE_STATE`.

## Common Pitfalls
- **Permissions:** Always check for `READ_SMS`, `SEND_SMS`, `READ_CONTACTS`, and `READ_PHONE_STATE`.
- **Default App Status:** Many features only work if MessageMe is the **Default SMS App**.
- **Real-time Updates:** Use `ContentObserver` on `content://mms-sms/conversations` to keep the UI in sync with the system database.

## Future Refactoring Target
The project is currently monolithic. Future agents should prioritize moving UI components and data layers into separate files/modules.
