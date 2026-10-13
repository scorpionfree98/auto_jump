# Android APK 参数化编译环境
# 基于本地已有的 ubuntu:22.04；全部使用国内镜像源，不依赖代理。
FROM ubuntu:22.04

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:/opt/gradle-8.2/bin
ENV DEBIAN_FRONTEND=noninteractive

# 本镜像配合 `docker build --network=host` 使用：直接复用主机网络，
# apt→阿里云、SDK→dl.google.com 直连、gradle→腾讯云，均国内可达，无需代理。

# apt 换阿里云源
RUN sed -i 's|http://archive.ubuntu.com|http://mirrors.aliyun.com|g; s|http://security.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless curl unzip ca-certificates python3 && \
    rm -rf /var/lib/apt/lists/*

# 安装 Android 命令行工具（dl.google.com 主机直连可用）
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    cd $ANDROID_SDK_ROOT/cmdline-tools && \
    curl -fsSL -o tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q tools.zip && \
    mv cmdline-tools latest && \
    rm tools.zip

# 接受许可并安装编译所需 SDK 组件
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true && \
    sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;34.0.0" >/dev/null

# 安装 Gradle 8.2（腾讯云镜像，与 AGP 8.1 匹配）
RUN curl -fsSL -o /tmp/gradle.zip https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip

WORKDIR /workspace
