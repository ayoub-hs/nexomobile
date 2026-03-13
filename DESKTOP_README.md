# NexoPOS Desktop Build Guide

## Overview

This project includes a **Compose Desktop** module. The active development target is Linux x64 (Intel/AMD), with ARM64 packaging kept available for later deployment.

## Project Structure

```
Nexoposv50/
├── app/           # Android app (existing)
├── shared/        # Shared Kotlin/JVM business logic
└── desktop/       # Compose Desktop app for Linux
```

## Prerequisites

### On your development machine:
- JDK 17 or higher
- Gradle 8.x (included via wrapper)

### On H313 Armbian box:
- JDK 17 ARM64 (install via `apt install openjdk-17-jdk`)
- Light GUI (LXDE, LXQt, or Openbox)
- X11 or Wayland display server

## Build Instructions

### 1. Build the Desktop app

From the project root:

```bash
./gradlew :desktop:build
```

### 2. Run locally (for testing on dev machine)

```bash
./gradlew :desktop:run
```

### 3. Create distributable package for Linux x64

```bash
./gradlew :desktop:packageDeb -PtargetPlatform=linux_x64
```

This creates a `.deb` package in `desktop/build/compose/binaries/main/deb/`

### 4. Create distributable package for Linux ARM

```bash
./gradlew :desktop:packageDeb
```

This creates a `.deb` package in `desktop/build/compose/binaries/main/deb/`

### 5. Transfer to H313 and install

```bash
# On your dev machine
scp desktop/build/compose/binaries/main/deb/*.deb user@h313-ip:/tmp/

# On H313 box
sudo dpkg -i /tmp/NexoPOS-Desktop_*.deb
```

### 6. Run on target machine

```bash
# Make sure X11/Wayland is running
NexoPOS-Desktop
```

Or run the JAR directly on Intel/Linux:

```bash
java -jar desktop/build/libs/desktop-x64-uber.jar
```

## Testing Checklist on H313

When you run the desktop app on your H313 box, verify:

- [ ] Window opens without crashes
- [ ] UI is responsive (not laggy)
- [ ] Memory usage is acceptable (check with `htop` - should be < 700MB)
- [ ] Theme/colors render correctly
- [ ] Buttons respond to clicks

## Hardware Integration (Next Steps)

Once the basic app runs, we'll add:

1. **USB Barcode Scanner** - keyboard wedge input handling
2. **USB ESC/POS Printer** - direct device write to `/dev/usb/lp*` or `/dev/ttyUSB*`
3. **Cash Drawer** - serial write to `/dev/ttyUSB0`

### Permissions Setup

You'll need to add your user to the correct groups:

```bash
# For USB devices
sudo usermod -aG dialout $USER
sudo usermod -aG lp $USER

# Create udev rules for printer/drawer if needed
sudo nano /etc/udev/rules.d/99-nexopos.rules
```

Example udev rule:
```
SUBSYSTEM=="usb", ATTR{idVendor}=="XXXX", ATTR{idProduct}=="YYYY", MODE="0666"
KERNEL=="ttyUSB[0-9]*", MODE="0666", GROUP="dialout"
```

Then reload:
```bash
sudo udevadm control --reload-rules
sudo udevadm trigger
```

## Memory Optimization for 2GB System

If memory is tight, tune JVM args in the launcher:

```bash
java -Xmx512m -Xms256m -jar desktop.jar
```

## Troubleshooting

### "Could not find or load main class"
- Ensure you built with `./gradlew :desktop:build`
- Check that `desktop/build/libs/desktop-all.jar` exists

### Display issues on Armbian
- Verify X11 is running: `echo $DISPLAY` (should show `:0` or similar)
- Try running with software rendering: `_JAVA_OPTIONS='-Dsun.java2d.opengl=false' java -jar desktop.jar`

### High memory usage
- Reduce JVM heap: `-Xmx384m`
- Close other applications
- Consider using a lighter window manager

## Development Workflow

1. Make changes in `:shared` or `:desktop` modules
2. Test locally: `./gradlew :desktop:run`
3. Build for ARM: `./gradlew :desktop:packageDeb`
4. Deploy to H313 and test with real hardware

## Next: Migrating Android Features

Once the basic desktop app is validated on H313, we'll:

1. Move models, API clients, and business logic from `:app` to `:shared`
2. Port UI screens from Android to Desktop (reusing Compose code)
3. Implement hardware interfaces (scanner, printer, drawer)
4. Add navigation and state management

---

**Status**: ✅ Basic desktop module created and ready for H313 testing
