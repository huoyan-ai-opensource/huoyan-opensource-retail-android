# AGENTS.md

本文件为 AI 编码代理（Claude Code / Codex / Hermes）在 `huoyan-opensource-retail-android` 仓库工作时的权威指南。**动手前先读本文件。**

## 重构与兼容约定

- 除非用户明确要求兼容旧逻辑，否则重构、替换或升级功能时只保留新逻辑，直接删除被替代的旧实现。
- 不得自行增加或保留旧配置迁移、旧接口适配、旧格式识别、兼容分支、回退到旧实现或新旧双轨运行逻辑。
- 同步清理仅为旧逻辑存在的配置项、参数、依赖、测试和文档，不留下无调用代码或冗余兼容层。
- 正常的新逻辑校验、错误处理和当前功能所需的设备重连不属于旧逻辑兼容；按实际职责保留，不借兼容之名扩大实现。
- 此规则适用于当前任务涉及的改动范围，不擅自扩展为清理无关模块；用户明确指定的兼容要求仅在指定范围内生效。

## 设计稿维护

- 唯一设计源文件为 `design/huoyan-retail.pen`，所有功能模块及横竖屏设计统一在此文件维护，不再创建或维护独立分稿。
- 模块排列、页面与节点索引见 `design/DESIGN-INDEX.md`；调整模块或页面时同步更新索引。
- `.pen` 内容通过 Pencil MCP 读取和修改，完成后保存源文件。

## 项目定位

火眼开源零售安卓客户端（通用版本）：面向 **生鲜 / 零食 / 烘焙 / 小碗菜** 四类零售场景的 AI 识别 + 收银客户端。

**Package:** `cc.huoyan.retail`
**Min SDK:** 24 | **Target SDK:** 27 | **Compile SDK:** 33
**Java:** 11 | **NDK:** 25.2.9519653

## 构建（重要：必须 JBR-17）

Gradle 7.5 **不支持 Java 21**（class major 65 报错）。构建前必须：

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :app:assembleDebug        # 构建 Debug APK
bash gradlew :app:testDebugUnitTest    # 单元测试
```

gradlew 可能无执行位，用 `bash gradlew` 调用。

## 架构（页面 MVP 边界拆分）

页面采用轻量 MVP，底层保留既有单例 Manager；源码按业务职责组织：

```
app/src/main/java/cc/huoyan/retail/
├── home/             # 首页导航
├── checkout/         # 收银 UI；Presenter 交互协调，domain/ 状态与计价，integration/ 商品加载与输出
├── goods/            # 商品管理、商品仓库、搜索与筛选
├── learning/         # ProductLearningActivity / StudyActivity、样本图片管理
├── settings/         # 设置页面、授权/连接/设备设置组件
├── recognition/      # 识别循环、结果判定与绘图；框和响应模型引用 JAR
├── pricing/          # 金额分(long)+BigDecimal、计价策略、混合购物车
├── feedback/         # 本地商品/收银选择转 JAR 反馈参数；组装与提交由 JAR 负责
├── device/           # camera / scale / print、语音及设备信息
├── data/             # SDK 商品内存快照 GoodsEntity、SharedPreferences
├── sdk/              # 宿主配置/线程装配、安装 UI、图片展示
├── config/           # Scene / SceneDefaults / ModuleConfig
├── diagnostics/      # 日志记录、日志页面与展示模型
├── common/           # 跨业务基础工具，ui/ 为通用界面组件
├── receiver/         # 开机与 debug 注入广播入口
└── RetailApplication.java / RetailConstants.java / ThreadAffinity.java（JNI）
```

详细归属见 [docs/code-structure.md](docs/code-structure.md)。JAR 目录及依赖规则见 [huoyan-sdk-client/ARCHITECTURE.md](huoyan-sdk-client/ARCHITECTURE.md)。单元测试及设备测试同步按业务包组织。商品数据只通过 SDK 壳接口获取和修改，宿主不保存商品数据库、目录缓存或商品 ID 映射。

### 收银边界与计价分发
- 识别结果/手动点选商品 → `PricingMode.fromUnit(goods.getUnit())` → COUNT 走计件键盘确认（RecognizeCountKeyboardDialog），WEIGHT 走称重确认链路
- 入车前统一 `PricingGate.check`（weighing 关 + WEIGHT 商品 → 拦截提示，不入车）
- `CheckoutRows` 将页面清单复制为 `SaleLine`；`CheckoutSession` 独占会话、重量、识别锁定与打印状态，发布不可变 `CheckoutState`
- `SalePricing` 校验称重同价与模块开关，统一经 `CartCalculator.computeTotal` 结算；业务重量为 BigDecimal，单价/总额为 long 分
- `CheckoutPresenter` 是收银页 MVP 的 Presenter：接收页面操作，协调清单、改品、数量/重量输入和打印，不持有 Activity/控件；`MainActivity` 实现 `CheckoutPresenter.View`，负责控件、弹窗、设备及生命周期
- `CheckoutGoodsLoader` 负责异步 SDK 商品查询、结果合并与过期丢弃；`CheckoutOutput` 固定反馈快照并执行打印/反馈，页面退出不取消已提交输出
- `checkout.domain` 只依赖 Java 与 `pricing`，禁止引入 Android、数据库实体、Bitmap 或 SDK 单例；Presenter 负责交互与状态协调，Activity 负责 View 实现、弹窗、渲染及设备生命周期

### 页面 MVP
- 首页、商品管理、学习图片列表、日志及设置异步流程已拆出 Presenter；完整职责表见 `docs/code-structure.md`。
- Presenter 与 View 同属业务包；视图销毁时断开引用，后台执行器不能通过方法引用保留 Activity/Fragment。已提交写操作按既有业务完成，查询回调按代次丢弃。
- `SettingsPageActivity` 保留导航容器，简单控件绑定不建转发层；`StudyActivity` 保留相机预览及控件，学习事务/反馈由 `StudyPresenter` 协调，Bitmap/SDK/文件由 `StudyLearningSource` 持有；图片批量学习继续用现有 ViewModel。

### 关键约定
- 金额一律**分(long) + BigDecimal**，禁止 double；重量 BigDecimal(kg)
- 中文注释：新拆出的类、接口、每个方法（含构造/私有方法）和每个字段必须注明职责及必要的线程/生命周期约束；新增 public 方法必须有中文 Javadoc
- 日志打点（仅 BuildConfig.DEBUG）：`[Toast] <文案>`（ToastEx 出口）、`[Biz] key=value`（回显值）——平台 wait_log/assert_toast/assert_value 依赖
- 跨应用 AIDL 服务标识 `com.huoyan.DataService`（SDK 生态）**禁止改动**

## 测试

- **单元测试**：`app/src/test/java`（同包命名 XxxTest，纯 JUnit 无 Robolectric——Android 类在 mockable jar 不赋值字段，纯逻辑避免依赖 android 类）

## 签名

正式签名私钥和密码不入库（.gitignore 排除 `*.jks`、`*.keystore`）。唯一例外是用户明确要求随库公开的 `signing/xinhai-debug-demo.jks`：该签名仅用于非商业的开源版本实验及测试 Release 打包，密码公开，不得用于商业发布。debug 使用本机自动生成的调试签名；本地和 CNB 的 release 默认使用上述固定实验签名，说明见 `signing/README.md`。正式 release 在未入库的 `local.properties` 中完整配置 `signing.storeFile`、`signing.storePassword`、`signing.keyAlias`、`signing.keyPassword`；不在构建脚本中保留正式签名的私钥路径或密码默认值。

## UI 反推换算（huoyan-snacks 继承，C50D sw720dp）

### 弹窗与操作反馈规范

- 业务确认框必须复用 `common.ui.AlertDialog`（通常通过 `DialogUtil.showAlertDialog`），禁止使用系统 / AppCompat 默认标题、正文和按钮样式；自定义内容 Dialog 的宿主不属于默认确认框。
- 等待操作使用项目 `LoadingDialog`，由所属页面或子 FragmentManager 管理；成功、失败或异常结束后关闭，重建时依据在途状态恢复，避免重复提交与遗留遮罩。
- 操作结果统一通过 `ToastEx` 一次性提示，成功 / 失败图标按真实回执判断，不能匹配文案推断。
- 全局规则：所有页面、卡片、弹窗等模块，默认不得直接在触发操作的模块内回显状态，包括请求中、处理进度、成功、失败及操作结果；此规则不限于云端弹窗或模块底部。需要等待提示时使用项目 LoadingDialog，结果通过 ToastEx 一次性提示。
- 仅当需求明确指定展示某项状态时，才允许在模块内显示该状态，例如云端服务的开启 / 关闭状态。例外仅适用于指定状态，不能据此追加其他请求进度或结果。
- 云端服务中，门店登录与自动查询静默进行；开启服务、同步商品和同步识别库使用 LoadingDialog，结果使用 ToastEx；保留明确要求展示的云端服务状态。同步识别库的确认必须使用项目 AlertDialog。

- dimens 引用：`px = N × 2 × density`（sw 翻倍映射，C50D density=1.5 → N×3）
- 硬编码 dp：`px = N × density`（×1.5）
- 色板：#F2F4F8（背景）/ #293A51（主文字）/ #3F3EED（强调蓝）/ #FA6400（警示橙）

## 重要文件

- `app/build.gradle`：APK 命名 `huoyan-retail-{version}-{code}-{ts}-{type}.apk`
