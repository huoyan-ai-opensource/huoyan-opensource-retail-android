// AIDL 通讯入口（需求 C1/C4、决策 #4、Issue #36 全量定稿）：跨进程调用同一 SDK 门面。
// 方法名/参数顺序即对外契约，一次定稿（只增不改；Issue #69 破坏性例外：setAuthToken
// 真删——方法删除使后续方法 Binder 事务码整体平移，旧客户端须同步升级，与 #64/#66
// 破坏性变更同批定稿未发布）；错误模型统一 code（0=成功）+ reason。
// 整个 AIDL 通道仅 API 27+ 可用；API 21～26 不装配、不注册、不返回 Binder，使用 WS。
// 图像高频通道经 ImageRequestParcel.sharedMemory（SharedMemory，主路径）
// 或 bytes（≤1MB 小图）传入；bytes/shmKey 不绕过 AIDL 的版本下限。
// 跨进程发送端自行 unmap/close 原对象；接收端 Stub 在五个图像入口请求结束时
// 统一 close 接收句柄（含拒绝和异常），Reader 只借用并在复制后 unmap。
// 接收端请求关闭后不得复用；后台任务只持有独立字节副本。
// native 壳内等待预算：普通 30 秒；init/activate/batchLearn/备份恢复 120 秒；
// 反馈/上传各 60 秒；执行前排队最多 2 秒。40010=尚未执行，可退避重试；
// 40009=结果未知，已开始工作继续安全收尾，写操作不得自动重放。
// 绑定、商品和下载流程不在上述 native/反馈总预算的完整覆盖范围内。
package cc.huoyan.app.aidl;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.CloudStatusParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FailedImageParcel;
import cc.huoyan.app.aidl.FailedImagesResultParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsBatchItemParcel;
import cc.huoyan.app.aidl.GoodsBatchResultParcel;
import cc.huoyan.app.aidl.GoodsCountResultParcel;
import cc.huoyan.app.aidl.GoodsDownloadProgressParcel;
import cc.huoyan.app.aidl.GoodsDownloadResultParcel;
import cc.huoyan.app.aidl.GoodsListResultParcel;
import cc.huoyan.app.aidl.GoodsQueryResultParcel;
import cc.huoyan.app.aidl.GoodsParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageFlowProgressParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.ItemClazzImageParcel;
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.PhotoUploadResultParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;
import cc.huoyan.app.aidl.LicenseModelsParcel;

interface ISdkCommService {
    // ---- 版本/能力 ----
    // 接口版本（当前为 2；识别结果统一使用带版本头的完整字段格式）
    // 能力快照：apiVersion + shared_memory/detect/recognize/item_learn 等支持标志
    CapabilityParcel getCapabilities();

    // ---- 状态/授权 ----
    // 只读状态查询（13 字段，code=0 表示快照读取成功；不触发 init/JNI/网络）。
    // sdkState 含 STOPPED 等 17 项；STOPPED 仅在门面退役时发布，不等同前台 ACTION_STOP。
    // STOPPED 清活动身份、证书/模型详情与网络观测，modelReady=false、busy=false。
    StatusSnapshotParcel getStatus();
    // 证书视图全量（授权模型/时间戳族；licenseId/needRenew 已随 #133 删除）
    CertInfoParcel getCertInfo();
    // 模型下载进度（downloaded/total，total=0 表示未知）
    DownloadProgressParcel getDownloadProgress();
    // 激活码和所选模型编码均非空
    BaseResultParcel activate(String activationCode, String modelCode);
    // 续期
    // 后端基址配置（url 非空）
    BaseResultParcel setBaseUrl(String url);

    // ---- 识别/检测 ----
    // 纯检测画框：boxes[]（x/y/w/h/score，画布绝对坐标）
    DetectResultParcel detect(in ImageRequestParcel image);
    // 识别闭环：rotate ∈ {0,90,180,270}（帧旋转角）；回执条目包含
    // clazz/distance/box/square/rank/candidates 完整字段
    RecognizeResultParcel recognize(in ImageRequestParcel image, int rotate);

    // ---- 学习/遗忘 ----
    // 学习样本（clazz 为 1–50 位英文字母或数字字符串），成功回执携带新 uid
    LearnResultParcel itemLearn(in ImageRequestParcel image, String clazz);
    // 按分类整组遗忘
    BaseResultParcel itemForget(String clazz);
    // 按 UID 单条遗忘（uid 为 itemLearn 回执持有契约的持久化句柄）
    BaseResultParcel itemForgetByUid(long uid);

    // ---- 已学列表 ----
    // 已学类别列表（clazz[] 与 uidCount[] 下标一一对应；Issue #110 分页补全——
    // page/size 缺省 1/100（传 0 用缺省），size ∈ [1,100] 上限 100；回执含
    // total/pages/current/size 分页元数据 + 每类缩略图 URL 数组）
    ItemClazzListParcel getItemClazzList(int page, int size);
    // 类别下样本图片分页列表（Issue #110：uid/image_available/image_url/mime_type；
    // page/size 缺省 1/100，size ∈ [1,100]；图片缺失/损坏仍返回记录）
    ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size);
    // 查询上一次失败图片：code/reason 区分失败与成功空列表，items 返回全部失败记录。
    // 返回类型已从裸 List 改为结果信封，客户端须同步更新 AIDL/Parcelable。
    FailedImagesResultParcel getFailedImages();

    // ---- 业务上报（Issue #46；Issue #69 破坏性变更：setAuthToken 真删——原
    //      X-User-Token 令牌通道废弃，上报认证改 wujing 设备签名（X-Signature 头族，
    //      canonical + Keystore RSA——壳内自动，业务 App 无感）；与 #64/#66 同批
    //      定稿未发布，旧客户端须同步升级；Issue #113 两段式：删 shopId（归属
    //      取验签设备）、photoFileUrl 必填 http(s) 前缀、items[] 保留候选、最终选择和原图检测框） ----
    // 提交识别反馈：request_id/photo_file_url 必填；items 含 item_type、detect、result、检测框。
    // feedback_type 0正常/1修改，按 items 推导；学习小图由壳自动登记 item，不走反馈。
    BaseResultParcel submitFeedback(in FeedbackParcel feedback);

    // ---- 图片上传（Issue #113 两段式：uploadPhoto 独立字节上传能力，复用
    //      detect/recognize 同款 ImageRequestParcel 载体） ----
    // 上传 JPEG/PNG 图片字节并返回 object URL（供 submitFeedback photo_file_url）：
    //   format 必须 FORMAT_JPEG/FORMAT_PNG（否则 -1 参数错）；bytes 内联 ≤1MiB，
    //   大图用 SharedMemory（接收 ≤32MiB）；>8MiB 经大小限制器压缩到 ≤8MiB，
    //   未装配限制器或超出载体接收上限返回 -1。≤1MiB 原样上传；1-8MiB 图片
    //   压缩（最长边 1280/质量 80）后上传。
    //   WS 对应格式/参数错误为 -1，binary 帧接收超限为 40008。
    //   排队未执行返回 40010；网络结果不确定返回 40009，原因见 reason。
    //   上传凭证和 PUT 共用 60 秒预算，不自动重传；明确重试可能产生重复对象。
    // 每次独立上传、不加幂等（业务端持返回 URL 复用，不重复上传）。
    PhotoUploadResultParcel uploadPhoto(in ImageRequestParcel image);

    // ---- 服务初始化（Issue #112 破坏性变更：waitUntilReady 删除，携带客户端版本
    //      init(appVersion)——客户端显式触发初始化，同步返回动作结果；机器状态查
    //      getStatus，不得解析 reason 猜状态。旧客户端须同步升级） ----
    // init(appVersion) 触发完整初始化链（配置装配 → Keystore 身份 → native 运行时 →
    // register → 证书/模型准备）并同步返回动作结果 BaseResultParcel：
    //   0    = 初始化链路完成（含 NOT_ACTIVATED——是否已激活查 getStatus）
    //   -110 = 存在未完成的异步前置（KEY_ROTATION 待审，不等待人工审核）
    //   -111 = 初始化动作或内部轮换编排失败
    //   -1   = 内部参数/配置错误
    // 幂等且单飞：已完成重复调用快速返回，进行中复用同一次结果，失败终态后可重试。
    // appVersion 为宿主客户端 versionName；版本保存后随 SDK 后续心跳上报。
    BaseResultParcel init(String appVersion);

    // ---- 批量学习（Issue #68） ----
    // 壳层组合（C 库无批量原语，不触 native 面）：一次 detect → 对全部检测框
    // 逐框裁切原图（框坐标为原图像素坐标，边界 clamp 到图内）→ 逐框 itemLearn
    // (crop, clazz)。单 clazz 语义：所有框学到同一分类（同商品多框 = 多角度
    // 样本一次学完，多次往返收敛为一次调用）。
    // 回执 ItemLearnBatchParcel：
    //   code=0 + boxes[]：每框独立结果（x/y/w/h/score 框几何——detect 原始口径
    //     不因裁切改写；uid 该框学习句柄——业务端必须持久化持有（UID 持有契约
    //     同 itemLearn）；code=0 该框成功 / 非 0 该框失败 + reason 带原因——
    //     裁切无效/引擎错误等，单框失败不阻断其他框（部分成功语义））；
    //   code=0 + boxes 空：无检测框（正常空结果非异常，对齐 detect 空框）；
    //   code≠0 + reason：整体失败（detect 失败/参数非法——一次都没学），
    //     detect 的框一个未学，部分成功不会出现在整体层。
    // 全参载体（width/height 几何必需，同 detect/itemLearn）；组合在同一次 biz
    // 串行提交内完成（C 句柄串行约束），耗时约等于一次 detect + N 次 itemLearn。
    // 原有 detect/itemLearn 语义不变（本方法为其上的组合便捷口）。
    ItemLearnBatchParcel batchLearn(in ImageRequestParcel image, String clazz);

    // ---- 云端 wujing 接口（Issue #70，2026-08-28 06:20 定稿：本次仅壳内
    //      WujingApi 方法面（bind-shop/bind-account/商品目录/图片清单/指令
    //      拉取/回执，设备签名出网），AIDL/WS 对外暴露**暂缓**——方法尾插与
    //      命令面随后续 Issue 另行定稿；Issue #73 起开始暴露：bindDevice
    //      尾插如下，其余 5 接口（syncCatalog/getCatalogImages/fetchCommands/
    //      ackCommand）暴露另随后续 Issue） ----
    // 设备绑定统一入口（Issue #73，#72 小驼峰后 AIDL/WS 同名）：一次完成
    // 账号+店铺绑定——内部顺序编排（bind-account 成功才 bind-shop，复用 #70
    // WujingApi 方法面；后端幂等——account 幂等跳过 + shop 续绑，重复调用
    // 安全 = 同流程重试）。
    // 回执 BindingResultParcel 区分两组字段：
    //   request —— 客户端传入回显（accountId/shopId/shopName/shopNo 全字段，
    //              没传置空；错误回执同样保留可得回显供对账）；
    //   response —— 后端实际落库终态（account_id/account_name/shop_id/
    //              shop_name/shop_no，bind-shop 回执快照；仅 code=0 有效）。
    // 全绑语义：accountId（必填正整数——账号 ID）、shopId（必填——app_shop_id，
    //   绑已有门店）缺参前置拒绝（ERR_ARGS，不触网）；shopName 可选（Issue
    //   #107：缺省/空白经请求 DTO 构造期以 shopId 回填——request 回显回填值）；
    //   shopNo 可选（不传后端自动生成；传了且同 accountId 重复后端 409）。
    // 成功后绑定结果（response 组）+ 绑定时间戳持久化 SDK 壳本地（统一
    //   prefs binding.* 键组，设备绑定信息权威副本，每次成功覆盖保持最新）。
    // 失败（含 409 shop_no 已存在）→ code≠0 + reason 原样透传服务端说明，
    //   失败不落库；权限拒绝 code=40004。
    BindingResultParcel bindDevice(in BindDeviceParcel req);

    // ---- 下载图片并学习 + 商品 CRUD（Issue #74 尾插，2026-08-28 定稿：激活后
    //      【无拉商品逻辑——只有拉图片+学习】；失败客户端重试分流：激活失败 →
    //      activate，下载/学习失败 → downloadImagesAndLearn） ----
    // 同步等待下载学习流程终态；code=0 代表拿到执行结果，是否全成功看 phase/failCode。
    // 进行中重复触发拒绝；超时结果未知时后台继续收尾，禁止立即重复触发。
    // 分页拉取门店图片清单 → 下载 → 逐张 itemLearn 进 VDB（clazz = 商品 PLU 原值）
    // → 发布成功图片。终态回执完整保留下载、学习、跳过统计及 failedImages；
    // successCount/failCount/failStats 为学习统计，downloadSuccess/downloadFail/
    // downloadFailStats 为下载统计。客户端须与 SDK 同步更新 Parcelable 定义。
    ImageFlowProgressParcel downloadImagesAndLearn();
    // 下载阶段进度快照：无流程 active=false（IDLE）；进行中 active=true +
    // current/total（1/N，current=已处理张数，失败也计）；SUCCESS/FAILED 停留
    // 可查；FAILED 时 failCode≠0 + failReason（聚合原因）+ failStats（「原因=次数」
    // 逗号串）；successCount/failCount 为本次触发以来累计（重新触发重置）。
    ImageFlowProgressParcel getImageDownloadProgress();
    // 学习阶段进度快照：同上语义（本阶段投影；下载阶段计数经 getImageDownloadProgress）。
    ImageFlowProgressParcel getImageLearnProgress();
    // 商品列表（分页 + 同步语义；客户端主动管理）：syncTimestamp=0 全量（未删除），
    //   >0 增量（update_time ≥ 游标，含软删行——isDeleted=1 据此删本地）；回执
    //   syncTimestamp 为下次增量游标。current ≥ 1、size ∈ [1,1000]，非法拒绝
    //   （code=-1 ERR_ARGS 不触网）；设备未绑定门店后端 404 透传 reason。
    GoodsListResultParcel listGoods(long syncTimestamp, int current, int size);
    // 创建商品：name/plu 必填（空白拒绝不触网），其余可选（未传 = null/哨兵）；
    // PLU 同门店重复后端 400 透传 reason；回执 record 为创建后终态（含审计四字段）。
    GoodsResultParcel createGoods(in GoodsSaveParcel req);
    // 更新商品：goodsId 十进制数字串；仅传入字段生效（name/plu 传则不可空白）；
    // 他店/不存在/已软删 → 404（不泄露存在性）；回执 record 为更新后终态。
    GoodsResultParcel updateGoods(String goodsId, in GoodsSaveParcel req);
    // 软删商品（isDeleted=1，名下图片级联软删；增量同步据此删本地）。
    BaseResultParcel deleteGoods(String goodsId);

    // ---- 云端托管开关（Issue #81 尾插，AIDL/WS 同名小驼峰 setCloudStatus/
    //      getCloudStatus；既有方法顺序不变——只尾插不删除不重排） ----
    // 云端托管开关单向迁移：false→true 成功且只保存 true（统一 prefs 键
    // is_cloud_enabled；不触发 downloadGoods——开关与同步解耦）；true→false
    // 明确拒绝（code≠0 + reason「不可关闭」，状态保持 true）；false→false
    // 幂等成功。纯本地持久化——未绑定/未激活同样可开启（不触网/native）。
    // 回执 CloudStatusParcel：code=0 时 enabled 为终态。
    CloudStatusParcel setCloudStatus(boolean enabled);
    // 读取当前开关（缺省 false；重启读同一持久化状态）。
    CloudStatusParcel getCloudStatus();

    // ---- 商品分页同步 + 独立进度（Issue #82 尾插，AIDL/WS 同名小驼峰
    //      downloadGoods/getDownloadGoodsProgress；既有方法顺序不变——只尾插
    //      不删除不重排） ----
    // 商品分页同步（只同步云端商品到壳本地库——不下载图片、不学习，与
    // downloadImagesAndLearn 独立）：分页拉云端商品（复用 listGoods 同源
    // wujing 商品目录接口；current 从 1 递增至回执 pages 终止不越页），无本地
    // 游标全量、有游标增量；全部页面成功后单事务 upsert/软删清理
    // （is_deleted=1 删本地）+ 提交 sync_timestamp 游标。**同步执行**并返回
    // 最终统计（非异步受理假成功）；失败（分页/本地事务）code≠0 + reason，
    // 游标不推进——已处理统计经 getDownloadGoodsProgress 查询。
    // size：0 = 缺省 100；1..1000 显式；非法（负或 >1000）code=-1 前置拒绝
    // 不触网。进行中重复触发 code≠0 拒绝（不阻塞 Binder——立即返回）。
    // 回执 GoodsDownloadResultParcel：code=0 时 phase=SUCCESS + 统计
    // （added/updated/deleted 按 goods_id 终态计一次、total 为商品总数非页码、
    // failCount 为无效行计数——不阻断其余行、hasChanges、syncMode、
    // syncTimestamp 本次提交游标）。
    GoodsDownloadResultParcel downloadGoods(int size);
    // 商品同步进度快照（独立契约——不影响 getImageDownloadProgress/
    // getImageLearnProgress 图片进度）：active（无任务恒 false）、phase
    // （IDLE/FETCHING/APPLYING/SUCCESS/FAILED 终态停留可查）、current/total
    // （已处理商品行数/本次商品总数——**非页码**）、successCount/failCount、
    // addedCount/updatedCount/deletedCount、hasChanges、syncMode、
    // syncTimestamp（已提交游标——失败保留旧值）、failReason（仅 FAILED）。
    // 空商品同步 SUCCESS 0/0；失败保留计数与旧游标。
    GoodsDownloadProgressParcel getDownloadGoodsProgress();

    // ---- 指定商品重学（Issue #83 尾插，AIDL/WS 同名小驼峰
    //      downloadImagesAndLearnByGoodsId；既有方法顺序不变——只尾插不删除
    //      不重排） ----
    // 指定商品下载图片并重学：只请求指定 goods_id 的图片清单（不拉门店全量
    // 图片，不触商品同步/本地商品库/sync_timestamp——与 downloadGoods 解耦）。
    // 流程：逐 goods_id 拉图片清单 → 按记录 goods_id 校验 Room 本地商品
    //（不存在或已软删跳过——不下载不写 VDB，计 skippedCount/skipReasonStats）
    // → 每有效商品先清理 clazz=商品 PLU 原值 旧学习数据（VDB 整类遗忘 + 公共
    // 目录/索引联动；清理失败跳过该商品继续其余）→ 下载/解码/itemLearn/
    // 发布 huoyan/goods/<goodsId>/<uid>.jpg（同 downloadImagesAndLearn 契约）。
    // **同步执行**返回完整成功/失败/跳过统计（非触发即返回）；部分失败按
    // 商品/图片隔离不阻断其他有效商品；进行中（含全量入口在跑）重复触发
    // code≠0 拒绝。goodsIds：非空 long[]——空数组 code=-1 前置拒绝（校验在
    // 协调层单点，与 WS 同一防线）；元素为商品 goods_id（clazz 同源）。
    // 回执 ImageFlowProgressParcel（active=false 终态投影 + publicDir +
    // skippedCount/skipReasonStats 尾插字段；current/total=学习处理张数）。
    ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(in long[] goodsIds);

    // ---- 本地商品查询/筛选/批量管理（Issue #84 尾插，AIDL/WS 同名小驼峰；
    //      纯本地读写壳 Room 商品库——不触网/native/线程；既有方法顺序
    //      不变——只尾插不删除不重排） ----
    // 精确查询：主键查本地可见行；不存在（含已软删）code=404 + reason
    //（云端「不存在/已软删→404」同口径）；goodsId 空白 code=-1 前置拒绝。
    GoodsResultParcel getGoods(String goodsId);
    // 本地查询：keyword 命中 name/plu/pinyin/goods_id 任一（ASCII 大小写不
    // 敏感，与 name/plu 单字段条件 AND）；isDeleted -1=只查未删除（缺省同）、
    // 0/1 精确；current 0=缺省 1（<0 拒绝）、size 0=缺省 20（<0 或 >1000
    // 拒绝）；sortBy 缺省 goods_id、白名单 goods_id/name/plu/update_time/
    // sort；sortOrder 缺省 asc（asc/desc 忽略大小写）；排序稳定（goods_id
    // 升序决胜）。非法参数 code=-1 明确错误（纯本地无网络面）。回执
    // GoodsQueryResultParcel：total/pages/current/size/records（records 元素
    // 含 isDeleted——isDeleted=1 筛选时记录携带软删状态）。
    GoodsQueryResultParcel queryGoods(String keyword, String name, String plu,
            int isDeleted, int current, int size, String sortBy, String sortOrder);
    // 本地计数：过滤四参同 queryGoods（无分页/排序）；回执 count。
    GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu,
            int isDeleted);
    // 本地批量字段覆盖：changes 复用 GoodsSaveParcel（字段集恰为 #78 白名单
    // 14 项——goods_id/is_deleted/source/sync_status/create_time/update_time
    // 结构上无位可传；哨兵 -1/has=false = 不更新该字段，name/plu 传入即不可
    // 空白）；goodsIds 空数组 code=-1 前置拒绝；逐条 items[]（goods_id/code/
    // reason）支持部分成功（不存在/已软删该条失败不阻断）；整批单 Room
    // 事务，存储异常 code≠0 整体回滚。
    GoodsBatchResultParcel batchUpdateGoods(in String[] goodsIds, in GoodsSaveParcel changes);
    // 本地批量软删：置 is_deleted=1 + sync_status=DELETED（行保留可查——
    // queryGoods isDeleted=1 过滤面；#85 上行对账依据）；goodsIds 空数组
    // code=-1 前置拒绝；逐条部分成功；整批单 Room 事务。
    GoodsBatchResultParcel batchDeleteGoods(in String[] goodsIds);

    // ---- 识别数据备份/查询/恢复（Issue #106 尾插，AIDL/WS 同名小驼峰
    //      backupRecognitionData/getRecognitionDataInfo/restoreRecognitionData；
    //      既有方法顺序不变——只尾插不删除不重排。对外契约一律「识别数据」，
    //      不出现内部存储术语） ----
    // 备份识别数据（无参）：校验当前库完整性（损坏中止——不把坏数据备份）→
    // 整库备份到壳固定备份文件 → 校验备份文件 → 备份时间点落壳持久化（时间
    // 点经 getRecognitionDataInfo 查询）。与学习/遗忘/商品 CRUD 共享互斥
    // （冲突 BUSY code≠0 + reason，不排队）；需引擎就绪（未就绪明确报错）。
    BaseResultParcel backupRecognitionData();
    // 查询备份信息（无参、只读不互斥、空态不报错）：回执
    // RecognitionDataInfoParcel 四字段——backupTimeEpochMs（0 = 从未备份）、
    // backupTimeText（yyyy-MM-dd HH:mm:ss，未备份空串）、schemaVersion/
    // fileFormatVersion（当前库两版本号，未知/未备份 0）。
    RecognitionDataInfoParcel getRecognitionDataInfo();
    // 恢复识别数据（无参）：壳固定备份文件缺席前置拒绝 → 先备份当前库为
    // 回滚点 → 整库恢复 → 校验主库 → 壳内自动重建引擎（返回即生效——已打开
    // 引擎不替换句柄，销毁重建）。恢复执行失败或恢复后校验损坏：用回滚点
    // 覆盖回滚 → 重建引擎 → code≠0 + reason（含回滚失败明细）；数据已恢复
    // 但引擎重建失败不回滚（数据有效，识别惰性重建自愈）。互斥/引擎就绪同
    // backupRecognitionData。
    BaseResultParcel restoreRecognitionData();
    // 按输入激活码查询支持模型，适用于正常激活。
    LicenseModelsParcel queryLicenseModels(String activationCode);
    // 无参查询当前设备绑定授权的支持模型，不依赖本地激活码。
    LicenseModelsParcel queryDeviceModels();
}
