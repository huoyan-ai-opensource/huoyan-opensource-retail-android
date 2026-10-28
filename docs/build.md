# 构建 App 和 SDK JAR

[文档导航](README.md) · [首次运行](../QUICKSTART.md) · [测试](testing.md) · [发布](releasing.md)

## 环境

从仓库根目录构建，使用仓库自带的 Gradle Wrapper，不需要另外安装 Gradle。

| 组件 | 当前配置 |
| --- | --- |
| 构建 Java | JDK / JBR 17；Android Studio 的 Gradle JDK 也选 17 |
| Java 源码级别 | 11 |
| Gradle / Android Gradle Plugin | 7.5 / 7.4.2 |
| Android SDK | Platform 33、Build Tools 30.0.3、Platform Tools |
| Android NDK | 25.2.9519653 |
| CMake | 3.22.1 |
| App 系统范围 | minSdk 24，targetSdk 28；硬件和配套 SDK 要求见[硬件说明](hardware.md) |

当前 Wrapper 不支持 Java 21。如果新版 Android Studio 默认带 Java 21，请单独安装 JDK/JBR 17，再在 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** 中选择它。不要仅修改项目源码级别。

在 Android Studio 的 **SDK Manager → SDK Tools → Show Package Details** 中安装上表 NDK 和 CMake；**SDK Platforms** 中安装 Android 13 / API 33。命令行也可以使用 SDK Manager：

```bash
sdkmanager "platform-tools" "platforms;android-33" "build-tools;30.0.3" "ndk;25.2.9519653" "cmake;3.22.1"
sdkmanager --licenses
```

首次同步和构建需要联网下载依赖；仓库使用 Google Maven、Central 镜像、JitPack 等仓库。已有完整缓存时可加 `--offline`，缺少依赖时不能离线首次构建。

## 获取源码并设置 SDK 路径

```bash
git clone https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android.git
cd huoyan-opensource-retail-android
```

在 Android Studio 中打开此目录，等待 Gradle 同步。Studio 通常自动创建 `local.properties`。命令行用户将 [local.properties.example](../local.properties.example) 复制为 `local.properties`，把 `sdk.dir` 换成本机 Android SDK 路径。Windows 路径可用正斜杠，例如 `C:/Users/your-name/AppData/Local/Android/Sdk`。不要提交本机 `local.properties`。

## Windows PowerShell

路径仅为示例，请换成自己的 JDK 17 安装位置。

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
```

CMD 对应写法：

```bat
set "JAVA_HOME=C:\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat :app:assembleDebug
```

## macOS / Linux

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
java -version
bash gradlew --version
bash gradlew :app:assembleDebug
```

macOS 安装 JDK 后也可用 `/usr/libexec/java_home -v 17` 查找其路径。Wrapper 没有执行权限时使用 `bash gradlew` 即可。

## 常用任务和产物

以下以 Bash 展示；Windows 将 `bash gradlew` 替换为 `.\gradlew.bat`。

| 目标 | 命令 | 产物 |
| --- | --- | --- |
| 调试 App | `bash gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/*.apk` |
| Release App | `bash gradlew :app:assembleRelease` | `app/build/outputs/apk/release/*.apk` |
| 独立 JAR | `bash gradlew :huoyan-sdk-client:clientJar` | `huoyan-sdk-client/build/outputs/jar/huoyan-sdk-client-2.0.0.jar` |
| 完整 JAR 交付包 | `bash gradlew :huoyan-sdk-client:clientDistribution` | `huoyan-sdk-client/build/distributions/huoyan-sdk-client-2.0.0.zip` |
| 更新 App 中的 JAR | `bash gradlew :huoyan-sdk-client:updateAppSdkJar` | 替换 `app/src/main/libs` 中对应 JAR |

APK 文件名为 `huoyan-retail-{versionName}-{versionCode}-{时间戳}-{buildType}.apk`。构建成功不代表已激活或模型已就绪，安装后的步骤见[快速开始](../QUICKSTART.md)。

**App 使用仓库内已打包 JAR，不直接依赖 SDK module。** 修改 `huoyan-sdk-client` 源码后，先执行 `updateAppSdkJar`，再构建和测试 App。只运行 `assembleDebug` 不会更新 JAR。接入自己应用时使用完整 ZIP 中的依赖与文档，见 [JAR 接入指南](../huoyan-sdk-client/INTEGRATION_GUIDE.md)。

## 签名与安装

Debug 使用本机 Android 工具生成的调试签名；Release 默认使用随仓库公开的实验签名。该实验签名只用于非商业的开源版本实验及测试 Release，不得用于商业发布，详细规则见[签名说明](../signing/README.md)。

正式发布需在未入库的 `local.properties` 中同时提供 `signing.storeFile`、`signing.storePassword`、`signing.keyAlias`、`signing.keyPassword`，示例中的签名字段均为注释，必须替换为自己的配置。不要将正式私钥、密码或激活码提交到仓库。

不同签名的 APK 不能直接覆盖安装。同一包名从 Debug 切换为实验 Release、或从实验签名切换为自己的签名时，先做好数据备份，再根据 Android 安装提示处理；卸载会删除 App 本地数据，不要直接对业务设备卸载测试。

## 常见构建问题

| 现象 | 排查方式 |
| --- | --- |
| `Unsupported class file major version 65` | 检查 `JAVA_HOME` 和 Studio Gradle JDK，均使用 17，重启 Gradle 同步 |
| `SDK location not found` | 在 `local.properties` 填写正确的 `sdk.dir`，确认是 SDK 根目录 |
| NDK / CMake 缺失 | SDK Manager 安装上表精确版本；不要随意修改项目版本绕过缺项 |
| Maven 下载失败、超时 | 检查报错仓库、网络和代理；先恢复依赖下载，再重试；离线模式不能补齐缺失依赖 |
| 编译通过但 App 仍使用旧 SDK 行为 | 重新执行 `:huoyan-sdk-client:updateAppSdkJar` 后再构建 App |
| Gradle 输出编码异常 | 使用 UTF-8 终端；项目 JVM 已配置 `-Dfile.encoding=UTF-8` |

提交构建问题时附上 Java/Gradle 版本、任务名和第一个失败原因，并先移除本机用户名、访问令牌及私密服务信息。
