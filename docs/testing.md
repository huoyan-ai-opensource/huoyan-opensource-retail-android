# 测试

[文档导航](README.md) · [构建环境](build.md) · [贡献指南](../CONTRIBUTING.md)

## 不需要设备的检查

使用 JDK/JBR 17，在仓库根目录运行：

```bash
bash gradlew :app:testDebugUnitTest :huoyan-sdk-client:testDebugUnitTest :huoyan-sdk-client:compileExample :huoyan-sdk-client:clientDistribution :app:assembleDebug :app:assembleDebugAndroidTest
```

Windows PowerShell 把 `bash gradlew` 替换为 `.\gradlew.bat`。以上任务需要 Android 构建工具及依赖缓存，但不要求安装 SDK 壳、激活码、USB 相机或云端账号；`assembleDebugAndroidTest` 只编译设备测试，不执行它们。

| 位置 | 用途 |
| --- | --- |
| `app/src/test/java` | 金额、计价、购物车、Presenter 等 JVM 单元测试 |
| `huoyan-sdk-client/src/test/java` | JAR 连接、状态、请求及辅助逻辑测试；部分用例使用本机回环网络 |
| `huoyan-sdk-client/samples` | 第三方 Java 接入示例，`compileExample` 验证它们能与生成 JAR 一起编译 |
| `app/src/androidTest/java` | Android 设备测试：UI、Bitmap、外设或真实 SDK 集成；运行条件依具体类而定 |

App 测试使用提交在 `app/src/main/libs` 的 JAR。SDK module 测试验证源码；两者不能互相替代。若改了 SDK 并需要 App 一同使用新行为，先执行 `:huoyan-sdk-client:updateAppSdkJar`，然后再运行上面的任务。

JVM 报告位于 `app/build/reports/tests/testDebugUnitTest/index.html` 和 `huoyan-sdk-client/build/reports/tests/testDebugUnitTest/index.html`，对应 JUnit XML 位于各模块的 `build/test-results/testDebugUnitTest`。

`StudyFilesTest` 的符号链接用例依赖 `File.getCanonicalFile()` 解析链接的文件系统语义。Windows JDK 不提供该行为时，这一个用例通过明确的环境前提标为跳过；GitHub/CNB 的 Linux 检查继续执行该断言。查看报告时应同时检查失败数与跳过数。

仓库附带的 JAR 账号配置工具另有 Python 标准库测试：

```bash
python3 -m unittest discover -s huoyan-sdk-client/tools -p 'test_*.py'
```

这组测试需要 Python 3 与 JDK 17，详情见 [JAR 账号交付说明](../huoyan-sdk-client/PACKAGING.md)。Windows 可将 `python3` 换为已安装的 `python`。

## 在设备上测试

先用 `adb devices` 确认目标设备已授权，优先复用现有测试设备。只连接要执行测试的设备；多设备环境使用 Android 工具支持的目标选择，不要误操作业务机器。

例如只验证提示与弹窗层级：

```bash
bash gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.huoyan.retail.common.ui.ToastLayerDeviceTest
```

该用例会启动首页、打开弹窗并验证屏幕像素，不提交激活或商品业务写请求。运行设备测试仍会安装调试 APK，先确认签名可覆盖以及设备数据可用于测试。设备报告位于 `app/build/reports/androidTests/connected`。

**不要直接在生产设备执行全部 `connectedDebugAndroidTest`。** 部分设备测试涉及打印、绑定、商品、学习和反馈；运行前先阅读目标测试类、使用隔离测试设备和授权账号，并确认会修改的数据。`RealFeedbackClientDeviceTest` 需要显式 `realFeedbackTest=true` 才会连接真实 SDK，其固定测试门店及商品仅适用于原验收环境；社区贡献者不应照抄启用参数对真实业务云执行。

## 完整业务验收

按[快速开始](../QUICKSTART.md)准备相机、配套 SDK 和激活，再记录：

1. App、JAR、SDK 壳版本，Android 版本、ABI、设备型号及连接方式。
2. 首次安装、SDK 未安装、未激活、模型准备中、就绪后的页面行为。
3. 新增计件商品和称重商品、学习、识别、数量/重量确认和结算。
4. 秤和打印机的启用/禁用组合；未配对应外设时应有明确提示。
5. App 重启、SDK 重启、网络断开后的连接恢复；已激活离线是否可用以 SDK 证书、密钥、模型和实际回执为准。

发布检查与自动流程见[发布说明](releasing.md)。JVM/编译检查通过不能代表真实相机、模型精度、硬件兼容和云端授权已经通过验收。
