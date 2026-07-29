#!/bin/bash
#
# Demo: JVerify's --interpret mode.
#
# Verification (the default mode) proves a method's contracts hold for *all*
# inputs by handing proof obligations to SMT. --interpret mode instead
# *concretely executes* the entry point(s) the producer marks with
# @InterpretEntry, treating contracts (check / precondition / ...) as runtime
# assertions. It reuses the same Java -> Laurel -> Core pipeline, then runs the
# program in Strata's Core interpreter (`laurelInterpretBinary`) rather than
# generating VCs. The @InterpretEntry marker travels through Laurel/Core
# metadata, so no --entry flag (or name mangling) is needed.
#
# Usage:
#   ./examples-interpret/run_interpret_demo.sh
#
# Prerequisites (see CLAUDE.md):
#   - Strata back-end built:  cd Strata/StrataCLI && lake build
#   - Verifier CLI built:     ./gradlew installDist
#   - JVERIFY_STRATA set, or pass --strata ./Strata

set -o pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

VERIFIER="$REPO_ROOT/verifier/build/install/verifier/bin/verifier"
DEMO="$REPO_ROOT/examples-interpret/org/strata/jverify/interpret/InterpretDemo.java"
LIBRARY_JAR="$REPO_ROOT/library/build/libs/library-1.0-SNAPSHOT.jar"

# Point at the bundled Strata submodule unless the caller already set it.
export JVERIFY_STRATA="${JVERIFY_STRATA:-$REPO_ROOT/Strata}"

if [ ! -x "$VERIFIER" ]; then
    echo "Verifier CLI not found at $VERIFIER"
    echo "Build it first:  ./gradlew installDist"
    exit 1
fi

run() {
    local title="$1"; shift
    echo
    echo "============================================================"
    echo "$title"
    echo "  \$ verifier $*"
    echo "============================================================"
    "$VERIFIER" "$@"
    echo "(exit code: $?)"
}

# 1. Concretely execute the @InterpretEntry method (runOk). No --entry needed;
#    the marker is read from Laurel/Core metadata. notAnEntry() is not run.
run "1) Interpret the @InterpretEntry method (runOk): expect 0 errors, exit 0" \
    --interpret --classpath "$LIBRARY_JAR" -- \
    "$DEMO"

# 2. The same file under ordinary verification, for contrast. Here EVERY method
#    is checked, so notAnEntry()'s check(false) fails -- showing that interpret
#    mode (1) ran only the marked entry, not the whole program.
run "2) For contrast: ordinary verification checks ALL methods" \
    --classpath "$LIBRARY_JAR" -- \
    "$DEMO"

echo
echo "Done. (1) ran only the @InterpretEntry method and passed; (2) verified the"
echo "whole file and reported notAnEntry()'s check(false). The entry point was"
echo "selected purely from the producer's @InterpretEntry marker -- no --entry,"
echo "no name mangling."
