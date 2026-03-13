# NexoPOS Mobile

Android and desktop point-of-sale clients for NexoPOS, built with Kotlin.

## Modules

- `app/`: Android application built with Jetpack Compose
- `desktop/`: Compose Desktop client
- `shared/`: Shared Kotlin business logic

## Requirements

- JDK 17
- Android SDK for Android builds

## Build

```bash
./gradlew assembleDebug
./gradlew :desktop:build
```

## Test

```bash
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
```

## Configuration

The clients are configured at runtime with your server URL and authentication token. Do not commit local SDK paths, tokens, keystores, generated build outputs, or release artifacts.

## Project Docs

- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
- [TESTING.md](TESTING.md)
- [DESKTOP_README.md](DESKTOP_README.md)
- [desktop/ARCHITECTURE.md](desktop/ARCHITECTURE.md)
- [docs/MOBILE_API_SPECIFICATION.md](docs/MOBILE_API_SPECIFICATION.md)

## Repository Status

This cleanup removed internal planning, audit, and session documents so the repository contains only user-facing project documentation.

