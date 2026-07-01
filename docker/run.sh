#!/usr/bin/env bash
# 一键封装：构建 Docker 镜像（首次较慢，之后有缓存），并在容器内编译 APK。
# 用 --network=host 复用主机网络，全部走国内镜像源直连。所有参数透传给 build_apk.sh。
#
# 示例：
#   ./run.sh --name "飞书直达" --package "com.autojump.feishu" --output "feishu.apk"
#   ./run.sh --name "钉钉直达" --link "dingtalk://xxx" --output "dingtalk.apk"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IMAGE_NAME="autojump-builder"

echo "==> 构建/复用 Docker 镜像: $IMAGE_NAME"
docker build --network=host -t "$IMAGE_NAME" "$SCRIPT_DIR"

echo "==> 在容器内编译 APK"
# 把项目目录挂进容器，Gradle 缓存也挂出来加速二次构建。
docker run --rm --network=host \
    -v "$PROJECT_DIR":/workspace \
    -v autojump-gradle-cache:/root/.gradle \
    -w /workspace \
    "$IMAGE_NAME" \
    bash docker/build_apk.sh "$@"

echo "==> 完成，APK 在项目根目录"
