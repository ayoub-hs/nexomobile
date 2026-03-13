# NexoPOS Android App

Android point-of-sale client built with Jetpack Compose. The app uses NexoPOS mobile APIs for offline-first sync and POS workflows.

**Requirements**
- JDK 17
- Android SDK with API 33+ (minSdk 33, targetSdk 36)

**Key Features**
- Sanctum authentication with encrypted token storage.
- Bootstrap and delta sync for products, customers, categories, and payment methods.
- Sales POS with search, cart, checkout, and register workflows.
- Orders list, detail, and incremental sync.
- Inventory adjustments and history.
- Procurement list, detail, create, update, receive, and cancel flows.
- Manufacturing orders and BOMs (when server module is installed).
- Container management (inventory, balances, movements, charges).
- Special customer flows (outstanding tickets, wallet topups, balances).
- Price lookup and scanner-admin product flows.
- Barcode scanning with CameraX + MLKit.
- ESC/POS printing over Bluetooth or TCP.
- Offline queues for orders and stock adjustments with WorkManager replay.

**Configuration**
- Base URL and token are set in the Settings screen.
- Store name and printer configuration are stored in DataStore.
- Tokens are stored in `EncryptedSharedPreferences`.

**Local Storage**
- Room database stores products, categories, customers, payment methods, sync metadata, queued orders, and queued stock adjustments.

**Build**
```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

**Test**
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedAndroidTest
```

**Permissions**
- `INTERNET`
- `CAMERA`
- `BLUETOOTH` and `BLUETOOTH_CONNECT` (for printers and scanners)
