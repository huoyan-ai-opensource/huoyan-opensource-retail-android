package cc.huoyan.sdk.client.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.Collections;
import cc.huoyan.sdk.client.*;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import java.util.Objects;

/** 公共 API 的通信适配实现；应用请通过 HuoyanSdkClient.create 创建。 */
public final class DefaultSdkClient implements HuoyanSdkClient {
    private final SdkChannelBridge transport;
    private final boolean jpeg;
    /** 当前宿主 APK 版本；每次初始化均透传，实例间不共享。 */
    private final String appVersion;
    private volatile boolean closed;
    private final SdkLog log;
    private final Object bitmapRecognitionLock = new Object();
    private int[] recognitionPixels;
    private byte[] recognitionRgb;

    /** 使用应用上下文及显式配置建立客户端。 */
    public DefaultSdkClient(Context context, SdkConfig config) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(config, "config 不能为空");
        Context application = context.getApplicationContext();
        if (config.getChannel() == SdkConfig.Channel.AIDL && android.os.Build.VERSION.SDK_INT < 27) {
            throw new IllegalArgumentException("SDK AIDL 需要 Android API 27+，请使用 WS");
        }
        this.appVersion = ClientAppVersion.read(application);
        this.log = config.getLog();
        this.jpeg = config.getChannel() == SdkConfig.Channel.WS;
        this.transport = jpeg
                ? new WsSdkBridge(application, cc.huoyan.sdk.client.internal.ws.WsV2Codec.parseEndpoint(config.getWsEndpoint()), config.getLog())
                : new AidlSdkBridge(application, config.getLog());
    }

    DefaultSdkClient(SdkChannelBridge transport, boolean jpeg, String appVersion) {
        this.transport = transport;
        this.appVersion = appVersion;
        this.jpeg = jpeg;
        this.log = (tag, message) -> {};
    }

    private ImageRequestParcel carrier(SdkImage image) {
        Objects.requireNonNull(image, "图像不能为空");
        byte[] bytes = image.getBytes();
        return image.getFormat() == SdkImage.FORMAT_RGB888
                ? transport.createImageRequest(bytes, image.getWidth(), image.getHeight())
                : transport.createEncodedImageRequest(bytes, image.getWidth(), image.getHeight(), image.getFormat());
    }

    private ImageRequestParcel learningCarrier(SdkImage image) {
        Objects.requireNonNull(image, "图像不能为空");
        if (image.getFormat() == SdkImage.FORMAT_RGB888) return carrier(image);
        byte[] bytes = image.getBytes();
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (decoded == null) throw new IllegalArgumentException("学习图像无法解码为 JPEG/PNG");
        try {
            if ((image.getWidth() > 0 && image.getWidth() != decoded.getWidth())
                    || (image.getHeight() > 0 && image.getHeight() != decoded.getHeight())) {
                throw new IllegalArgumentException("学习图像声明尺寸与解码尺寸不一致");
            }
            return carrier(SdkImage.rgb888(decoded));
        } finally {
            decoded.recycle();
        }
    }

    @Override
    public synchronized void connect() {
        if (!closed) transport.connect();
    }

    @Override
    public synchronized void close() {
        closed = true;
        transport.disconnect();
    }

    @Override
    public boolean isConnected() {
        return !closed && transport.isConnected();
    }

    @Override
    public BaseResult initialize() {
        return ModelMapper.fromWire(transport.init(appVersion));
    }

    @Override
    public Capability getCapabilities() {
        return ModelMapper.fromWire(transport.getCapabilities());
    }

    @Override
    public StatusSnapshot getStatus() {
        return ModelMapper.fromWire(transport.getStatus());
    }

    @Override
    public CertInfo getCertInfo() {
        return ModelMapper.fromWire(transport.getCertInfo());
    }

    @Override
    public DownloadProgress getDownloadProgress() {
        return ModelMapper.fromWire(transport.getDownloadProgress());
    }

    /** 将动态模型回执转换为公共不可变对象。 */
    @Override public LicenseModels queryDeviceModels() {
        return ModelMapper.fromWire(transport.queryDeviceModels());
    }

    /** 按激活码查询支持模型，适用于正常激活。 */
    @Override public LicenseModels queryLicenseModels(String activationCode) {
        return ModelMapper.fromWire(transport.queryLicenseModels(activationCode));
    }

    @Override
    public BaseResult activate(String activationCode, String modelCode) {
        return ModelMapper.fromWire(transport.activate(activationCode, modelCode));
    }

    @Override
    public DetectResult detect(SdkImage image) {
        ImageRequestParcel request = carrier(image);
        return request == null
                ? new DetectResult(IMAGE_UNAVAILABLE, "当前系统或通道无法承载此图像", Collections.emptyList())
                : ModelMapper.fromWire(transport.detect(request));
    }

    @Override
    public RecognizeResult recognize(SdkImage image, int rotate) {
        if (rotate != 0 && rotate != 90 && rotate != 180 && rotate != 270) {
            throw new IllegalArgumentException("rotate 必须为 0/90/180/270");
        }
        long started = System.nanoTime();
        ImageRequestParcel request = carrier(image);
        return recognizePrepared(request, rotate, started, started, started, System.nanoTime());
    }

    /** 同步识别位图；AIDL 复用转换缓冲区，返回前不允许下一帧覆盖内联载体。 */
    @Override
    public RecognizeResult recognize(Bitmap bitmap) {
        if (jpeg) return HuoyanSdkClient.super.recognize(bitmap);
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("位图不能为空或已回收");
        }
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        long count = (long) width * height;
        if (count > SdkImage.MAX_TRANSPORT_BYTES / 3) {
            throw new IllegalArgumentException("位图过大");
        }
        long started = System.nanoTime();
        synchronized (bitmapRecognitionLock) {
            long acquired = System.nanoTime();
            if (recognitionPixels == null || recognitionPixels.length != count) {
                recognitionPixels = new int[(int) count];
                recognitionRgb = new byte[(int) count * 3];
            }
            bitmap.getPixels(recognitionPixels, 0, width, 0, 0, width, height);
            RgbImages.writeRgb888(recognitionPixels, recognitionRgb);
            long encoded = System.nanoTime();
            ImageRequestParcel request = transport.createImageRequest(recognitionRgb, width, height);
            return recognizePrepared(request, 0, started, acquired, encoded, System.nanoTime());
        }
    }

    private RecognizeResult recognizePrepared(ImageRequestParcel request, int rotate,
                                              long started, long acquired, long encoded, long carried) {
        long returned = carried;
        long mapped = carried;
        boolean transportCompleted = false;
        boolean mappingCompleted = false;
        String outcome = "exception";
        try {
            RecognizeResultParcel wire = request == null ? null : transport.recognize(request, rotate);
            returned = System.nanoTime();
            transportCompleted = true;
            RecognizeResult result = request == null
                    ? new RecognizeResult(IMAGE_UNAVAILABLE, "当前系统或通道无法承载此图像", Collections.emptyList())
                    : ModelMapper.fromWire(wire);
            mapped = System.nanoTime();
            mappingCompleted = true;
            outcome = result == null ? "no_receipt" : String.valueOf(result.getCode());
            return result;
        } finally {
            long finished = System.nanoTime();
            // 日志异常不能改变识别结果；实际输出由调用方的 DEBUG 日志出口控制。
            try {
                log.log("SdkRecognitionTiming", "[Biz] recognize_client_timing channel=" + (jpeg ? "ws" : "aidl")
                        + " code=" + outcome
                        + " payloadBytes=" + (request == null ? 0 : request.getSize())
                        + " format=" + (request == null ? 0 : request.getFormat())
                        + " bufferWaitUs=" + (acquired - started) / 1000
                        + " imageEncodeUs=" + (encoded - acquired) / 1000
                        + " carrierUs=" + (carried - encoded) / 1000
                        + " transportUs=" + ((transportCompleted ? returned : finished) - carried) / 1000
                        + " resultMapUs=" + (transportCompleted ? ((mappingCompleted ? mapped : finished) - returned) / 1000 : 0)
                        + " totalUs=" + (finished - started) / 1000);
            } catch (RuntimeException ignored) {
                // 调用方日志出口不得中断业务。
            }
        }
    }

    @Override
    public LearnResult learn(SdkImage image, String clazz) {
        cc.huoyan.sdk.client.operation.SdkInputs.clazz(clazz);
        ImageRequestParcel request = learningCarrier(image);
        return request == null
                ? new LearnResult(IMAGE_UNAVAILABLE, "当前系统或通道无法承载此图像", 0L)
                : ModelMapper.fromWire(transport.itemLearn(request, clazz));
    }

    @Override
    public ItemLearnBatch batchLearn(SdkImage image, String clazz) {
        cc.huoyan.sdk.client.operation.SdkInputs.clazz(clazz);
        ImageRequestParcel request = learningCarrier(image);
        return request == null
                ? new ItemLearnBatch(IMAGE_UNAVAILABLE, "当前系统或通道无法承载此图像", Collections.emptyList())
                : ModelMapper.fromWire(transport.batchLearn(request, clazz));
    }

    @Override
    public BaseResult forgetClass(String clazz) {
        cc.huoyan.sdk.client.operation.SdkInputs.clazz(clazz);
        return ModelMapper.fromWire(transport.itemForget(clazz));
    }

    @Override
    public BaseResult forgetSample(long uid) {
        cc.huoyan.sdk.client.operation.SdkInputs.positiveId(uid);
        return ModelMapper.fromWire(transport.itemForgetByUid(uid));
    }

    @Override
    public ItemClazzList listClasses(int page, int size) {
        return ModelMapper.fromWire(transport.getItemClazzList(page, size));
    }

    @Override
    public ItemClazzImagePage listImages(String clazz, int page, int size) {
        cc.huoyan.sdk.client.operation.SdkInputs.clazz(clazz);
        return ModelMapper.fromWire(transport.getItemClazzImages(clazz, page, size));
    }

    @Override
    public FailedImagesResult getFailedImages() {
        return ModelMapper.fromWire(transport.getFailedImages());
    }

    @Override
    public CloudStatus getCloudStatus() {
        return ModelMapper.fromWire(transport.getCloudStatus());
    }

    @Override
    public CloudStatus setCloudStatus(boolean enabled) {
        return ModelMapper.fromWire(transport.setCloudStatus(enabled));
    }

    @Override
    public BaseResult backupRecognitionData() {
        return ModelMapper.fromWire(transport.backupRecognitionData());
    }

    @Override
    public RecognitionDataInfo getRecognitionDataInfo() {
        return ModelMapper.fromWire(transport.getRecognitionDataInfo());
    }

    @Override
    public BaseResult restoreRecognitionData() {
        return ModelMapper.fromWire(transport.restoreRecognitionData());
    }

    @Override
    public BaseResult setBaseUrl(String url) {
        return ModelMapper.fromWire(transport.setBaseUrl(url));
    }

    @Override
    public BaseResult submitFeedback(Feedback feedback) {
        return ModelMapper.fromWire(transport.submitFeedback(ModelMapper.toWire(feedback)));
    }

    @Override
    public PhotoUploadResult uploadPhoto(SdkImage image) {
        ImageRequestParcel request = carrier(image);
        return request == null
                ? new PhotoUploadResult(IMAGE_UNAVAILABLE, "当前系统或通道无法承载此图像", null)
                : ModelMapper.fromWire(transport.uploadPhoto(request));
    }

    @Override
    public BindingResult bindDevice(String shopId, String shopName, String shopNo) {
        if (shopId == null || shopId.trim().isEmpty()) {
            throw new IllegalArgumentException("shopId 不能为空");
        }
        return ModelMapper.fromWire(transport.bindDevice(new cc.huoyan.app.aidl.BindDeviceParcel(
                BundledAccount.getAccountId(), shopId, shopName, shopNo)));
    }

    @Override
    public GoodsResult createGoods(GoodsSave req) {
        return ModelMapper.fromWire(transport.createGoods(ModelMapper.toWire(req)));
    }

    @Override
    public GoodsResult updateGoods(String goodsId, GoodsSave req) {
        return ModelMapper.fromWire(transport.updateGoods(goodsId, ModelMapper.toWire(req)));
    }

    @Override
    public BaseResult deleteGoods(String goodsId) {
        return ModelMapper.fromWire(transport.deleteGoods(goodsId));
    }

    @Override
    public ImageFlowProgress downloadImagesAndLearn() {
        return ModelMapper.fromWire(transport.downloadImagesAndLearn());
    }

    @Override
    public ImageFlowProgress getImageDownloadProgress() {
        return ModelMapper.fromWire(transport.getImageDownloadProgress());
    }

    @Override
    public ImageFlowProgress getImageLearnProgress() {
        return ModelMapper.fromWire(transport.getImageLearnProgress());
    }

    @Override
    public SdkImage image(Bitmap bitmap) {
        return jpeg ? SdkImage.jpeg(bitmap) : SdkImage.rgb888(bitmap);
    }
    /** 分页读取云端商品目录；syncTimestamp=0 全量，增量游标由业务端管理。 */
    @Override
    public GoodsListResult listGoods(long syncTimestamp, int current, int size) {
        return ModelMapper.fromWire(transport.listGoods(syncTimestamp, current, size));
    }

    /** 将云端商品同步到 SDK 本地库，返回最终统计；不修改业务端数据库。 */
    @Override
    public GoodsDownloadResult downloadGoods(int size) {
        return ModelMapper.fromWire(transport.downloadGoods(size));
    }

    /** 查询 SDK 商品同步进度；失败时保留上次已提交游标。 */
    @Override
    public GoodsDownloadProgress getDownloadGoodsProgress() {
        return ModelMapper.fromWire(transport.getDownloadGoodsProgress());
    }

    /** 按指定商品 ID 下载图片并重新学习；会替换这些分类的旧样本，不自动重试。 */
    @Override
    public ImageFlowProgress downloadImagesAndLearnByGoodsId(long[] goodsIds) {
        return ModelMapper.fromWire(transport.downloadImagesAndLearnByGoodsId(goodsIds));
    }

    /** 按商品 ID 查询 SDK 本地商品；不存在返回 404。 */
    @Override
    public GoodsResult getGoods(String goodsId) {
        return ModelMapper.fromWire(transport.getGoods(goodsId));
    }

    /** 分页筛选 SDK 本地商品；isDeleted=-1 默认未删除，current/size=0 使用 SDK 默认值。 */
    @Override
    public GoodsQueryResult queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder) {
        return ModelMapper.fromWire(transport.queryGoods(keyword, name, plu, isDeleted, current, size, sortBy, sortOrder));
    }

    /** 按条件统计 SDK 本地商品数量。 */
    @Override
    public GoodsCountResult getGoodsCount(String keyword, String name, String plu, int isDeleted) {
        return ModelMapper.fromWire(transport.getGoodsCount(keyword, name, plu, isDeleted));
    }

    /** 批量更新 SDK 本地商品；只覆盖提供字段，逐条检查 items 的结果。 */
    @Override
    public GoodsBatchResult batchUpdateGoods(String[] goodsIds, GoodsSave changes) {
        return ModelMapper.fromWire(transport.batchUpdateGoods(goodsIds, ModelMapper.toWire(changes)));
    }

    /** 批量软删除 SDK 本地商品；整体成功仍须逐条检查 items。 */
    @Override
    public GoodsBatchResult batchDeleteGoods(String[] goodsIds) {
        return ModelMapper.fromWire(transport.batchDeleteGoods(goodsIds));
    }

}
