# 代码目录

工程包含两个 Gradle 模块：`app` 实现零售业务和设备交互，`huoyan-sdk-client` 提供独立 SDK 客户端及 AIDL/WS 通信。应用源码根目录为 `app/src/main/java/cc/huoyan/retail`。

| 包 | 职责与主要入口 |
| --- | --- |
| `home` | 首页导航、云服务提示；`HomeActivity` |
| `checkout` | 收银页面、MVP Presenter、清单改品、数量/重量输入；`MainActivity`、`CheckoutPresenter`、`CheckoutRows` |
| `checkout.domain` | 收银状态、称重校验、混合计价、不可变小票与打印任务 |
| `checkout.integration` | SDK 商品异步加载、打印与反馈接入 |
| `goods` | 商品管理、仓库、搜索与筛选；`GoodsManagementActivity`、`GoodsRepository` |
| `learning` | 拍照学习、样本列表、图片保存；`ProductLearningActivity`、`StudyActivity` |
| `settings` | 授权、SDK 连接、识别/设备设置及其弹窗；`SettingsPageActivity` |
| `recognition` | 识别循环、稳定结果判定及绘图；响应模型和纯几何使用 JAR |
| `pricing` | 计件/称重策略、计价校验、购物车金额计算 |
| `feedback` | `FeedbackInputs`：SDK 商品内存快照、收银选择和相机坐标的 JAR 入参转换 |
| `device` | 相机、秤、打印分别位于 `camera`、`scale`、`print`；设备信息和语音位于本包 |
| `data` | SDK 商品内存快照 `GoodsEntity`、SharedPreferences；不持久化商品数据 |
| `sdk` | SDK 配置、线程、安装 UI 和 Glide 图片展示；协议与通用实现归 JAR |
| `config` | 零售场景和模块开关 |
| `diagnostics` | 日志页面、日志模型、写入及展示 |
| `common` | 跨业务基础工具；`ui` 存放通用界面基类、控件和弹窗 |
| `receiver` | 开机启动和调试广播入口 |

业务专用的适配器、弹窗和模型放在对应业务包，共用界面组件放入 `common.ui`。辅助类保持包内可见，跨包只开放必要入口。`FileUtil` 等历史工具尚有混合职责。

单元测试和设备测试分别位于 `app/src/test/java`、`app/src/androidTest/java`，按被测业务包组织。跨页面测试以主要断言对象归属，例如首页到学习页的分页测试位于 `learning`。

## 收银内部边界

```text
MainActivity（实现 CheckoutPresenter.View）
  ↕ 操作事件 / View 回调
CheckoutPresenter
  ├─ CheckoutViewState → 摘要、清单选择、按钮状态
  ├─ CheckoutRows → SaleLine → CheckoutSession → SalePricing / pricing
  ├─ GoodsLoader → CheckoutGoodsLoader → SdkGoodsSource → HuoyanSdkClient
  └─ Output → 截图 / CheckoutOutput → 打印机 / SDK 反馈
```

- `CheckoutPresenter` 接收点击、弹窗确认、改品和打印操作，通过 View 更新页面，不调用 Android API、不持有 Activity 或控件。
- `CheckoutViewState` 决定摘要、空态、改品按钮和价格冲突提示，View 负责映射文案、颜色与控件。集合只读，商品行沿用现有适配器模型。
- `CheckoutSession` 独占会话状态，秤读数、稳定计数、识别锁定、手改重量、编辑/打印资格及打印完成均通过事件方法更新。主线程写入，识别线程只读不可变快照。
- `SalePricing` 校验整秤称重商品同价、排除未匹配和被模块开关拦截的行，再交给 `CartCalculator`。整秤重量只计价一次，计件行按各自数量计价。重量使用 BigDecimal，金额使用 long 分。
- 自动去皮仅用于开启称重模块后的锁定识别清单：同价称重商品按单件皮重（克）乘数量累加，不同价时不应用，删除或改品后重新计算。`CheckoutSession` 管理去皮互斥、失败抑制和本页自动皮重清理；识别变化比较总重，计价使用设备净重。`AclasScaleManager` 等待目标皮重回读及之后的稳定净重才确认命令，等待期间禁止打印，使用页面 LoadingDialog 和真实结果 Toast。
- `SaleLine` 和小票不含数据库实体、识别框、Bitmap。`CheckoutRows` 负责把页面对象复制为业务输入；`checkout.domain` 不反向依赖页面、接入层或 Android。
- `CheckoutGoodsLoader` 批量查询商品，保留未匹配行，通过 `GoodsVo` 交付给页面；缩略图由 View 在主线程创建。`LatestRefresh` 合并待执行查询，在重开会话、人工修改或页面退出后丢弃旧结果。
- `CheckoutOutput` 在提交时固定反馈内容，接管截图并负责回收。打印数据发送成功后的反馈失败不改判为打印失败；`CheckoutSession` 按打印任务和会话代次处理完成回调，旧回调不会结束新单。

`MainActivity` 负责相机帧、SDK 识别调度、控件和生命周期。计价规则和可变会话状态放在业务层。

识别页的 `ScaleOperationsDialog` 复用页面秤连接，`SdkStatusDialog` 只读查询 SDK 状态；等待操作由子 `LoadingDialog` 管理。在途模型不持有页面。`RecognitionWatermarkView` 在列表背后绘制固定四角，空态动图由 Activity 的 Glide 生命周期管理。`settings/ExitPasswordDialog` 与 `ExitPasswordStore` 负责本机退出验证和维护页密码配置，系统返回与版本号三击使用同一验证入口。

Presenter 由 Activity 的 `onCreate` 注入 View、商品加载及输出端口。`onPause` 作废查询但保留清单；`finish`/`onDestroy` 解除 View 和截图端口引用。已提交打印继续完成，回调可更新会话但不再访问退出的页面。商品加载使用应用主线程执行器，避免异步回调通过执行器间接保留 Activity。

## 其他页面的 MVP 边界

View、Presenter 和数据适配器与业务同包，后两者保持包内可见。不设跨业务的 presenter/model 目录或 BasePresenter。

| View | Presenter | 职责 |
| --- | --- | --- |
| `HomeActivity` | `HomePresenter` | 前台状态轮询、五秒间隔、暂停后丢弃旧结果 |
| `GoodsManagementActivity` | `GoodsManagementPresenter` | 搜索、排序、分页、学习计数、写入互斥 |
| `AddGoodsDialog` | `GoodsFormPresenter` | 表单校验、后台重复 PLU 查询、编辑快照与单位保留 |
| `RecognizeModifyGoodsDialog` | `ModifyGoodsPresenter` | 相似候选支持数、排序合并、后台搜索与选择状态 |
| `GoodsSearchDialog` | `GoodsSearchPresenter` | 索引加载、百毫秒防抖、搜索结果与错误交付 |
| `StudyActivity` | `StudyPresenter` | 拍照互斥、学习商品快照、保存/遗忘及待反馈批次收尾 |
| `ProductLearningActivity` | `ProductLearningPresenter` | 商品查询、图片分页、重试、拍摄/删除后的历史结果合并 |
| `LogActivity`（已停用） | `LogPresenter` | 文件查看入口及 Activity 注册已移除，底层日志记录保留 |
| `SdkConnectionFragment` | `SdkConnectionPresenter` | 通道保存和连接；复用 WS 查询协调器 |
| `WsConnectionDialog`、SDK 设置页 | `WsConnectionPresenter` | 统一地址校验、探测/发现、取消；地址弹窗只保存端点 |
| `CameraFragment` | `CameraSettingsPresenter` | 设备/分辨率切换、首帧回显、恢复时重启节流 |
| `AclasScaleSettingsFragment` | `ScaleSettingsPresenter` | 串口选择、测试会话、置零/去皮回执隔离 |
| `PrintSettingsFragment` | `PrintSettingsPresenter` | 设备发现、连接互斥、明确点击后的测试打印 |
| `GoodsFragment` | `LibraryPresenter` | 备份/恢复互斥、元信息与分类数量查询、跨视图重建保留写操作结果 |
| `PanelFragment` | `PanelPresenter` | 云开关、授权与版本快照读取 |
| `ActivationFlowFragment` | `ModelActivationPresenter`、`ModelActivationViewModel` | 首页/授权激活与识别设置切换共用会话；动态列表、选中后提交、取消返回、旋转不重发；子 FragmentManager 管理输入、扫码、列表与 LoadingDialog |
| `ScanWechatQRCodeDialog` | `QrScanPresenter` | 帧事件合并、250ms 节流、后台解码、单次交付及关闭后的回执隔离 |
| `SystemFragment` | `ShopBindingPresenter`、`RecognitionSettingsPresenter` | 门店绑定；SDK 激活模型只读查询和过期回执丢弃 |

数据适配器（`HomeStatusSource`、`GoodsManagementData`、`LearningPageSource`、`LogFileSource`、设置包中的 Source/Device）调用 SDK 和设备实现。Presenter 使用独立 Handler 向主线程投递，不捕获 Activity/Fragment；销毁时断开 View，丢弃旧查询结果。商品写入、库备份恢复和已提交绑定继续完成，页面读取及进度展示可以取消。

识别设置使用 `queryDeviceModels()` 返回的 current 项显示模型名称，用同一客户端 `getStatus()` 显示版本；缺失时显示占位。切换先选模型，有证书激活序列号时直接激活，缺码时弹窗补输并携带所选模型代码重新激活，提交前校验查询通道仍为当前通道。置信度与交并比当前仅作为本地 UI 配置保存，默认均为 0.45；现有 SDK 未提供对应读写接口，尚未下发引擎。

`SettingsPageActivity` 负责导航，偏好控件绑定、文案与弹窗留在 View；图片批量学习沿用 `ImageLearningViewModel` 的生命周期模型。

`StudyActivity` 负责相机预览、检测框、控件和展示钩子；`StudyPresenter` 管理学习目标、即时保存、整组/单样本遗忘和待反馈批次。`StudyLearningSource` 只持应用 Context，处理 SDK、学习帧 Bitmap、图片文件与反馈上传。`ProductLearningPresenter` 管理图片分页；商品查询回调 `selected` 与学习目标回调 `showLearningTarget` 分开，避免继承回调冲突。

检测和学习共用串行队列，SDK 请求不重叠。`onPause` 只取消周期预览，已提交学习继续完成；销毁时解绑 View，在途回执仍可上传或删除反馈文件，队列排空后释放帧转换资源。整组遗忘使用提交时商品快照清理 SDK 和本地数据，旧回执不能更新新选商品。商品表单和改品候选在后台读取，查询按代次丢弃旧结果。

扫码相机回调只向 `QrScanPresenter` 提供尺寸，主线程从预览控件截取图片，`QrBitmapFrame` 在后台执行 ZXing 解码并回收截图。窗口关闭即解绑扫码 View；后台队列有序关闭，已接管的图片仍完成回收。扫码类型放在 Fragment arguments 中，支持无参重建。

`ActivationCode` 处理十六位手输校验、分段和提交格式，扫码允许其他长度的 serial。`PanelPresenter` 负责去重、解析和配置保存；激活提交后，后续点击不能覆盖正在使用的码。手输清除旧成功标记，扫码保留该标记；输入框格式化和进度文案由 View 处理。

新拆出的类、接口、方法（含构造和私有方法）及字段均需中文注释，说明状态归属、回调线程、取消或持久化语义。

## Android 和数据兼容

- `applicationId` 与应用命名空间为 `cc.huoyan.retail`，FileProvider authority 为 `cc.huoyan.retail.fileprovider`，JNI 导出函数与新类名一致。SDK 的 AIDL 协议包、服务标识及外部 debug 注入 action 保持不变。
- 首页与收银入口分别为 `.home.HomeActivity`、`.checkout.MainActivity`；首页直接注册桌面入口，已移除旧 `.activity.*` 别名。新包名作为独立应用安装，不会自动继承旧包的应用数据。
- 商品查询、增删改和数量统计统一经过 `goods/SdkGoodsSource` 调用 SDK 壳接口；SDK 是唯一商品来源。页面仅持有内存快照与搜索索引，宿主不创建商品 SQLite 数据库，不保存商品目录或 PLU → SDK ID 映射；云端同步只调用 SDK `downloadGoods`。
- 资源文件按 Android 的 `layout-port`、`layout-land`、`values-sw*` 规则组织。

## 验证

使用项目要求的 JBR-17：

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`PackageStructureDeviceTest` 检查新包名、首页与收银直接入口，以及横竖屏/不同宽度资源中的自定义控件类引用。

收银回归包括 `CheckoutPresenterTest` 的交互、弹窗过期和退出后回调，`CheckoutSessionTest` 的状态转换与打印隔离、`LatestRefreshTest` 的过期查询拦截，以及 C50D 的 `LandscapeCheckoutDeviceTest`（真实秤混合计价/打印、改数量结算和重新开单）。

通用 USB ESC/POS 打印实现、兼容范围和测试入口见 [printer-escpos.md](printer-escpos.md)。

`LandscapeCheckoutDeviceTest` 默认实际出纸；连续复跑且打印机等待取票时，可传 instrumentation 参数 `-e skipReceiptPrinting true`，仅跳过出纸，仍验证真实秤计价及结算状态。测试结束恢复原模块开关。

设置设备测试也支持 `-e skipReceiptPrinting true`，保留设备枚举和连接验证，仅跳过测试票出纸。

## SDK JAR 边界

JAR 的分包与依赖规则见 [模块目录规则](../huoyan-sdk-client/ARCHITECTURE.md)。`app/sdk` 负责 `SdkChannelManager` 的宿主配置和线程及 `SdkGoodsImages` 的图片加载。`common/install/ApkInstallController` 统一处理 SDK 与客户端的下载、未知来源授权和系统安装器交互；客户端固定发行地址位于 `RetailConstants.CLIENT_LATEST_APK_URL`。

SDK 返回 `RecognizeResult/RecognitionItem/RecognitionCandidate/DetectBox`；收银 `CheckoutRecognition/RecognitionSelection` 只组合原始回执和人工选择。`goods/SaleModeMapper` 和 `SdkClassMapping` 属于宿主商品业务。学习条目引用 JAR 框并保留本地图片编号与拍摄时间。旧 `iou/conf` 设置没有现行调用链，已移除无效控件和本地配置模型。
