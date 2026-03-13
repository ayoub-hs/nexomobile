#!/bin/bash
# Build script for NexoPOS Desktop (Linux x64 by default)

set -e

echo "======================================"
echo "NexoPOS Desktop Build Script"
echo "======================================"
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check Java version
echo -e "${BLUE}Checking Java version...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}Java not found. Please install JDK 17 or higher.${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Java 17 or higher required. Found: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java version OK${NC}"
echo ""

# Build options
BUILD_TYPE=${1:-run}
TARGET_PLATFORM=${TARGET_PLATFORM:-linux_x64}

case $BUILD_TYPE in
    run)
        echo -e "${BLUE}Running desktop app locally (current architecture)...${NC}"
        ./gradlew :desktop:run
        echo -e "${YELLOW}Note: Use 'build' command to create ARM64 JAR${NC}"
        ;;
    
    build)
        echo -e "${BLUE}Building uber JAR for ${TARGET_PLATFORM} (includes all dependencies)...${NC}"
        ./gradlew :desktop:uberJar -PtargetPlatform=${TARGET_PLATFORM}
        echo ""
        echo -e "${GREEN}✓ Build complete!${NC}"
        
        # Check both possible JAR names
        if [ -f "desktop/build/libs/desktop-arm64-uber.jar" ]; then
            JAR_SIZE=$(du -h desktop/build/libs/desktop-arm64-uber.jar | cut -f1)
            echo "JAR location: desktop/build/libs/desktop-arm64-uber.jar"
            echo "JAR size: ${JAR_SIZE}"
        elif [ -f "desktop/build/libs/desktop-x64-uber.jar" ]; then
            JAR_SIZE=$(du -h desktop/build/libs/desktop-x64-uber.jar | cut -f1)
            echo "JAR location: desktop/build/libs/desktop-x64-uber.jar"
            echo "JAR size: ${JAR_SIZE}"
        elif [ -f "desktop/build/libs/desktop.jar" ]; then
            JAR_SIZE=$(du -h desktop/build/libs/desktop.jar | cut -f1)
            echo "JAR location: desktop/build/libs/desktop.jar"
            echo "JAR size: ${JAR_SIZE}"
        fi
        echo -e "${YELLOW}Note: Override with TARGET_PLATFORM=linux_arm64 when you need ARM builds${NC}"
        ;;
    
    package)
        echo -e "${BLUE}Creating distributable package for ${TARGET_PLATFORM}...${NC}"
        ./gradlew :desktop:packageDistributableArchive -PtargetPlatform=${TARGET_PLATFORM}
        echo ""
        echo -e "${GREEN}✓ Package created!${NC}"
        echo "Archive location: desktop/build/compose/binaries/main/"
        echo -e "${YELLOW}Note: Override with TARGET_PLATFORM=linux_arm64 when you need ARM builds${NC}"
        ;;
    
    deb)
        echo -e "${BLUE}Creating .deb package for ${TARGET_PLATFORM} Debian/Ubuntu...${NC}"
        ./gradlew :desktop:packageDeb -PtargetPlatform=${TARGET_PLATFORM}
        echo ""
        echo -e "${GREEN}✓ .deb package created!${NC}"
        echo "Package location: desktop/build/compose/binaries/main/deb/"
        echo -e "${YELLOW}Note: Override with TARGET_PLATFORM=linux_arm64 when you need ARM builds${NC}"
        ;;
    
    clean)
        echo -e "${BLUE}Cleaning build artifacts...${NC}"
        ./gradlew clean
        echo -e "${GREEN}✓ Clean complete!${NC}"
        ;;
    
    test-h313)
        echo -e "${BLUE}Building uber JAR and preparing for H313 deployment (ARM64)...${NC}"
        ./gradlew :desktop:uberJar -PtargetPlatform=linux_arm64
        
        # Try both possible JAR locations
        JAR_FILE="desktop/build/libs/desktop-arm64-uber.jar"
        if [ ! -f "$JAR_FILE" ]; then
            JAR_FILE="desktop/build/libs/desktop.jar"
        fi
        if [ -f "$JAR_FILE" ]; then
            echo ""
            echo -e "${GREEN}✓ Build successful!${NC}"
            echo ""
            JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
            echo "JAR size: ${JAR_SIZE}"
            echo ""
            echo "Next steps:"
            echo "1. Transfer to H313:"
            echo "   ${BLUE}scp $JAR_FILE user@h313-ip:/home/user/desktop.jar${NC}"
            echo ""
            echo "2. On H313, run:"
            echo "   ${BLUE}java -jar desktop.jar${NC}"
            echo ""
            echo "3. Recommended settings for ARM64 (2GB+ system):"
            echo "   ${BLUE}java -Xmx1024m -XX:+UseG1GC -jar desktop.jar${NC}"
            echo ""
            echo "4. For low memory systems (<2GB):"
            echo "   ${BLUE}java -Xmx768m -XX:+UseSerialGC -jar desktop.jar${NC}"
        else
            echo -e "${RED}Build failed - JAR not found${NC}"
            exit 1
        fi
        ;;
    
    *)
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  run        - Run desktop app locally (current architecture)"
        echo "  build      - Build desktop JAR (default: linux_x64)"
        echo "  package    - Create distributable desktop archive"
        echo "  deb        - Create desktop .deb package for Debian/Ubuntu"
        echo "  clean      - Clean build artifacts"
        echo "  test-h313  - Build ARM64 JAR and show H313 deployment instructions"
        echo ""
        echo "Examples:"
        echo "  $0              # Run locally"
        echo "  $0 build        # Build x64 JAR"
        echo "  TARGET_PLATFORM=linux_arm64 $0 build   # Build ARM64 JAR"
        echo "  $0 test-h313    # Prepare for H313 testing"
        exit 1
        ;;
esac
