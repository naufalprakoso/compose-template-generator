# Samples

The Gradle sample fixture lives in `samples/kmp-build-fixture`.

It contains a generated MVVM + Koin feature in a Kotlin Multiplatform Gradle project. External UI/DI APIs are represented by local stubs so the fixture stays fast and deterministic.

Run it with:

```bash
./gradlew -p samples/kmp-build-fixture compileKotlinJvm
```

Additional fixture projects should be added here when a supported integration needs end-to-end verification against real third-party dependencies.
