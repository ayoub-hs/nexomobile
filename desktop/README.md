# NexoPOS Desktop App

Compose Desktop point-of-sale client. The desktop app uses the same mobile API surface for sync and adds register and order management for daily operations.

**Requirements**
- JDK 17

**Key Features**
- POS screen with cart, payments, and receipt printing.
- Orders list with print actions.
- Register operations (open, close, cash in, cash out, session history).
- Container receive flow and customer container balances.
- Barcode lookup and keyboard shortcut support.
- Hardware test screen for scanner, printer, and drawer.
- Offline order queue stored in SQLite.

**Configuration**
- Base URL, token, store name, and printer settings are stored in Java Preferences.
- Tokens are encrypted with an AES key stored at `~/.nexopos/desktop.key`.
- Local database is stored at `~/.nexopos/nexopos.db`.

**Build and Run**
```bash
./gradlew :desktop:run
./gradlew :desktop:build
```

**Packaging**
```bash
./gradlew :desktop:packageDeb
./gradlew :desktop:uberJar -PtargetPlatform=linux_x64
./gradlew :desktop:uberJar -PtargetPlatform=linux_arm64
```

**API Usage**
- Sync and catalog: `/api/mobile/*`
- Orders create/update: `/api/orders`
- Register management: `/api/cash-registers/*`
