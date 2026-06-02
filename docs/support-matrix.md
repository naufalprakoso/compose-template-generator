# Support Matrix

Compose Template Generator is conservative by design: it edits only project files with recognizable structure and writes TODO patches for everything else.

| Area | Supported in 0.1.2 | Fallback |
| --- | --- | --- |
| Koin | `modules(...)` and `modules(listOf(...))` composition roots | TODO registration file |
| Kotlin Inject | `@Component` declarations that can accept a generated module supertype | TODO registration file |
| Hilt | Aggregate `@Module` declarations with optional `includes` | TODO registration file |
| Navigation Compose | `NavHost(...) { ... }` blocks | TODO registration file |
| Voyager | Named route registries such as `voyagerScreens` or `screens` | TODO registration file |
| Circuit | Named registries such as `circuitScreens` or `screenBindings` | TODO registration file |
| Decompose | Named registries such as `decomposeConfigs` or `childConfigs` | TODO registration file |
| Appyx | Named registries such as `appyxNodes` or `nodes` | TODO registration file |
| Gradle Kotlin DSL | `commonMain.dependencies`, `androidMain`, top-level KSP dependency blocks, and `libs.versions.toml` library aliases | Gradle TODO patch |

The plugin currently prioritizes Kotlin DSL projects. Groovy Gradle projects receive conservative patch blocks rather than deep structural edits.
