#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Generate JUnit regression/error-revealing tests with Randoop.

Usage:
  ./$(basename "$0") [options] [-- extra-randoop-args]

Options:
  --time-limit=N        Seconds to generate tests (default: 60)
  --output-limit=N      Max tests to output (default: 100000)
  --output-dir=PATH     Output directory (default: build/randoop-output)
  --module=NAME         Gradle module to test (default: dynamodb-simplified-core)
  --test-jar=PATH       Path to the JAR under test (default: auto from module build)
  --classlist=PATH      File listing classes to test (default: test all from JAR)
  --randoop-jar=PATH    Path to randoop-all JAR (default: /tmp/randoop-all-4.3.4.jar)
  --help, -h            Show this help

Any arguments after -- are passed through to Randoop.

Examples:
  ./$(basename "$0") --time-limit=120
  ./$(basename "$0") --module=dynamodb-simplified-demo --time-limit=30
  ./$(basename "$0") --time-limit=10 -- --npe-on-non-null-input=ERROR
EOF
}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- Defaults --------------------------------------------------------------
RANDOOP_JAR="${RANDOOP_JAR:-/tmp/randoop-all-4.3.4.jar}"
TIME_LIMIT=60
OUTPUT_LIMIT=100000
OUTPUT_DIR="build/randoop-output"
MODULE="dynamodb-simplified-core"
TEST_JAR=""
CLASSLIST=""

# ---- Parse arguments -------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --time-limit=*)     TIME_LIMIT="${1#*=}"; shift ;;
    --time-limit)       TIME_LIMIT="$2"; shift 2 ;;
    --output-limit=*)   OUTPUT_LIMIT="${1#*=}"; shift ;;
    --output-limit)     OUTPUT_LIMIT="$2"; shift 2 ;;
    --output-dir=*)     OUTPUT_DIR="${1#*=}"; shift ;;
    --output-dir)       OUTPUT_DIR="$2"; shift 2 ;;
    --module=*)         MODULE="${1#*=}"; shift ;;
    --module)           MODULE="$2"; shift 2 ;;
    --test-jar=*)       TEST_JAR="${1#*=}"; shift ;;
    --test-jar)         TEST_JAR="$2"; shift 2 ;;
    --classlist=*)      CLASSLIST="${1#*=}"; shift ;;
    --classlist)        CLASSLIST="$2"; shift 2 ;;
    --randoop-jar=*)    RANDOOP_JAR="${1#*=}"; shift ;;
    --randoop-jar)      RANDOOP_JAR="$2"; shift 2 ;;
    --help|-h)          usage; exit 0 ;;
    --)                 shift; break ;;
    *)                  echo "Unknown option: $1"; exit 1 ;;
  esac
done

# ---- Check Randoop JAR -----------------------------------------------------
if [ ! -f "$RANDOOP_JAR" ]; then
  echo "Error: Randoop JAR not found at $RANDOOP_JAR"
  echo "Download it from https://github.com/randoop/randoop/releases"
  exit 1
fi

# ---- Build module JAR if needed --------------------------------------------
if [ -z "$TEST_JAR" ]; then
  echo ">>> Building $MODULE..."
  ./gradlew :"$MODULE":jar -q
  TEST_JAR="$(find "$MODULE/build/libs" -name "$MODULE-*.jar" ! -name "*-sources*" ! -name "*-javadoc*" | head -1)"
  if [ -z "$TEST_JAR" ]; then
    echo "Error: No JAR found for module $MODULE"
    exit 1
  fi
fi
echo ">>> Test JAR: $TEST_JAR"

# ---- Build dependency classpath from Gradle cache --------------------------
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
CP="$TEST_JAR"

# Collect all AWS SDK JARs (any version — JVM picks first class)
AWS_JARS=$(find "$GRADLE_CACHE/software.amazon.awssdk" \
  -name "*.jar" ! -name "*-sources*" ! -name "*-javadoc*" 2>/dev/null | sort -u || true)
echo ">>> Found $(echo "$AWS_JARS" | wc -l) dependency JARs"

# Collect core third-party dependencies
OTHER_DEPS="
  org.slf4j/slf4j-api
  org.jspecify/jspecify
  org.reactivestreams/reactive-streams
  io.netty/netty-buffer
  io.netty/netty-codec
  io.netty/netty-codec-http
  io.netty/netty-codec-http2
  io.netty/netty-common
  io.netty/netty-handler
  io.netty/netty-resolver
  io.netty/netty-transport
  io.netty/netty-transport-native-unix-common
  io.netty/netty-transport-classes-epoll
"

# Build the classpath string
for jar in $AWS_JARS; do
  CP="$CP:$jar"
done

OTHER_COUNT=0
for dep in $OTHER_DEPS; do
  found=$(find "$GRADLE_CACHE/$dep" \
    -name "*.jar" ! -name "*-sources*" ! -name "*-javadoc*" 2>/dev/null \
    | sort -u | head -1 || true)
  if [ -n "$found" ]; then
    CP="$CP:$found"
    OTHER_COUNT=$((OTHER_COUNT + 1))
  fi
done
echo ">>> Found $OTHER_COUNT third-party dependency JARs"

# ---- Build Randoop arguments -----------------------------------------------
RANDOOP_CMD=(
  java -Xmx3000m
  -cp "$CP:$RANDOOP_JAR"
  randoop.main.Main gentests
  --time-limit="$TIME_LIMIT"
  --output-limit="$OUTPUT_LIMIT"
  --junit-output-dir="$OUTPUT_DIR"
)

# Add either --testjar or --classlist
if [ -n "$CLASSLIST" ]; then
  RANDOOP_CMD+=(--classlist="$CLASSLIST")
else
  RANDOOP_CMD+=(--testjar="$TEST_JAR")
fi

# Append any extra Randoop arguments passed after --
RANDOOP_CMD+=("$@")

# ---- Run -------------------------------------------------------------------
mkdir -p "$OUTPUT_DIR"
echo ">>> Time limit: ${TIME_LIMIT}s"
echo ">>> Output dir: $OUTPUT_DIR"
echo ">>> Randoop command:"
echo "    ${RANDOOP_CMD[*]}"
echo ""

"${RANDOOP_CMD[@]}"

echo ""
echo ">>> Done. Generated tests in $OUTPUT_DIR"
