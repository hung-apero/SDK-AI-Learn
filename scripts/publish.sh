#!/usr/bin/env bash
set -euo pipefail

# Publish SDK-AI-Learn artifacts to local (~/.m2) and/or remote (Apero Artifactory).
#
# Usage:
#   scripts/publish.sh --local
#   scripts/publish.sh --remote
#   scripts/publish.sh --local --remote
#   scripts/publish.sh --all              # alias for --local --remote
#   scripts/publish.sh --local --no-clean # skip clean step

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODULES=(
  ":ai-speech"
)

DO_LOCAL=0
DO_REMOTE=0
DO_CLEAN=1

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 [--local] [--remote] [--all] [--no-clean]" >&2
  exit 1
fi

for arg in "$@"; do
  case "$arg" in
    --local)    DO_LOCAL=1 ;;
    --remote)   DO_REMOTE=1 ;;
    --all)      DO_LOCAL=1; DO_REMOTE=1 ;;
    --no-clean) DO_CLEAN=0 ;;
    -h|--help)
      sed -n '3,11p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $arg" >&2; exit 1 ;;
  esac
done

TASKS=()
for m in "${MODULES[@]}"; do
  [[ $DO_CLEAN  -eq 1 ]] && TASKS+=("$m:clean")
  [[ $DO_LOCAL  -eq 1 ]] && TASKS+=("$m:publishToMavenLocal")
  [[ $DO_REMOTE -eq 1 ]] && TASKS+=("$m:publishMavenPublicationToReleaseRepository")
done

echo "==> ./gradlew ${TASKS[*]}"
./gradlew "${TASKS[@]}"
# Final artifact coordinates + URL are printed by the publish tasks themselves
# (see ai-speech/build.gradle.kts → tasks.withType<PublishTo*>{ doLast { ... } }).
