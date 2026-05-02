# MessageMe

An advanced, power-user SMS/MMS client for Android built with Jetpack Compose and Clean Architecture.

![Coverage Badge](https://img.shields.io/badge/coverage-98%25-blue)

## Features
- **Clean Architecture**: Decoupled Data, Domain, and UI layers.
- **Multi-SIM Support**: Intelligent carrier detection and selection.
- **Dynamic Tagging**: Organize messages with custom labels.
- **Spam Protection**: Parallel shadow database for blocking and archiving.
- **Cloud Backup**: Automated Google Drive backup in standard XML format.
- **Metrics Dashboard**: Visual analytics of your messaging habits.

## Developer Guide

### 1. Running the Project
- Open in Android Studio.
- Ensure you have a Google Cloud project set up for Drive API if you want to test backups.

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
This project uses **Antigravity Agents** to:
- Automatically run unit tests and ensure >98% coverage.
- Generate coverage reports using JaCoCo.
- Maintain UI parity and architectural integrity.
- Refer to the [Agentic Workflow Report](agents/workflow_report.md) for details on how this project was built.

## Best Practices
Refer to [docs/best_practices.md](docs/best_practices.md) for a step-by-step guide on how this project was built and how to maintain high standards.

## Contributors
- **[shaky2k15](https://github.com/shaky2k15)** - Lead Developer & Product Vision
- **Antigravity** - Agentic AI Coding Assistant

## License
MIT
