package cc.huoyan.sdk.client;

import android.content.Context;
import android.graphics.Bitmap;
import cc.huoyan.sdk.client.internal.DefaultSdkClient;
import cc.huoyan.sdk.client.model.*;
import java.util.List;

/**
 * SDK 客户端：只传递请求与结果，不访问相机、秤、打印机或业务数据库。
 * connect 异步建连；isConnected 后在工作线程 initialize，再按 getStatus 判断就绪。
 * 除 connect/close/isConnected 外，远程方法同步阻塞，请由调用方工作线程执行。
 * code=0 表示请求成功；null 表示未获得回执，写操作结果可能未知，不自动重试。
 * 图像由调用方提供，通道载体由库内部创建和释放。close 后实例不可复用。
 */
public interface HuoyanSdkClient extends AutoCloseable {
    /** 通道不可用的本地错误码，不与 SDK 错误码冲突。 */
    int LOCAL_DISCONNECTED = Integer.MIN_VALUE;
    /** 本地无法创建图像载体；请求尚未发送到 SDK。 */
    int IMAGE_UNAVAILABLE = Integer.MIN_VALUE + 1;

    /** 已开始执行但结果未知；写操作不得自动重放。 */
    int RESULT_UNKNOWN = 40009;
    /** 请求尚未执行，可由调用方退避后重试。 */
    int NOT_EXECUTED = 40010;

    /** 创建独立客户端；内部仅持有应用上下文，不保存业务配置。 */
    static HuoyanSdkClient create(Context context, SdkConfig config) {
        return new DefaultSdkClient(context, config);
    }

    /** 异步连接 SDK（幂等）；本机 WS 会绑定拉起 SDK，远端 WS 不启动本机服务。 */
    void connect();

    /** 断开并停止重连；关闭后的实例不可再次连接。 */
    void close();

    /** 查询通信连接状态；已连接不等于模型就绪。 */
    boolean isConnected();

    /** 初始化 SDK 并自动携带宿主 APK 的 versionName；是否已激活通过 getStatus 查询。 */
    BaseResult initialize();

    /** 查询 API 版本、能力及 SDK 应用版本；版本信息不依赖激活，SDK 版本读取失败时为 unknown/-1。 */
    Capability getCapabilities();

    /** 查询 sdkState、modelReady 等状态；code=0 仅表示查询成功。 */
    StatusSnapshot getStatus();

    /** 查询授权及证书信息。 */
    CertInfo getCertInfo();

    /** 查询模型下载字节进度；total=0 表示总量未知。 */
    DownloadProgress getDownloadProgress();

    /** 按激活码查询支持模型；后台调用，适用于正常激活。 */
    LicenseModels queryLicenseModels(String activationCode);
    /** 无参查询当前设备绑定授权的支持模型。 */
    LicenseModels queryDeviceModels();

    /** 使用激活码与选中的模型代码提交一次激活；写请求不得自动重试。 */
    BaseResult activate(String activationCode, String modelCode);

    /** 检测调用方图像中的目标框；不识别分类，须在工作线程调用。 */
    DetectResult detect(SdkImage image);

    /** 识别图像并返回分类、框和候选；rotate 只能为 0/90/180/270，须在工作线程调用。 */
    RecognizeResult recognize(SdkImage image, int rotate);

    /** 将图像学习到指定分类编码，返回样本 UID；工作线程调用，不自动重试。 */
    LearnResult learn(SdkImage image, String clazz);

    /** 检测大图并逐框学习到同一分类；每框返回独立结果，允许部分成功。 */
    ItemLearnBatch batchLearn(SdkImage image, String clazz);

    /** 删除指定分类全部学习样本；由业务端决定何时调用。 */
    BaseResult forgetClass(String clazz);

    /** 按学习回执 UID 删除单个样本。 */
    BaseResult forgetSample(long uid);

    /** 分页查询已学分类；page/size 为 0 时使用 SDK 默认值 1/100。 */
    ItemClazzList listClasses(int page, int size);

    /** 分页查询分类样本图片；图片展示由业务端负责。 */
    ItemClazzImagePage listImages(String clazz, int page, int size);

    /** 查询失败图片及错误回执；只有 code=0 且 items 为空才表示没有失败记录。 */
    FailedImagesResult getFailedImages();

    /** 查询 SDK 保存的云服务开关。 */
    CloudStatus getCloudStatus();

    /** 设置云服务开关；以成功回执为准，当前 SDK 开启后不可关闭。 */
    CloudStatus setCloudStatus(boolean enabled);

    /** 请求 SDK 备份识别数据，不访问业务端文件。 */
    BaseResult backupRecognitionData();

    /** 查询 SDK 识别备份的时间与版本。 */
    RecognitionDataInfo getRecognitionDataInfo();

    /** 请求 SDK 恢复备份；SDK 负责数据校验和引擎重建。 */
    BaseResult restoreRecognitionData();

    /** 设置 SDK 使用的上报基址。 */
    BaseResult setBaseUrl(String url);

    /** 提交包含已上传图片 URL 的反馈；内容由业务端构造。 */
    BaseResult submitFeedback(Feedback feedback);

    /** 上传调用方提供的 JPEG/PNG 字节；成功后返回 URL。 */
    PhotoUploadResult uploadPhoto(SdkImage image);

    /** 将外部门店或账号唯一标识绑定到 JAR 内置账号；须在工作线程调用。 */
    default BindingResult bindDevice(String shopId) {
        return bindDevice(shopId, null, null);
    }

    /**
     * 绑定外部标识及可选门店信息；accountId 由 JAR 内置，不接受调用方覆盖。
     * shopId 为非空字符串，原样传递，可为第三方店铺 ID 或账号唯一标识。
     * 返回 request.shopId 为外部标识，返回 shopId 为后端内部门店 ID；不自动重试。
     */
    BindingResult bindDevice(String shopId, String shopName, String shopNo);

    /** 透传 SDK 商品创建请求；价格单位为整数分，不维护客户端商品库。 */
    GoodsResult createGoods(GoodsSave req);

    /** 按 SDK 商品 ID 更新显式提供的字段；可选字段 null 表示不更新。 */
    GoodsResult updateGoods(String goodsId, GoodsSave req);

    /** 透传 SDK 商品删除请求；不改变客户端数据库，不自动重试。 */
    BaseResult deleteGoods(String goodsId);

    /** 请求 SDK 下载图片并学习；阻塞至 SDK 返回最终统计。 */
    ImageFlowProgress downloadImagesAndLearn();

    /** 查询 SDK 图片下载进度，可与下载请求并行。 */
    ImageFlowProgress getImageDownloadProgress();

    /** 查询 SDK 图片学习进度，可与学习请求并行。 */
    ImageFlowProgress getImageLearnProgress();

    /** 从 RGB888 字节构造图像快照；紧凑排列，长度须为 width*height*3。 */
    default SdkImage image(byte[] rgb, int width, int height) {
        return SdkImage.rgb888(rgb, width, height);
    }

    /** 编码调用方位图；AIDL 使用 RGB888，WS 使用 JPEG90，不回收位图。 */
    SdkImage image(Bitmap bitmap);

    /** 将调用方位图编码为 JPEG90，不回收位图。 */
    default SdkImage jpegImage(Bitmap bitmap) {
        return SdkImage.jpeg(bitmap);
    }

    /** 构造 JPEG/PNG 图片上传快照，不读取或删除业务文件。 */
    default SdkImage photoImage(byte[] bytes, int format) {
        return SdkImage.encoded(bytes, 0, 0, format);
    }

    /** 在工作线程识别调用方位图；位图已转正时不再旋转。 */
    default RecognizeResult recognize(Bitmap bitmap) {
        return recognize(image(bitmap), 0);
    }

    /** 识别已转正的图像帧，须在工作线程调用。 */
    default RecognizeResult recognize(SdkImage image) {
        return recognize(image, 0);
    }

    /** 在工作线程检测调用方位图。 */
    default DetectResult detect(Bitmap bitmap) {
        return detect(image(bitmap));
    }

    /** 在工作线程学习调用方位图到指定分类，不自动裁剪。 */
    default LearnResult learn(Bitmap bitmap, String clazz) {
        return learn(SdkImage.rgb888(bitmap), clazz);
    }
    /** 分页读取云端商品目录；syncTimestamp=0 全量，增量游标由业务端管理。 */
    GoodsListResult listGoods(long syncTimestamp, int current, int size);

    /** 将云端商品同步到 SDK 本地库，返回最终统计；不修改业务端数据库。 */
    GoodsDownloadResult downloadGoods(int size);

    /** 查询 SDK 商品同步进度；失败时保留上次已提交游标。 */
    GoodsDownloadProgress getDownloadGoodsProgress();

    /** 按指定商品 ID 下载图片并重新学习；会替换这些分类的旧样本，不自动重试。 */
    ImageFlowProgress downloadImagesAndLearnByGoodsId(long[] goodsIds);

    /** 按商品 ID 查询 SDK 本地商品；不存在返回 404。 */
    GoodsResult getGoods(String goodsId);

    /** 分页筛选 SDK 本地商品；isDeleted=-1 默认未删除，current/size=0 使用 SDK 默认值。 */
    GoodsQueryResult queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder);

    /** 按条件统计 SDK 本地商品数量。 */
    GoodsCountResult getGoodsCount(String keyword, String name, String plu, int isDeleted);

    /** 批量更新 SDK 本地商品；只覆盖提供字段，逐条检查 items 的结果。 */
    GoodsBatchResult batchUpdateGoods(String[] goodsIds, GoodsSave changes);

    /** 批量软删除 SDK 本地商品；整体成功仍须逐条检查 items。 */
    GoodsBatchResult batchDeleteGoods(String[] goodsIds);

}
