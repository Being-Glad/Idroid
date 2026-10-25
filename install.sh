#!/usr/bin/env bash
# ==============================================================================
# Idroid One-Line macOS Installer
# Downloads the verified Universal 2 macOS DMG directly from GitHub Releases,
# installs Idroid to /Applications, and strips Gatekeeper quarantine flags.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/Being-Glad/Idroid/main/install.sh | bash
# ==============================================================================

set -euo pipefail

CYAN='\033[0;36m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                ⚡️ Idroid for macOS                  ║${NC}"
echo -e "${CYAN}║     Flagship Ecosystem Bridge (macOS & Android)      ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Ensure running on macOS
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo -e "${RED}Error: This installer is intended for macOS only.${NC}"
  exit 1
fi

REPO="Being-Glad/Idroid"
MOUNT_DIR="/tmp/idroid_dmg_mount_$$"
TMP_DMG="/tmp/idroid_universal_$$.dmg"

echo -e "${BLUE}==>${NC} Fetching latest release metadata from GitHub..."
LATEST_DMG_URL=$(curl -s "https://api.github.com/repos/${REPO}/releases/latest" \
  | grep "browser_download_url.*universal.*\.dmg" \
  | cut -d '"' -f 4 \
  || true)

if [[ -z "${LATEST_DMG_URL}" ]]; then
  # Fallback to direct download URL
  LATEST_DMG_URL="https://github.com/${REPO}/releases/latest/download/Idroid_1.0.0_universal.dmg"
fi

echo -e "${BLUE}==>${NC} Downloading Universal 2 macOS DMG (Intel & Apple Silicon M-series)..."
curl -fSL --progress-bar "${LATEST_DMG_URL}" -o "${TMP_DMG}"

cleanup() {
  if [[ -d "${MOUNT_DIR}" ]]; then
    hdiutil detach "${MOUNT_DIR}" -quiet -force 2>/dev/null || true
    rm -rf "${MOUNT_DIR}" 2>/dev/null || true
  fi
  rm -f "${TMP_DMG}" 2>/dev/null || true
}
trap cleanup EXIT

echo -e "${BLUE}==>${NC} Mounting disk image..."
mkdir -p "${MOUNT_DIR}"
hdiutil attach "${TMP_DMG}" -mountpoint "${MOUNT_DIR}" -nobrowse -quiet

if [[ ! -d "${MOUNT_DIR}/Idroid.app" ]]; then
  echo -e "${RED}Error: Idroid.app was not found inside the downloaded DMG.${NC}"
  exit 1
fi

echo -e "${BLUE}==>${NC} Installing Idroid to /Applications..."
rm -rf "/Applications/Idroid.app"
cp -R "${MOUNT_DIR}/Idroid.app" "/Applications/"

echo -e "${BLUE}==>${NC} Configuring macOS security permissions (stripping quarantine)..."
xattr -cr "/Applications/Idroid.app"

echo ""
echo -e "${GREEN}✓ Successfully installed Idroid in /Applications!${NC}"
echo -e "${CYAN}Launching Idroid...${NC}"
open "/Applications/Idroid.app"

echo ""
echo -e "Enjoy the seamless synergy between macOS and Android! 🚀"
