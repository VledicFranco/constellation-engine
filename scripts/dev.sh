#!/bin/bash
# Constellation Engine - Development Startup Script
# Usage: ./scripts/dev.sh [--server-only] [--watch-only] [--hot-reload]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

echo -e "${CYAN}================================================${NC}"
echo -e "${CYAN}  Constellation Engine - Development Environment${NC}"
echo -e "${CYAN}================================================${NC}"
echo ""

# Parse arguments
SERVER_ONLY=false
WATCH_ONLY=false
HOT_RELOAD=false

for arg in "$@"; do
    case $arg in
        --server-only)
            SERVER_ONLY=true
            ;;
        --watch-only)
            WATCH_ONLY=true
            ;;
        --hot-reload)
            HOT_RELOAD=true
            ;;
    esac
done

# Check dependencies
command -v sbt >/dev/null 2>&1 || { echo -e "${RED}ERROR: sbt is not installed${NC}"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo -e "${RED}ERROR: npm is not installed${NC}"; exit 1; }

start_server() {
    echo -e "${GREEN}Starting Constellation Server...${NC}"
    echo -e "${YELLOW}  HTTP API: http://localhost:8080${NC}"
    echo -e "${YELLOW}  LSP WebSocket: ws://localhost:8080/lsp${NC}"
    echo ""

    if [ "$HOT_RELOAD" = true ]; then
        echo -e "${MAGENTA}Hot-reload enabled - server will restart on code changes${NC}"
        sbt "~exampleApp/reStart" &
    else
        sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer" &
    fi
}

start_extension_watch() {
    echo -e "${GREEN}Starting TypeScript watch...${NC}"
    cd vscode-extension && npm run watch &
    cd ..
}

# Main logic
if [ "$SERVER_ONLY" = true ]; then
    start_server
    wait
elif [ "$WATCH_ONLY" = true ]; then
    start_extension_watch
    wait
else
    # Full dev environment
    echo -e "${CYAN}Starting full development environment...${NC}"
    echo ""

    # Start server
    start_server

    # Wait for server to start
    echo -e "${YELLOW}Waiting for server to start...${NC}"
    sleep 5

    # Start extension watch
    start_extension_watch

    echo ""
    echo -e "${GREEN}================================================${NC}"
    echo -e "${GREEN}  Development environment is ready!${NC}"
    echo -e "${GREEN}================================================${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Open VSCode in this directory"
    echo "  2. Press F5 to launch the extension"
    echo "  3. Open a .cst file and start coding!"
    echo ""
    echo -e "${YELLOW}Keyboard shortcuts in VSCode:${NC}"
    echo "  Ctrl+Shift+R  - Run script"
    echo "  Ctrl+Shift+D  - Show DAG visualization"
    echo "  Ctrl+Space    - Autocomplete"
    echo ""

    # Wait for all background processes
    wait
fi
