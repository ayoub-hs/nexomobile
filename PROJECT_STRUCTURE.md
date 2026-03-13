# NexoPOS Project Structure

## Overview

NexoPOS is now a **multi-platform POS system** with shared business logic:

- **Android app** (existing) - Mobile POS
- **Desktop app** (new) - Linux ARM POS for H313 Armbian

## Module Architecture

```
Nexoposv50/
│
├── app/                          # Android application
│   ├── src/main/java/com/nexopos/mobile/
│   │   ├── ui/                   # Jetpack Compose UI
│   │   │   └── theme/            # Android theme (uses Activity/WindowCompat)
│   │   └── ...                   # Android-specific code
│   └── build.gradle.kts          # Android dependencies
│
├── shared/                       # Platform-agnostic business logic
│   ├── src/main/kotlin/com/nexopos/shared/
│   │   ├── Platform.kt           # Platform abstraction
│   │   └── hardware/             # Hardware interfaces
│   │       ├── BarcodeScanner.kt # Barcode scanner interface
│   │       ├── ReceiptPrinter.kt # Printer interface + models
│   │       └── CashDrawer.kt     # Cash drawer interface
│   └── build.gradle.kts          # Shared dependencies (Retrofit, Coroutines, etc.)
│
└── desktop/                      # Compose Desktop application
    ├── src/main/kotlin/com/nexopos/desktop/
    │   ├── Main.kt               # Entry point + navigation
    │   ├── platform/
    │   │   └── DesktopPlatform.kt # Desktop platform implementation
    │   ├── ui/
    │   │   ├── theme/
    │   │   │   └── Theme.kt      # Desktop theme (no Android deps)
    │   │   └── screens/
    │   │       └── HardwareTestScreen.kt # USB device testing UI
    │   └── hardware/             # Desktop hardware implementations
    │       ├── DesktopBarcodeScanner.kt   # USB keyboard wedge
    │       ├── DesktopReceiptPrinter.kt   # ESC/POS via /dev/usb/lp*
    │       └── DesktopCashDrawer.kt       # Serial via /dev/ttyUSB0
    └── build.gradle.kts          # Compose Desktop dependencies

```

## Dependency Flow

```
┌─────────────┐         ┌──────────────┐
│   :app      │         │  :desktop    │
│  (Android)  │         │   (Linux)    │
└──────┬──────┘         └──────┬───────┘
       │                       │
       │   depends on          │   depends on
       │                       │
       └───────────┬───────────┘
                   │
                   ▼
            ┌──────────────┐
            │   :shared    │
            │ (Pure Kotlin)│
            └──────────────┘
```

## Key Design Principles

### 1. Platform Abstraction

Hardware features are defined as **interfaces** in `:shared`:

```kotlin
// shared/src/.../hardware/BarcodeScanner.kt
interface BarcodeScanner {
    val scannedBarcodes: Flow<String>
    suspend fun startScanning()
    suspend fun stopScanning()
}
```

Each platform provides its own implementation:

- **Android**: `AndroidBarcodeScanner` (CameraX + MLKit)
- **Desktop**: `DesktopBarcodeScanner` (USB keyboard wedge)

### 2. Shared Business Logic

Move these from `:app` to `:shared` as you migrate:

- API clients (Retrofit/Ktor)
- Data models (DTOs, entities)
- Repositories
- Use cases / business logic
- ViewModels (using plain coroutines, not AndroidX ViewModel)

### 3. UI Reuse

Most Compose UI code can be shared:

- **Reusable**: Composables using Material3, basic layouts, lists, forms
- **Platform-specific**: Window management, system bars, permissions, camera

Strategy:
- Extract pure UI composables to `:shared` or a new `:ui-common` module
- Keep platform-specific wrappers in `:app` and `:desktop`

## Hardware Integration

### Android (existing)
- **Barcode**: CameraX + MLKit barcode scanning
- **Printer**: `androidx.print` or Bluetooth printer APIs
- **Drawer**: Usually via printer ESC/POS commands

### Desktop (new)
- **Barcode**: USB scanner as keyboard wedge (HID input)
- **Printer**: Direct ESC/POS to `/dev/usb/lp0` or `/dev/ttyUSB1`
- **Drawer**: Serial write to `/dev/ttyUSB0` (`echo '1' > /dev/ttyUSB0`)

## Build Commands

### Android
```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

### Desktop
```bash
# Run locally
./gradlew :desktop:run

# Build JAR
./gradlew :desktop:build

# Package for distribution
./gradlew :desktop:packageDeb

# Or use the helper script
./build-desktop.sh run
./build-desktop.sh test-h313
```

## Migration Roadmap

### Phase 1: Validation (Current)
- [x] Create `:shared` and `:desktop` modules
- [x] Set up Compose Desktop with basic UI
- [x] Create hardware interface abstractions
- [x] Implement stub hardware services
- [ ] **Test on H313 hardware** ← YOU ARE HERE
- [ ] Validate performance and stability

### Phase 2: Business Logic Migration
- [ ] Move API client from `:app` to `:shared`
- [ ] Move models/DTOs to `:shared`
- [ ] Move repositories to `:shared`
- [ ] Update `:app` to use `:shared` module
- [ ] Verify Android app still works

### Phase 3: UI Migration
- [ ] Port main screens to Desktop
- [ ] Implement navigation
- [ ] Add state management
- [ ] Reuse theme/styling

### Phase 4: Hardware Integration
- [ ] Implement real barcode scanning (keyboard listener)
- [ ] Complete ESC/POS printer commands
- [ ] Test cash drawer control
- [ ] Add error handling and recovery

### Phase 5: Feature Parity
- [ ] Product catalog
- [ ] Cart management
- [ ] Checkout flow
- [ ] Receipt printing
- [ ] Offline mode
- [ ] Sync with backend

## Testing Strategy

### Desktop Hardware Testing

Use the **Hardware Test Screen** (click "Hardware Test" button):

1. **Barcode Scanner**
   - Focus text field
   - Scan barcode with USB scanner
   - Verify barcode appears in "Last scanned"

2. **Printer**
   - Click "Check Status"
   - Click "Print Test Receipt"
   - Verify receipt prints

3. **Cash Drawer**
   - Click "Check Connection"
   - Click "Open Drawer"
   - Verify drawer opens

### Unit Testing

Add tests in each module:

```bash
# Test shared logic
./gradlew :shared:test

# Test desktop
./gradlew :desktop:test

# Test Android
./gradlew :app:testDebugUnitTest
```

## Deployment

### Android
- Standard APK/AAB via Play Store or direct install

### Desktop (H313 Armbian)

**Option 1: JAR file**
```bash
# Build
./gradlew :desktop:build

# Transfer
scp desktop/build/libs/desktop.jar user@h313:/home/user/

# Run on H313
java -jar desktop.jar
```

**Option 2: .deb package**
```bash
# Build
./gradlew :desktop:packageDeb

# Transfer and install
scp desktop/build/compose/binaries/main/deb/*.deb user@h313:/tmp/
ssh user@h313 'sudo dpkg -i /tmp/NexoPOS-Desktop_*.deb'

# Run
NexoPOS-Desktop
```

## Configuration

### Desktop Hardware Paths

Edit these files if your USB devices are on different paths:

- `desktop/.../DesktopReceiptPrinter.kt` - Change `devicePath` (default: `/dev/usb/lp0`)
- `desktop/.../DesktopCashDrawer.kt` - Change `devicePath` (default: `/dev/ttyUSB0`)

### JVM Memory Tuning

For 2GB H313 system, tune memory:

```bash
java -Xmx512m -Xms256m -jar desktop.jar
```

Or edit the launcher script after installing .deb package.

## Resources

- [Compose Multiplatform Docs](https://github.com/JetBrains/compose-multiplatform)
- [ESC/POS Command Reference](https://reference.epson-biz.com/modules/ref_escpos/)
- [Linux Serial Port Programming](https://tldp.org/HOWTO/Serial-Programming-HOWTO/)

---

**Status**: ✅ Multi-platform architecture established, ready for H313 validation
