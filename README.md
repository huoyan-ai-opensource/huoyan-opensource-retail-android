# huoyan-opensource-retail-android

火眼开源零售安卓客户端，支持 **生鲜 / 零食 / 烘焙 / 小碗菜** 的 AI 识别和收银。

应用包名：`cc.huoyan.retail`；SDK JAR 包名：`cc.huoyan.sdk.client`。

## SDK JAR 对接

[火眼 SDK JAR 对接文档](huoyan-sdk-client/INTEGRATION_GUIDE.md) · [Java 接入示例](huoyan-sdk-client/samples/RecognitionIntegrationExample.java)

可以在现有应用中调用 JAR，也可以直接改造本客户端。JAR 负责与火眼 SDK 通信。

app 使用 `app/src/main/libs/huoyan-sdk-client-2.0.0.jar`，运行时依赖见 `app/build.gradle`。`huoyan-sdk-client` 独立打包和测试，app 构建不会自动更新 JAR。修改 SDK 源码后，用 JBR-17 执行 `bash gradlew :huoyan-sdk-client:updateAppSdkJar`，再构建 app。完整交付 ZIP 由 `:huoyan-sdk-client:clientDistribution` 生成。

支持 SDK 的 `OFFLINE` 状态：本地证书有效、模型就绪且密钥未丢失时，可以进入收银和学习；无需等待云端校准回到 `READY`。云端操作仍按 SDK 的真实回执处理，不因离线改为本地成功。

## 功能

- AI 识别（USB 摄像头 + 火眼 SDK）→ 商品 → 计价分发（计件键盘 / 称重确认）
- 混合购物车结算（`CartCalculator`：称重+计件统一计算，逐条目 HALF_UP 舍入）
- 模块开关（weighing / print，本地 SP 配置，按门店场景默认值）
- 计价前置校验（称重模块关闭时拦截称重商品）

## 代码目录

业务分包、设备接入和测试位置见 [代码目录说明](docs/code-structure.md)。

## 构建

构建使用 JBR-17；Gradle 7.5 不支持 Java 21。将下面的 `JAVA_HOME` 换成本机路径。

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :app:assembleDebug        # Debug APK（huoyan-retail-{version}-*.apk）
bash gradlew :app:assembleRelease      # Release APK（默认使用开源实验签名）
bash gradlew :app:testDebugUnitTest    # 单元测试
```

debug 使用 Android 构建工具在本机生成的调试签名。本地和 CNB 的 release 默认使用仓库内的 [`xinhai-debug-demo` 签名](signing/README.md)，并关闭调试功能。**该签名仅用于非商业的开源版本实验及测试 Release 打包，不得用于商业发布；测试私钥和密码随仓库公开。**

正式发布时，在不入库的 `local.properties` 中完整填写 `signing.storeFile`（建议绝对路径）、`signing.storePassword`、`signing.keyAlias`、`signing.keyPassword`，release 会改用该签名。正式签名私钥和密码不提交到仓库。

### CNB tag 打包

当前默认版本为 `2.0.6`，`versionCode` 为 `7`。根目录 `.cnb.yml` 在推送 tag 后使用 `.cnb/Dockerfile` 中的 JBR 17 和 Android SDK 环境，执行单元测试、构建 Release APK，并上传到该 tag 对应的 CNB Release 附件。

tag 使用 `MAJOR.MINOR.PATCH` 或 `vMAJOR.MINOR.PATCH` 格式，例如 `2.0.0`、`v2.0.0`；APK 的 `versionName`、应用名称和文件名使用去掉 `v` 的版本号。其他格式会使构建失败。`versionCode` 取自 `app/build.gradle`，后续发布需手动递增。

先提交 CI 配置及版本变更，再在目标提交上推送 tag：

```bash
git tag v2.0.6
git push origin v2.0.6
```

产物为 `huoyan-retail-2.0.6-7-{时间戳}-release.apk`。CI 自动使用仓库内的 `xinhai-debug-demo` 实验签名，无需额外配置签名密钥；后续实验版本使用同一签名并递增 `versionCode` 后，可覆盖安装。

## 测试

- 单元测试：`app/src/test/java`（纯 JUnit，金额/计价/结算逻辑）
- 平台自动化（android-test-lab）：`test-design/scenarios/*.json`，真机 C50D 跑场景断言（toast/金额/UI）

编码约定见 [AGENTS.md](AGENTS.md)。
