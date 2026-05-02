# MessageMe — Agentic Skill Guide

This guide documents the specific agentic workflows and prompt engineering skills used by the Engineering Manager to guide Antigravity through the MessageMe modernization.

## 1. Architectural Reasoning & Planning
Instead of asking for code fixes, we used **Planning Mode** to establish a technical contract.
- **Pattern**: "Run a scalability analysis and categorize risks into P1 (breaks soon) and P2 (architecture debt)."
- **Outcome**: Identified the deprecated Google Sign-In and Main-thread SQLite leaks before they caused production issues.

## 2. Governance through Automation
We moved beyond "telling" developers what to do and started "enforcing" via code.
- **Skill**: Building custom Android Lint detectors using AI.
- **Pattern**: "Write a Lint detector that flags any direct access to TagsDbHelper outside the data package."
- **Outcome**: Automated code reviews for the most common architectural violation in the project.

## 3. Handling API Deprecations (Auth & identity)
Migrating auth flows is high-risk. We used Antigravity to map the old legacy classes to modern Identity equivalents.
- **Skill**: Cross-library mapping.
- **Pattern**: "Replace GoogleSignInClient with Identity.getAuthorizationClient and migrate the backupToDrive logic to use the raw accessToken."
- **Outcome**: Successfully modernized the Google Drive engine without breaking the existing OAuth client configuration.

## 4. Reactive UI Patterns
Moving from `remember {}` to ViewModels required a holistic view of the application lifecycle.
- **Skill**: StateFlow orchestration.
- **Pattern**: "Centralize all screen state in ViewModels and use a companion-object SharedFlow to signal tag updates across screens."
- **Outcome**: Achieved visual parity with the legacy app while gaining rotation-safe state and reactive cross-screen updates.

---

## Skills Reusable for Future Projects
1. **ADR-First Development**: Always generate an Architecture Decision Record (ADR) before implementing a major pattern change.
2. **Lint-as-Review**: Encode architectural boundaries into Lint rules to reduce PR review cycles.
3. **Dispatcher Enforcement**: Explicitly audit all ContentResolver calls for `withContext(Dispatchers.IO)`.
