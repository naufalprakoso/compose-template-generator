# Testing

Run:

```bash
./gradlew test
./gradlew build
./gradlew verifyPlugin
```

Current tests cover pure generation behavior, source-set detection, naming conversion, template rendering, inspection heuristics, and quick-fix target planning. IDE fixture tests can be expanded around the registered inspections after the plugin API surface stabilizes.
