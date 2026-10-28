# 火眼 SDK JAR 对接文档

**JAR 版本：2.0.0｜Android API 24+｜Java 11**

火眼 SDK 单独安装，客户端通过 JAR 的 Java 方法调用 SDK。随附的零售客户端源码称为“参考客户端”。

[导入 JAR](#import-jar) · [连接与激活](#connect-sdk) · [业务调用](#business-flows) · [错误处理](#error-handling) · [参考客户端](#reference-client) · [主接口速查](#client-api) · [验收与排障](#verification) · [辅助 API](#helper-api)

## 1. 选择适合的接入方式

| 接入方式 | 适用情况 | 建议路径 |
| --- | --- | --- |
| 在现有应用中使用 JAR | 已有商品、相机或收银系统，只需增加识别能力 | 导入 JAR → 连接、激活 → 提交图片 → 处理识别结果 |
| 基于参考客户端改造 | 希望沿用已有页面和零售流程 | [运行参考客户端](#reference-client) → 体验学习与收银 → 修改商品、设备及页面 |

接入前向对接人员取得配套的 SDK 安装包和激活方式，确认设备支持并登记客户端包名。账号内置于交付 JAR，更换账号需取得对应 JAR，见第 5.1 节。

先用一张固定图片试通流程。设备已经学过商品时可以直接识别；新设备先按第 4.1 节给一个商品拍照学习，再识别它。

“学习”指把商品图片交给 SDK，一份学习成功的图片记录称为“样本”。模型就绪判断见第 3.2 节。

<a id="import-jar"></a>

## 2. 导入 JAR

### 2.1 依赖与编译配置

解压交付 ZIP，将 `libs/` 中全部 JAR 放到第三方应用的 `app/libs/`。参考配置：

```groovy
android {
    compileSdk 33
    defaultConfig { minSdk 24 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
}
```

若只导入主 JAR，由 Gradle 管理其他依赖，则使用：

```groovy
dependencies {
    implementation files('libs/huoyan-sdk-client-2.0.0.jar')
    implementation 'org.java-websocket:Java-WebSocket:1.5.2'
    implementation 'com.alibaba:fastjson:1.2.47'
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20'
}
```

两种依赖方式选一种。主 JAR 不内嵌第三方类，也不携带 Gradle 依赖元数据；完整 ZIP 包含运行时依赖，使用 Maven 时由 Gradle 解析。

保留 JAR 中的 Java 资源 `raw/config`，不要通过 `packagingOptions` 排除它或在宿主添加同名资源；账号配置说明见 [README](README.md)。

JAR 已附带混淆规则。需要手动添加时，将 `consumer-rules.pro` 放入 app 目录，在启用混淆的 buildType 中添加 `proguardFiles 'consumer-rules.pro'`。

| 依赖 | 用途及引入方式 |
| --- | --- |
| Java-WebSocket 1.5.2 | 显式声明；JAR 的 WebSocket 通信 |
| FastJSON 1.2.47 | 显式声明；JAR 的 SDK 协议 JSON 编解码 |
| OkHttp 4.10.0 | 显式声明；JAR 的 SDK APK 下载 |
| Kotlin 标准库 JDK8 1.8.20 | 显式声明；统一 OkHttp/Okio 所需的 Kotlin 运行时版本，并引入 JDK7、stdlib、stdlib-common 及 JetBrains annotations |
| SLF4J API 1.7.25 | Java-WebSocket 运行时所需的传递依赖，由 Gradle 自动引入，无需直接声明 |
| Okio / Okio JVM 3.0.0 | OkHttp 的传递依赖 |

### 2.2 Android 配置

在应用的 `<manifest>` 下声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="cc.huoyan.permission.SDK" />
<queries>
    <package android:name="cc.huoyan" />
</queries>
```

第三方应用包名需登记获得接入许可；修改参考客户端包名后也需重新登记。相机、USB 及安装权限按应用实际功能配置。

目标 API 28+ 的应用使用明文 WS 或 HTTP 图片地址时，需允许访问对应主机。以下网络安全配置仅包含本机地址，远端连接需补充实际主机：

```xml
<!-- application 属性 -->
<application android:networkSecurityConfig="@xml/huoyan_network_security">
    <!-- 应用组件 -->
</application>
```

```xml
<!-- res/xml/huoyan_network_security.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

<a id="connect-sdk"></a>

## 3. 连接、激活与识别准备

### 3.1 选择连接配置

AIDL 用于同机通信，WebSocket（简称 WS）通过网络连接。

| 火眼 SDK 部署位置 | JAR 配置 |
| --- | --- |
| 本机，Android API 27+ | `SdkConfig.aidl()` |
| 本机，Android API 24+ | `SdkConfig.websocket("ws://127.0.0.1:18086/ws")` |
| 另一台可访问设备 | `SdkConfig.websocket("ws://192.168.1.115:18086/ws")`，替换实际 IP |

API 24～26 使用 WebSocket。远端模式无需在客户端设备安装火眼 SDK。WS 地址当前支持 `ws://host:port/path`，端口必填，省略路径时使用 `/ws`；不支持 `wss://`、URL 用户名密码、查询参数或片段。

需要查找设备时，使用第 10.1 节的 `SdkDiscovery`。

### 3.2 已激活设备：识别一张图片

未激活设备先[完成激活](#activation)。下面演示单次识别；连续识别复用连接。

把 [RecognitionIntegrationExample.java](samples/RecognitionIntegrationExample.java) 加入项目，在后台线程运行。`bitmap` 是你提供的一张方向正确的图片：

```java
SdkConfig config = android.os.Build.VERSION.SDK_INT >= 27
        ? SdkConfig.aidl()
        : SdkConfig.websocket("ws://127.0.0.1:18086/ws");

try (HuoyanSdkClient client = RecognitionIntegrationExample.openReady(context, config)) {
    SdkImage input = client.image(bitmap);
    List<RecognitionItem> items = RecognitionIntegrationExample.recognize(client, input);
    // 按 item.getClazz() 查找自己的商品，再展示或进入业务流程。
}
```

片段需导入 `cc.huoyan.sdk.client.*`、`cc.huoyan.sdk.client.model.RecognitionItem`、`java.util.List` 及 `example.RecognitionIntegrationExample`；调用方处理 `IOException`、`InterruptedException` 和本地参数异常，中断时恢复线程中断标记。

示例的 `openReady` 负责连接和初始化，检查识别能力及模型状态；超时或模型未就绪时抛出异常。示例 `recognize()` 将 JAR 的 `RecognizeResult` 解包为条目列表。JAR 调用顺序如下：

```text
HuoyanSdkClient.create(context, config)
  → connect() 异步连接
  → isConnected() 确认已连接
  → initialize() 并检查结果
  → getStatus() 查询模型状态
  → SdkStatus.modelReady(status) 为 true
  → recognize() / learn() / batchLearn()
```

`isConnected()` 只表示通信连接成功。`initialize()` 不等于激活完成；状态 `READY`、`OFFLINE` 表示火眼 SDK 已启动，**识别与学习通过 `SdkStatus.modelReady(status)` 判断**。其中 `OFFLINE` 是本地可用但设备身份尚未与云端校准的状态，仍须确认证书有效、模型就绪且密钥未丢失；不需要等待联网才使用本地能力。

`initialize()` 自动携带宿主 APK 的 `versionName`，覆盖首次初始化、重连后的初始化和显式重试；包信息不可查时不伪造版本。须使用支持该初始化签名的配套 SDK，协议字段及上报方式见 [README](README.md)。

<a id="activation"></a>

### 3.3 激活火眼 SDK

首次激活按激活码选模型；已激活设备切换模型时查询当前设备授权。

| 步骤 | 调用与处理 |
| --- | --- |
| 连接 | `create()` → `connect()` → 等待 `isConnected()`；激活页面只要求通信连接，不要求模型已就绪 |
| 初始化与查询 | 调用 `initialize()` 并检查 code/reason，再读 `getStatus()`、`getCertInfo()`；按下文 sdkState 规则判断是否继续激活 |
| 取得激活码 | 用户手输激活码，或从二维码中取出 serial 字段（激活码） |
| 选择模型 | 正常激活后台调用 `queryLicenseModels(activationCode)`，切换调用 `queryDeviceModels()`，按返回顺序显示动态名称，保留点选项的 `modelCode` |
| 提交激活 | 在后台线程调用 `activate(activationCode, modelCode)` 一次，取消选择不提交 |
| 展示准备进度 | 另一后台线程查询 `getDownloadProgress()`，并调用 `getStatus()` 查询状态 |
| 完成 | 检查激活结果；状态查询成功且 modelReady=true 后允许识别/学习，并刷新授权信息 |

激活页面使用 [ActivationStatusExample.java](samples/ActivationStatusExample.java) 的 `openConnected(context, config)`。第 3.2 节的 `openReady()` 在模型未就绪时会关闭连接并抛异常，不适用于首次激活。

状态查询成功且 sdkState 为 `NOT_ACTIVATED` 时引导激活；`REGISTERED` 时结合授权信息判断。其他错误按 code/reason 处理，结果未知时按第 6.2 节核对，不重复提交。

以下 `client` 已连接，`activationCode` 为用户确认的输入，`modelCode` 为所选项的 `getModelCode()`：

```java
BaseResult result = ActivationStatusExample.activateOnce(client, activationCode, modelCode);
if (result == null || result.getCode() == HuoyanSdkClient.RESULT_UNKNOWN) {
    // 激活结果未知：刷新状态与授权信息核对，不自动再提交。
} else if (result.getCode() != 0) {
    // 展示 result.getCode()/getReason()，按错误处理后再决定下一步。
} else {
    // 激活调用成功；继续查询模型状态，尚不能仅凭此结果开放识别。
}
```

示例用 `activation.ActivationCode.normalize(input)` 整理激活码，再调用 `client.activate()`。去除连字符后为 16 位的码按四位分段，其他长度的扫码 serial 原样传递。`validManual(input)` 只检查手输码长度，不验证授权，也不用于拦截其他长度的扫码 serial。JSON 二维码先提取 serial 再提交。

双查询接口对应 SDK 壳 0.1.3（AIDL api_version=3）。`LicenseModels.code/reason` 表示查询结果；`models` 每项包含 `modelCode/modelName/chip/current`，数量、名称和顺序不固定，`current=true` 标识当前模型。

切换模型时，用同一客户端 `getCertInfo().getActivationSerial()` 返回的序列号与所选 modelCode 调用 `activate()`。缺少序列号则在选模后补输或扫码，保留所选模型再提交。选择当前项或取消均不写入；切换独立于本地配置保存。

`activate()` 返回 `BaseResult`（code/reason），模型状态和证书需另行查询；门店绑定使用 `bindDevice()`。已提交请求的生命周期见第 6.1 节。`getDownloadProgress()` 返回模型下载进度：

| `DownloadProgress` 方法 | 类型/单位 | 含义 |
| --- | --- | --- |
| `getCode()` / `getReason()` | int / String | 本次进度查询是否成功及原因 |
| `getDownloaded()` | long，字节 | 已下载数据量 |
| `getTotal()` | long，字节 | 总量，0 表示未知 |

参考客户端每次查完进度后等 2 秒再查。查询失败时继续核对激活结果和状态；下载到 100% 后仍要等待 modelReady=true。

### 3.4 获取火眼 SDK 运行状态

`client.getStatus()` 返回 `StatusSnapshot`，用于判断识别条件及排障。**先检查回执非 null 且 code=0，再读取状态字段。**

| 方法 | 类型 | 内容及使用方式 |
| --- | --- | --- |
| `getCode()` | int | 本次查询结果码，0 为成功 |
| `getReason()` | String | 查询失败原因；区别于 SDK 当前的 recentError |
| `getSdkState()` | String | 当前运行状态，含义和处理方式见下表 |
| `getModelReady()` | Boolean | 模型是否就绪；使用 `SdkStatus.modelReady()` 结合离线授权与密钥事实判断能否识别/学习 |
| `getDeviceId()` | String | 火眼 SDK 设备标识，用于设备信息及排查；不是客户端自定义门店 ID |
| `getModelVersion()` | String | 当前模型版本，不是火眼 SDK 安装包版本 |
| `getCertificateValid()` | Boolean | 授权证书是否有效；OFFLINE 也必须明确为 true 才开放本地识别/学习 |
| `getCertificateValidUntil()` | String | 证书有效期文本；日期计算可使用 CertInfo 的时间戳 |
| `getTrustLevel()` | String | 火眼 SDK 给出的设备信任等级；需要排查时提供此值 |
| `getKeyLost()` | Boolean | 设备授权所需的密钥是否丢失；为 true 时结合 sdkState 排查 |
| `getBackendConnected()` | Boolean | 火眼 SDK 能否连接服务器；客户端是否连上火眼 SDK 要看 isConnected() |
| `getBusy()` | Boolean | 是否正在执行操作，用于避免重复发起任务 |
| `getBusyOperation()` | String | 当前操作名称，可能为空；用于诊断，不通过匹配文字决定下一步 |
| `getStateUpdatedAt()` | Long，毫秒时间戳 | 状态更新时间，不是这次查询发起时间 |
| `getRecentError()` | String | 最近仍未解决的错误说明，可能为空；用于排查 |

Boolean/Long 为 null 表示未取得信息，不能当作 false/0。查询失败时暂停依赖就绪状态的新请求，不沿用上次的“可以识别”。busy 不能替代 modelReady；任务顺序见第 6.1 节。

`getStatus()` 是一次性查询；持续观察由宿主定时查询，或使用第 10.2 节的 `SdkStartupController`。

#### sdkState 有哪些值

信任等级、密钥和授权更新由 SDK 处理。以下状态用于决定下一步操作，内部诊断信息不直接展示给用户。

| 值 | 含义 | 建议处理 |
| --- | --- | --- |
| `NOT_INIT` | 尚未初始化 | 发起一次初始化并检查结果 |
| `INITIALIZING` | 正在初始化 | 等待并查询状态，避免重复初始化 |
| `REGISTERED` | 已注册，激活/准备流程尚未完成 | 查询授权信息，按实际状态引导激活或等待 |
| `NOT_ACTIVATED` | 未激活 | 引导输入激活码 |
| `READY` | 火眼 SDK 已启动 | 继续确认 modelReady=true |
| `OFFLINE` | 本地可用，设备身份尚未与云端校准 | 证书有效、modelReady=true 且密钥未丢失时可识别/学习；继续读状态等待 SDK 自动恢复 READY |
| `MODEL_PREPARING` | 模型正在准备 | 展示下载进度或“准备中”提示，继续查询 |
| `MODEL_NOT_READY` | 模型未就绪 | 暂停识别/学习，结合 recentError 排查 |
| `STOPPED` | 火眼 SDK 已停止 | 恢复连接/服务后重新确认状态 |
| `CLEANUP_PENDING` | 数据清理尚未完成 | 等待清理完成，不凭初始化成功恢复业务 |
| `KEY_ROTATION_PENDING` | 设备授权信息更新，等待审核 | 等待审核，联系对接人员处理 |
| `KEY_ROTATION_RESTORING` | 设备授权信息更新已获准，正在恢复 | 等待恢复完成并重新确认就绪 |
| `KEY_ROTATION_REJECTED` | 设备授权信息更新被拒绝 | 联系对接人员处理，不循环提交激活 |
| `KEY_ROTATION_RATE_LIMITED` | 设备授权信息更新请求过于频繁 | 等待限制解除，避免频繁重试 |
| `CERTIFICATE_EXPIRED` | 授权证书过期 | 查询授权信息及续期情况，联系对接人员 |
| `CERTIFICATE_INVALID` | 授权证书无效 | 检查设备与授权配置，联系对接人员 |
| `BACKEND_UNAVAILABLE` | 火眼 SDK 后端暂不可用 | 检查网络及服务，恢复后重查 |
| `STARTUP_FAILED` | 启动失败 | 根据 recentError 排查，处理后重新确认状态 |
| `DISABLED` | 火眼 SDK 已禁用 | 联系对接人员确认授权或使用策略 |
| null / 未识别的新值 | 尚未确认状态或新增状态 | 保留返回值供排查，不默认当成 READY；检查 modelReady |

`OFFLINE` 不等于客户端与 SDK 断连，也不代表证书过期。激活、切换模型、反馈、上传、同步以及开启云服务后的商品写入仍按各自的云端结果处理；不要因离线自动关闭云服务或将失败改成本地成功。

### 3.5 查询授权信息与 SDK 版本

通过 `client.getCapabilities()` 返回的 `Capability` 读取 SDK 安装包版本，不依赖激活或模型就绪。版本查询与授权查询独立处理，证书不可用不阻止版本展示。

| `Capability` 方法 | 类型 | 内容及空值处理 |
| --- | --- | --- |
| `getSdkVersionName()` | String | SDK 安装包版本名；`unknown` 表示未取得版本 |
| `getSdkVersionCode()` | int | SDK 安装包版本号；`-1` 表示未取得版本 |

无回执或版本缺失时显示占位符，不用 modelVersion 替代。错误回执仍可能携带版本；功能支持标志须在 code=0 时读取，见第 3.6 节。

`client.getCertInfo()` 返回 `CertInfo`，用于查询授权类型、激活时间和到期时间等证书信息。

| 方法 | 类型/单位 | 内容及空值处理 |
| --- | --- | --- |
| `getCode()` / `getReason()` | int / String | 查询结果，成功后再读证书字段 |
| `getActivationSerial()` | String | 激活时使用的码；未激活时可能为空 |
| `getLicenseType()` | String | 授权类型，按返回值展示；可能为空，不固定假设只有某几种类型 |
| `getModelCode()` / `getModelChannel()` | String / String | 授权对应的模型编码及渠道 |
| `getActivatedAt()` | long，秒时间戳 | 激活时间，0 表示无数据 |
| `getIssuedAt()` | long，秒时间戳 | 证书签发时间，0 表示无数据 |
| `hasRenewedAt()` / `getRenewedAt()` | boolean / long，秒 | 先检查 hasRenewedAt，再使用最近续期时间 |
| `hasNextRenewAt()` / `getNextRenewAt()` | boolean / long，秒 | 先检查 hasNextRenewAt，再使用下次续期时间 |
| `getExpiresAt()` | long，秒时间戳 | 到期时间；0 可能表示无数据或永久授权，页面显示“未提供到期时间”，不要直接显示“永久有效” |

```java
CertInfo cert = client.getCertInfo();
if (cert != null && cert.getCode() == 0) {
    String licenseType = cert.getLicenseType();
    Date activatedAt = cert.getActivatedAt() > 0
            ? new Date(cert.getActivatedAt() * 1000L) : null;
    // 按客户端时区格式化 activatedAt；续期字段先检查对应 has 方法。
}
```

示例需导入 `cc.huoyan.sdk.client.model.CertInfo` 及 `java.util.Date`。CertInfo 时间为**秒**，StatusSnapshot 的 stateUpdatedAt 为**毫秒**。授权续期由 SDK 处理，JAR 只提供续期信息查询。

### 3.6 查询支持的功能

`Capability` 的 code=0 时，读取 `isDetect()`、`isRecognize()`、`isItemLearn()`、`isItemForget()`、`isItemQuery()` 判断对应能力。`getApiVersion()` 返回接口版本；`isSharedMemoryImage()` 表示图像传输支持，通常由 JAR 处理。业务能否执行仍需核对模型状态。

### 3.7 准备输入图片

| 已有图片 | 调用方式 |
| --- | --- |
| 方向正确的 Bitmap | `client.image(bitmap)` 或直接 `client.recognize(bitmap)` |
| RGB888 字节 | `SdkImage.rgb888(bytes, width, height)` |
| JPEG/PNG 字节 | `SdkImage.encoded(bytes, width, height, format)`，格式用 `FORMAT_JPEG` / `FORMAT_PNG` |
| JPEG/PNG 尺寸未知 | `SdkImage.encoded(bytes, 0, 0, format)` |

RGB888 每像素为红、绿、蓝 3 字节，不能有行尾填充，总长度须为 `width × height × 3`；NV21/YUV 需先转换。相机采集和有效区域裁剪由宿主完成。

图像上限为 32 MiB。学习接口会将 JPEG/PNG 解码为 RGB888，解码后也不能超限；AIDL 不能建立大图传输时，回退上限为 RGB 1 MiB、JPEG/PNG 256 KiB，超出会返回 `IMAGE_UNAVAILABLE`。优先使用实际需要的图像区域和尺寸。

`SdkImage` 会复制输入字节；Bitmap 在转换完成前不得被覆盖或回收，JAR 不回收调用方 Bitmap。`client.image(bitmap)` 在 AIDL 下转 RGB888、WS 下转质量参数为 90 的 JPEG，转换也应在后台执行。

建议先把图片旋转到正确方向，以 `rotate=0` 提交。`recognize(image, rotate)` 仅接受 0/90/180/270；使用非零旋转时需核对返回框在预览图片上的位置。

<a id="business-flows"></a>

## 4. 按业务场景组合方法

### 4.1 新品拍照学习

学习、识别、查询与遗忘统一使用商品 PLU 原值字符串作为 `clazz`，允许 1–50 位 ASCII 英文字母与数字。大小写和前导零均保留，例如 `A001`、`a001`、`001` 是不同类别。不要转成 long 或映射成商品 ID。样本 UID 仍为 long。

```java
String clazz = "A001"; // 商品 PLU 原值。
ItemLearnBatch result = RecognitionIntegrationExample.learnCategory(client, input, clazz);
List<Long> uids = RecognitionIntegrationExample.successfulUids(result);
// 保存成功 UID；同时保留 result 的每个框结果，展示部分成功或失败原因。
```

| 方法 | 适合的图片 | 成功结果 |
| --- | --- | --- |
| `learn(image, clazz)` | 单目标图片；火眼 SDK 选取最高检测分框学习 | `LearnResult`，code=0 且 uid>0 |
| `batchLearn(image, clazz)` | 同类多个目标合拍，所有框学到同一分类 | `ItemLearnBatch`，每个框检查 code=0 且 uid>0 |
| `detect(image)` | 学习前需要画框预览 | 检测框列表；实际学习仍以学习返回为准 |

批量学习允许部分成功；整体 code=0 且框列表为空表示未检测到目标。若 3 个框中只有 2 个成功，保留这 2 个 UID 并显示剩余失败原因，不自动重学整张图。

学习成功后就能用于识别，无需再调用保存方法。关闭页面不会撤销已学样本；需要撤销时使用本次成功 UID 调用 `forgetSample(uid)`。

### 4.2 收银识别与商品匹配

按识别分类匹配业务商品，用户确认或改品后，按计件/称重方式计价。

| 识别字段 | 客户端用途 |
| --- | --- |
| `getClazz()` | 主分类编码字符串，用它查找对应商品 |
| `getCandidates()` | 候选分类，可供用户人工选择 |
| `getLeft/Top/Right/Bottom()` | 输入图像中的像素框，用于定位、绘制或裁剪 |
| `getDistance()` | 识别返回的距离数值，不是正确率；普通接入可先保留，不自行猜测阈值 |
| `getRank()` / `getSquare()` | 其他识别数值，没有使用需求时可忽略 |

`DetectResult.getBoxes()` 使用 `x/y/w/h/score`；识别框使用 `left/top/right/bottom`。空结果属于正常识别结果。多个框可能是同类商品，不要只因 clazz 相同就删掉重复项；找不到对应商品时保留编码，允许人工选择。

| 标识 | 含义 | 使用约定 |
| --- | --- | --- |
| `clazz` | 识别分类 | 学习、识别、查询与遗忘均使用商品 PLU 原值字符串，规则见第 4.1 节 |
| `uid` | 一个学习样本 | 火眼 SDK 返回的正 long，用于样本定位和删除 |
| 业务商品编码 / PLU | 客户端自己的商品标识 | 直接作为 clazz，保留大小写和前导零 |
| `goodsId` | 火眼 SDK 商品 ID | 用于可选的商品接口或反馈，不等同于 clazz、uid 或本地数据库主键 |

切换门店或 SDK 设备后，按当前商品的 PLU 匹配识别结果。

连续识别可在上一张处理完后取相机最新图片，避免排队。宿主决定结果接受条件、人工确认时的暂停策略及计价；改品时分别保存原始结果与最终选择，供反馈使用。

### 4.3 查看、补学与删除样本

| 需求 | 调用 |
| --- | --- |
| 查看已学分类 | `listClasses(page, size)` |
| 查看一个分类的样本 | `listImages(clazz, page, size)` |
| 删除单个错误样本 | `forgetSample(uid)` |
| 清空某分类的全部样本 | `forgetClass(clazz)` |

分类分页中的 `clazz[]`、`uidCount[]` 按下标对应；非空 `thumbnails[]` 同样对应。分页从 1 开始，查询时检查返回页码及总页数。样本即使缺少图片也应保留 UID，方便删除。

样本图片用返回的完整 `imageUrl` 加载，不自行拼端口；`imageAvailable=false` 时显示占位图。火眼 SDK 重启后地址可能变化，应重新查询。

第 10.5 节的 `LearnedClazzPages`、`LearnedImagePages` 和 `SampleImages` 提供分页汇总、UID 查图及地址缓存，并说明取消和缓存失效方式。

### 4.4 提交识别反馈

每个识别目标对应一个 `FeedbackItem`。`detect` 保留该框原始命中和候选，人工操作不改写它；`result` 保存最终商品及来源 `sdk` / `manual`。删除目标时 `result` 上报 `{}`，即使此前已改品，仍保留原始识别和检测框。

```java
List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(
        detections, imageWidth, imageHeight);
FeedbackSubmission submission = FeedbackSubmission.fromFile(photo,
        Feedback.FEEDBACK_TYPE_NORMAL, items,
        FeedbackSubmission.PhotoRetention.KEEP);
FeedBackUploadHelper.submit(client, submission, worker, completion);
```

`detections` 为 `List<FeedbackDetection>`，包含原商品、最终商品、删除标记、原始命中距离、同框 SDK 候选及上传原图坐标。商品使用 `FeedbackProduct(goodsId, name, plu)`，以 PLU 标识；没有 SDK 商品 ID 时 goodsId 可传 null，不传宿主数据库主键。

`prepareDetectFeedback()` 将原命中及其距离放在 detect 首项，再按 clazz 原值去重，按首次出现顺序追加同框候选及其距离；商品名和距离不参与去重，不混入其他框。没有候选时仍保留原命中。distance 保留三位小数，不传检测 score。

| 用户操作 | item_type | result |
| --- | --- | --- |
| 确认原商品 | 0 | SDK 原商品 |
| 商品 A 改为 B | 1 | 最终选择 B |
| 删除目标（含改品后删除） | 2 | 空对象 `{}` |

构造参数 `feedbackType` 只接受 0/1，最终上报值由 JAR 按 items 计算：全部正常为 0，存在修改或删除为 1。因此示例传入 `FEEDBACK_TYPE_NORMAL` 也能提交改品或删除结果。

学习小图的上传和云端登记由 SDK 自动完成，客户端不提交学习 feedback，也不直接调用小图 HTTP 接口。

反馈图片须为 JPEG/PNG 且不超过 8 MiB。`KEEP` 保留源文件；`DELETE_ON_SUCCESS` 只在提交成功且文件未改变时删除专用临时图片。失败或结果未知时保留文件。后台读取和提交，更新界面需切回主线程。重试时复用同一个 `FeedbackSubmission`，沿用 requestId 和上传 URL；成功后不会再次提交。

### 4.5 图片与框的坐标对应

绘制框使用识别输入图的坐标；反馈框必须属于上传原图。参考客户端裁剪后识别、上传原图时，需要还原裁剪偏移：

```text
原图 1920×1080，裁剪左上角为 (200,100)
识别输入中目标框为 (100,80)～(340,260)
原图中目标框为 (300,180)～(540,360)
反馈填写 x=300、y=180、width=240、height=180
```

反馈 width/height 是框的宽高，不是整张图尺寸。存在缩放、旋转、镜像时保存图像变换并还原；预览绘制还需考虑控件缩放和留白。不要将上一帧的框用于另一张图。

## 5. 其他可选功能

### 5.1 门店绑定

需要把设备关联到门店、同步商品或提交反馈时，按项目要求绑定门店。火眼 SDK 若返回未绑定错误，先完成绑定再继续相关操作。

```java
BindingResult binding = client.bindDevice("erp-shop-007");
// 可选门店信息：client.bindDevice("erp-shop-007", "一号店", null)
```

客户端传入稳定且不与其他门店重复的 `shopId`，支持字母、前导零和 UUID；账号由交付 JAR 提供，调用方不能覆盖。绑定成功后区分保存：

| 字段 | 含义 |
| --- | --- |
| `binding.getRequest().getShopId()` | 客户端传入的外部门店标识 |
| `binding.getShopId()` | 火眼 SDK 返回的门店 ID |
| `binding.getAccountId()` | 实际绑定账号 ID |
| `binding.getShopAddress()` | 门店地址，来自后台 `address` |
| `binding.getShopPhone()` | 门店电话，来自后台 `shop_phone` |

地址和电话未填写时返回空字符串；成功绑定后与归属一起保存，空值也覆盖旧店资料。电话不使用联系人手机号替代。

绑定失败时保留原门店信息。多个系统共用一个账号时，可给门店 ID 加不同前缀，避免重名；重连不会自动重新绑定门店。

### 5.2 商品资料与同步

已有商品系统时，可继续使用自己的数据库，直接以商品 PLU 原值作为 clazz。参考客户端通过火眼 SDK 商品接口查询和修改商品，不另建商品数据库或持久化商品映射。创建、查询、更新和同步方法见第 8.4 节。

`GoodsSave` 构造顺序：

```java
new GoodsSave(name, plu, image, ingredientsList, shelfLife, pinyin,
        category, categoryName, unit, saleMode, tareWeight, price, sort, status);
```

创建时 name/plu 必填；更新时可选字段传 null 表示不修改，数值 0 是实际值。`price` 为 Long 类型的整数分，`tareWeight` 为 Long 类型的克；saleMode 为 0 按个、1 按重量；status 为 1 上架、2 下架；sort 小在前。category 等业务分类按对接项目约定。

`listGoods(syncTimestamp, current, size)` 查询服务器上的商品。syncTimestamp 传 0 表示查询全部商品；本次数据全部保存到自己的数据库后，再保存返回的 syncTimestamp，下次用它查询之后的变更。`downloadGoods(size)` 将商品同步到火眼 SDK 本地数据中，不会写入客户端数据库，也不等于图片已学习。

批量更新或删除即使整体 code=0，也需每一项检查结果。商品同步需检查最终阶段、失败原因和增删改统计。多数商品方法使用 String ID，`downloadImagesAndLearnByGoodsId` 使用正 long 数组，转换前检查范围。

### 5.3 图片下载学习与备份恢复

| 需求 | 方法组合 |
| --- | --- |
| 已有云端商品图片，下载并学习 | `downloadImagesAndLearn()` → 独立查询下载/学习进度 |
| 更新指定商品图片样本 | `downloadImagesAndLearnByGoodsId(goodsIds)`；会替换相关分类旧样本 |
| 查看失败图片 | `getFailedImages()`；code=0 且列表为空才表示无失败记录 |
| 备份识别数据 | `backupRecognitionData()` / `getRecognitionDataInfo()` |
| 恢复识别数据 | `restoreRecognitionData()` → 重新检查模型状态并刷新样本 |

图片下载学习会等待最终统计返回，进度查询请使用另一后台线程。检查 `ImageFlowProgress.outcome()`，同时展示下载失败、学习失败和跳过数量；不能仅用 code=0 判断全部学习成功。IDLE 不是完成成功。

恢复前暂停识别与学习，恢复成功后再次确认模型可用。第三方客户端自行维护的商品数据库不包含在识别数据备份中，应由该客户端管理。

### 5.4 多个页面共用连接

使用 `SdkSession` 共享连接、`SdkStartupController` 观察启动状态，后台与回调 Executor 由宿主提供。调用见 [SessionIntegrationExample.java](samples/SessionIntegrationExample.java)，重建连接和停止观察的约束见第 10.2 节。

### 5.5 在客户端中下载和安装火眼 SDK

用 `SdkConfig.requiresLocalShell()` 判断本机安装需求，`SdkShell.installedVersion()` 查询安装版本，`SdkApkDownloader` 下载配套安装包。下载回调、取消和文件清理约定见第 10.8 节。设备版本与下载地址由对接方确认。

<a id="error-handling"></a>

## 6. 线程、状态与错误处理

### 6.1 线程与生命周期

SDK 请求会阻塞等待结果，与图片转换一样须在后台执行。更新页面时切回主线程，丢弃已关闭页面或已切换商品的旧结果。

修改同一商品样本或切换门店的操作，建议一个完成后再做下一个。批量学习、恢复数据期间暂停识别；耗时操作和进度查询用不同后台线程。

连续识别使用同一个连接；页面离开时停止提交新图片。全局连接可供其他页面继续使用，真正不再使用时调用 `client.close()`。关闭后的客户端实例不可重连，需重新 create。已提交的学习、绑定、删除和反馈，不会因页面退出、线程中断或关闭连接而自动撤销。

自动重连只恢复通信，不自动调用 `initialize()`。宿主确认重新连接后，需在后台初始化并重新检查模型状态，再恢复识别。也可重新启动 `SdkStartupController` 完成此流程；它在模型就绪后已结束观察，需先 stop 再 start。

### 6.2 返回结果与处理原则

先检查结果非 null，再检查 code=0，最后读取状态、列表或每一项结果。错误时保留 code/reason。需要日志可通过 `config.withLog((tag, message) -> ...)` 接入。

| 结果 | 处理 |
| --- | --- |
| code=0 | 请求成功，再检查 modelReady、样本 UID、批量操作中每一项的结果或流程统计 |
| null / `RESULT_UNKNOWN`（40009） | 还不知道是否成功；学习、删除、激活等操作先核对，不自动重发 |
| `LOCAL_DISCONNECTED`（-2147483648） | 本次请求未发送，等待连接；之前已经发出的请求仍可能已执行 |
| `NOT_EXECUTED`（40010） | 本次没有执行，可间隔一段时间再试，并限制重试次数 |
| `IMAGE_UNAVAILABLE`（-2147483647） | 本次图像请求未发送，调整图像或检查连接方式 |
| -1001 / -1002 | 未绑定 / 不允许执行；检查门店绑定或按 reason 排查配置 |
| -1003 / -1004 | 存储失败 / 配置保存失败；检查设备存储与火眼 SDK 状态 |
| 其他非零值或参数异常 | 保留原因，修正输入或排查对应功能 |

学习超时后先查样本核对，不直接重学；普通查询失败可以重查。SDK 后续上传失败也不改变已返回的成功 UID。

运行状态的完整取值与处理见第 3.4 节，也可用 `SdkStatus.issue(initCode, status)` 获取问题类型并展示自己的文案。

### 6.3 等待上限

| WebSocket 操作 | 当前等待上限 |
| --- | --- |
| 常规查询（下列模型查询除外）、识别、检测、单目标学习、遗忘、商品操作/同步 | 35 秒 |
| 模型查询 `queryLicenseModels()` / `queryDeviceModels()` | 125 秒 |
| 初始化、激活、批量学习、识别库备份/恢复 | 125 秒 |
| 图片上传、反馈 | 65 秒 |
| 门店绑定 | 60 秒 |
| 整库或指定商品图片下载学习 | 30 分钟 |

AIDL 调用没有可强制终止的超时；不要将等待结束等同于操作未执行。不知道总量时只显示“处理中”，只有 total>0 才计算百分比。

<a id="reference-client"></a>

## 7. 运行和修改参考客户端

参考客户端面向生鲜、零食、烘焙和小碗菜。建议先跑通“新增商品 → 拍照学习 → 收银识别 → 人工确认”，再改造页面或提取 JAR 调用。

参考客户端已经配置好 JAR 依赖，不必再按第 2 节重复导入。构建时将 Gradle JDK 设置为 JBR-17，并配置 Android SDK；在源码根目录执行 `bash gradlew :app:assembleDebug`，生成的 APK 位于 `app/build/outputs/apk/`。安装到已部署配套火眼 SDK 的设备后，从设置页面确认连接方式。设备选型及相机、秤、打印机接线按实际项目确认。

app 构建使用 `app/src/main/libs/huoyan-sdk-client-2.0.0.jar`，不依赖 SDK module。修改 SDK 源码后，先执行 `bash gradlew :huoyan-sdk-client:updateAppSdkJar` 替换此 JAR（包括内置账号配置），再构建 app。

以下 Java 路径相对于 `app/src/main/java/cc/huoyan/retail/`。

| 功能 | 源码位置（相对于上述前缀） | 内容 |
| --- | --- | --- |
| 激活与授权面板 | `settings/PanelFragment.java`、`PanelPresenter.java`、`PanelSdkSource.java`（同目录） | 手输/扫码激活、显示进度、状态及证书展示 |
| 连接配置和首页状态 | `sdk/SdkChannelManager.java`、`home/HomeActivity.java` | 保存连接配置、共享 JAR 客户端、展示是否可以识别 |
| 选择本机或远端连接 | `settings/SdkConnectionFragment.java`、`SdkConnectionPresenter.java`（同目录） | 选择连接方式、发现与切换设备 |
| 商品与分类对应 | `goods/SdkClassMapping.java`、`goods/` | 校验 PLU 并原样作为 clazz，通过 SDK 商品接口读取商品 |
| 新品拍照学习 | `learning/StudyActivity.java`、`StudyPresenter.java`、`StudyLearningSource.java`（同目录） | 图片准备、学习调用、成功 UID 与样本展示 |
| 商品学习列表 | `learning/ProductLearningActivity.java`、`ProductLearningPresenter.java`（同目录） | 选择商品、进入学习与查看样本 |
| 收银识别 | `checkout/MainActivity.java`、`recognition/RecognitionLoop.java` | 相机输入、调用时机、商品匹配和人工确认 |
| 人工修正反馈 | `feedback/FeedbackInputs.java`、`checkout/integration/CheckoutOutput.java` | 保存提交时用户选择的商品，再提交反馈 |
| 样本图片展示 | `sdk/SdkGoodsImages.java` | 使用返回 URL、缓存，图片变化后重新查询 |
| 相机、秤、打印设备 | `device/` | 按实际硬件替换设备接入 |

参考客户端的等待使用 LoadingDialog，操作结果使用 ToastEx，页面不追加内部诊断状态。以下 Java 示例随 JAR 提供。

| 示例 | 用途 |
| --- | --- |
| [RecognitionIntegrationExample.java](samples/RecognitionIntegrationExample.java) | 连接、检测识别、学习、样本、反馈及错误处理 |
| [ActivationStatusExample.java](samples/ActivationStatusExample.java) | 未激活设备连接、单次激活提交；配合状态和授权查询 |
| [SdkExample.java](samples/SdkExample.java) | 最小连接与调用 |
| [SessionIntegrationExample.java](samples/SessionIntegrationExample.java) | 可选的共享连接与状态查询 |
| [FeedbackIntegrationExample.java](samples/FeedbackIntegrationExample.java) | 识别反馈组合调用与文件保留 |

<a id="client-api"></a>

## 8. HuoyanSdkClient 方法速查

下表列出 `HuoyanSdkClient` 的公开方法，类型位于 `cc.huoyan.sdk.client` 及其 `model` 包。请求火眼 SDK 的方法在后台线程执行；公共辅助 API 见第 10 节。

### 8.1 连接、状态与绑定

| 方法 | 返回 | 关键点 |
| --- | --- | --- |
| `HuoyanSdkClient.create(Context, SdkConfig)` | `HuoyanSdkClient` | 创建连接对象，传入应用 Context |
| `connect()` | void | 异步连接，重复调用不重复连接，支持自动重连 |
| `isConnected()` | boolean | 仅通信状态 |
| `close()` | void | 关闭后创建新实例才能再使用 |
| `initialize()` | `BaseResult` | 自动携带宿主 APK 的 versionName 初始化，仍需查询就绪 |
| `getCapabilities()` | `Capability` | 查询支持的功能及 SDK 安装包版本；版本不依赖激活，缺失时为 unknown/-1 |
| `getStatus()` | `StatusSnapshot` | code、sdkState、modelReady、busy、recentError 等 |
| `getCertInfo()` | `CertInfo` | 授权及证书信息 |
| `getDownloadProgress()` | `DownloadProgress` | 模型下载字节进度，total=0 不计算百分比 |
| `queryLicenseModels(activationCode)` | `LicenseModels` | 正常激活按输入激活码查询；空码返回错误 |
| `queryDeviceModels()` | `LicenseModels` | 无参查询当前设备支持的模型 |
| `activate(String activationCode, String modelCode)` | `BaseResult` | 激活后继续确认模型状态 |
| `bindDevice(String shopId)` | `BindingResult` | 外部标识，账号由 JAR 内置 |
| `bindDevice(String shopId, String shopName, String shopNo)` | `BindingResult` | 增加可选门店信息 |

### 8.2 图像、识别、学习与样本

| 方法 | 返回 | 关键点 |
| --- | --- | --- |
| `image(byte[] rgb, int width, int height)` | `SdkImage` | 复制 RGB888 字节作为本次图片 |
| `image(Bitmap bitmap)` | `SdkImage` | AIDL RGB888、WS JPEG90 |
| `jpegImage(Bitmap bitmap)` | `SdkImage` | JPEG90 |
| `photoImage(byte[] bytes, int format)` | `SdkImage` | JPEG/PNG，未知尺寸 |
| `detect(SdkImage)` / `detect(Bitmap)` | `DetectResult` | 只有检测框，不识别类别 |
| `recognize(SdkImage)` / `recognize(Bitmap)` | `RecognizeResult` | 方向正确的图片 |
| `recognize(SdkImage, int rotate)` | `RecognizeResult` | rotate 仅 0/90/180/270 |
| `learn(SdkImage, String clazz)` / `learn(Bitmap, String clazz)` | `LearnResult` | 一个样本 UID，clazz 为 1–50 位英文字母与数字字符串 |
| `batchLearn(SdkImage, String clazz)` | `ItemLearnBatch` | 所有目标学到同一分类，逐个检查 code/uid |
| `listClasses(int page, int size)` | `ItemClazzList` | 1-based，0 使用默认 1/100 |
| `listImages(String clazz, int page, int size)` | `ItemClazzImagePage` | UID、图片可用状态、URL |
| `forgetSample(long uid)` | `BaseResult` | 删除一个样本 |
| `forgetClass(String clazz)` | `BaseResult` | 删除分类全部样本 |

### 8.3 反馈、云学习与维护

| 方法 | 返回 | 关键点 |
| --- | --- | --- |
| `uploadPhoto(SdkImage)` | `PhotoUploadResult` | JPEG/PNG 上传后取 photoFileUrl |
| `submitFeedback(Feedback)` | `BaseResult` | 包含已上传 URL 和反馈项 |
| `getCloudStatus()` | `CloudStatus` | 查询云开关 |
| `setCloudStatus(boolean enabled)` | `CloudStatus` | 当前火眼 SDK 的云服务开启后不可关闭；检查返回结果 |
| `setBaseUrl(String url)` | `BaseResult` | 火眼 SDK 上传数据使用的服务器地址，不是 WS 地址；按实际部署配置 |
| `downloadImagesAndLearn()` | `ImageFlowProgress` | 等待最终结果返回 |
| `downloadImagesAndLearnByGoodsId(long[] goodsIds)` | `ImageFlowProgress` | 火眼 SDK 商品 ID，重新学习会替换相关旧样本 |
| `getImageDownloadProgress()` / `getImageLearnProgress()` | `ImageFlowProgress` | 可用独立线程查询 |
| `getFailedImages()` | `FailedImagesResult` | code=0 且 items 为空才表示无失败记录 |
| `backupRecognitionData()` | `BaseResult` | 火眼 SDK 管理的识别库备份 |
| `getRecognitionDataInfo()` | `RecognitionDataInfo` | 备份信息 |
| `restoreRecognitionData()` | `BaseResult` | 恢复后重新检查状态 |

### 8.4 商品资料与同步

| 方法 | 返回 | 关键点 |
| --- | --- | --- |
| `createGoods(GoodsSave req)` | `GoodsResult` | 创建后从 `getRecord().getGoodsId()` 保存火眼 SDK 商品 ID |
| `updateGoods(String goodsId, GoodsSave req)` | `GoodsResult` | 仅更新传入的字段 |
| `deleteGoods(String goodsId)` | `BaseResult` | 单商品删除；不修改客户端本地商品 |
| `getGoods(String goodsId)` | `GoodsResult` | 查询火眼 SDK 本地商品；不存在返回 404 |
| `queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder)` | `GoodsQueryResult` | 查询火眼 SDK 本地库；`isDeleted=-1` 默认未删除，页码/页容量 0 使用火眼 SDK 默认 |
| `getGoodsCount(String keyword, String name, String plu, int isDeleted)` | `GoodsCountResult` | 按相同筛选条件取 count |
| `batchUpdateGoods(String[] goodsIds, GoodsSave changes)` | `GoodsBatchResult` | 整体 code=0 后仍须逐条检查 items 的 code/reason |
| `batchDeleteGoods(String[] goodsIds)` | `GoodsBatchResult` | 批量软删除；逐条处理失败项，不重发已成功项 |
| `listGoods(long syncTimestamp, int current, int size)` | `GoodsListResult` | 按页查询服务器商品，时间戳为 0 时查全部商品；返回 records、分页信息、syncMode、syncTimestamp |
| `downloadGoods(int size)` | `GoodsDownloadResult` | 将云商品同步至火眼 SDK 本地库并等待最终统计；独立于图片下载学习 |
| `getDownloadGoodsProgress()` | `GoodsDownloadProgress` | 查询商品同步进度，可在另一后台线程观察 |

<a id="verification"></a>

## 9. 试运行与常见问题

按实际接入范围验证以下流程。

1. 确认 JAR 与火眼 SDK 配套、应用包名已登记，连接成功后取得 modelReady=true。
2. 验证识别：使用已学商品的固定图片，核对分类与商品；覆盖无目标和未匹配商品。接入学习功能时，先学一个分类并保存成功 UID，再识别。
3. 接入样本管理时验证分页、图片展示和单样本删除；只有明确成功才刷新为已删除状态。
4. 接入相机后核对裁剪、旋转、画框位置，以及页面退出后旧结果不会更新新页面。
5. 如使用反馈、商品同步或恢复，分别验证成功、失败、部分成功及结果未知时的处理。

| 问题 | 优先检查 |
| --- | --- |
| 无法连接 / 不允许的调用方 | 火眼 SDK 部署、应用包名登记、权限、IP/端口及连接方式 |
| 已连接但无法识别 | modelReady、激活状态、模型准备及 recentError |
| 有分类但找不到商品 | clazz 与业务商品编码是否对应正确、商品是否属于当前门店或设备 |
| 已学成功但图片加载失败 | UID 是否保留、imageAvailable、完整 URL、网络策略及地址刷新 |
| 画框或反馈位置偏移 | 本次图像的裁剪偏移、缩放、旋转与上传原图坐标 |
| 长任务期间进度不刷新 | 操作与进度查询是否共用唯一后台线程 |

<a id="helper-api"></a>

## 10. 公共辅助 API

以下类型均位于 `cc.huoyan.sdk.client` 根包或注明的子包；返回模型位于 `model`。表中标为“后台”的方法在调用线程执行阻塞工作，须由宿主安排后台线程。纯校验、结果解释和几何计算不发起 SDK 请求。`BooleanSupplier`、`Supplier`、`Function`、`Consumer` 来自 `java.util.function`，`Executor` 来自 `java.util.concurrent`。

### 10.1 连接配置与设备发现

以下类型位于根包。

| 方法 | 返回 | 用法与约束 |
| --- | --- | --- |
| `SdkConfig.aidl()` / `SdkConfig.websocket(String url)` | `SdkConfig` | 创建连接配置，平台要求和地址规则见第 3.1 节 |
| `SdkConfig.supportedChannel(SdkConfig.Channel channel, int apiLevel)` | `Channel` | API 27 以下返回 WS，否则返回传入通道；不创建连接 |
| `SdkConfig.Channel.value()` / `SdkConfig.Channel.fromValue(String value)` | `String` / `Channel` | 持久化值为 `aidl` / `ws`；解析时仅 `ws` 返回 WS，其余值返回 AIDL，仍需检查平台支持 |
| `SdkConfig.requiresLocalShell()` | `boolean` | AIDL 或本机 WS 需要本机 SDK；远端 WS 不需要 |
| `SdkConfig.withLog(SdkLog log)` | `SdkConfig` | 返回使用新日志出口的配置；log 不可为 null |
| `SdkConfig.getChannel()` / `getWsEndpoint()` / `getLog()` | `Channel` / `String` / `SdkLog` | 读取配置；AIDL 的 WS 地址为 null |
| `SdkLog.log(String tag, String message)` | `void` | 宿主实现日志接口；不保证在主线程调用，不直接操作控件 |
| `SdkDiscovery.endpointForIp(String input)` | `String` | 校验并规范化 IPv4，生成 `ws://IP:18086/ws`；无效输入抛 `IllegalArgumentException` |
| `SdkDiscovery.ipFromEndpoint(String endpoint)` | `String` | 提取 IPv4；无法解析时返回 `127.0.0.1`，不代表地址有效或探测成功 |
| `SdkDiscovery.probe(String endpoint)` | `boolean` | 后台验证 SDK 状态回执；true 只表示服务响应，不表示模型就绪 |
| `SdkDiscovery.discover(String preferredIp)` | `List<String>` | 后台扫描本机 wlan/eth IPv4 网段及指定地址，返回排序后的响应 IP；入参须为合法 IPv4，读取网络接口可能抛 `SocketException` |

默认地址常量为 `SdkConfig.DEFAULT_WS_ENDPOINT`；发现工具提供 `SdkDiscovery.DEFAULT_PORT`（18086）和 `DEFAULT_PATH`（`/ws`）。

### 10.2 连接等待与共享会话

以下类型位于 `session`。

| 方法或构造器 | 返回 | 用法与约束 |
| --- | --- | --- |
| `SdkConnection.await(HuoyanSdkClient client, long timeoutMillis, long pollMillis, BooleanSupplier active)` | `boolean` | 后台调用 connect 并等待连接；连接成功返回 true，超时、取消或中断返回 false；保留中断标记，不关闭客户端；timeoutMillis≥0、pollMillis>0 |
| `new SdkSession(SdkConfig config, Function<SdkConfig, HuoyanSdkClient> factory)` | `SdkSession` | 延迟创建客户端；工厂不持有页面，可使用应用 Context 创建客户端 |
| `SdkSession.getConfig()` | `SdkConfig` | 返回当前配置，不触发连接 |
| `SdkSession.getClient()` | `HuoyanSdkClient` | 首次调用时通过工厂创建，之后复用；不自动 connect |
| `SdkSession.configure(SdkConfig next)` | `void` | 通道或地址改变时关闭旧客户端；仅更换日志配置不重建已有客户端；不转移或重发旧请求 |
| `SdkSession.close()` | `void` | 关闭当前客户端；之后 getClient 创建新的客户端实例 |
| `new SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker, Executor callbacks, SdkLog log)` | `SdkStartupController` | 注入客户端来源、后台执行器、回调执行器和日志；默认轮询间隔 200ms |
| `new SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker, Executor callbacks, SdkLog log, long pollMillis)` | `SdkStartupController` | 指定正数轮询间隔；worker 用于阻塞查询，callbacks 决定回调线程 |
| `SdkStartupController.start(SdkStartupController.Listener listener)` | `void` | 启动连接、初始化与状态观察；同轮重复调用无效，模型就绪后结束轮询 |
| `SdkStartupController.stop()` | `void` | 停止观察并丢弃旧代次回调；不关闭连接、不撤销已提交操作；再次观察先 stop 再 start |
| `SdkStartupController.Listener.onStatus(int initCode, StatusSnapshot status, boolean modelReady)` | `void` | 在 callbacks 执行器回调；status 可能为 null，initCode 表示初始化结果 |

`active` 在后台被读取，跨线程修改的取消标记应保证可见性。超时或取消连接等待不等于取消 SDK 业务操作。

### 10.3 图像快照与输入校验

`SdkImage` 位于根包；`SdkInputs` 位于 `operation`；`ActivationCode` 位于 `activation`。

| 方法 | 返回 | 用法与约束 |
| --- | --- | --- |
| `SdkImage.rgb888(byte[] bytes, int width, int height)` | `SdkImage` | 复制紧凑 RGB888，字节数须等于 width×height×3，宽高为正 |
| `SdkImage.rgb888(Bitmap bitmap)` / `SdkImage.jpeg(Bitmap bitmap)` | `SdkImage` | 后台提取 RGB888 或编码 JPEG90；不裁剪、旋转或回收调用方 Bitmap |
| `SdkImage.encoded(byte[] bytes, int width, int height, int format)` | `SdkImage` | 复制非空 JPEG/PNG 字节，未知尺寸传 0；格式使用 `FORMAT_JPEG` / `FORMAT_PNG` |
| `SdkImage.getBytes()` | `byte[]` | 每次返回独立副本；修改副本不影响快照，大图复制放在后台 |
| `SdkImage.getWidth()` / `getHeight()` / `getFormat()` / `getSize()` | `int` | 像素宽高、格式、字节数；编码图片宽高允许为 0 |
| `SdkInputs.positiveId(long id)` | `long` | 正数原样返回，否则抛 `IllegalArgumentException`；用于样本 UID 等数字 ID |
| `SdkInputs.clazz(String value)` | `String` | 校验 1–50 位 ASCII 字母或数字后原样返回；非法输入抛 `IllegalArgumentException` |
| `SdkInputs.isClazz(String value)` | `boolean` | 同一分类规则的布尔校验，null 返回 false，不修改原值 |
| `ActivationCode.normalize(String input)` | `String` | 整理激活码格式，具体规则见第 3.3 节 |
| `ActivationCode.validManual(String input)` | `boolean` | 检查手输码长度，不验证授权有效性；不用于拦截其他长度的扫码 serial |

图像格式常量为 `FORMAT_RGB888=1`、`FORMAT_JPEG=2`、`FORMAT_PNG=3`；`MAX_TRANSPORT_BYTES` 为 32 MiB。反馈图片另有 8 MiB 上限。

### 10.4 批量学习、长操作与结果解释

以下操作工具位于 `operation`，`ImageFlowProgress` 位于 `model`。

| 方法 | 返回 | 用法与约束 |
| --- | --- | --- |
| `BatchLearning.learnedBoxesOf(ItemLearnBatch batch)` | `List<ItemLearnBox>` | 整体 code=0 时提取 code=0 且 uid>0 的框，按 UID 去重并保留顺序；空回执或整体失败返回空列表，不能据此判断“无目标” |
| `BatchLearning.boxDetailOf(ItemLearnBatch batch)` | `String` | 生成每框 UID/失败码诊断文本，不参与成功判定 |
| `SdkOperations.learnImages(HuoyanSdkClient client)` | `ImageFlowProgress` | 后台提交一次整库图片下载学习；未连接返回 LOCAL_DISCONNECTED，缺回执或运行时异常转 RESULT_UNKNOWN |
| `SdkOperations.imageProgress(HuoyanSdkClient client, boolean download)` | `ImageFlowProgress` | 后台查询进度；true 查下载、false 查学习；运行时异常转 RESULT_UNKNOWN，底层无回执仍可能返回 null |
| `SdkOperations.activate(HuoyanSdkClient client, String code, String modelCode)` | `BaseResult` | 后台规范化激活码并提交一次；未连接返回 LOCAL_DISCONNECTED，缺回执或运行时异常转 RESULT_UNKNOWN |
| `SdkOperations.activationProgress(HuoyanSdkClient client)` | `DownloadProgress` | 后台查询模型下载进度；未连接、异常或缺回执返回 null |
| `OperationOutcome.fromCode(int code)` | `OperationOutcome` | 0→SUCCESS；LOCAL_DISCONNECTED / NOT_EXECUTED→NOT_EXECUTED；RESULT_UNKNOWN→UNKNOWN；其他码→FAILURE |
| `ImageFlowProgress.phase()` | `ImageFlowProgress.Phase` | 将阶段转换为 IDLE、DOWNLOADING、LEARNING、SUCCESS、FAILED、UNKNOWN；缺失或未知值返回 UNKNOWN，原值由 getPhase 保留 |
| `ImageFlowProgress.learningStarted()` | `boolean` | code=0 且处于学习/成功阶段或 current>0 时为 true；用于选择等待进度，不代表最终成功 |
| `ImageFlowProgress.outcome()` | `ImageFlowProgress.Outcome` | 解释最终操作回执为 SUCCESS、PARTIAL_SUCCESS、FAILURE、NOT_EXECUTED、UNKNOWN；下载或学习失败数量>0 可得到部分成功 |

`SdkOperations` 不自动重试。进度查询与长操作应使用不同后台线程；轮询进度不能替代最终操作回执。`outcome()` 不把 skippedCount 单独判为部分成功，宿主仍须读取跳过数量与原因。第 6.2 节的原始结果码仍需保留，例如 `IMAGE_UNAVAILABLE` 表示图像请求未发送，不能只依赖通用分类推断所有错误的发送状态。

### 10.5 样本分页与图片地址缓存

以下类型位于 `samples`。分页方法均在后台调用；返回 URL 不表示已下载图片。

| 方法 | 返回 | 用法与约束 |
| --- | --- | --- |
| `LearnedClazzPages.loadAll(HuoyanSdkClient client)` | `ItemClazzList` | 每页 100 条汇总全部分类 |
| `LearnedClazzPages.loadAll(HuoyanSdkClient client, int pageSize)` | `ItemClazzList` | 指定正数页容量；任一页失败返回失败回执或 null，不返回部分列表；没有取消条件参数 |
| `LearnedImagePages.loadPage(HuoyanSdkClient client, String clazz, int page, int size, BooleanSupplier isCurrent)` | `ItemClazzImagePage` | 查询单页并校验回执与页码，页码从 1 开始 |
| `LearnedImagePages.loadNewestPage(HuoyanSdkClient client, String clazz, int size, BooleanSupplier isCurrent)` | `ItemClazzImagePage` | 先读取总页数再取末页；并发删除导致页数缩小时最多重新定位一次 |
| `LearnedImagePages.loadAll(HuoyanSdkClient client, String clazz)` | `List<ItemClazzImage>` | 每页 100 条读完整图集，保留缺图样本 |
| `LearnedImagePages.loadAll(HuoyanSdkClient client, String clazz, BooleanSupplier isCurrent)` | `List<ItemClazzImage>` | 增加页面/商品有效性检查，避免继续查询旧商品 |
| `LearnedImagePages.findUrls(HuoyanSdkClient client, String clazz, long[] uids, int size, BooleanSupplier isCurrent)` | `Map<Long, String>` | 从最新页倒查正 UID，返回找到的有效图片地址；未找到或缺图的 UID 不进入 Map |
| `LearnedImagePages.latestUrls(List<ItemClazzImage> images, int limit)` | `List<String>` | 本地按 UID 降序提取最多 limit 个不重复 HTTP(S) 地址；跳过缺图项，不发请求 |
| `LearnedImagePages.isHttpUrl(String url)` | `boolean` | 检查 HTTP(S) 协议和 host，不验证地址是否可访问 |

`LearnedImagePages` 在每页请求前后检查 `isCurrent` 和线程中断；失效时抛 `CancellationException`，查询失败或分页异常抛 `IllegalStateException`。取消检查不会强制中止已发出的请求，宿主仍需在更新页面前核对代次。

| 方法或构造器 | 返回 | 用法与约束 |
| --- | --- | --- |
| `new SampleImages(long ttlMillis, int previewLimit)` | `SampleImages` | 设置地址缓存时长（毫秒）和预览数上限，两者须非负；不创建线程 |
| `SampleImages.update(ItemClazzList result, HuoyanSdkClient client)` | `void` | 用成功的全量分类回执更新缩略图、客户端归属及缓存版本；失败回执不更新，不发请求 |
| `SampleImages.refreshAddresses(HuoyanSdkClient client)` | `void` | 宿主在后台调用时刷新过期地址，不定时刷新；切换客户端时失效旧缓存，查询期间失效的结果不写缓存 |
| `SampleImages.previews(String clazz, HuoyanSdkClient client)` | `List<String>` | 后台取得最新有效预览 URL，可能分页查询；返回不可修改列表 |
| `SampleImages.thumbnail(String clazz, HuoyanSdkClient client)` | `String` | 后台取得封面；没有有效缩略图时查询样本，仍无图返回 null |
| `SampleImages.invalidate()` / `SampleImages.version()` | `void` / `long` | 清空地址缓存并递增版本 / 读取当前版本；可将版本用于宿主图片缓存签名 |

学习、删除、恢复、切换门店后调用 `invalidate()`。缓存只管理地址，图片加载由宿主完成；`previews()`、`thumbnail()` 的分页异常仍由调用方处理。

### 10.6 反馈会话

以下类型位于 `feedback`，反馈条目和商品模型位于 `model`。

| 方法或构造器 | 返回 | 用法与约束 |
| --- | --- | --- |
| `new FeedbackSubmission(byte[] bytes, int feedbackType, List<FeedbackItem> items)` | `FeedbackSubmission` | 复制图片字节和条目列表，不发请求；feedbackType 只接受 0/1，图片提交时校验 |
| `FeedbackSubmission.fromFile(File photo, int feedbackType, List<FeedbackItem> items, FeedbackSubmission.PhotoRetention retention)` | `FeedbackSubmission` | 固定文件引用，首次 submit 才读取；retention 为 null 时使用 KEEP |
| `FeedbackSubmission.submit(HuoyanSdkClient client)` | `BaseResult` | 后台同步上传并提交反馈；复用 requestId 和已取得 URL，成功后再次调用不重发；直接调用时运行时异常可能向外抛出 |
| `FeedbackSubmission.getStage()` | `FeedbackSubmission.Stage` | UPLOAD 为尚未确认上传成功，SUBMIT 为已有 URL，COMPLETE 为收到成功反馈回执 |
| `FeedbackSubmission.getRequestId()` | `String` | 返回本实例固定请求标识 |
| `FeedbackSubmission.getUploadedUrl()` | `String` | 返回已确认的上传 URL，上传成功前为 null |
| `FeedBackUploadHelper.prepareDetectFeedback(List<FeedbackDetection> detections, int imageWidth, int imageHeight)` | `List<FeedbackItem>` | 本地组装不可修改的条目列表；传上传原图尺寸和坐标，字段规则见第 4.4 节 |
| `FeedBackUploadHelper.submit(HuoyanSdkClient client, FeedbackSubmission submission, Executor executor, Consumer<BaseResult> completion)` | `void` | 在宿主后台执行器提交，completion 在同一执行线程执行；将提交运行时异常转为 RESULT_UNKNOWN，不自动重试 |

`submit()`、`getStage()`、`getUploadedUrl()` 使用同一实例锁，提交期间读取阶段或 URL 可能阻塞，不在主线程轮询这些 getter。阶段只记录已确认的本地步骤，不能推断结果未知的请求是否已在后端执行。文件策略 `KEEP` / `DELETE_ON_SUCCESS` 及 8 MiB 上限见第 4.4 节；对应常量为 `FeedbackSubmission.MAX_PHOTO_BYTES`。

### 10.7 几何与状态工具

几何工具位于 `geometry`，状态工具位于 `status`。

| 方法 | 返回 | 用法与约束 |
| --- | --- | --- |
| `DetectBoxParser.parse(float[] points, DetectGeometry geometry)` | `DetectBoxParser.DetectRect` | BBOX 读取前 4 值 `[left,top,right,bottom]`；OBB 读取前 8 值四顶点并取外接矩形；null 或长度不足返回 null，不自动猜格式 |
| `DetectBoxParser.DetectRect.of(float x1, float y1, float x2, float y2)` | `DetectRect` | 用 min/max 归一化两对角点；结果通过公开只读字段 left/top/right/bottom 读取 |
| `FeedbackBounds.fromPoints(float[] points, int imageWidth, int imageHeight)` | `int[]` | 顶点取外接框、四舍五入，输出 `[x,y,width,height]`；正尺寸时裁剪到边界；null、少于两点、奇数长度或非有限坐标返回全零框 |
| `SdkStatus.shellStarted(StatusSnapshot status)` | `boolean` | 仅在有效回执 code=0 且 sdkState=READY 时为 true；不确认模型可用 |
| `SdkStatus.modelReady(StatusSnapshot status)` | `boolean` | 有效回执 code=0 且 modelReady=true 时为 true |
| `SdkStatus.issue(int initCode, StatusSnapshot status)` | `SdkIssue` | 结合初始化码和状态分类问题，优先处理未连接；模型就绪返回 NONE |
| `SdkError.fromCode(int code)` | `SdkError` | LOCAL_DISCONNECTED / NOT_EXECUTED→NOT_CONNECTED，RESULT_UNKNOWN→RESULT_UNKNOWN，-3→NOT_INITIALIZED，-42/-50→NOT_ACTIVATED，其余→OTHER；不用于判断成功 |
| `SdkError.defaultMessage()` / `SdkIssue.defaultMessage()` | `String` | 返回默认中文说明，保留原始 code/reason；宿主可映射本地化文案 |

几何工具只转换已明确的坐标；裁剪偏移、旋转和预览缩放由宿主处理。

### 10.8 SDK 下载与回调

以下类型位于 `deployment`。

| 方法或构造器 | 返回 | 用法与约束 |
| --- | --- | --- |
| `SdkShell.installedVersion(PackageManager packageManager)` | `PackageInfo` | 查询本机 SDK 安装版本；未安装返回 null |
| `new SdkApkDownloader(String url, File directory)` | `SdkApkDownloader` | 创建单次下载器；提供可写临时目录，下载地址按实际部署确认 |
| `SdkApkDownloader.start(SdkApkDownloader.Listener listener)` | `void` | 异步下载，每个实例仅允许一次 start |
| `SdkApkDownloader.cancel()` | `void` | 取消网络请求和读取，后台清理未交付文件；不负责删除已交付文件 |
| `SdkApkDownloader.Listener.onProgress(long downloaded, long total)` | `void` | 后台回调字节进度；total<0 表示未知，仅 total>0 时计算百分比 |
| `SdkApkDownloader.Listener.onComplete(File apk)` | `void` | 后台交付完整文件；宿主负责校验、安装及后续清理 |
| `SdkApkDownloader.Listener.onFailure(IOException error)` | `void` | 后台通知下载失败；主动取消不触发此回调 |

宿主取消下载时自行结束等待提示，并在回调更新 UI 前确认页面仍有效。`SdkShell.PACKAGE_NAME` 和 `DEFAULT_APK_URL` 提供 SDK 包名及默认发行地址；安装权限和 FileProvider 仍由宿主管理。
