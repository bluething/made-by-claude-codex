#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Build output goes to stderr so stdout only contains the ATM session.
./mvnw -q -B -ntp -DskipTests package </dev/null 1>&2

exec java -jar target/atm-cli.jar
