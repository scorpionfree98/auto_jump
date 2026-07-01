#!/usr/bin/env bash
# APK 改包名 + 重签名工具
#
# 输入：已编译好的 APK 文件
# 操作：解包 → 改包名/应用名 → 回编译 → 重新签名
# 输出：新的 APK（可与原版共存安装）
#
# 用法：
#   ./repack_apk.sh input.apk \
#       --name "新应用名" \
#       --package "com.new.package" \
#       --output "output.apk"
#
# 依赖：Docker（会自动构建包含 apktool/apksigner 的镜像）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="apk-repacker"

# ---------- 解析参数 ----------
INPUT_APK=""
NEW_NAME=""
NEW_PACKAGE=""
OUTPUT_APK="repacked.apk"

show_usage() {
    cat <<EOF
用法: $0 <input.apk> [选项]

必需参数:
  input.apk              待修改的原始 APK 文件路径

可选参数:
  --name <新名字>        修改应用显示名称（桌面图标名字）
  --package <新包名>     修改 applicationId（如 com.app.v2）
  --output <输出路径>    输出文件名（默认 repacked.apk）

示例:
  $0 原版.apk --name "飞书直达" --package "com.feishu.jump" --output 飞书版.apk
  $0 app.apk --package "com.app.clone" --output app-clone.apk
EOF
    exit 1
}

if [[ $# -lt 1 ]]; then
    show_usage
fi

INPUT_APK="$1"
shift

if [[ ! -f "$INPUT_APK" ]]; then
    echo "错误：找不到输入文件 $INPUT_APK"
    exit 1
fi

while [[ $# -gt 0 ]]; do
    case "$1" in
        --name)    NEW_NAME="$2"; shift 2 ;;
        --package) NEW_PACKAGE="$2"; shift 2 ;;
        --output)  OUTPUT_APK="$2"; shift 2 ;;
        -h|--help) show_usage ;;
        *) echo "未知参数: $1"; show_usage ;;
    esac
done

if [[ -z "$NEW_NAME" && -z "$NEW_PACKAGE" ]]; then
    echo "错误：至少需要指定 --name 或 --package 其中之一"
    show_usage
fi

INPUT_APK="$(realpath "$INPUT_APK")"
OUTPUT_APK="$(realpath "$OUTPUT_APK")"

echo "==> 输入: $INPUT_APK"
[[ -n "$NEW_NAME" ]] && echo "    新名字: $NEW_NAME"
[[ -n "$NEW_PACKAGE" ]] && echo "    新包名: $NEW_PACKAGE"
echo "    输出: $OUTPUT_APK"

# ---------- 构建/复用 Docker 镜像 ----------
echo "==> 构建/复用 APK 改包镜像..."
docker build --network=host -t "$IMAGE_NAME" -f "$SCRIPT_DIR/Dockerfile.repack" "$SCRIPT_DIR"

# ---------- 在容器内执行改包 ----------
echo "==> 开始改包..."
WORK_DIR="/work"
docker run --rm --network=host \
    -v "$(dirname "$INPUT_APK")":/input:ro \
    -v "$(dirname "$OUTPUT_APK")":/output \
    -e INPUT_APK="/input/$(basename "$INPUT_APK")" \
    -e OUTPUT_APK="/output/$(basename "$OUTPUT_APK")" \
    -e NEW_NAME="$NEW_NAME" \
    -e NEW_PACKAGE="$NEW_PACKAGE" \
    "$IMAGE_NAME"

echo "==> 完成: $OUTPUT_APK"
