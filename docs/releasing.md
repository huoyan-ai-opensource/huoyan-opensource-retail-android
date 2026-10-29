# 版本与发布

[文档导航](README.md) · [构建](build.md) · [测试](testing.md) · [兼容表](version-compatibility.md) · [更新记录](../CHANGELOG.md)

版本附件统一通过 GitHub Releases 发布。可在本地构建后手动上传附件，也可由维护者手动运行本仓库的 Actions 发布；推送代码或 tag 不自动构建和发布。普通问题和贡献可从 GitHub Issue/PR 进入；SDK 激活和私密问题按[支持说明](../SUPPORT.md)联系。

## 版本由哪里维护

| 项目 | 当前版本 | 配置位置 |
| --- | --- | --- |
| App versionName | 2.0.6 | `app/build.gradle` 的 `appVersionName` 默认值；CI 可用 `-PappVersionName` 覆盖 |
| App versionCode | 7 | `app/build.gradle` 的 `defaultConfig.versionCode`，发布新安装包需递增 |
| SDK JAR | 2.0.0 | `huoyan-sdk-client/build.gradle` 中的 `sdkClientVersion`；App 依赖需同步更新 |
| SDK 壳 | 由配套 SDK 发布维护 | 运行时查询版本，见兼容表 |

App、JAR、SDK 壳是不同产物，可以独立版本。JAR 版本变化时同时检查模块产物名、App JAR 依赖、接入文档和兼容表，不能只改 App 版本。

## 中英文 Word 交付规则（强制）

两份指南源文件保存在 `app/src/main/libs/`。`<JAR版本>` 必须与本次客户端 JAR 一致，不能使用 App 版本代替。当前版本的文件名和交付位置如下：

| 语言 | 项目源文件名 / Release Assets 文件名 | ZIP 内路径 |
| --- | --- | --- |
| 中文 | `huoyan-sdk-client-2.0.0-integration-guide.zh-CN.docx` | `docs/huoyan-sdk-client-2.0.0-integration-guide.zh-CN.docx` |
| 英文 | `huoyan-sdk-client-2.0.0-integration-guide.en.docx` | `docs/huoyan-sdk-client-2.0.0-integration-guide.en.docx` |

后续版本固定沿用 `huoyan-sdk-client-<JAR版本>-integration-guide.zh-CN.docx` 和 `huoyan-sdk-client-<JAR版本>-integration-guide.en.docx` 的格式：

1. `huoyan-sdk-client-<JAR版本>.zip` 的 `docs/` 必须同时包含两份指南，不能只在 ZIP 外补英文。
2. 同一 Release 的 Assets 也必须单独提供两份指南，文件名与内容均与项目及 ZIP 内文件一致，不得临时重命名。
3. Assets 直接显示完整文件名，不设置自定义 `label`；使用 `gh release` 上传时不追加 `#label`，不显示“英文接入指南 English”等替代名称。
4. 缺少任一语言或内容不一致时停止发布。`clientDistribution` 拦截缺失或空文件；发布工作流从 ZIP 提取两份指南，逐份与项目源文件比对后交付。
5. `SHA256SUMS` 覆盖所有实际附件（不包含清单自身）。若手动增加独立 JAR 或其他附件，也必须计算摘要；附件变化后同步摘要和发布说明。
6. JAR 升级时同步两份指南源文件名、App JAR 依赖、README、接入文档、下载链接及兼容表。下载入口统一为 GitHub Releases，不恢复 CNB Release 链接。

## 发布前准备

1. 更新 `versionCode`、App 默认版本、`CHANGELOG.md` 和已验证版本组合；App tag 必须是 `MAJOR.MINOR.PATCH` 或 `vMAJOR.MINOR.PATCH`。
2. 若修改 SDK 源码，执行 `:huoyan-sdk-client:updateAppSdkJar` 并提交计划随 App 发布的 JAR，确认其账号配置适合公开交付。App 构建不会自动更新 JAR。
3. 运行[无设备检查](testing.md)，在指定测试设备按首次运行和业务路径验收；列明本版本没有测试的硬件。
4. 确认签名。自动流程默认使用公开实验签名，产物为非商业实验/测试版本。商业发布必须使用自己的正式签名并单独管理私密配置，不能直接把自动生成的实验 APK 用作商业发布。
5. 先通过 `git remote -v` 确认目标为 GitHub 仓库。当前只支持手动触发构建和发布。
6. 按上表核对项目、ZIP 和 Assets 的中英文指南，以及所有附件的摘要。手动上传和 Actions 发布遵守同一规则。

本地只打包不会创建远程 Release：

```bash
bash gradlew --no-daemon --console=plain -PappVersionName=2.0.6 :app:testDebugUnitTest :huoyan-sdk-client:testDebugUnitTest :huoyan-sdk-client:compileExample :huoyan-sdk-client:clientDistribution :app:assembleRelease :app:assembleDebugAndroidTest
python3 -m unittest discover -s huoyan-sdk-client/tools -p 'test_*.py'
```

`appVersionName` 替换为目标 App 版本；Windows 使用 `.\gradlew.bat`。修改工作流前先执行该工作流的整组测试与构建，修改后验证实际产物。本地 Windows 验证通过不代表 GitHub Ubuntu 工作流已通过，线上结果需以实际运行记录为准。

## GitHub

- `.github/workflows/android.yml` 仅在 Actions 页面选择“Android 检查”并点击“Run workflow”时执行只读检查，编译 App 与设备测试、运行 JVM 测试、编译 JAR 示例、生成交付 ZIP；不会激活设备或调用真实云业务。
- `.github/workflows/release.yml` 仅在维护者手动运行“Android 实验版本发布”并填入现有版本 tag 时执行测试和 Release 构建。构建 job 检出该 tag，只有仓库读取权限；发布 job 单独取得 `contents: write`，用 GitHub 自带的 `GITHUB_TOKEN` 创建该 tag 的 Release，发布说明记录实际构建的提交号。
- Release 附件必须包含 App APK、JAR 交付 ZIP、两份使用项目原始文件名的中英文 Word 指南和 `SHA256SUMS`。发布说明附带构建来源和实验签名说明；配套 SDK 的实际验证版本应维护在更新记录/兼容表中。
- 工作流使用固定提交版本的 Actions。升级时从上游核对提交及变更，再同时更新相关工作流。

本地构建并手动发布：

1. 运行本节前的本地测试与打包命令，核对 APK 的版本、签名、交付 ZIP 及其中的两份 Word 指南，计算所有附件的 SHA-256。
2. 在 GitHub 仓库的 Releases 页面新建版本，选择发布 tag 及实际打包的提交，填写更新记录和实验签名说明。
3. 附加 Release APK、JAR 交付 ZIP、两份 Word 指南和 `SHA256SUMS`。逐份确认 Word 的文件名、内容与 ZIP 及项目源文件一致，Assets 没有自定义 label，再发布。

若选择手动运行 GitHub 发布工作流，先由维护者确认目标提交并推送版本 tag，例如：

```bash
git tag v2.0.6
git push <指向GitHub的remote名> v2.0.6
```

示例 remote 名是占位符。推送后仍需进入 Actions，手动运行“Android 实验版本发布”，在 `tag` 输入框填写该 tag；仅推送 tag 不会发布。不要重复发布已经公开的版本；若 Release 已存在，创建步骤会失败，应检查现状后决定修正方式，避免静默替换用户已下载的产物。构建失败可查看 Actions 日志及测试报告；不能将编译失败的版本标为已发布。

## 下载和校验

下载入口：[GitHub Releases](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/releases)。若还没有对应 Release，可先按[构建文档](build.md)本地构建；源码中存在版本号不代表已发布附件。

Linux：下载 `SHA256SUMS` 与清单中的同一 Release 全部附件，放在同一目录后执行 `sha256sum -c SHA256SUMS`。macOS 使用 `shasum -a 256 -c SHA256SUMS`。Windows 使用 `Get-FileHash -Algorithm SHA256 <文件名>`，将结果与清单逐项比较。摘要用于确认附件完整性，不代替 APK 签名校验或对下载来源的确认。

SDK JAR ZIP 包含主 JAR、运行时依赖、接入文档、示例和混淆规则；识别 SDK APK、模型及激活码并不包含在其中，获取方式见[使用与激活说明](../USAGE.md)。
