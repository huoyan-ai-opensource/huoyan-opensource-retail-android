package cc.huoyan.sdk.client.internal;

import cc.huoyan.app.aidl.PhotoUploadResultParcel;
import java.util.List;
import cc.huoyan.app.aidl.*;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.CloudStatusParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.ImageFlowProgressParcel;
import cc.huoyan.app.aidl.FailedImageParcel;
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

/**
 * SDK 2.0 通道抽象：上层业务面向本接口编程，不感知底层是
 * AIDL（bindService 跨进程，SDK 契约 {@code cc.huoyan.app.aidl.ISdkCommService}）
 * 还是 WebSocket v2。
 *
 * <p>方法面按 SDK 契约「业务客户端接入与重连」文档第 2/5 节与现有
 * {@code SDKManager} 语义收敛，回执类型直接复用 SDK Parcelable（错误经回执
 * {@code code}（0=成功）+ {@code reason} 返回，不抛异常）；连接生命周期参照
 * 文档模板 {@code SdkCommBinding}（bind/断线重连/close）。

 */
public interface SdkChannelBridge {

    /** 本地哨兵：通道未连接/连接断开（非服务端回执码，不与 SDK 侧 0/负值/40004 冲突）。 */
    int LOCAL_DISCONNECTED = Integer.MIN_VALUE;

    /**
     * 发起连接（幂等）：AIDL 通道 bindService 拉起 SDK 并进入断线退避重连，
     * WS 通道建立 WebSocket v2 连接。
     */
    void connect();

    /** 断开并停止重连（close/unbind 后不再有任何重连动作）。 */
    void disconnect();

    /**
     * 通道当前是否已连接。
     *
     * @return true=已连接（连接成功 ≠ 初始化完成，调用前先 {@link #init}）
     */
    boolean isConnected();

    /**
     * 在后台线程触发/复用初始化，再通过 getStatus().getSdkState() 判断机器状态。
     * @return 永非 null：0 链路完成（可能未激活），-110 KEY_ROTATION 待审，
     * -111 初始化失败，-1 配置错误；本地断连返回 LOCAL_DISCONNECTED。
     */
    BaseResultParcel init(String appVersion);

    /**
     * 能力快照：apiVersion + shared_memory/detect/recognize/item_learn 等支持标志。
     *
     * @return 能力快照；未连接或调用失败返回 null
     */
    CapabilityParcel getCapabilities();

    /**
     * 只读状态查询（机器状态统一读取 sdkState，code=0 表示查询成功）。
     *
     * @return 状态快照；未连接或调用失败返回 null
     */
    StatusSnapshotParcel getStatus();

    /**
     * 证书视图全量（授权模型 + 时间戳族）。
     *
     * @return 证书视图；未连接、未激活或调用失败返回 null
     */
    CertInfoParcel getCertInfo();

    /**
     * 模型下载进度（downloaded/total，total=0 表示未知）。
     *
     * @return 下载进度；未连接或调用失败返回 null
     */
    DownloadProgressParcel getDownloadProgress();

    /** 按激活码查询支持模型，适用于正常激活。 */
    cc.huoyan.app.aidl.LicenseModelsParcel queryLicenseModels(String activationCode);
    /** 无参查询当前设备绑定授权的支持模型。 */
    cc.huoyan.app.aidl.LicenseModelsParcel queryDeviceModels();

    /** 提交激活码和模型代码；保留断连、未知结果与真实回执，不自动重放。 */
    BaseResultParcel activate(String activationCode, String modelCode);

    /**
     * 按本通道构造 RGB888 图像载体：AIDL 使用共享内存或小图内联，WS 直接持有字节。
     * 返回对象必须交给同一个桥的图像方法，不可跨通道复用；调用结束前不得修改原始字节。
     *
     * @param rgbFrame 紧凑 RGB888，长度必须等于 width*height*3
     * @param width 图像宽度，必须大于 0
     * @param height 图像高度，必须大于 0
     * @return 图像载体；本通道无法承载时返回 null
     */
    ImageRequestParcel createImageRequest(byte[] rgbFrame, int width, int height);

    /**
     * 按通道选择识别/检测图像格式：AIDL 提取 RGB888，WS 覆写为 JPEG90。
     * 不改变裁剪位图的尺寸和方向，不接管位图所有权；载体交给同一桥执行调用。
     *
     * @param bitmap 已裁剪并转正的位图
     * @return 本通道图像载体；无法承载时返回 null
     */
    default ImageRequestParcel createImageRequest(android.graphics.Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("图像 Bitmap 不能为空或已回收");
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return createImageRequest(RgbImages.rgb888FromArgbPixels(pixels, width, height),
                width, height);
    }

    /** 将裁剪位图编码为 JPEG 90；尺寸和方向不变，不接管位图所有权。 */
    default ImageRequestParcel createJpegImageRequest(android.graphics.Bitmap bitmap) {
        return JpegImages.encode(bitmap);
    }

    /**
     * 纯检测画框（识别域画布坐标 boxes + 置信度）。
     *
     * @param image 由当前桥 createImageRequest 构造的图像载体
     * @return 检测回执；未连接或入参非法（code=-1）见回执；通道未连接返回 null
     */
    DetectResultParcel detect(ImageRequestParcel image);

    /**
     * 识别闭环（SDK 门面合并统一入口——原 recognizeV2 并入本方法）：
     * 回执条目包含 clazz/distance/box/square/rank/candidates 完整字段。
     *
     * @param image  图像载体
     * @param rotate 帧旋转角 ∈ {0, 90, 180, 270}
     * @return 识别回执；未连接返回 null
     */
    RecognizeResultParcel recognize(ImageRequestParcel image, int rotate);

    /**
     * 学习样本（clazz 分类编码 int64），成功回执携带新 uid。
     *
     * @param image  图像载体
     * @param clazz 分类编码
     * @return 学习回执；未连接返回 null
     */
    LearnResultParcel itemLearn(ImageRequestParcel image, String clazz);

    /**
     * 批量学习（SDK 组合口，尾插）：大图一次传入，SDK 内 detect 一次 +
     * 全部检测框逐框裁切学习（单 clazz——所有框学到同一分类，同商品多框多角度
     * 样本一次学完）。回执每框独立结果：code=0 + boxes[] 携带每框几何与该框
     * uid（code≠0 该框失败不阻断其他框，部分成功语义）；boxes 空 = 无检测框
     * 的正常空结果；整体 code≠0 = detect 失败/参数非法（一次都没学）。
     *
     * @param image 大图载体（width/height 几何必需，同 detect/itemLearn）
     * @param clazz 分类编码（全部框学到同一分类）
     * @return 批量学习回执；未连接返回 null
     */
    ItemLearnBatchParcel batchLearn(ImageRequestParcel image, String clazz);

    /**
     * 按分类整组遗忘（clazz 分类编码 int64）。
     *
     * @param clazz 分类编码
     * @return 遗忘回执；未连接返回 null
     */
    BaseResultParcel itemForget(String clazz);

    /**
     * 按 UID 单条遗忘（uid 为 itemLearn 回执的持久化句柄）。
     *
     * @param uid 样本 UID
     * @return 遗忘回执；未连接返回 null
     */
    BaseResultParcel itemForgetByUid(long uid);

    /**
     * 已学类别分页（clazz[]/uidCount[]/thumbnails[] 下标对应，含分页元数据）。
     * page/size 为 0 时使用 SDK 默认值 1/100，size 上限 100。
     *
     * @return 类别列表；未连接或调用失败返回 null
     */
    ItemClazzListParcel getItemClazzList(int page, int size);

    /** 查询类别下样本图片分页（page/size 为 0 时使用 SDK 默认值 1/100）。 */
    ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size);

    /** 查询全部失败图片及 code/reason；断连返回 null，只有成功回执可将空列表视为无记录。 */
    FailedImagesResultParcel getFailedImages();

    /** 查询 SDK 持久化的云端服务开关；未连接或调用失败返回 null。 */
    CloudStatusParcel getCloudStatus();

    /** 设置云端服务开关；仅 code=0 时 enabled 为真实终态，当前 SDK 开启后不可关闭。 */
    CloudStatusParcel setCloudStatus(boolean enabled);

    /** 备份识别数据；互斥冲突或引擎未就绪通过 code/reason 返回。 */
    BaseResultParcel backupRecognitionData();

    /** 查询识别数据备份时间和版本；从未备份是正常空态。 */
    RecognitionDataInfoParcel getRecognitionDataInfo();

    /** 从 SDK 固定备份恢复识别数据，SDK 负责校验、回滚和引擎重建。 */
    BaseResultParcel restoreRecognitionData();

    /**
     * 配置业务上报域基址（持久化）。
     *
     * @param url 基址（非空）
     * @return 回执；未连接返回 null
     */
    BaseResultParcel setBaseUrl(String url);

    /** 提交已上传图片的反馈；URL 必须为 http(s)，归属由 SDK 的设备签名确定。 */
    BaseResultParcel submitFeedback(FeedbackParcel feedback);

    /** 按当前通道构造 JPEG/PNG 字节载体；AIDL 大图使用共享内存，无法承载时返回 null。 */
    default ImageRequestParcel createPhotoRequest(byte[] bytes, int format) {
        return new ImageRequestParcel(null, bytes, bytes.length, 0, 0, format);
    }

    /** 构造保留几何的编码图像载体；所有权只在单次调用期间有效。 */
    default ImageRequestParcel createEncodedImageRequest(byte[] bytes, int width, int height, int format) {
        return new ImageRequestParcel(null, bytes, bytes.length, width, height, format);
    }

    /** 上传图片到线上并返回 URL；调用结束由桥释放载体，非零回执不得继续提交反馈。 */
    PhotoUploadResultParcel uploadPhoto(ImageRequestParcel image);

    // ================= 设备绑定与商品 CRUD（SDK 尾插契约） =================

    /**
     * 设备绑定统一入口（AIDL/WS 同名）：一次完成账号+店铺绑定
     * （SDK 内顺序编排 bind-account → bind-shop，后端幂等，重复调用安全）。
     *
     * <p>回执两组字段：{@code getRequest()} 为传入回显（对账用，错误回执同样
     * 保留可得回显）；response 组（accountId/accountName/shopId/shopName/shopNo
     * 字符串化）为后端实际落库终态，仅 code=0 有效。绑定以 SDK 回执为准——
     * 扫码成功 ≠ 绑定成功，失败不改写客户端绑定状态。</p>
     *
     * @param req 绑定请求（accountId 必填正整数、shopId 必填、shopName 可选（缺省由 SDK 以 shopId 回填）、
     *            shopNo 可选）
     * @return 绑定回执；未连接返回 null
     */
    BindingResultParcel bindDevice(BindDeviceParcel req);

    /**
     * 创建商品：name/plu 必填（SDK 侧空白拒绝不触网），其余可选
     * （载体哨兵 -1/hasX=false = 未传）。PLU 同门店重复后端 400 透传 reason。
     *
     * <p>尽力同步语义：失败/超时/断连即结束本次尝试，不重试
     * 不补传；本地 CRUD 成功不受影响。成功从回执 record 取 SDK 商品 ID
     * （{@code getRecord().getGoodsId()}，雪花 ID 字符串）供后续更新/删除定位，
     * 不得以本地自增 ID 或 PLU 冒充。</p>
     *
     * @param req 商品保存载体（价格整数分、皮重克、saleMode 0 按个/1 按重量）
     * @return 单条回执（record 为创建后终态，含审计四字段）；未连接返回 null
     */
    GoodsResultParcel createGoods(GoodsSaveParcel req);

    /**
     * 更新商品：goodsId 为 SDK 商品 ID（十进制数字串）；仅传入
     * 字段生效（name/plu 传则不可空白）；他店/不存在/已软删 → 404 透传。
     *
     * <p>尽力同步语义同 {@link #createGoods}：缺少有效 SDK ID 映射时应由调用方
     * 直接跳过（不入桥），桥内失败不重试。</p>
     *
     * @param goodsId SDK 商品 ID
     * @param req     商品保存载体（仅传入字段生效）
     * @return 单条回执（record 为更新后终态）；未连接返回 null
     */
    GoodsResultParcel updateGoods(String goodsId, GoodsSaveParcel req);

    /**
     * 软删商品：SDK 内部负责关联学习数据/图片/索引清理，
     * 客户端<b>不追加</b> itemForget 或 SDK 目录清理。SDK 按自身云开关分流（本地
     * 自管模式仅 SDK 本地软删）。
     *
     * <p>尽力同步语义同 {@link #createGoods}：失败即丢弃，本地删除保持成功。</p>
     *
     * @param goodsId SDK 商品 ID（十进制数字串）
     * @return 删除回执；未连接返回 null
     */
    BaseResultParcel deleteGoods(String goodsId);

    /** 同步下载小图包并学习；后台调用，返回最终统计，失败不自动重试。 */
    ImageFlowProgressParcel downloadImagesAndLearn();
    /** 查询下载阶段进度；可与下载学习调用并行，未连接返回 null。 */
    ImageFlowProgressParcel getImageDownloadProgress();
    /** 查询学习阶段进度；可与下载学习调用并行，未连接返回 null。 */
    ImageFlowProgressParcel getImageLearnProgress();
    /** 分页读取云端商品目录；syncTimestamp=0 全量，增量游标由业务端管理。 */
    GoodsListResultParcel listGoods(long syncTimestamp, int current, int size);

    /** 将云端商品同步到 SDK 本地库，返回最终统计；不修改业务端数据库。 */
    GoodsDownloadResultParcel downloadGoods(int size);

    /** 查询 SDK 商品同步进度；失败时保留上次已提交游标。 */
    GoodsDownloadProgressParcel getDownloadGoodsProgress();

    /** 按指定商品 ID 下载图片并重新学习；会替换这些分类的旧样本，不自动重试。 */
    ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(long[] goodsIds);

    /** 按商品 ID 查询 SDK 本地商品；不存在返回 404。 */
    GoodsResultParcel getGoods(String goodsId);

    /** 分页筛选 SDK 本地商品；isDeleted=-1 默认未删除，current/size=0 使用 SDK 默认值。 */
    GoodsQueryResultParcel queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder);

    /** 按条件统计 SDK 本地商品数量。 */
    GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu, int isDeleted);

    /** 批量更新 SDK 本地商品；只覆盖提供字段，逐条检查 items 的结果。 */
    GoodsBatchResultParcel batchUpdateGoods(String[] goodsIds, GoodsSaveParcel changes);

    /** 批量软删除 SDK 本地商品；整体成功仍须逐条检查 items。 */
    GoodsBatchResultParcel batchDeleteGoods(String[] goodsIds);

}
