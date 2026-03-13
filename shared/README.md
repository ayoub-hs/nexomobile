# Shared Kotlin Module

Cross-platform Kotlin/JVM module used by both the Android app and the Compose Desktop client. It defines shared models, repository contracts, hardware abstractions, and currency utilities.

**What This Module Contains**
- Platform abstraction: `Platform` and `SharedModule` in `shared/src/main/kotlin/com/nexopos/shared/Platform.kt`.
- Hardware interfaces: barcode scanner, receipt printer, and cash drawer in `shared/src/main/kotlin/com/nexopos/shared/hardware/`.
- Shared data models for products, customers, orders, and registers in `shared/src/main/kotlin/com/nexopos/shared/models/`.
- Repository interfaces for customer, product, order, and register operations in `shared/src/main/kotlin/com/nexopos/shared/repo/`.
- Currency helpers and a `Money` type in `shared/src/main/kotlin/com/nexopos/shared/utils/Currency.kt`.

**Key Models**
- Products and units: `Product`, `UnitQuantity`, `UnitDetail`, `ContainerLink`.
- Customers and payments: `Customer`, `CustomerGroup`, `PaymentMethod`.
- Orders: `CreateOrderRequest`, `OrderProductRequest`, `OrderPaymentRequest`, `OrderType`.
- Registers: `Register`, `RegisterHistory`, `RegisterResponse`, `RegisterHistoryResponse`.

**Hardware Abstractions**
- `BarcodeScanner` exposes a `Flow<String>` of scans and start/stop lifecycle.
- `ReceiptPrinter` prints a `Receipt` and reports `PrinterStatus`.
- `CashDrawer` opens the drawer and can report connection status.

**Repository Contracts**
- `ProductRepository`, `CustomerRepository`, `OrderRepository`, `RegisterRepository`.
- Android and Desktop modules provide their own implementations and handle caching or networking as needed.

**Currency Utilities**
- `Currency` helpers use `BigDecimal` to avoid floating-point errors.
- `Money` provides arithmetic, formatting, and helper operations for totals.

**Dependencies**
- Kotlin Coroutines (`kotlinx-coroutines-core`)
- Retrofit + Moshi + OkHttp (shared network modeling compatibility)
- JUnit 4 for tests

**Build**
```bash
./gradlew :shared:build
```

**Tests**
```bash
./gradlew :shared:test
```

**Usage**
- Android app and Desktop client depend on `:shared` for models and contracts.
- Implement repository interfaces and hardware interfaces in platform modules.
