# Marketplace Description Draft

Compose Template Generator helps Kotlin Multiplatform teams create Compose feature scaffolds from inside IntelliJ IDEA or Android Studio.

Use the New Compose Feature action to choose a feature name, package, target source sets, architecture style, navigation option, DI style, and optional data layers. The plugin previews every file before writing and avoids overwriting existing files by default.

It supports common KMP and Compose patterns including MVVM, MVI, Slack Circuit, Decompose, simple features, and Clean Architecture. It can also detect common libraries such as Koin, Kotlin Inject, Voyager, Decompose, Circuit, Ktor, Apollo, SQLDelight, and Room.

When project files match common patterns, the plugin can register generated Koin modules, expose Kotlin Inject dependencies, include generated Hilt modules, add Navigation Compose routes to a `NavHost`, add registry-list entries for Voyager/Circuit/Decompose/Appyx, and insert dependency aliases into `commonMain.dependencies`. If the target file is not safe to edit, it creates a TODO patch instead of changing the project automatically.

The plugin also includes inspections and quick fixes for common Kotlin Multiplatform issues, including Android-only APIs in `commonMain` and missing `actual` implementations.
