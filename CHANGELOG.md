# Changelog

## 0.1.3

- Fixes generated file writes so the IntelliJ write command runs on the EDT.
- Avoids read-access assertions seen on newer Android Studio builds when write actions are triggered from worker threads.

## 0.1.2

- Adds a guided wizard layout with target, architecture, files, and preview sections.
- Adds scaffold presets and working generated file toggles for previews, README files, tests, and optional artifacts.
- Groups project-change previews into conflicts, modify existing, manual review, and create new.
- Adds module/source-root selection and debounced preview refresh.
- Makes `ProjectStyle` and `stateHolderType` affect generated paths, packages, files, and DI registration.
- Adds a generated Compose preview template and a plain state-holder template.
- Improves post-generate notifications with written, skipped, and warning summaries.
- Uses command-grouped file writes through the IntelliJ Platform write-command API.
- Adds plugin icons and improves local Android Studio path configuration for build/verifier tasks.

## 0.1.1

- Adds safe auto-registration for Koin `modules(...)` calls when the composition root is recognized.
- Adds Kotlin Inject module inheritance for recognized `@Component` declarations.
- Adds Hilt module inclusion for recognized aggregate `@Module` files and generates Android-only Hilt modules.
- Adds safe auto-registration for Navigation Compose `NavHost` graphs when the graph shape is recognized.
- Adds registry-list registration for Voyager, Circuit, Decompose, and Appyx projects that keep route entries in named lists.
- Adds Gradle dependency insertion and version-catalog alias patch previews for known dependencies.
- Adds generated-source compile smoke coverage for every architecture template.
- Adds Gradle sample build coverage for generated KMP sources.
- Adds clearer preview warnings for existing file updates and TODO fallback patches.
- Adds a local-only privacy policy and removes the unused telemetry setting.
- Adds a support matrix and CI workflow for plugin test/build checks.
- Replaces the deprecated project root lookup used by project scanning.
- Keeps TODO patch files as the fallback when an integration target cannot be updated safely.

## 0.1.0

- Initial Marketplace build.
- Adds the Compose feature wizard, dry-run preview, source-set detection, and file generation.
- Adds KMP inspections and quick fixes for Android-only common code and missing `actual` implementations.
- Adds settings, project health tool window, and Gradle publishing support.
