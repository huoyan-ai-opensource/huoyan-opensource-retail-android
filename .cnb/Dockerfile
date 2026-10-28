FROM ubuntu:22.04

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates curl git unzip libfontconfig1 libfreetype6 libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*

# 固定为与本地相同的 JBR 17，避免 Gradle 7.5 在 Java 21 下无法启动。
ENV JAVA_HOME=/opt/jbr
RUN curl --fail --location --retry 3 \
        https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-linux-x64-b1367.22.tar.gz \
        --output /tmp/jbr.tar.gz \
    && echo 'cb2de787894fbc42b59ed6c867f933083ce8c80d11fbd944f02d6144f6607671303a278c684777829b53d977e93b36a95122830b22ad602d0662d0433f8e7cf5  /tmp/jbr.tar.gz' | sha512sum --check \
    && mkdir -p "${JAVA_HOME}" \
    && tar -xzf /tmp/jbr.tar.gz -C "${JAVA_HOME}" --strip-components=1 \
    && rm /tmp/jbr.tar.gz

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# AGP 7.4.2 默认使用 Build Tools 30.0.3；NDK/CMake 与 app/build.gradle 保持一致。
RUN curl --fail --location --retry 3 \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        --output /tmp/android-tools.zip \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/android-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/android-tools.zip \
    && (yes | sdkmanager --licenses > /dev/null; test "${PIPESTATUS[1]}" -eq 0) \
    && sdkmanager "platform-tools" "platforms;android-33" "build-tools;30.0.3" \
        "ndk;25.2.9519653" "cmake;3.22.1"
