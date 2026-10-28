# 版本与升级

App、SDK 客户端 JAR、独立 SDK APK 和模型分别发布，版本号不要求相同。不要用 App 版本判断 SDK 原生引擎或模型版本。

## 当前源码基线

| 部分 | 当前基线 | 查看位置 |
| --- | --- | --- |
| 零售 App | 默认 `2.0.6`，`versionCode=7`，包名 `cc.huoyan.retail` | `app/build.gradle`；App 首页与设备授权页 |
| SDK 客户端 JAR | `2.0.0`，Java 11，Android API 24+ | `app/src/main/libs/huoyan-sdk-client-2.0.0.jar`；`huoyan-sdk-client/build.gradle` |
| 独立 SDK APK | 需配套火眼识物 SDK，包名 `cc.huoyan` | App 的 SDK 版本信息或 JAR `getCapabilities()` |
| 授权模型查询协议 | 接入指南以 SDK 壳 `0.1.3`、AIDL `api_version=3` 介绍双查询接口 | [激活与切换模型](../huoyan-sdk-client/INTEGRATION_GUIDE.md#connect-sdk) |
| 模型 | 按设备芯片、授权和所选场景交付 | 授权模型列表及 `getModelVersion()`；不是 APK 版本 |

这是当前文档和源码要求，不是对任意 `0.1.3+` SDK 的完整兼容承诺。当前 JAR 初始化还会传递宿主 App 的 `versionName`：AIDL 使用 `init(String appVersion)`，WS 使用 `params.app_version`。取得 SDK 时应确认其支持该签名、当前接口集合、账号配置、目标芯片及模型；同一个版本标签下也不要自行混用来源不明的 APK、JAR 和原生库。

SDK 版本未知时显示 `unknown` 或 `—`，不等于模型未就绪。反过来，模型就绪也不能证明所有新增接口存在。实际配套以 Release 说明和项目方交付清单为准；Release 还未列出新组合时，先确认再批量升级。

## 通信和运行边界

| 部署 | 系统要求 | 配置 |
| --- | --- | --- |
| 本机 AIDL | Android API 27+ | `SdkConfig.aidl()` |
| 本机 WS | Android API 24+ | `SdkConfig.websocket("ws://127.0.0.1:18086/ws")` |
| 远端 WS | 客户端 API 24+，SDK 设备需满足自身要求 | `SdkConfig.websocket("ws://<SDK设备IP>:18086/ws")`；代码中用真实 IPv4 替换占位符 |

WS 当前支持明文 `ws://`、必填端口及 `/ws` 默认路径；不支持 `wss://`、URL 用户名密码、查询参数或片段。只用于可信网络。第三方应用 target API 28+ 时，明文 WS 和 HTTP 图片需要按接入指南设置网络安全策略。

本地识别与学习用有效快照的 `SdkStatus.modelReady(status)` 判断。`OFFLINE` 还需证书有效且密钥未丢失，不要求等待联网后进入 `READY`；首次激活、模型下载及云端操作仍需要对应服务可用。

## 升级顺序

1. 阅读目标 Release 和 [CHANGELOG](../CHANGELOG.md)，确认 App、JAR、SDK、模型和 Android/ABI 的配套关系。
2. 涉及模型或商品学习数据时，先按业务需要使用「学习与识别库」备份，并确认恢复条件；不要把一个模型的备份直接恢复给不兼容的另一个模型。
3. 按交付说明升级 SDK APK；保持正式签名一致。再升级 App，并在「关于设备」确认 SDK、模型及授权信息。
4. 用一个商品完成查询、拍照学习、识别及计价；需要时再验证真实秤和打印机。完成后再升级其他设备。

当前 App 使用已打包 JAR，App 构建不会自动编译并替换 SDK module。开发者改动 JAR 源码后必须执行：

```shell
# Windows PowerShell
.\gradlew.bat :huoyan-sdk-client:updateAppSdkJar :app:assembleDebug

# macOS / Linux
bash gradlew :huoyan-sdk-client:updateAppSdkJar :app:assembleDebug
```

完整第三方交付使用 `:huoyan-sdk-client:clientDistribution`，同时交付运行时依赖、样例和指南；不要只发主 JAR 却遗漏依赖。按[构建指南](build.md)先选择 JBR 17 和正确 Android SDK。

## 安装签名与包名

- Debug 使用本机生成的 Android 调试签名；不同开发者的 Debug APK 通常不能互相覆盖。
- 默认 Release 使用公开实验签名，只供非商业实验测试。正式发布使用自己的签名，详见[签名说明](../signing/README.md)。
- 覆盖安装要求签名兼容，`versionCode` 不能低于已安装版。更换正式签名不能当普通更新处理。
- 修改客户端包名后需重新登记接入许可；更换交付账号需取得对应 JAR。不要将包名变更与设备激活数据混为一谈。
- 卸载 App 或 SDK 会清除对应数据，SDK 的授权、密钥和学习数据可能受影响；出现签名错误时先确认数据保留方案。

## 发布时记录的内容

每次发布应明确 App `versionName/versionCode`、JAR 版本、SDK APK 版本/API、支持模型或芯片范围、测试过的设备和流程、已知限制及升级要求，并给附件提供 SHA-256 摘要。`latest` 下载地址便于安装，不是不可变的版本证据；复现问题时记录实际安装版本与附件校验值。
