# NexoPOS Mobile Clients

Multi-platform clients for NexoPOS built in Kotlin. This repository contains the Android app, the Compose Desktop client, and a shared Kotlin module.

**Modules**
- `app/` Android app (Jetpack Compose)
- `desktop/` Compose Desktop client
- `shared/` Shared Kotlin models and interfaces

**Requirements**
- JDK 17
- Android SDK (minSdk 33, targetSdk 36)

**Build**
```bash
./gradlew :app:assembleDebug
./gradlew :desktop:run
./gradlew :desktop:build
```

**Configuration**
- Both clients require a NexoPOS base URL and a Sanctum token.
- Android stores tokens in `EncryptedSharedPreferences` and settings in DataStore.
- Desktop stores settings in Java Preferences and encrypts tokens with a local AES key.

**Offline Support**
- Android queues orders and stock adjustments locally and replays them via WorkManager.
- Desktop queues orders in SQLite and retries sync in the order repository.

**Docs**
- Android details: `app/README.md`
- Desktop details: `desktop/README.md`
