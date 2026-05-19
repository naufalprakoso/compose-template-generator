# Compose Template Generator

Compose Template Generator is an IntelliJ IDEA / Android Studio plugin for Kotlin Multiplatform and Compose Multiplatform teams. It provides architecture-aware feature generation, source-set detection, KMP guardrails, inspections, quick fixes, settings, and a project health tool window.

## Highlights

- New feature actions from `Tools > Compose Template Generator > New Compose Feature` and `New > Compose Feature`.
- Multi-step wizard with feature info, architecture ecosystem, feature options, and dry-run preview.
- Generation support for MVVM, MVI, Slack Circuit, Decompose, Simple Feature, and Clean Architecture.
- Source-set-aware output for `commonMain`, `commonTest`, `androidMain`, and `iosMain`.
- DI planning for Koin, Kotlin Inject, Hilt Android-only, and Manual DI.
- Navigation planning for Navigation Compose, Voyager, Circuit Navigation, Decompose Navigation, and Appyx.
- Inspections for Android-only common code, missing actuals, missing previews/tests/DI/navigation, source-set imports, and architecture smells.
- Settings under `Settings > Tools > Compose Template Generator`.

## Run

```bash
./gradlew runIde
```

## Test

```bash
./gradlew test
./gradlew build
./gradlew verifyPlugin
```

The project intentionally does not configure publishing.
