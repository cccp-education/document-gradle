#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for document-gradle.
# The functional/BDD shards and the release publishing stay on the sharded jobs;
# this contract covers the static, unit and publication gates.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/document-plugin"

job="${1:-}"
case "$job" in
  fast)
    ./gradlew ktlintCheck validatePlugins --no-daemon --console=plain
    ;;
  test)
    ./gradlew test --no-daemon --console=plain
    ;;
  publish)
    case "${PUBLISH_TARGET:-local}" in
      local)
        ./gradlew publishToMavenLocal --no-daemon --console=plain
        ;;
      central)
        ./gradlew publishAggregationToCentralPortal --no-daemon --console=plain
        ;;
      *)
        printf 'unsupported publish target for document-gradle: %s\n' "$PUBLISH_TARGET" >&2
        printf 'supported: local | central\n' >&2
        exit 2
        ;;
    esac
    ;;
  *)
    printf 'unsupported job for document-gradle: %s\n' "${job:-<none>}" >&2
    printf 'supported: fast | test | publish\n' >&2
    exit 2
    ;;
esac
