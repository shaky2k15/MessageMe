# Implementation History & User Requirements

This document tracks the evolution of the MessageMe app from a basic SMS client to its current state.

## Phase 1: Foundation & Cloud Integration
- **Requirement:** Enable Google Drive backups.
- **Implementation:** Integrated OAuth 2.0 and Drive REST API.
- **Requirement:** Backups must be interoperable with offline tools.
- **Implementation:** Built a custom XML export engine that injects metadata into standard SMS schema, compatible with "SMS Backup & Restore".

## Phase 2: Metadata & Management
- **Requirement:** Add tags to messages and filter by tags.
- **Implementation:** Created `TagsDbHelper` (SQLite) to store per-message tags. Implemented `TagFilterScreen`.
- **Requirement:** Blocking, Spam reporting, and Archiving.
- **Implementation:** Added blocklist and archive tables. Created a dedicated "Spam & Blocked" folder.
- **Requirement:** Metrics Dashboard.
- **Implementation:** Built a `WebView`-based dashboard generating dynamic HTML with message/tag statistics.

## Phase 3: Personalization & UI Polish
- **Requirement:** Color-code messages.
- **Implementation:** Added color picker dialog; persisted hex codes in SQLite.
- **Requirement:** Inbox should reflect custom colors.
- **Implementation:** Updated `InboxItem` cards to dynamically inherit the custom color of the latest message in the thread.
- **Requirement:** Improve Sidebar.
- **Implementation:** Replaced the simple menu with a `ModalNavigationDrawer` (30% width) featuring dynamic tag categories.

## Phase 4: Communications & Multi-SIM
- **Requirement:** Enhanced "New Message" compose panel.
- **Implementation:** Added native Contact Picker button and styled input fields with light grey backgrounds and no borders.
- **Requirement:** Multi-SIM support.
- **Implementation:** Integrated `SubscriptionManager` to detect SIM cards and added a SIM selection toggle near the Send button.
- **Requirement:** Rename "Send SMS" to "Send".
- **Implementation:** Updated button text and added a send icon.

## Phase 5: Architectural Modernization & Governance (May 2026)
- **Requirement:** Resolve high-risk legacy Auth and UI-thread dependencies.
- **Implementation:** 
    - Migrated UI state management from monolithic `MainActivity` to lifecycle-aware `InboxViewModel` and `ThreadViewModel`.
    - Introduced `MessageRepository` as the single source of truth, offloading all database and ContentResolver I/O to `Dispatchers.IO`.
    - Implemented a thread-safe Singleton pattern for `TagsDbHelper` to prevent connection leaks.
    - Successfully replaced deprecated `GoogleSignIn` with modern `Identity.getAuthorizationClient` for Google Drive backup authentication.
- **Requirement:** Ensure future architectural scalability and prevent regression.
- **Implementation:**
    - Established a **Governance Framework** including Custom Android Lint rules to block direct DB access from the UI.
    - Integrated **Detekt** for static analysis and **GitHub Actions** for CI/CD gates.
    - Documented all major technical shifts via **Architecture Decision Records (ADRs)**.

## Project Origin
- **User:** shaky2k15
- **Developer AI:** Antigravity (Google DeepMind)
