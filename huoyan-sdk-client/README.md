# 火眼 SDK JAR

简体中文 | [English](README.en.md)

官网：[www.huoyan.cc](https://www.huoyan.cc)

通过 JAR 的 Java 方法调用火眼 SDK，接入步骤见 [中文对接文档](INTEGRATION_GUIDE.md) / [English Integration Guide](INTEGRATION_GUIDE.en.md)。参考客户端源码同时提供，可参考调用方式或直接改造页面。

**运行要求：JAR 2.0.0，Android API 24+，Java 11。** 火眼 SDK 单独安装在提供识别服务的设备上，并与 JAR 使用配套版本；连接另一台设备时，客户端设备无需安装火眼 SDK。接入前登记客户端包名并完成所需授权。AIDL 连接需要 API 27+，API 24～26 使用 WebSocket。

账号由 JAR 内的 Java 资源 `raw/config` 提供。打包 APK 时请保留该文件，不要在宿主添加同名资源或通过 `packagingOptions` 排除它。

| 交付内容 | 用途 |
| --- | --- |
| `libs/` | 主 JAR 及运行所需的其他 JAR |
| `INTEGRATION_GUIDE.md` | 接入步骤、参数和结果说明 |
| `INTEGRATION_GUIDE.en.md` | 英文版接入步骤、参数和结果说明 |
| `README.md` / `README.en.md` | 中英文交付说明 |
| `consumer-rules.pro` | 混淆配置 |
| `docs/huoyan-sdk-client-2.0.0-接入指南.docx` | 可离线阅读的 Word 接入指南 |
| `USAGE.md` / `THIRD_PARTY_NOTICES.md` | 使用、激活及第三方组件说明 |
| `docs/licenses/` | 第三方许可原文及中文参考译文汇总 |
| `samples/` | 可参考的 Java 调用代码 |
| 参考客户端源码（单独提供） | 运行完整流程或修改现有客户端 |
