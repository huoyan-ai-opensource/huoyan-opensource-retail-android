# 参与贡献

欢迎通过 [GitHub Issues](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/issues) 反馈问题，通过 [Pull Request](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/pulls) 提交改进。提交前请阅读[使用与激活说明](USAGE.md)和[社区交流约定](CODE_OF_CONDUCT.md)。

## 开始开发

1. Fork 仓库并从目标分支创建自己的工作分支。
2. 按[构建指南](docs/build.md)配置 JBR 17、Android SDK、NDK 和 CMake。不要将 `local.properties` 或本机路径提交到仓库。
3. 阅读 [AGENTS.md](AGENTS.md)、[代码目录说明](docs/code-structure.md)。涉及 SDK JAR 时，再阅读 [SDK 架构](huoyan-sdk-client/ARCHITECTURE.md)和[接入指南](huoyan-sdk-client/INTEGRATION_GUIDE.md)。
4. 先复现问题，再做与问题直接相关的修改。较大的业务或接口变更，建议先开 Issue 说明预期行为。

App 源码向所有人开放使用，识别能力仍需联系火眼激活。仓库代码、样例或公开实验签名不能代替 SDK 授权。申请方式见[支持与联系](SUPPORT.md)。

## 代码与资源约定

- 沿用所在文件的命名、缩进和格式，避免把整库格式化混入功能提交。
- 新增注释、日志与职责说明使用中文；对外方法说明清楚线程、参数和生命周期约束。
- 金额使用分 `long` 和 `BigDecimal`，重量使用 `BigDecimal`；商品数据通过 SDK 接口操作。
- UI 沿用项目布局、主题和通用弹窗，涉及横竖屏的变更同时检查两种布局。
- 新增资源和第三方依赖时，说明来源，并同步更新[第三方组件说明](THIRD_PARTY_NOTICES.md)。
- 提交中不要包含激活码、账号凭据、正式签名、真实业务照片、客户数据或未脱敏的设备日志。

## 验证改动

Windows PowerShell 在项目根目录执行以下任务，先按构建指南配置 `JAVA_HOME`；macOS/Linux 将 `gradlew.bat` 改为 `bash gradlew`。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

修改 JAR 源码、对接协议或样例时，补充执行：

```powershell
.\gradlew.bat :huoyan-sdk-client:testDebugUnitTest :huoyan-sdk-client:compileExample :huoyan-sdk-client:clientDistribution
```

App 使用已经打包的 `app/src/main/libs/huoyan-sdk-client-2.0.0.jar`，构建 App 不会自动重打 SDK。需要让 App 使用本次 JAR 改动时，先执行 `:huoyan-sdk-client:updateAppSdkJar`，再验证 App，并检查提交中确实包含计划交付的 JAR。

设备测试位于 `app/src/androidTest/java`。在已连接的测试设备上执行 `:app:connectedDebugAndroidTest` 前，先阅读相关测试类的前置条件：部分用例需要真实 SDK、已激活的测试设备、商品或外设，也可能修改测试数据。不要在生产设备上直接运行整套用例。完整说明见[构建与测试](docs/build.md)。

不涉及行为的文档或模板改动，检查文字、相对链接和配置格式即可。无法执行硬件验证时，在 PR 中如实写明未验证的部分。

## 提交 Pull Request

PR 说明请包含问题、修改后的行为、相关 Issue、验证方式和剩余限制。UI 改动附横竖屏截图；SDK 接口变更说明 App、JAR 和 SDK 壳的版本关系，并更新接入指南及[兼容表](docs/version-compatibility.md)。用户可感知的变更记入 [CHANGELOG.md](CHANGELOG.md)。

GitHub 和 [CNB 仓库](https://cnb.cool/xinhaizhikong/xlabs/huoyan-opensource-retail-android) 都有本项目。提交时写明分支和提交号；同一问题若已在另一平台提出，请附链接，方便关联讨论。
