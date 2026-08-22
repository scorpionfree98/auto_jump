#!/usr/bin/env bash
set -euo pipefail

readonly REMOTE_HOST="172.31.129.120"
readonly REMOTE_DIR="/home/kmkuang/codex_build/auto_jump_click"
readonly REMOTE_ANDROID_HOME="/home/kmkuang/codex_build/autojump-signing/android"
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
ssh "$REMOTE_HOST" bash -s -- "$REMOTE_DIR" "$REMOTE_ANDROID_HOME" "$IMAGE_NAME" "$@" <<'REMOTE_SCRIPT'
set -euo pipefail
remote_dir="$1"
android_home="$2"
image_name="$3"
shift 3

cd "$remote_dir"
mkdir -p "$android_home"
chmod 700 "$android_home"

# 服务器维护时 Docker 套接字可能短暂重置。只重试 Docker 自身的 125 错误；
# Gradle 编译或测试失败保持原始状态，不做可能掩盖问题的重试。
build_status=125
for attempt in 1 2 3; do
    set +e
    docker run --rm --network=host \
        -v "$remote_dir":/workspace \
        -v autojump-gradle-cache:/root/.gradle \
        -v "$android_home":/root/.android \
        -w /workspace \
        "$image_name" \
        ./gradlew "$@" --no-daemon
    build_status=$?
    set -e
    if [[ "$build_status" -ne 125 ]]; then
        break
    fi
    echo "Docker 暂时不可用，第 $attempt 次启动失败，等待后重试..." >&2
    sleep 3
done

# 容器以 root 写入挂载目录；恢复所有权，保证下一次 rsync 可以更新源码。
host_uid="$(id -u)"
host_gid="$(id -g)"
for attempt in 1 2 3; do
    if docker run --rm \
        -v "$remote_dir":/workspace \
        -v "$android_home":/android-signing \
        "$image_name" \
        sh -c 'chown -R "$1:$2" /workspace /android-signing && chmod 700 /android-signing && if [ -f /android-signing/debug.keystore ]; then chmod 600 /android-signing/debug.keystore; fi' sh "$host_uid" "$host_gid"; then
        break
    fi
    if [[ "$attempt" -eq 3 ]]; then
        echo "警告：构建目录所有权恢复失败，下次同步前需要处理。" >&2
    else
        sleep 3
    fi
done

exit "$build_status"
REMOTE_SCRIPT
