# 版本与发布

[文档导航](README.md) · [构建](build.md) · [测试](testing.md) · [兼容表](version-compatibility.md) · [更新记录](../CHANGELOG.md)

GitHub 与 CNB 都有公开仓库。GitHub 可在本地构建后手动上传附件，也可由维护者手动运行本仓库的 Actions 发布；推送代码或 tag 不自动构建和发布。CNB 发布流程由 [CNB 仓库](https://cnb.cool/xinhaizhikong/xlabs/huoyan-opensource-retail-android)维护。两端的提交、tag 和附件分别管理，不自动同步。普通 GitHub 问题和贡献可从 GitHub Issue/PR 进入；SDK 激活和私密问题按[支持说明](../SUPPORT.md)联系。

## 版本由哪里维护

| 项目 | 当前版本 | 配置位置 |
| --- | --- | --- |
| App versionName | 2.0.6 | `app/build.gradle` 的 `appVersionName` 默认值；CI 可用 `-PappVersionName` 覆盖 |
| App versionCode | 7 | `app/build.gradle` 的 `defaultConfig.versionCode`，发布新安装包需递增 |
| SDK JAR | 2.0.0 | `huoyan-sdk-client/build.gradle` 中的 JAR/ZIP 文件名及 App 依赖位置 |
| SDK 壳 | 由配套 SDK 发布维护 | 运行时查询版本，见兼容表 |

App、JAR、SDK 壳是不同产物，可以独立版本。JAR 版本变化时同时检查模块产物名、App JAR 依赖、接入文档和兼容表，不能只改 App 版本。

## 发布前准备

1. 更新 `versionCode`、App 默认版本、`CHANGELOG.md` 和已验证版本组合；App tag 必须是 `MAJOR.MINOR.PATCH` 或 `vMAJOR.MINOR.PATCH`。
2. 若修改 SDK 源码，执行 `:huoyan-sdk-client:updateAppSdkJar` 并提交计划随 App 发布的 JAR，确认其账号配置适合公开交付。App 构建不会自动更新 JAR。
3. 运行[无设备检查](testing.md)，在指定测试设备按首次运行和业务路径验收；列明本版本没有测试的硬件。
4. 确认签名。自动流程默认使用公开实验签名，产物为非商业实验/测试版本。商业发布必须使用自己的正式签名并单独管理私密配置，不能直接把自动生成的实验 APK 用作商业发布。
5. 先确认 `git remote -v`，再决定向 GitHub 还是 CNB 发布。GitHub 当前只支持手动触发构建和发布；CNB 的 tag 行为以其当前流水线配置为准。

本地只打包不会创建远程 Release：

```bash
bash gradlew :app:testDebugUnitTest :huoyan-sdk-client:testDebugUnitTest :huoyan-sdk-client:clientDistribution :app:assembleRelease
```

## GitHub

- `.github/workflows/android.yml` 仅在 Actions 页面选择“Android 检查”并点击“Run workflow”时执行只读检查，编译 App 与设备测试、运行 JVM 测试、编译 JAR 示例、生成交付 ZIP；不会激活设备或调用真实云业务。
- `.github/workflows/release.yml` 仅在维护者手动运行“Android 实验版本发布”并填入现有版本 tag 时执行测试和 Release 构建。构建 job 检出该 tag，只有仓库读取权限；发布 job 单独取得 `contents: write`，用 GitHub 自带的 `GITHUB_TOKEN` 创建该 tag 的 Release，发布说明记录实际构建的提交号。
- Release 附件包含 App APK、JAR 交付 ZIP 和 `SHA256SUMS`。发布说明附带构建来源和实验签名说明；配套 SDK 的实际验证版本应维护在更新记录/兼容表中。
- 工作流使用固定提交版本的 Actions。升级时从上游核对提交及变更，再同时更新相关工作流。

本地构建并手动发布：

1. 运行本节前的本地测试与打包命令，核对 APK 的版本、签名与交付 ZIP，并计算附件的 SHA-256。
2. 在 GitHub 仓库的 Releases 页面新建版本，选择发布 tag 及实际打包的提交，填写更新记录和实验签名说明。
3. 附加 Release APK、JAR 交付 ZIP 和 `SHA256SUMS`，核对附件完整后发布。Word 指南已在 ZIP 中，也可单独附加便于下载。

若选择手动运行 GitHub 发布工作流，先由维护者确认目标提交并推送版本 tag，例如：

```bash
git tag v2.0.6
git push <指向GitHub的remote名> v2.0.6
```

示例 remote 名是占位符。推送后仍需进入 Actions，手动运行“Android 实验版本发布”，在 `tag` 输入框填写该 tag；仅推送 tag 不会发布。不要重复发布已经公开的版本；若 Release 已存在，创建步骤会失败，应检查现状后决定修正方式，避免静默替换用户已下载的产物。构建失败可查看 Actions 日志及测试报告；不能将编译失败的版本标为已发布。

## CNB

CNB 的流水线、构建环境和附件上传规则由 [CNB 仓库](https://cnb.cool/xinhaizhikong/xlabs/huoyan-opensource-retail-android)维护。发布前进入 CNB 仓库的工作副本，核对其当前流程、目标提交、版本和签名；GitHub 仓库不维护 `.cnb.yml` 或 `.cnb/` 配置。

确认 CNB 当前流程支持版本 tag 发布后，在该工作副本执行，例如：

```bash
git tag v2.0.6
git push <指向CNB的remote名> v2.0.6
```

相同 tag 重跑是否覆盖附件，以 CNB 仓库当前发布配置为准；正式分发后应使用新的版本发布修正。两端要发布同一版本时，应确认指向同一提交并分别推送，不能只凭 tag 名相同判断内容一致。

## 下载和校验

下载入口：[GitHub Releases](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/releases) · [CNB 仓库](https://cnb.cool/xinhaizhikong/xlabs/huoyan-opensource-retail-android)。若还没有对应 Release，可先按[构建文档](build.md)本地构建；源码中存在版本号不代表已发布附件。

Linux：下载 `SHA256SUMS` 与同一 Release 的 APK/ZIP，放在同一目录后执行 `sha256sum -c SHA256SUMS`。macOS 使用 `shasum -a 256 -c SHA256SUMS`。Windows 使用 `Get-FileHash -Algorithm SHA256 <文件名>`，将结果与清单逐项比较。摘要用于确认附件完整性，不代替 APK 签名校验或对下载来源的确认。

SDK JAR ZIP 包含主 JAR、运行时依赖、接入文档、示例和混淆规则；识别 SDK APK、模型及激活码并不包含在其中，获取方式见[使用与激活说明](../USAGE.md)。
