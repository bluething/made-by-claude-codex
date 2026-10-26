#!/usr/bin/env bash
#
# Builds the application and starts an interactive ATM session.
# Every run starts with an empty in-memory bank.
#
# Build output goes to stderr and Maven reads from /dev/null, so stdout only
# contains the ATM session and piped commands reach the application untouched.

set -euo pipefail

readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly JAR="${PROJECT_DIR}/target/atm-simulator.jar"

"${PROJECT_DIR}/mvnw" --file "${PROJECT_DIR}/pom.xml" \
    --quiet --batch-mode --no-transfer-progress \
    -DskipTests -Djacoco.skip=true \
    package </dev/null 1>&2

exec java -jar "${JAR}" "$@"
