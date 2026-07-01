#!/usr/bin/env bash
# 参数化构建 APK：自动修改程序名称 / 默认跳转链接 / 包名，然后编译出 APK
#
# 用法示例：
#   ./build_apk.sh --name "飞书直达" \
#                  --link "example://page/path" \
#                  --package "com.autojump.feishu" \
#                  --output "feishu_jump.apk"
#
# 所有参数均可选，不传则用源码里的默认值。
set -euo pipefail

# ---------- 默认值 ----------
APP_NAME=""
DEFAULT_LINK=""
PACKAGE=""
OUTPUT="app-debug.apk"

# 脚本所在目录的上一级 = 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------- 解析参数 ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --name)    APP_NAME="$2"; shift 2 ;;
        --link)    DEFAULT_LINK="$2"; shift 2 ;;
        --package) PACKAGE="$2"; shift 2 ;;
        --output)  OUTPUT="$2"; shift 2 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

echo "==> 项目目录: $PROJECT_DIR"

# ---------- 备份将被修改的源码，退出时自动还原（保证源码不被污染） ----------
STRINGS_XML="$PROJECT_DIR/app/src/main/res/values/strings.xml"
SPUTIL_JAVA="$PROJECT_DIR/app/src/main/java/com/autojump/app/util/SPUtil.java"
BUILD_GRADLE="$PROJECT_DIR/app/build.gradle"
BACKUP_DIR="$(mktemp -d)"
cp "$STRINGS_XML"  "$BACKUP_DIR/strings.xml"
cp "$SPUTIL_JAVA"  "$BACKUP_DIR/SPUtil.java"
cp "$BUILD_GRADLE" "$BACKUP_DIR/build.gradle"
restore_sources() {
    cp "$BACKUP_DIR/strings.xml"  "$STRINGS_XML"
    cp "$BACKUP_DIR/SPUtil.java"  "$SPUTIL_JAVA"
    cp "$BACKUP_DIR/build.gradle" "$BUILD_GRADLE"
    rm -rf "$BACKUP_DIR"
    echo "==> 已还原源码到默认状态"
}
trap restore_sources EXIT

# ---------- 1. 修改程序名称 ----------
if [[ -n "$APP_NAME" ]]; then
    echo "==> 修改程序名称为: $APP_NAME"
    python3 - "$PROJECT_DIR/app/src/main/res/values/strings.xml" "$APP_NAME" <<'PY'
import sys, re
path, name = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    s = f.read()
s = re.sub(r'(<string name="app_name">).*?(</string>)',
           lambda m: m.group(1) + name + m.group(2), s)
with open(path, "w", encoding="utf-8") as f:
    f.write(s)
PY
fi

# ---------- 2. 修改默认跳转链接 ----------
if [[ -n "$DEFAULT_LINK" ]]; then
    echo "==> 修改默认跳转链接"
    python3 - "$PROJECT_DIR/app/src/main/java/com/autojump/app/util/SPUtil.java" "$DEFAULT_LINK" <<'PY'
import sys, re
path, link = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    s = f.read()
s = re.sub(r'(private static final String DEFAULT_DEEP_LINK = "";)',
           lambda m: m.group(1) + link + m.group(2), s)
with open(path, "w", encoding="utf-8") as f:
    f.write(s)
PY
fi

# ---------- 3. 修改包名（applicationId，不改源码 package 结构） ----------
if [[ -n "$PACKAGE" ]]; then
    echo "==> 修改 applicationId 为: $PACKAGE"
    python3 - "$PROJECT_DIR/app/build.gradle" "$PACKAGE" <<'PY'
import sys, re
path, pkg = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    s = f.read()
s = re.sub(r'(applicationId ").*?(")',
           lambda m: m.group(1) + pkg + m.group(2), s)
with open(path, "w", encoding="utf-8") as f:
    f.write(s)
PY
fi

# ---------- 4. 编译 ----------
echo "==> 开始编译 APK ..."
cd "$PROJECT_DIR"

# 把 http_proxy 环境变量转成 Gradle 能用的 JVM 代理参数（容器内下载依赖需要）
GRADLE_PROXY_ARGS=""
if [[ -n "${http_proxy:-}" ]]; then
    PROXY_HOST=$(echo "$http_proxy" | sed -E 's#https?://##; s#:.*##')
    PROXY_PORT=$(echo "$http_proxy" | sed -E 's#.*:##; s#/##g')
    GRADLE_PROXY_ARGS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
    echo "==> Gradle 代理: $PROXY_HOST:$PROXY_PORT"
fi

# 加大下载超时，应对国内镜像偶发的 Read timed out；失败自动重试若干次
GRADLE_NET_ARGS="-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=120000"

BUILD_OK=0
for attempt in 1 2 3; do
    echo "==> 编译尝试 $attempt/3 ..."
    if gradle :app:assembleDebug --no-daemon $GRADLE_PROXY_ARGS $GRADLE_NET_ARGS; then
        BUILD_OK=1
        break
    fi
    echo "==> 第 $attempt 次失败（多为下载超时），10s 后重试..."
    sleep 10
done

if [[ "$BUILD_OK" -ne 1 ]]; then
    echo "==> 编译失败，请检查网络或日志"
    exit 1
fi

# ---------- 5. 复制产物 ----------
BUILT_APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
cp "$BUILT_APK" "$PROJECT_DIR/$OUTPUT"
echo "==> 编译完成: $PROJECT_DIR/$OUTPUT"
