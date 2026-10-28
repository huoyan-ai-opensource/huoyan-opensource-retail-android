# 火眼 SDK JAR

客户端自动读取宿主 APK 的 `versionName`，首次初始化、重连后的初始化和显式重试
均携带该版本：AIDL 调用 `init(String appVersion)`，WS 发送 `params.app_version`。
版本由 SDK 保存并随后续心跳上报，客户端不新增直接向后台上报版本的请求。
宿主无需手填版本号；包信息不可查时不伪造版本。客户端须配套使用支持该签名的新版 SDK 壳。

`OFFLINE` 表示已恢复本地能力、设备身份尚未与云端校准。`SdkStatus.shellStarted()` 同时识别 `READY` 和 `OFFLINE`；`SdkStatus.modelReady()` 在离线态继续核对证书有效、模型就绪且密钥未丢失，满足后可识别与学习。云端反馈、上传和同步等仍按 SDK 回执处理，联网校准由 SDK 完成，宿主不重复初始化。


通过 JAR 的 Java 方法调用火眼 SDK，接入步骤见 [对接文档](INTEGRATION_GUIDE.md)。参考客户端源码同时提供，可参考调用方式或直接改造页面。

**运行要求：JAR 2.0.0，Android API 24+，Java 11。** 火眼 SDK 单独安装在提供识别服务的设备上，并与 JAR 使用配套版本；连接另一台设备时，客户端设备无需安装火眼 SDK。接入前登记客户端包名并完成所需授权。AIDL 连接需要 API 27+，API 24～26 使用 WebSocket。

账号由 JAR 内的 Java 资源 `raw/config` 提供。打包 APK 时请保留该文件，不要在宿主添加同名资源或通过 `packagingOptions` 排除它。

| 交付内容 | 用途 |
| --- | --- |
| `libs/` | 主 JAR 及运行所需的其他 JAR |
| `INTEGRATION_GUIDE.md` | 接入步骤、参数和结果说明 |
| `consumer-rules.pro` | 混淆配置 |
| `samples/` | 可参考的 Java 调用代码 |
| 参考客户端源码（单独提供） | 运行完整流程或修改现有客户端 |
