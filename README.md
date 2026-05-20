# Compose Template Generator

Compose Template Generator is an IntelliJ IDEA and Android Studio plugin for generating Kotlin Multiplatform / Compose feature scaffolds.

The plugin is meant for projects that already have a preferred app structure but still spend time creating the same screen, state, repository, navigation, DI, preview, and test files by hand.

## What it does

- Adds `Tools > Compose Template Generator > New Compose Feature`.
- Creates feature files for MVVM, MVI, Slack Circuit, Decompose, simple features, and Clean Architecture.
- Writes source-set-aware files for `commonMain`, `commonTest`, `androidMain`, and `iosMain`.
- Detects common project libraries such as Koin, Kotlin Inject, Voyager, Decompose, Circuit, Ktor, Apollo, SQLDelight, and Room.
- Shows a dry-run preview before writing files and skips existing files by default.
- Adds inspections and quick fixes for common KMP mistakes, including Android-only APIs in `commonMain` and missing `actual` implementations.
- Can register generated Koin modules and Navigation Compose routes when the existing project file has a recognizable shape.
- Can add dependency lines to `commonMain.dependencies` when matching aliases exist in `gradle/libs.versions.toml`.

The generated Gradle, DI, and navigation patches are intentionally conservative. When the plugin cannot safely edit an integration point, it creates a TODO-style patch instead of changing project wiring silently.

## Project layout

- `src/main/kotlin/com/kmpfeaturekit/actions` - IDE entry points.
- `dialogs` - the feature wizard and default detection.
- `generator` - preview and file write flow.
- `templates` - generated Kotlin and documentation templates.
- `sourceSet`, `di`, and `navigation` - project scanning and integration planning.
- `inspections` and `quickfix` - KMP source-set checks and fixes.
- `toolwindow` and `settings` - project health view and plugin settings.

## Development

Prerequisites:

- JDK 21
- Android Studio installed at `/Applications/Android Studio.app`

Run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

Run tests:

```bash
./gradlew test
```

Build a Marketplace ZIP:

```bash
./gradlew buildPlugin
```

Verify compatibility:

```bash
./gradlew verifyPlugin
```

The ZIP is written to `build/distributions/`.

## Release notes

See [CHANGELOG.md](CHANGELOG.md).

## License

Apache-2.0. See [LICENSE](LICENSE).
