# Features

## Generator

Compose Template Generator generates cohesive feature slices rather than isolated files. The generator adapts names, structure, dependencies, navigation, DI, platform targets, and tests from a single request.

Supported architecture ecosystems:

- MVVM
- MVI
- Slack Circuit
- Decompose
- Simple Feature
- Clean Architecture

Supported integration families:

- Koin, Kotlin Inject, Hilt Android-only, Manual DI
- Navigation Compose, Voyager, Circuit Navigation, Decompose Navigation, Appyx
- Ktor, Apollo GraphQL, Retrofit Android-only
- SQLDelight, Room Android-only, DataStore

## Safety

- Dry-run preview before generation.
- File conflict detection.
- No silent overwrite.
- Risky DI/navigation insertions are downgraded to TODO patch files.
- Telemetry is disabled by default.

## Guardrails

- Android-only APIs in `commonMain`.
- Missing `actual` implementation.
- Duplicate or mismatched platform actuals.
- Missing DI/navigation registration.
- Missing previews and tests.
- Source-set import violations.
- Suspicious architecture placement.
