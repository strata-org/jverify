#!/bin/bash
# Pipeline: Java → Laurel Ion → Laurel Source → Core → Verify
# Usage: ./java-to-laurel-pipeline.sh <java-file> [output-dir]

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <java-file> [output-dir]"
  exit 1
fi

JAVA_FILE="$1"
OUTPUT_DIR="${2:-/tmp/jverify-pipeline}"
JVERIFY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Extract base name
BASE=$(basename "$JAVA_FILE" .java)

mkdir -p "$OUTPUT_DIR"

echo "=== Pipeline: $JAVA_FILE ==="
echo "Output directory: $OUTPUT_DIR"
echo

# Step 1: Java → Laurel Ion
echo "[1/4] Java → Laurel Ion"
export JVERIFY_Strata="$JVERIFY_ROOT/Strata"
"$JVERIFY_ROOT/verifier/build/install/verifier/bin/verifier" \
  --backend Strata \
  --print-ir "$OUTPUT_DIR/$BASE.ion" \
  "$JAVA_FILE" || true
echo "  → $OUTPUT_DIR/$BASE.ion"

# Step 2: Laurel Ion → Laurel Source
echo "[2/4] Laurel Ion → Laurel Source"
cd "$JVERIFY_ROOT/Strata"
lake exe strata laurelPrint < "$OUTPUT_DIR/$BASE.ion" > "$OUTPUT_DIR/$BASE.laurel" 2>/dev/null
echo "  → $OUTPUT_DIR/$BASE.laurel"

# Step 3: Laurel Source → Core (debug format)
echo "[3/4] Laurel Source → Core"
lake exe strata laurelToCore "$OUTPUT_DIR/$BASE.laurel" > "$OUTPUT_DIR/$BASE.core" 2>/dev/null
echo "  → $OUTPUT_DIR/$BASE.core"

# Step 4: Verify
echo "[4/4] Verifying..."
RESULTS=$(lake exe strata laurelAnalyze "$OUTPUT_DIR/$BASE.laurel" 2>/dev/null | grep -E "^[a-zA-Z_0-9]+: Core\.Outcome\.")
echo "$RESULTS" | sed 's/^/  /'
if echo "$RESULTS" | grep -qE "\.fail|\.unknown"; then
  echo "  ✗ Verification failed"
  exit 1
else
  echo "  ✓ All VCs passed"
fi

echo
echo "=== Files generated ==="
ls -la "$OUTPUT_DIR/$BASE".*
