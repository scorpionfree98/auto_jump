#!/usr/bin/env bash
set -euo pipefail

readonly REMOTE_HOST="172.31.129.120"
readonly REMOTE_DIR="/home/kmkuang/codex_build/auto_jump_click"
readonly CONTAINER="autojump-emulator"
readonly REMOTE_ADB="/android/sdk/platform-tools/adb"
readonly SYSTEM_IMAGE_URL="https://dl.google.com/android/repository/sys-img/android/x86_64-28_r04.zip"
readonly SYSTEM_IMAGE_SHA1="d47a85c8f4e9fd57df97814ad8884eeb0f3a0ef0"
readonly REMOTE_CACHE="/home/kmkuang/codex_build/android-emulator-cache"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command_name="${1:-status}"

case "$command_name" in
  build)
    bash "$SCRIPT_DIR/sync_to_builder.sh"
    ssh "$REMOTE_HOST" \
      "mkdir -p '$REMOTE_CACHE'; if ! echo '$SYSTEM_IMAGE_SHA1  $REMOTE_CACHE/x86_64-28_r04.zip' | sha1sum -c - >/dev/null 2>&1; then aria2c -x16 -s16 -k1M --allow-overwrite=true --auto-file-renaming=false -d '$REMOTE_CACHE' -o x86_64-28_r04.zip '$SYSTEM_IMAGE_URL'; fi; echo '$SYSTEM_IMAGE_SHA1  $REMOTE_CACHE/x86_64-28_r04.zip' | sha1sum -c -; docker build -f '$REMOTE_DIR/docker/emulator/Dockerfile.aosp' -t autojump-emulator:aosp-api28 '$REMOTE_CACHE'"
    ;;
  start)
    bash "$SCRIPT_DIR/sync_to_builder.sh"
    ssh "$REMOTE_HOST" \
      "cd '$REMOTE_DIR' && docker compose -f docker/emulator/compose.yaml up -d"
    ;;
  stop)
    ssh "$REMOTE_HOST" \
      "cd '$REMOTE_DIR' && docker compose -f docker/emulator/compose.yaml stop"
    ;;
  status)
    ssh "$REMOTE_HOST" \
      "docker ps --filter name=^/${CONTAINER}$ --format '{{.Names}} {{.Status}}'; docker exec '$CONTAINER' '$REMOTE_ADB' devices 2>/dev/null || true; docker exec '$CONTAINER' '$REMOTE_ADB' shell getprop sys.boot_completed 2>/dev/null || true"
    ;;
  wait)
    ssh "$REMOTE_HOST" \
      "docker exec '$CONTAINER' '$REMOTE_ADB' wait-for-device; until [ \"\$(docker exec '$CONTAINER' '$REMOTE_ADB' shell getprop sys.boot_completed 2>/dev/null | tr -d '\\r')\" = 1 ]; do sleep 5; done; echo emulator-ready"
    ;;
  install)
    apk_path="${2:-app/build/outputs/apk/debug/app-debug.apk}"
    if [[ ! "$apk_path" =~ ^[A-Za-z0-9_./-]+\.apk$ ]] || [[ "$apk_path" == /* ]] || [[ "$apk_path" == *".."* ]]; then
      echo "Invalid project-relative APK path: $apk_path" >&2
      exit 2
    fi
    ssh "$REMOTE_HOST" \
      "docker exec '$CONTAINER' '$REMOTE_ADB' install -r '/workspace/$apk_path'"
    ;;
  adb)
    shift
    if [[ $# -eq 0 ]]; then
      echo "Usage: $0 adb <adb arguments>" >&2
      exit 2
    fi
    ssh "$REMOTE_HOST" docker exec "$CONTAINER" "$REMOTE_ADB" "$@"
    ;;
  *)
    echo "Usage: $0 {build|start|stop|status|wait|install|adb ...}" >&2
    exit 2
    ;;
esac
