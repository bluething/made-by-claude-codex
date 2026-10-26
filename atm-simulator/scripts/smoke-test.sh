#!/usr/bin/env bash
#
# End-to-end check of the real launcher:
#   1. start.sh replays the example session from the problem statement exactly;
#   2. a second run starts with an empty bank (no state survives a restart).

set -euo pipefail

readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly FIXTURES="${PROJECT_DIR}/src/test/resources/example-session"

readonly ACTUAL="$(mktemp)"
trap 'rm -f "${ACTUAL}"' EXIT

echo "smoke test: example session"
# Run first and diff after: inside a process substitution a failing start.sh would go unnoticed.
"${PROJECT_DIR}/start.sh" < "${FIXTURES}/input.txt" > "${ACTUAL}"
diff -u "${FIXTURES}/expected.txt" "${ACTUAL}"

echo "smoke test: fresh state on every start"
second_run="$(printf 'login Alice\n' | "${PROJECT_DIR}/start.sh")"
if ! grep -q 'Your balance is \$0' <<<"${second_run}"; then
    echo "expected a fresh bank, got:" >&2
    echo "${second_run}" >&2
    exit 1
fi

echo "smoke test: passed"
