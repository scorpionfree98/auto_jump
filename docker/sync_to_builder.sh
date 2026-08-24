#!/usr/bin/env bash
set -euo pipefail

readonly REMOTE_HOST="172.31.129.120"
readonly REMOTE_DIR="/home/kmkuang/codex_build/auto_jump_click"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ ! -f "$PROJECT_DIR/settings.gradle" || ! -f "$PROJECT_DIR/app/build.gradle" ]]; then
    echo "错误：无法确认 AutoJump 项目根目录: $PROJECT_DIR" >&2
    exit 1
fi

if [[ "$REMOTE_DIR" != "/home/kmkuang/codex_build/auto_jump_click" ]]; then
    echo "错误：远程目录不在允许范围内" >&2
    exit 1
fi

echo "==> 准备远程构建目录"
ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"

echo "==> 同步源码到 $REMOTE_HOST:$REMOTE_DIR"
rsync -az --delete \
    --exclude '.git' \
    --exclude '.gradle' \
    --exclude '.idea' \
    --exclude '.DS_Store' \
    --exclude 'build' \
    --exclude '*/build' \
    --exclude 'artifacts' \
    --exclude '*.apk' \
    --exclude '*.aab' \
    --exclude 'local.properties' \
    "$PROJECT_DIR/" "$REMOTE_HOST:$REMOTE_DIR/"

echo "==> 同步完成"
