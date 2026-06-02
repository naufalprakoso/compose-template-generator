# Compose Template Generator Improvement Report

## Summary

Compose Template Generator is a focused IntelliJ IDEA / Android Studio plugin for generating Kotlin Multiplatform and Compose feature scaffolds. The project already has a clear plugin descriptor, Gradle IntelliJ Platform setup, generation services, source-set detection, DI/navigation registration planners, inspections, quick fixes, README, changelog, support matrix, privacy notes, CI, and a meaningful unit-test suite that includes generated-source compile smoke tests.

The highest-risk areas are input validation, path safety, UI-thread work, and release verification. The generation pipeline is conservative about existing files and integration edits, but before this pass it accepted invalid Kotlin feature names, blank or relative source roots, and unvalidated module fields. That could produce non-compilable Kotlin or write files into unintended relative paths. The action also reported generation with a modal info dialog and did not surface warnings clearly through the IDE notification system.

## High Priority Issues

- Invalid feature names can generate invalid Kotlin identifiers and file names. Numeric-only names such as `123` normalize into class names that cannot compile.
- Blank or relative source-set roots can produce unsafe relative writes. `FeatureDialogDefaults.fallback` can return an empty source root for projects without `basePath`, and the dialog did not block it.
- `targetModule` and source-root fields were not validated, so the preview and generation flow could proceed with malformed or misleading paths.
- `FeatureDialogDefaultsResolver.sourceSetRootForPath` used Unix-only string matching, leaving Windows or mixed-separator path handling fragile.
- `ProjectScanService.scan()` used to recursively read project files synchronously while the dialog opened. It now skips generated/metadata directories, caps scanned text, and caches results until explicit refresh.
- `FileWriteService` used to run under a plain write action. It now groups writes under a named `WriteCommandAction`; fuller VFS/document-write fixture coverage is still worth adding before expanding modify behavior.

## Medium Priority Improvements

- The action should be disabled when no project is available and can be marked `DumbAware` if the invoked flow is safe during indexing.
- Generation results should use plugin notifications and include warnings/skips instead of only a modal success count.
- Conflict selection is previewed, but existing create targets are still skipped unless overwrite is explicitly enabled. The current default is safe, but the dialog copy should stay clear that selecting an existing create target does not overwrite it.
- `FileWriteService` append mode for non-replacing `MODIFY` plans uses a broad substring check and direct append. The current planners mostly produce full-file replacements or TODO create files, but this path needs stronger tests before expanding.
- CI now runs `verifyPlugin`; the Android Studio path can be overridden with `-PandroidStudioPath` or `ANDROID_STUDIO_PATH`.
- README development prerequisites mention JDK 21 while the Gradle toolchain and CI use JDK 17.
- There are no marketplace screenshots in the repository, and the release checklist is informal.

## Low Priority Improvements

- `plugin.svg` exists but plugin descriptor metadata does not explicitly declare an icon path.
- Marketplace description text exists both in Gradle/plugin metadata and `docs/marketplace-description.md`; future releases should keep these synchronized.
- Preview refresh is debounced in the wizard. Future polish could move heavier scans to a background task.
- Additional tests could cover write-service behavior and preview warning details with a lightweight IntelliJ test fixture.

## Marketplace Readiness

- Plugin name, vendor, description, Kotlin plugin mode support, inspections, actions, notification group, and services are declared in `plugin.xml`.
- README explains purpose, generated capabilities, project layout, development commands, privacy, and support matrix.
- Changelog exists and records versions `0.1.0` and `0.1.1`.
- Marketplace draft copy and privacy policy exist under `docs/`.
- CI exists for tests, sample fixture compile, plugin verifier, and plugin packaging.
- No screenshots were found. Marketplace listing readiness would improve with screenshots/GIFs of the action, wizard preview, generated files, and inspections.
- Publishing is configured through the Gradle IntelliJ Platform plugin and expects `intellijPlatformPublishingToken`; publishing was intentionally not run.

## Recommended Fix Plan

1. Harden validation for feature name, base package, target module, source-set root, and platform selection.
2. Normalize source-root path detection for Windows and mixed separators.
3. Improve action availability and notification quality.
4. Add unit tests for validation and path detection edge cases.
5. Update README with accurate JDK requirements, supported structures, troubleshooting, and a release checklist.
6. Capture marketplace screenshots/GIFs for the action, guided wizard preview, generated files, inspections, and tool window.
7. Run a focused IntelliJ ToolWindowFactory API migration pass to reduce verifier warnings.

## Follow-up Implementation Update

- `ProjectStyle` now changes generated layout paths and package roots for feature-based, layer-based, and hybrid structures.
- `stateHolderType` now controls generated state-holder files and DI registration, including a real plain state holder template.
- File writing is grouped under `WriteCommandAction`.
- Project scanning is cached and refreshable from the tool window.
- The selected-change popup now uses the filename as title and shows the path above the IDE-style preview editor.
- Android Studio path configuration is overridable through Gradle property or environment variable.
