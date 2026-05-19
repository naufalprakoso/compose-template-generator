# Architecture

The plugin is organized around stable services and small model types.

- `actions/` registers IDE entry points.
- `dialogs/` and `wizard/` contain the generation UI.
- `generator/` builds feature plans and writes files.
- `templates/` renders reusable Kotlin/documentation templates.
- `sourceSet/` detects KMP source sets and platform folders.
- `di/` and `navigation/` produce safe registration plans.
- `inspections/` and `quickfix/` implement guardrails.
- `toolwindow/` shows project health and source-set/module inventory.
- `settings/` stores configurable defaults.
- `utils/` contains pure helpers.
- `testing/` contains fixture-oriented test support.

The main orchestration path is:

`FeatureWizardModel -> FeatureGenerationService.preview() -> DryRunPreview -> FeatureGenerationService.generate() -> FileWriteService`

Generation is data-driven by `ArchitectureType`, `FeatureOptions`, and `ProjectScanResult`. Template rendering is intentionally independent from IntelliJ PSI so it can be tested cheaply.
