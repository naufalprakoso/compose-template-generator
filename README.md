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
- Can register generated Koin modules, Kotlin Inject modules, Hilt modules, and supported navigation entries when the existing project file has a recognizable shape.
- Supports Navigation Compose plus registry-list wiring for Voyager, Circuit, Decompose, and Appyx.
- Can add dependency lines to `commonMain.dependencies` and patch existing `gradle/libs.versions.toml` catalogs when generated dependencies need aliases.
- Runs generated sample sources through a temporary Gradle KMP project in the test suite.

The generated Gradle, DI, and navigation patches are intentionally conservative. When the plugin cannot safely edit an integration point, it creates a TODO-style patch instead of changing project wiring silently.

## Supported auto-wiring

- Koin: adds generated feature modules to recognized `modules(...)` and `modules(listOf(...))` calls.
- Kotlin Inject: adds the generated `FeatureInjectModule` to recognized `@Component` supertypes.
- Hilt: adds generated modules to recognized aggregate `@Module(includes = [...])` declarations when the project keeps one.
- Navigation Compose: inserts generated routes into recognized `NavHost` blocks.
- Voyager, Circuit, Decompose, and Appyx: appends generated navigation graph entries to recognized registry lists.
- Gradle Kotlin DSL: adds dependency aliases to `commonMain.dependencies`; if the version catalog exists and the alias is missing, the catalog patch is previewed as a second file update.

See [docs/support-matrix.md](docs/support-matrix.md) for the exact supported project shapes and fallbacks.

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

- JDK 17
- Android Studio installed locally. The default path is `/Applications/Android Studio.app`; override it with `-PandroidStudioPath="/path/to/Android Studio.app"` or `ANDROID_STUDIO_PATH`.

Run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

Use a custom Android Studio path when needed:

```bash
./gradlew -PandroidStudioPath="/Applications/Android Studio.app" runIde
```

Run tests:

```bash
./gradlew test
```

The repository also includes a generated Kotlin Multiplatform sample fixture:

```bash
./gradlew -p samples/kmp-build-fixture compileKotlinJvm
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

## Usage

1. Open a Kotlin Multiplatform / Compose project in Android Studio or IntelliJ IDEA.
2. Run `Tools > Compose Template Generator > New Compose Feature`, or use `New > Compose Feature`.
3. Enter a feature name, base package, target module, and module `src` directory.
4. Review the generated files and integration patches in the preview.
5. Leave unsafe or unfamiliar project changes unchecked and wire them manually from the generated TODO patches.

Feature names must normalize to valid Kotlin identifiers. Source-set roots must be absolute paths to a module `src` directory, for example `/path/to/project/shared/src`.

## Supported project structures

The generator targets conventional KMP layouts such as:

```text
shared/
  build.gradle.kts
  src/
    commonMain/kotlin
    commonTest/kotlin
    androidMain/kotlin
    iosMain/kotlin
```

It can create missing feature package folders inside those source sets. Auto-wiring is intentionally limited to recognized DI, navigation, and Gradle shapes; unsupported shapes receive TODO patch files.

## Troubleshooting

- If the wizard cannot infer a useful source root, select a file under `src/commonMain/kotlin`, `src/androidMain/kotlin`, or another KMP source set before opening the action.
- If generated dependency patches are shown as TODOs, check that `commonMain.dependencies` and `gradle/libs.versions.toml` follow the supported shapes in [docs/support-matrix.md](docs/support-matrix.md).
- If no files are written, inspect the notification for skipped existing files. Existing create targets are skipped by default to avoid overwriting user code.
- If `verifyPlugin` fails locally, confirm `-PandroidStudioPath` or `ANDROID_STUDIO_PATH` points to a valid Android Studio installation.

## Release checklist

- Run `./gradlew clean build`.
- Run `./gradlew verifyPlugin`.
- Run `./gradlew buildPlugin`.
- Run `./gradlew -p samples/kmp-build-fixture compileKotlinJvm`.
- Update [CHANGELOG.md](CHANGELOG.md).
- Refresh Marketplace screenshots and copy in [docs/marketplace-description.md](docs/marketplace-description.md).
- Upload or publish the ZIP only after manual review of the generated artifact.

## Release notes

See [CHANGELOG.md](CHANGELOG.md).

## Privacy

The plugin does not collect telemetry or send project data outside the IDE. See [docs/privacy.md](docs/privacy.md).

## License

Apache-2.0. See [LICENSE](LICENSE).
