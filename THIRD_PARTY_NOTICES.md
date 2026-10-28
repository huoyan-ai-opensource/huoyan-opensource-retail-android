# 第三方组件与版权说明

本文件整理 App、客户端 JAR 及仓库随附二进制所涉及的第三方组件。火眼自有源码的使用及 SDK 激活规则见 [USAGE.md](USAGE.md)；该说明不替代第三方组件自己的许可证。

清单依据 `app/build.gradle`、`huoyan-sdk-client/build.gradle`、`gradle/libs.versions.toml`、本地二进制和相应版本的上游许可证/POM 整理，并于 2026-10-28 核对 App 的 `releaseRuntimeClasspath`。下表的版本是构建中声明的版本；Gradle 可能因传递依赖选择更高版本。查看实际 App 依赖可在项目根目录执行：

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```

完整条款集中保存在[原文汇总](docs/licenses/THIRD_PARTY_LICENSES.txt)和[中文参考译文汇总](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md)中；下表可直接进入对应的中文章节。

## Gradle 声明的组件

| 组件 / Maven 坐标 | 声明版本 | 上游许可及来源 |
| --- | --- | --- |
| [Glide](https://github.com/bumptech/glide/tree/v4.16.0)，`com.github.bumptech.glide:glide` | 4.16.0 | BSD 与部分代码的 Apache/MIT 等声明，见完整 [Glide 许可](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#glide)，不要将整个组件只标为 Apache |
| [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket/tree/v1.5.2)，`org.java-websocket:Java-WebSocket` | 1.5.2 | [MIT](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#java-websocket)，Copyright 2010–2020 Nathan Rajlich |
| [fastjson](https://github.com/alibaba/fastjson/tree/1.2.47)，`com.alibaba:fastjson` | 1.2.47 | Apache 2.0，上游 POM 声明 |
| [OkHttp](https://github.com/square/okhttp/tree/parent-4.10.0)，`com.squareup.okhttp3:okhttp` | 4.10.0 | Apache 2.0，上游 POM 声明 |
| [Kotlin 标准库](https://github.com/JetBrains/kotlin/tree/v1.8.20)，`org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.8.20 | Apache 2.0；同版本 `kotlin-stdlib`、`kotlin-stdlib-jdk7` 沿用其各自声明 |
| [Gson](https://github.com/google/gson/tree/gson-parent-2.11.0)，`com.google.code.gson:gson` | 2.11.0 | Apache 2.0，上游 POM 声明 |
| [EventBus](https://github.com/greenrobot/EventBus/tree/V3.1.1)，`org.greenrobot:eventbus` | 3.1.1 | Apache 2.0，上游 POM 声明 |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat)，`androidx.appcompat:appcompat` | 1.3.1；实际解析 1.4.1 | Apache 2.0；UVCAndroid 同时请求 1.4.1 |
| [AndroidX RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview)，`androidx.recyclerview:recyclerview` | 1.2.1 | Apache 2.0，上游 POM 声明 |
| [AndroidX ConstraintLayout](https://github.com/androidx/constraintlayout)，`androidx.constraintlayout:constraintlayout` | 2.1.4 | Apache 2.0，上游 POM 声明 |
| [AndroidX WorkManager](https://developer.android.com/jetpack/androidx/releases/work)，`androidx.work:work-runtime` | 2.8.1 | Apache 2.0，上游 POM 声明 |
| [Material Components](https://github.com/material-components/material-components-android/tree/1.4.0)，`com.google.android.material:material` | 1.4.0 | Apache 2.0，上游 POM 声明 |
| [ZXing Core](https://github.com/zxing/zxing/tree/zxing-3.4.1)，`com.google.zxing:core` | 3.4.1 | Apache 2.0，上游项目及父 POM 声明 |
| [xCrash](https://github.com/iqiyi/xCrash/tree/v3.0.0)，`com.iqiyi.xcrash:xcrash-android-lib` | 3.0.0 | 主体 MIT，另含公共领域及 BSD 等代码，见完整 [xCrash 许可](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#xcrash) |
| [UVCAndroid](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6)，`com.herohan:UVCAndroid` | 1.0.6 | 顶层 Apache 2.0；随附 native 组件分别列在下节 |
| [AndroidX Annotation](https://developer.android.com/jetpack/androidx/releases/annotation)，`androidx.annotation:annotation` | 1.2.0，JAR module 的 compileOnly | Apache 2.0；其他库可能请求更高版本 |

以上 Apache 2.0 组件的许可证全文见 [Apache 2.0 原文](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#apache-20)。组件各自的版权、NOTICE 和源文件头仍归原作者所有。

### 主要传递依赖

| 组件 | 引入关系与版本依据 | 许可与来源 |
| --- | --- | --- |
| [Okio](https://github.com/square/okio/tree/parent-3.0.0)，`com.squareup.okio:okio` / `okio-jvm` | OkHttp 4.10.0 引入，实际解析 3.0.0 | Apache 2.0，Okio POM |
| [SLF4J API](https://www.slf4j.org/license.html)，`org.slf4j:slf4j-api` | Java-WebSocket 1.5.2 引入，实际解析 1.7.25 | [MIT](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#slf4j)，Copyright 2004–2017 QOS.ch |
| [Hutool Core](https://github.com/dromara/hutool/tree/5.6.3)，`cn.hutool:hutool-core` | UVCAndroid 1.0.6 引入，实际解析 5.6.3 | [木兰宽松许可证第 2 版](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#hutool) |
| [JetBrains Annotations](https://github.com/JetBrains/java-annotations)，`org.jetbrains:annotations` | Kotlin 标准库引入，实际解析 13.0 | Apache 2.0，上游 POM 声明 |
| [Error Prone Annotations](https://github.com/google/error-prone)，`com.google.errorprone:error_prone_annotations` | Gson 引入，实际解析 2.27.0 | Apache 2.0，上游项目声明 |
| [Guava ListenableFuture](https://github.com/google/guava)，`com.google.guava:listenablefuture` | AndroidX Concurrent 引入，实际解析 1.0 | Apache 2.0，上游项目及父 POM 声明 |
| AndroidX Core、Activity、Fragment、Lifecycle、SavedState、Collection、Loader、Arch Core、Legacy 等 | AppCompat、Material、WorkManager 和 UVCAndroid 引入；各模块实际版本由解析结果确定 | Apache 2.0，来源 [AndroidX](https://cs.android.com/androidx/platform/frameworks/support) |
| Glide 的 gifdecoder、disklrucache、annotations 等 | Glide 4.16.0 引入，具体项以解析结果为准 | 保留 [Glide 完整许可](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#glide)中各部分的声明 |

JAR 分发 ZIP 还会收录 `releaseRuntimeClasspath` 中的运行时 JAR。它们仍按各自许可证使用，不因被放进同一个 ZIP 而转为火眼自有代码。

本次解析中，AndroidX Annotation 为 1.5.0、Core 为 1.7.0、Activity 为 1.2.4、Fragment 为 1.3.6、Room 为 2.5.0、SQLite 为 2.3.0；Kotlin 标准库系列统一为 1.8.20。依赖升级后应以重新解析的结果同步本清单。

## UVCAndroid 的 native 组件与本项目改动

UVCAndroid 1.0.6 的 AAR 随附 `libUVCCamera.so`、`libuvc.so`、`libusb1.0.so` 和 `libjpeg-turbo212.so`。上游 `UVCCamera.cpp` 文件头明确将 `libjpeg`、`libusb`、`libuvc`、`rapidjson` 与顶层 Apache 许可区分。

| 部分 | 对应上游源码 | 许可原文 |
| --- | --- | --- |
| UVCCamera Java / JNI 封装 | [UVCAndroid 1.0.6](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6/libuvccamera)；UVCCamera 源码头保留 Copyright 2014–2017 saki | [Apache 2.0](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#apache-20) |
| libuvc | [对应版本源码](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6/libuvccamera/src/main/jni/libuvc)，Copyright 2010–2015 Ken Tossell | [BSD](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#uvcandroid-libuvc) |
| libusb | [对应版本源码与构建文件](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6/libuvccamera/src/main/jni/libusb) | [LGPL 2.1](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#uvcandroid-libusb)；保留该库的源码获取和再分发条件 |
| libjpeg-turbo | [对应版本源码](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6/libuvccamera/src/main/jni/libjpeg-turbo) | [上游许可汇总](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#uvcandroid-libjpeg-turbo)及 [IJG 原文](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#ijg-readme)，包括 IJG、BSD 和 zlib 条款 |
| RapidJSON | [对应版本源码](https://github.com/shiyinghan/UVCAndroid/tree/1.0.6/libuvccamera/src/main/jni/rapidjson) | [MIT](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#uvcandroid-rapidjson)，Copyright 2011 Milo Yip |

This software is based in part on the work of the Independent JPEG Group.

本项目在 `app/build.gradle` 中通过字节码转换，将 UVCAndroid 的 `USBMonitor` 四处 USB 枚举调用替换为本项目的 `UsbDeviceEnumerator`。相机和 native 实现保持上游依赖提供的版本；修改位置与用途记录于构建脚本，不移除上游版权声明。

## 仓库随附的本地二进制

| 文件 | 当前构建用途 | 来源及授权说明 |
| --- | --- | --- |
| `app/src/main/libs/huoyan-sdk-client-2.0.0.jar` | App 显式引用的火眼客户端 JAR | 本仓库 `huoyan-sdk-client` module 生成；识别 SDK 激活及使用规则见 [USAGE.md](USAGE.md)和[接入指南](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/blob/main/huoyan-sdk-client/INTEGRATION_GUIDE.md) |
| `app/src/main/libs/AclasDriverSdk.jar` | App 显式引用的 Aclas 外设驱动 | 供应商组件；仓库未附独立许可证文本，使用及再分发范围应以供应商提供的授权为准，不能从本项目源码开放规则推导 |
| `app/src/main/jniLibs/arm64-v8a/libAclasArmPos.so`、`armeabi-v7a/libAclasArmPos.so` | 随 App 打包的 Aclas native 驱动 | 与供应商 JAR 同样单独确认授权 |
| `app/src/main/libs/pinyin/pinyin4j-2.5.0.jar` | App 显式引用的拼音库 | 本地文件的来源与原始许可文本尚需确认，详见下文 |

### pinyin4j 文件来源记录

本地 JAR 大小为 `188920` 字节，SHA-1 为 `ae353005f973da1cd8545b54354504c2503f6865`，Manifest 标注 `Built-By: Li Min`，未包含许可证或 Maven POM。

Maven Central 的 `com.belerweb:pinyin4j:2.5.0` POM 声明 BSD，但其公开 JAR SHA-1 为 `5a620aedec0f779f2123ba60fd4b74bfbd3e2646`，与本地文件不同。因此这里不将该 Maven 构件的 BSD 声明用作本地 JAR 已获许可的结论，也不单凭同名就推定它属于其他许可版本。需根据本地文件的实际交付来源补入对应许可。

参考：[Maven POM](https://repo.maven.apache.org/maven2/com/belerweb/pinyin4j/2.5.0/pinyin4j-2.5.0.pom)、[Maven JAR SHA-1](https://repo.maven.apache.org/maven2/com/belerweb/pinyin4j/2.5.0/pinyin4j-2.5.0.jar.sha1)。SHA-1 在此只用于区分文件来源，不作为发布安全校验方案。

## 保留的源文件版权

`app/src/main/java/cc/huoyan/retail/device/DeviceUtils.java` 保留 `Copyright 2017 JessYan` 和 Apache 2.0 文件头，来源作者链接为 [JessYanCoding](https://github.com/JessYanCoding)。该文件的声明保持有效，不因迁入本仓库而变为火眼原创。

## 测试与构建组件

| 组件 | 版本 / 用途 | 许可 |
| --- | --- | --- |
| JUnit 4 | 4.13.2；单元测试 | [EPL 1.0](docs/licenses/THIRD_PARTY_LICENSES.txt) / [中文](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md#junit) |
| AndroidX Test JUnit | 1.1.5；设备测试 | Apache 2.0 |
| AndroidX Espresso Core | 3.5.1；设备测试 | Apache 2.0 |
| Android Gradle Plugin | 7.4.2；构建工具 | Apache 2.0，来源 [Android 构建工具](https://android.googlesource.com/platform/tools/base/) |
| Gradle Wrapper | 7.5；构建工具 | Apache 2.0，来源 [Gradle](https://github.com/gradle/gradle/tree/v7.5.0) |

测试工具不因出现在仓库构建配置中而成为 Release APK 的业务组件。完整许可条款及固定版本来源见[原文汇总](docs/licenses/THIRD_PARTY_LICENSES.txt)和[中文参考译文汇总](docs/licenses/THIRD_PARTY_LICENSES.zh-CN.md)。
