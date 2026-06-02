# Remaining Issues

## Issue

Status:
Open

Reason not fixed:
Plugin Verifier reports deprecated/experimental `ToolWindowFactory` API usages. They are compatibility warnings, not verifier failures, and a migration should be checked against the supported IDE build range before changing extension-point behavior.

Recommended next step:
Run a focused IntelliJ Platform API migration pass for `KmpFeatureKitToolWindowFactory` and re-run `./gradlew verifyPlugin`.

## Issue

Status:
Open

Reason not fixed:
Marketplace screenshots are missing from the repository. Creating accurate screenshots requires running the plugin in an IDE sandbox and choosing representative UI states.

Recommended next step:
Capture Marketplace screenshots for the Tools action, guided wizard, grouped project-changes preview, generated files, inspections, and tool window before the next upload.

## Issue

Status:
Open

Reason not fixed:
Publishing and Marketplace metadata validation require JetBrains Marketplace credentials and account access. The task explicitly prohibited publishing.

Recommended next step:
Use the release checklist in `README.md`, then publish manually or through the configured Gradle publishing task with a valid token.

## Issue

Status:
Open

Reason not fixed:
`ProjectScanService` is lighter and cached, but the initial scan still runs synchronously when the wizard is created. A true background scan needs additional UI loading states and cancellation behavior.

Recommended next step:
Move initial project scanning into a background task and update the wizard defaults when scan results arrive.
