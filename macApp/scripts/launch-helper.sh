#!/usr/bin/env bash
# Launches the Nyora JVM helper sidecar. Run this once per session; the SwiftUI
# app discovers it via ~/Library/Application Support/Nyora/helper.port.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SUPPORT_DIR="$HOME/Library/Application Support/Nyora"
PORT_FILE="$SUPPORT_DIR/helper.port"

mkdir -p "$SUPPORT_DIR"
rm -f "$PORT_FILE"

cd "$REPO_ROOT"
exec ./gradlew :shared:run --console=plain -PnyoraHelperPortFile="$PORT_FILE"
