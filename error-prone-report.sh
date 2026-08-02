#!/usr/bin/env bash

set -uo pipefail

if ! project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"; then
    printf 'Unable to determine project directory.\n' >&2
    exit 1
fi

report_dir="$project_dir/build/reports/errorprone"
report_file="$report_dir/compile.log"

if ! mkdir -p "$report_dir"; then
    printf 'Unable to create report directory: %s\n' "$report_dir" >&2
    exit 1
fi

cd "$project_dir" || exit 1

"$project_dir/gradlew" \
    --project-dir "$project_dir" \
    --rerun-tasks \
    --console=plain \
    --info \
    compileJava \
    compileTestJava \
    2>&1 | tee "$report_file"
pipeline_status=("${PIPESTATUS[@]}")

gradle_status="${pipeline_status[0]}"
tee_status="${pipeline_status[1]}"

if (( gradle_status != 0 )); then
    exit "$gradle_status"
fi

if (( tee_status != 0 )); then
    exit "$tee_status"
fi

if [[ ! -s "$report_file" ]]; then
    printf 'Error Prone report is empty: %s\n' "$report_file" >&2
    exit 1
fi

exit 0
