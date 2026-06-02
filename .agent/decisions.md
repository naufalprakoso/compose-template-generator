# Decisions

## Decision 1

Problem:
Feature names, package names, target modules, source-set roots, and platform selection were not all validated before preview/generation.

Decision:
Centralize stricter validation in `ValidationUtils`, call it from the dialog, and enforce it again in `FeatureGenerationService.preview`.

Reason:
The dialog should prevent invalid requests, but generation services should also reject unsafe programmatic calls. This blocks invalid Kotlin identifiers, Kotlin package keywords, blank target modules, blank/relative source roots, non-`src` roots, and empty platform selections before file paths are planned.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/utils/ValidationUtils.kt`, `src/main/kotlin/com/kmpfeaturekit/dialogs/KmpFeatureWizardDialog.kt`, `src/main/kotlin/com/kmpfeaturekit/generator/FeatureGenerationService.kt`, `src/test/kotlin/com/kmpfeaturekit/utils/NameVariantsTest.kt`

Risk:
Low. Some previously accepted invalid inputs are now rejected intentionally.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew clean build`

## Decision 2

Problem:
Source-root detection and plan building used Unix-specific string assumptions.

Decision:
Normalize backslashes in source-root detection and use `Path` to derive generated roots and module root in `FeaturePlanBuilder`.

Reason:
The plugin should handle common Windows and mixed-separator paths in tests and avoid brittle `removeSuffix("/src")` module-root derivation.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/dialogs/FeatureDialogDefaults.kt`, `src/main/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilder.kt`, `src/test/kotlin/com/kmpfeaturekit/dialogs/FeatureDialogDefaultsResolverTest.kt`

Risk:
Low. IntelliJ `VirtualFile.path` usually uses `/`, and existing Unix behavior is preserved.

Verification:
`./gradlew test`, `./gradlew check`

## Decision 3

Problem:
The action used a modal info dialog and was not explicitly hidden when no project was available.

Decision:
Make the action a `DumbAwareAction`, hide/disable it without a project, report results through plugin notifications, show warnings/errors, and open the first written file.

Reason:
Notifications fit IDE workflows better than a modal count-only dialog, and opening the first generated file gives immediate feedback without changing generation semantics.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/actions/NewKmpFeatureAction.kt`, `src/main/kotlin/com/kmpfeaturekit/notifications/KmpFeatureKitNotifier.kt`

Risk:
Low to medium. The action now uses `DumbAwareAction`; the flow uses VFS/file APIs and does not depend on indexes for the guarded action entry point.

Verification:
`./gradlew test`, `./gradlew buildPlugin`, `./gradlew verifyPlugin`

## Decision 4

Problem:
Project scanning recursively read all small Gradle/Kotlin files under the project root, including build and IDE metadata directories.

Decision:
Skip common generated/metadata directories and cap synchronous scanned characters.

Reason:
This preserves lightweight library detection while reducing UI-freeze risk when the wizard or tool window scans large projects.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/services/ProjectScanService.kt`

Risk:
Low. Detection may ignore generated files under skipped directories, which is desirable for project-default inference.

Verification:
`./gradlew test`, `./gradlew verifyPlugin`

## Decision 5

Problem:
CI and README did not fully match the current verification expectations.

Decision:
Add `verifyPlugin` to the GitHub workflow, align README prerequisite with the JDK 17 Gradle toolchain, and add usage, troubleshooting, supported-structure, and release-checklist sections.

Reason:
Marketplace readiness improves when compatibility verification is part of CI and release steps are documented.

Files changed:
`.github/workflows/build.yml`, `README.md`

Risk:
Medium for CI only because `verifyPlugin` depends on the configured Android Studio installation path existing on the runner.

Verification:
Local `./gradlew verifyPlugin` passed against `/Applications/Android Studio.app`.

## Decision 6

Problem:
The wizard kept the selected change inline, allowed confusing horizontal behavior, and warnings were plain text instead of a readable wrapped list.

Decision:
Remove the inline selected-change panel, show selected change content in a popup dialog when a preview item is clicked, mark selected preview items with `[selected]`, make the main dialog refuse horizontal scrolling, and render warnings as wrapped bullet-list labels.

Reason:
The preview list remains compact, detailed content is available on demand, and long warnings remain readable without requiring horizontal scroll.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/dialogs/KmpFeatureWizardDialog.kt`

Risk:
Low. This changes dialog presentation but not file generation semantics.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew buildPlugin`, `./gradlew verifyPlugin`

## Decision 7

Problem:
Fallback integration files used `.todo.kt`, even though they may contain manual instructions or diff previews that should not be compiled as Kotlin.

Decision:
Generate fallback integration notes as `.todo.md` and update warning detection/tests accordingly.

Reason:
Manual integration notes should not be treated as production Kotlin source files. Markdown keeps the generated guidance visible while avoiding invalid source files.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilder.kt`, `src/main/kotlin/com/kmpfeaturekit/generator/FeatureGenerationService.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilderTest.kt`

Risk:
Low. Users still receive the manual patch guidance, but under a safer file extension.

Verification:
`./gradlew test`, `./gradlew check`

## Decision 8

Problem:
Generated service/repository wiring was reversed: `DefaultService` depended on `Repository`, while the expected layering is repository using a service/data source and use case using repository.

Decision:
Change generated templates to use `UseCase -> Repository -> Service`, and update Koin, Kotlin Inject, Hilt, and manual DI templates to match.

Reason:
This better matches common Android/KMP architecture and avoids a confusing data-layer dependency direction.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/templates/FeatureTemplates.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilderTest.kt`

Risk:
Medium. Generated constructor signatures changed, but the generated sample compile tests validate the supported template stacks.

Verification:
`./gradlew test`, `./gradlew -p samples/kmp-build-fixture compileKotlinJvm`

## Decision 9

Problem:
The generator wizard exposed a long flat form, had no presets or file-level control, refreshed immediately on every keystroke, and preview semantics were not grouped by user intent.

Decision:
Refactor the wizard into titled Target, Architecture, Files, and Preview sections; add scaffold presets; add file toggles for the generated artifacts that are actually supported; add a module combo and source-root folder picker; debounce preview refreshes; and group preview rows into Conflicts, Modify existing, Manual review, and Create new.

Reason:
This makes the generator flow easier to scan, gives users clear control over output size, and makes risky project changes easier to distinguish before write.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/dialogs/KmpFeatureWizardDialog.kt`, `src/main/kotlin/com/kmpfeaturekit/model/FeatureModels.kt`

Risk:
Medium. The wizard UI changed substantially, but the underlying generation service contract remains the same.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew buildPlugin`, `./gradlew verifyPlugin`

## Decision 10

Problem:
The preview option existed in the model but did not generate a preview file, while several unused options implied unsupported features.

Decision:
Generate a Compose preview file when preview is enabled, add tests for preview/readme/test toggles, and remove unsupported no-op options from `FeatureOptions`.

Reason:
Visible toggles should map to real output. Removing no-op options lowers product and maintenance ambiguity.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/model/FeatureModels.kt`, `src/main/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilder.kt`, `src/main/kotlin/com/kmpfeaturekit/templates/FeatureTemplates.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilderTest.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/GeneratedSampleCompileTest.kt`

Risk:
Medium. The generated default file set now includes a Compose preview file, which expects Compose preview tooling in real projects.

Verification:
`./gradlew test`, `./gradlew -p samples/kmp-build-fixture compileKotlinJvm`

## Decision 11

Problem:
Post-generate notification only reported counts and did not give users a concise list of written or skipped outputs.

Decision:
Render an HTML summary notification with counts, top written files, top skipped files, and warnings.

Reason:
Users should be able to verify the result of generation without hunting through the project tree.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/actions/NewKmpFeatureAction.kt`

Risk:
Low. This changes notification content only.

Verification:
`./gradlew test`, `./gradlew buildPlugin`

## Decision 14

Problem:
`ProjectStyle` was exposed in the wizard but did not affect generated paths or package roots.

Decision:
Add a `FeatureLayout` planner that maps feature-based, layer-based, and hybrid styles to distinct package roots and file paths.

Reason:
Visible architecture options should produce predictable output. The new layout helper keeps path decisions centralized and testable.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilder.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilderTest.kt`

Risk:
Medium. Layer-based style intentionally keeps Kotlin packages by layer while nesting files by feature folder; this is valid Kotlin but should be documented if users expect path/package parity.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew -p samples/kmp-build-fixture compileKotlinJvm`

## Decision 15

Problem:
`stateHolderType` was exposed in the wizard but generation still inferred the state holder only from architecture type.

Decision:
Coerce the requested state holder through `ArchitectureCompatibility`, generate the selected holder template, and update Koin imports/registrations accordingly.

Reason:
Users choosing Plain State Holder or AndroidX ViewModel should see that choice reflected in files and DI output.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilder.kt`, `src/main/kotlin/com/kmpfeaturekit/templates/FeatureTemplates.kt`, `src/test/kotlin/com/kmpfeaturekit/generator/FeaturePlanBuilderTest.kt`

Risk:
Medium. MVI currently supports AndroidX ViewModel or Plain State Holder through the compatibility model; adding a dedicated MVI Store state-holder enum can be considered later if product scope requires it.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew -p samples/kmp-build-fixture compileKotlinJvm`

## Decision 16

Problem:
File generation used a plain write action, which did not group generated writes as a named IDE command.

Decision:
Wrap file writes in `WriteCommandAction.runWriteCommandAction(project, "Generate Compose Feature", ...)`.

Reason:
Generated files should be associated with a clear IDE command for platform consistency and undo/history behavior.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/generator/FileWriteService.kt`

Risk:
Low. The underlying conservative write/skip semantics are unchanged.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew buildPlugin`

## Decision 17

Problem:
Project scanning could repeat expensive recursive reads from the wizard and tool window.

Decision:
Cache scan results in `ProjectScanService` and add an explicit Refresh button to the tool window.

Reason:
The wizard should reuse inferred defaults quickly, while users still need a clear manual refresh path after project changes.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/services/ProjectScanService.kt`, `src/main/kotlin/com/kmpfeaturekit/toolwindow/KmpFeatureKitToolWindowFactory.kt`

Risk:
Low. Auto-detected defaults may be stale until refresh, but generation inputs remain user-editable.

Verification:
`./gradlew test`, `./gradlew check`, `./gradlew verifyPlugin`

## Decision 18

Problem:
The local Android Studio path was hardcoded in Gradle verification dependencies.

Decision:
Resolve Android Studio path from `androidStudioPath` Gradle property, then `ANDROID_STUDIO_PATH`, then the existing macOS default.

Reason:
Local installation paths and CI runners differ; keeping the old default while allowing override improves portability.

Files changed:
`build.gradle.kts`, `README.md`

Risk:
Low. Existing local default is preserved.

Verification:
`./gradlew tasks`, `./gradlew buildPlugin`, `./gradlew verifyPlugin`

## Decision 12

Problem:
Clicking preview row text toggled the checkbox because each row was a single `JCheckBox`.

Decision:
Render each preview row as a separate checkbox plus clickable label. The checkbox toggles inclusion; the label opens the selected-change popup.

Reason:
This matches the expected interaction: check/uncheck is explicit on the checkbox only, while row text is for inspecting details.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/dialogs/KmpFeatureWizardDialog.kt`

Risk:
Low. This changes only row composition and click handling.

Verification:
`./gradlew test`, `./gradlew buildPlugin`

## Decision 13

Problem:
The selected-change popup used a plain text area, so code colors did not match the IDE editor theme.

Decision:
Render selected-change content with `EditorTextField` using the target file type and viewer mode.

Reason:
This gives Kotlin/Markdown/plain preview content IDE-style font, colors, and syntax highlighting instead of generic text-area styling.

Files changed:
`src/main/kotlin/com/kmpfeaturekit/dialogs/KmpFeatureWizardDialog.kt`

Risk:
Low. This changes only popup rendering.

Verification:
`./gradlew test`, `./gradlew buildPlugin`
