# 文档导航

第一次使用从 [App 快速开始](../QUICKSTART.md)阅读；已有自己的应用时从 [SDK JAR 接入指南](../huoyan-sdk-client/INTEGRATION_GUIDE.md)阅读。

## 使用与开发

| 文档 | 内容 |
| --- | --- |
| [App 快速开始](../QUICKSTART.md) | 下载、安装、授权、相机、商品学习、识别结算与可选云服务 |
| [构建指南](build.md) | 环境、Android Studio、Windows/macOS/Linux、测试和打包 |
| [测试指南](testing.md) | 单元测试、设备测试及真实授权和外设测试边界 |
| [发布指南](releasing.md) | GitHub 发布、附件、版本和验证 |
| [SDK 接入指南](../huoyan-sdk-client/INTEGRATION_GUIDE.md) / [English](../huoyan-sdk-client/INTEGRATION_GUIDE.en.md) | JAR 依赖、通信、激活、业务接口、状态和错误处理 |
| [Word 中文接入指南](../app/src/main/libs/huoyan-sdk-client-2.0.0-integration-guide.zh-CN.docx) / [English](../app/src/main/libs/huoyan-sdk-client-2.0.0-integration-guide.en.docx) | 与 JAR 一同交付的中英文完整对接文档 |
| [Java 示例](../huoyan-sdk-client/samples/RecognitionIntegrationExample.java) | 在自己的 App 中调用识别、学习、样本和反馈 |
| [版本与升级](version-compatibility.md) | App、JAR、SDK 壳、模型配套及签名升级注意事项 |
| [设备与外设](hardware.md) | Android、USB UVC 相机、Aclas 秤、USB ESC/POS 和权限 |
| [常见问题](faq.md) | 安装、连接、模型、离线、外设和日志收集 |
| [代码目录说明](code-structure.md) | App 业务分包、Presenter、状态和测试边界 |
| [JAR 架构](../huoyan-sdk-client/ARCHITECTURE.md) | 独立 SDK 的分层和交付边界 |
| [USB ESC/POS 小票打印](printer-escpos.md) | 协议、纸宽、USB 设备选择、回执与试打 |
| [签名说明](../signing/README.md) | 公开实验签名、正式签名及覆盖安装 |
| [使用说明](../USAGE.md) | 项目源码、SDK 和激活的使用范围 |
| [贡献指南](../CONTRIBUTING.md) | 开发、验证、问题和 PR 提交方式 |
| [安全反馈](../SECURITY.md) | 授权、账号及安全问题的私密反馈 |
| [更新记录](../CHANGELOG.md) | 发布变更和升级事项 |

## 设计源文件

横竖屏页面、布局和交互统一维护在 [`design/huoyan-retail.pen`](../design/huoyan-retail.pen) 中，直接在设计源文件内查看对应页面。设计图描述预期交互，设备型号、价格和状态示例不是真实后端回执。

按日期记录的一次性联调报告已从当前文档目录移除，旧版本和当时的验证证据可从 Git 历史追溯。仍适用的使用、排障和协议说明分别维护在上述文档及 SDK 接入指南中。

SDK 试用、包名登记和模型授权通过[火眼官网](https://www.huoyan.cc)联系项目方。普通问题通过 [GitHub Issues](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/issues)反馈，提交前按[常见问题](faq.md#如何收集问题信息)脱敏。
