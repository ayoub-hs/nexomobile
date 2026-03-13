#!/bin/bash
# Optimized startup script for NexoPOS Desktop on ARM64 devices
# This script detects system resources and applies optimal JVM settings

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}NexoPOS Desktop - ARM64 Optimized${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Find JAR file (try multiple locations)
JAR_FILE="desktop.jar"
if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE="desktop/build/libs/desktop-arm64-uber.jar"
fi
if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE="desktop/build/libs/desktop.jar"
fi

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found${NC}"
    echo "Please build the application first:"
    echo "  ./gradlew :desktop:build"
    exit 1
fi

# Detect total system memory
TOTAL_MEM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
TOTAL_MEM_MB=$((TOTAL_MEM_KB / 1024))

echo -e "${BLUE}System Information:${NC}"
echo "  Total Memory: ${TOTAL_MEM_MB}MB"

# Determine optimal JVM settings based on available memory
if [ $TOTAL_MEM_MB -ge 3072 ]; then
    # 3GB+ system - use generous settings
    MAX_HEAP="1536m"
    GC_TYPE="-XX:+UseG1GC"
    EXTRA_OPTS="-XX:MaxGCPauseMillis=200"
    echo -e "  Profile: ${GREEN}High Performance${NC}"
elif [ $TOTAL_MEM_MB -ge 2048 ]; then
    # 2-3GB system - balanced settings
    MAX_HEAP="1024m"
    GC_TYPE="-XX:+UseG1GC"
    EXTRA_OPTS="-XX:MaxGCPauseMillis=100"
    echo -e "  Profile: ${GREEN}Balanced${NC}"
elif [ $TOTAL_MEM_MB -ge 1536 ]; then
    # 1.5-2GB system - conservative settings
    MAX_HEAP="768m"
    GC_TYPE="-XX:+UseSerialGC"
    EXTRA_OPTS="-XX:MaxGCPauseMillis=50"
    echo -e "  Profile: ${YELLOW}Conservative${NC}"
else
    # <1.5GB system - minimal settings
    MAX_HEAP="512m"
    GC_TYPE="-XX:+UseSerialGC"
    EXTRA_OPTS="-XX:MaxGCPauseMillis=50 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40"
    echo -e "  Profile: ${YELLOW}Minimal (Warning: May be slow)${NC}"
fi

# Common optimization flags for ARM64
COMMON_OPTS="-Xmx${MAX_HEAP}"
COMMON_OPTS="$COMMON_OPTS $GC_TYPE"
COMMON_OPTS="$COMMON_OPTS $EXTRA_OPTS"
COMMON_OPTS="$COMMON_OPTS -XX:+UseStringDeduplication"
COMMON_OPTS="$COMMON_OPTS -Djava.awt.headless=false"
COMMON_OPTS="$COMMON_OPTS -Dskiko.fps.enabled=false"  # Disable FPS overlay

echo -e "  Heap Size: ${MAX_HEAP}"
echo -e "  GC Type: ${GC_TYPE}"
echo ""

# Check if verbose mode is requested
VERBOSE=""
if [ "$1" == "-v" ] || [ "$1" == "--verbose" ]; then
    VERBOSE="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
    echo -e "${YELLOW}Verbose GC logging enabled${NC}"
    echo ""
fi

# Launch application
echo -e "${GREEN}Starting NexoPOS Desktop...${NC}"
echo ""
echo "Command: java $COMMON_OPTS $VERBOSE -jar $JAR_FILE"
echo ""

# Execute
exec java $COMMON_OPTS $VERBOSE -jar "$JAR_FILE"
