# Changelog

## 0.1.1

- Adds safe auto-registration for Koin `modules(...)` calls when the composition root is recognized.
- Adds safe auto-registration for Navigation Compose `NavHost` graphs when the graph shape is recognized.
- Adds Gradle dependency insertion for known version-catalog aliases.
- Replaces the deprecated project root lookup used by project scanning.
- Keeps TODO patch files as the fallback when an integration target cannot be updated safely.

## 0.1.0

- Initial Marketplace build.
- Adds the Compose feature wizard, dry-run preview, source-set detection, and file generation.
- Adds KMP inspections and quick fixes for Android-only common code and missing `actual` implementations.
- Adds settings, project health tool window, and Gradle publishing support.
