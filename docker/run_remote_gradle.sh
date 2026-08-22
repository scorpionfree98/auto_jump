#!/usr/bin/env bash
set -euo pipefail

readonly REMOTE_HOST="172.31.129.120"
readonly REMOTE_DIR="/home/kmkuang/codex_build/auto_jump_click"
readonly IMAGE_NAME="autojump-builder-click"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -eq 0 ]]; then
    echo "用法: $0 <Gradle任务> [Gradle任务...]" >&2
    echo "示例: $0 :app:testDebugUnitTest :app:assembleDebug" >&2
    exit 1
fi

for task in "$@"; do
    if [[ ! "$task" =~ ^(clean|wrapper|:[A-Za-z0-9_.-]+(:[A-Za-z0-9_.-]+)*)$ ]]; then
        echo "错误：不允许的 Gradle 参数: $task" >&2
        exit 1
    fi
done

bash "$SCRIPT_DIR/sync_to_builder.sh"

echo "==> 在 120 Docker 环境执行: $*"
ssh "$REMOTE_HOST" bash -s -- "$REMOTE_DIR" "$IMAGE_NAME" "$@" <<'REMOTE_SCRIPT'
set -euo pipefail
remote_dir="$1"
image_name="$2"
shift 2

cd "$remote_dir"
set +e
docker run --rm --network=host \
    -v "$remote_dir":/workspace \
    -v autojump-gradle-cache:/root/.gradle \
    -w /workspace \
    "$image_name" \
    ./gradlew "$@" --no-daemon
build_status=$?
set -e

# 容器以 root 写入挂载目录；恢复所有权，保证下一次 rsync 可以更新源码。
host_uid="$(id -u)"
host_gid="$(id -g)"
docker run --rm \
    -v "$remote_dir":/workspace \
    "$image_name" \
    chown -R "$host_uid:$host_gid" /workspace

exit "$build_status"
REMOTE_SCRIPT
