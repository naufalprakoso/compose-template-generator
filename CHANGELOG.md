# Changelog

## 0.1.1

- Adds safe auto-registration for Koin `modules(...)` calls when the composition root is recognized.
- Adds Kotlin Inject `@Component` exposure when the component is recognized.
- Adds Hilt module inclusion for recognized aggregate `@Module` files and generates Android-only Hilt modules.
- Adds safe auto-registration for Navigation Compose `NavHost` graphs when the graph shape is recognized.
- Adds registry-list registration for Voyager, Circuit, Decompose, and Appyx projects that keep route entries in named lists.
- Adds Gradle dependency insertion for known version-catalog aliases.
- Adds generated-source compile smoke coverage for every architecture template.
- Replaces the deprecated project root lookup used by project scanning.
- Keeps TODO patch files as the fallback when an integration target cannot be updated safely.

## 0.1.0

- Initial Marketplace build.
- Adds the Compose feature wizard, dry-run preview, source-set detection, and file generation.
- Adds KMP inspections and quick fixes for Android-only common code and missing `actual` implementations.
- Adds settings, project health tool window, and Gradle publishing support.
