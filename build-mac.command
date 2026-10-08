#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Bu betik macOS üzerinde çalıştırılmalıdır."
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven bulunamadı. JDK 21+ ve Maven 3.9+ kurup yeniden çalıştırın."
  read -r -p "Kapatmak için Enter tuşuna basın..."
  exit 1
fi
if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage bulunamadı. JDK 21+ kurup yeniden çalıştırın."
  read -r -p "Kapatmak için Enter tuşuna basın..."
  exit 1
fi

mvn clean package

BUILD_ID="$(date +%Y%m%d-%H%M%S)"
INPUT_DIR="$ROOT_DIR/dist-mac-input-$BUILD_ID"
OUTPUT_DIR="$ROOT_DIR/dist-mac-build-$BUILD_ID"
APP_JAR="efsane-kokorec-frontend-1.0.0.jar"
mkdir -p "$INPUT_DIR"
cp "frontend/target/$APP_JAR" "$INPUT_DIR/$APP_JAR"

jpackage \
  --type app-image \
  --name EfsaneKokorec \
  --input "$INPUT_DIR" \
  --main-jar "$APP_JAR" \
  --main-class app.frontend.Launcher \
  --dest "$OUTPUT_DIR"

rm -f "$INPUT_DIR/$APP_JAR"
rmdir "$INPUT_DIR"
APP_PATH="$OUTPUT_DIR/EfsaneKokorec.app"
echo "Uygulama hazır: $APP_PATH"
echo "Taşımak için .app dosyasını ve yanındaki data klasörünü birlikte kopyalayın."
open "$APP_PATH"
