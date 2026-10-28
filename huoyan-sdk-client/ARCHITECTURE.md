# JAR 目录与职责

源码根包为 `cc.huoyan.sdk.client`，依赖方向为宿主 → JAR 公共 API → internal → SDK。JAR 不依赖零售 app。

| 目录 | 放置内容 |
| --- | --- |
| 根包 | 稳定入口 `HuoyanSdkClient`、配置 `SdkConfig`、图像 `SdkImage`、日志及服务发现 |
| `model` | 通道无关、不可变的 SDK 请求/回执模型；所有 Entity/Resp/Result/Data、嵌套条目及分页结果按协议来源统一放这里 |
| `session` | 会话创建/复用/切换/释放、连接等待、初始化与就绪观察；执行器和配置由宿主注入 |
| `status` | SDK 状态、模型状态及原生错误解释；返回类型及默认说明，不依赖 R.string |
| `operation` | 操作确定性、输入约束、批量学习解释、长操作与进度访问 |
| `samples` | 分类/图片分页、UID 查图、地址缓存和失效版本 |
| `activation` | 激活码格式兼容；输入框分段、扫码 UI 留给宿主 |
| `feedback` | 识别反馈组装、文件读取及显式授权清理、图像签名/大小/URL 校验、异步提交、requestId 和上传 URL 复用 |
| `geometry` | 明确坐标格式的纯几何工具；不猜测模型类型或设备裁剪配置 |
| `deployment` | SDK 包名/默认发行源、安装版本查询、APK 下载与取消；安装器及权限 UI 属于宿主 |
| `internal` / `internal.ws` | Binder/WS 协议、编码、旧线格式和平台载体；业务方不得直接引用 |
| `cc.huoyan.app.aidl` | SDK 既有 AIDL/Parcelable 兼容协议，保持包名、事务顺序和服务标识 |

新 API 放入对应目录，不另建 `utils`、`entity` 或响应模型目录。同义模型优先扩展已有 `model` 类型，不复制 v1/v2 响应供宿主转换。历史线格式解析放在 `internal`。

## 连接与结果

`SdkSession.configure` 在通道或端点变化时关闭旧客户端，不转移或重发旧请求。`SdkStartupController.stop` 停止观察和回调，不取消已提交操作。启动观察每个连接只尝试一次初始化，缺回执后继续读状态，不重复初始化。

`SdkStatus.shellStarted` 的 READY/OFFLINE 只确认 SDK 启动，激活成功且模型可用须由有效回执中的 `modelReady=true` 确认；OFFLINE 还要求 `certificateValid=true` 且 `keyLost` 不为 true。SDK 在后台校准云端身份，宿主继续读取状态，无需重复初始化。`OperationOutcome` 区分成功、失败、未发送与结果未知。AIDL 调用后出现 RemoteException，或 WS 在途超时/中断/断连，结果均为未知，不能自动重放写请求。

SDK 应用版本通过 `getCapabilities()` 的 `sdkVersionName/sdkVersionCode` 获取，不依赖激活或模型就绪；SDK 版本读取失败时返回 `unknown/-1`，宿主应显示占位符。`getCertInfo()` 用于授权信息。

## 宿主职责

宿主负责销售方式、PLU 与识别类别转换、计价、相机与秤、裁剪坐标系、文件所有权和 UI。零售宿主通过 JAR 商品接口查询和修改 SDK 商品，不另建商品数据库或持久化商品映射。`CheckoutRecognition` 组合原始 `RecognizeResult` 与 `RecognitionSelection`；人工改品只改本地选择，保留 JAR 返回的类别、distance、square、rank、候选和框。

反馈文件通过 `FeedbackSubmission.fromFile` 交给 JAR。`PhotoRetention.KEEP` 保留文件，`DELETE_ON_SUCCESS` 在明确成功后按读取快照检查并清理；目录由宿主决定。`FeedbackProduct` / `FeedbackDetection` 属于 `model`，app 的 `FeedbackInputs` 只转换本地实体和收银选择，协议、上传状态及文件清理由 JAR 管理。

## 构建与交付

`clientJar` 先执行 `verifySdkBoundary`，检查是否反向引用零售包或 UI 资源；`compileExample` 仅用交付 JAR 和 Android bootclasspath 编译示例。`clientDistribution` 随包发布运行时依赖。

零售 app 通过 `files('src/main/libs/huoyan-sdk-client-2.0.0.jar')` 接入，不依赖 SDK module。修改 SDK 源码或资源后，执行 `:huoyan-sdk-client:updateAppSdkJar` 替换 app 中的 JAR；app 构建不会自动更新它。

普通 JAR 不携带第三方类或 Gradle 依赖元数据，宿主依赖见 [接入配置](INTEGRATION_GUIDE.md#21-依赖与编译配置)。SDK 工程中的 `androidx.annotation` 为 `compileOnly` 编译期依赖，JUnit 仅用于测试，均不随发行 ZIP 提供。

发行时可通过 [账号修改命令](PACKAGING.md) 直接为已打包 JAR 设置客户 accountId，无需重新编译。
