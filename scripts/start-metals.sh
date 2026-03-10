#!/usr/bin/env bash
# start-metals.sh — Start the Metals MCP server for a Scala project and register it with Claude Code.
#
# Usage:
#   ./scripts/start-metals.sh [--workspace /path/to/project] [--name server-name]
#
# Defaults:
#   --workspace   current working directory
#   --name        basename of the workspace directory (e.g. "scala-metals-oss-constellation-engine")
#
# One Metals instance per project. Safe to re-run: detects if already running
# and re-registers the port with Claude Code without spawning a duplicate.

set -euo pipefail

METALS_MCP="C:/Users/atfm0/AppData/Local/Coursier/data/bin/metals-mcp.bat"
POLL_INTERVAL=2
TIMEOUT=120

# --- Argument parsing ---
WORKSPACE=""
SERVER_NAME=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workspace) WORKSPACE="$2"; shift 2 ;;
    --name)      SERVER_NAME="$2"; shift 2 ;;
    -h|--help)
      grep '^#' "$0" | head -12 | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

WORKSPACE="${WORKSPACE:-$(pwd)}"
WORKSPACE="$(cd "$WORKSPACE" && pwd)"

if [[ -z "$SERVER_NAME" ]]; then
  SERVER_NAME="scala-metals-$(basename "$WORKSPACE")"
fi

MCP_JSON="$WORKSPACE/.metals/mcp.json"

echo "Workspace:   $WORKSPACE"
echo "Server name: $SERVER_NAME"
echo ""

# Extract port from .metals/mcp.json: find URL like http://localhost:PORT/mcp
get_port() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  jq -r '.. | strings' "$f" 2>/dev/null \
    | sed -n 's/.*localhost:\([0-9]*\).*/\1/p' \
    | head -1 \
    || true
}

# --- Check if already running ---
EXISTING_PORT="$(get_port "$MCP_JSON")"
if [[ -n "$EXISTING_PORT" ]]; then
  if curl -s --max-time 2 "http://localhost:${EXISTING_PORT}/mcp" >/dev/null 2>&1; then
    echo "Metals already running on port ${EXISTING_PORT} — re-registering with Claude Code."
    claude mcp add-json "$SERVER_NAME" \
      "{\"url\":\"http://localhost:${EXISTING_PORT}/mcp\",\"type\":\"http\"}" \
      -s local
    echo "Done. Registered '$SERVER_NAME' at http://localhost:${EXISTING_PORT}/mcp"
    echo "Restart Claude Code to reconnect."
    exit 0
  fi
fi

# --- Start metals-mcp in background ---
echo "Starting metals-mcp..."

# Convert workspace to Windows backslash path for cmd.exe
WORKSPACE_WIN="$(cygpath -w "$WORKSPACE" 2>/dev/null || echo "${WORKSPACE//\//\\}")"

cmd.exe /c "$METALS_MCP --workspace \"$WORKSPACE_WIN\" --client claude-code" &
METALS_PID=$!

echo "PID: $METALS_PID"
echo "Waiting for server to be ready (timeout: ${TIMEOUT}s)..."

# --- Poll until mcp.json appears and port is alive ---
ELAPSED=0
PORT=""

while [[ $ELAPSED -lt $TIMEOUT ]]; do
  PORT="$(get_port "$MCP_JSON")"
  if [[ -n "$PORT" ]]; then
    if curl -s --max-time 2 "http://localhost:${PORT}/mcp" >/dev/null 2>&1; then
      break
    fi
  fi
  sleep $POLL_INTERVAL
  ELAPSED=$((ELAPSED + POLL_INTERVAL))
  echo -n "."
done

echo ""

if [[ -z "$PORT" ]]; then
  echo "ERROR: Metals did not start within ${TIMEOUT}s." >&2
  exit 1
fi

echo "Metals MCP ready on port $PORT"
echo ""

claude mcp add-json "$SERVER_NAME" \
  "{\"url\":\"http://localhost:${PORT}/mcp\",\"type\":\"http\"}" \
  -s local

echo "Done. Registered '$SERVER_NAME' at http://localhost:${PORT}/mcp"
echo "Restart Claude Code to reconnect."
