# Development

## Prerequisites

- JDK 21
- Network access for the first Gradle wrapper and IntelliJ Platform artifact resolution

## Common Commands

```bash
./gradlew test
./gradlew build
./gradlew runIde
./gradlew verifyPlugin
./gradlew buildPlugin
```

## Release Notes

Publishing is intentionally not configured. Build release zips with:

```bash
./gradlew buildPlugin
```
