# MessageMe

An advanced, power-user SMS/MMS client for Android built with Jetpack Compose and Clean Architecture.

![Coverage Badge](https://img.shields.io/badge/coverage-98%25-blue)

## Features
- **Clean Architecture**: Decoupled Data, Domain, and UI layers.
- **Multi-SIM Support**: Intelligent carrier detection and selection.
- **Dynamic Tagging**: Organize messages with custom labels.
- **Color Categories**: Color-code specific messages and filter by assigned colors using a sleek category grid row in the navigation drawer.
- **Custom Message Signatures**: Add custom signatures to your outgoing texts with persistent toggleable configuration in settings and a character limit of 60 characters.
- **Modern Adaptive Launcher Branding**: Displays a futuristic neon coral/warm gold gradient speech bubble containing an abstract "M" that adapts automatically to circular, square, or squircle frames.
- **Automated GitHub Releases**: Pipeline integration for automatically packing and publishing APK releases directly on Git tag pushes.
- **Spam Protection**: Parallel shadow database for blocking and archiving.
- **Cloud Backup**: Automated Google Drive backup in standard XML format.
- **Metrics Dashboard**: Visual analytics of your messaging habits.

## Compatibility

- **Minimum Requirement**: Android 11.0 (API Level 30) or higher.
- **Optimized For**: Android 16 (API Level 36) - Latest 2026 Release.
- **Device Support**: Fully compatible with modern high-end devices like the Samsung S24/S25/S26 series, including support for Edge-to-Edge displays and the latest Notification Permission models.

## Architecture Overview
This project follows a modern, reactive Android architecture:
- **UI Layer**: Jetpack Compose using `AndroidViewModel` for lifecycle-aware state management.
- **Domain Layer**: Repository pattern using `MessageRepository` as the single source of truth.
- **Data Layer**: Direct SMS/MMS ContentProvider access + a private Singleton SQLite database (`TagsDbHelper`) for metadata.
- **Authentication**: Migrated to modern `Identity.getAuthorizationClient` for Google Drive access.

Refer to our [Architecture Decisions (ADRs)](docs/decisions/) for detailed technical rationale.

## Governance & Standards
To ensure scalability and maintain code quality, the following tools are integrated into the build:
- **Custom Lint Rules**: Prevents architectural violations like direct DB access from the UI.
- **Detekt**: Static analysis for code smells and complexity.
- **CI/CD**: GitHub Actions pipeline enforcing build, lint, and test success.
- **PR Template**: Mandatory checklist for architectural compliance.

## Developer Guide

### 1. Verification & Quality
Run the following commands to ensure your changes meet the project standards:
```bash
./gradlew lint        # Checks custom architectural rules
./gradlew detekt      # Runs static analysis
./gradlew test        # Runs unit tests
```

### 2. Code Coverage
To view the current code coverage report:
1. Run the following command in the terminal:
   ```bash
   ./gradlew testDebugUnitTest jacocoTestReport
   ```
2. Open the report in your browser:
   ```bash
   open app/build/reports/jacoco/jacocoTestReport/html/index.html
   ```

### 3. Agentic Automation
This project is built and maintained with **Antigravity**. Refer to the [Skill Guide](docs/skill_guide.md) for the prompts and architectural patterns used to automate this project's evolution.

## Best Practices
Refer to [docs/best_practices.md](docs/best_practices.md) for a step-by-step guide on how this project was built and how to maintain high standards.

## Contributors
- **[shaky2k15](https://github.com/shaky2k15)** - Lead Developer & Product Vision
- **Antigravity** - Agentic AI Coding Assistant

## License
MIT
