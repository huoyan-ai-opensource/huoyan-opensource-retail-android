# 更新记录

这里记录 App 的主要变化。App、客户端 JAR 和 SDK 壳分别管理版本，配套关系见[版本兼容表](docs/version-compatibility.md)。条目根据源码提交整理，不代表每个平台都已上传对应 Release；可下载版本以各平台实际发布附件为准。

## 未发布

- 补充完整 App 快速开始、构建与测试、硬件、排障、版本兼容和文档导航。
- 补充使用与激活说明、第三方组件说明、贡献与私密反馈入口、GitHub Issue/PR 模板及自动检查配置。
- 补充中英文实机截图和 Word 接入指南；JAR 分发 ZIP 随附使用说明及中英文第三方许可汇总。
- 清理无引用的工具类、配置和旧 AAR，合并重复历史文档，并同步 App 使用的客户端 JAR。

## 2.0.6 — 2026-10-28

- 关闭打印模块时支持确认识别结果，无需通过打印操作完成确认。
- 补充无打印确认相关的单元测试和设备测试。

源码依据：[4bf5578](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/4bf5578097e9f43359e8e8ba5984adf5826e1a69)。当前默认 `versionCode` 为 `7`。

## 2.0.5 — 2026-10-28

- 修正识别结果行高度与区域文案，同步横竖屏布局和设计稿。

源码依据：[fe191d9](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/fe191d9fa1438fc7f93b368b86834621ac9629d2)。

## 2.0.4 — 2026-10-28

- 下载等待界面使用统一主题进度条，完善 APK 下载与安装进度展示。

源码依据：[6738025](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/6738025c5c0e267841979e2ce5bba4eea1dbf411)。

## 2.0.3 — 2026-10-28

- 补齐 SDK 接入文档。
- 将客户端下载纳入共用 SDK 下载与安装流程。

源码依据：[defeeb5](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/defeeb5)、[75b71df](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/75b71df)。

## 2.0.2 — 2026-10-28

- 默认跟随系统语言，统一相关英文文案及应用名称显示。

源码依据：[a90d5e2](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/a90d5e26b80bc1489493d45e65fdd4daf596034e)。

## 2.0.1 — 2026-10-23

- 发布配置更新至 `2.0.1`。

源码依据：[669ad14](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commit/669ad146fe3c574db38e0b959833ba3e91c1cb63)。更早的变更请查阅 [Git 提交历史](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/commits/main/)。
