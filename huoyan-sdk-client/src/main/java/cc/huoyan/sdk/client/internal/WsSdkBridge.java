package cc.huoyan.sdk.client.internal;

import cc.huoyan.app.aidl.PhotoUploadResultParcel;

import com.alibaba.fastjson.JSONObject;
import android.content.Context;
import cc.huoyan.sdk.client.SdkLog;
import cc.huoyan.sdk.client.internal.ws.WsParcelMapper;
import cc.huoyan.sdk.client.internal.ws.WsPendingTable;
import cc.huoyan.sdk.client.internal.ws.WsV2Codec;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import cc.huoyan.app.aidl.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

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
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

/**
 * WebSocket v2 通道实现：连接本机 SDK WS 服务端
 * （缺省 127.0.0.1:18086，SP 端口覆盖随后续阶段），协议 v2 requestId 异步——
 * 发送 JSON 命令帧（snake_case 命令名）+ 需图命令 binary 传图，响应按
 * requestId 路由回调用方（{@link WsPendingTable}），同步方法面经 future
 * 等待收敛为 {@link SdkChannelBridge} 的阻塞语义。
 *
 * <p>断线指数退避重连（1s 起倍增、上限 10s，连接成功即复位）；连接断开时
 * 在途请求全部作废。上下文未就绪时 {@link #connect} 为空操作
 * （对齐 {@link AidlSdkBridge} 的 JVM 单测防御）。本地失败语义：调用时未连接按接口契约返回 null /
 * {@link SdkChannelBridge#LOCAL_DISCONNECTED}；在途超时/断线映射为
 * code={@link SdkChannelBridge#LOCAL_DISCONNECTED} 的错误回执（reason 携带
 * 诊断）。图像载体仅支持带 RGB888 几何的 bytes（size&gt;0 取前缀）；
 * SharedMemory/shmKey/无几何 legacy bytes 不支持
 * （回执 code=-1）。init 发送携带宿主客户端版本的初始化命令，机器状态通过 getStatus 查询。
 */
public final class WsSdkBridge implements SdkChannelBridge {

    private static final String LOG_TAG = "WsSdkBridge";

    /** 常规命令的本地响应等待上限，不作为 SDK 命令参数。 */
    private static final long REQUEST_TIMEOUT_MS = 35_000L;

    /** 反馈/上传 SDK 内预算 60 秒，另留 5 秒接收回执。 */
    private static final long FEEDBACK_TIMEOUT_MS = 65_000L;

    /**
     * 绑定请求超时（SDK 内顺序编排 bind-account → bind-shop 两次出网 + 换维度
     * 清理互斥等待，放宽到 60s）。
     */
    private static final long BIND_TIMEOUT_MS = 60_000L;

    /** 初始化可能包含证书恢复、模型准备；仅限制本地传输等待，不向 SDK 传超时参数。 */
    private static final long INIT_TIMEOUT_MS = 125_000L;

    /** 断线重连初始退避。 */
    private static final long RECONNECT_INITIAL_MS = 1_000L;

    /** 断线重连退避上限。 */
    private static final long RECONNECT_MAX_MS = 10_000L;

    private final WsV2Codec.Endpoint endpoint;
    private final EventLog eventLog;
    private final BooleanSupplier applicationReady;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> recognitionStarts =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final WsPendingTable pending = new WsPendingTable();
    private final AtomicLong sessionEpoch = new AtomicLong();
    private final AtomicLong requestSeq = new AtomicLong();
    private final Object sendLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private final java.util.function.Supplier<SdkChannelBridge> localServiceFactory;
    private SdkChannelBridge localService;
    private volatile WebSocketClient client;
    private volatile boolean connected;
    private volatile ScheduledExecutorService reconnectExecutor;
    private volatile long reconnectDelay = RECONNECT_INITIAL_MS;

    /** 用调用方上下文连接 WS；本机端点通过绑定拉起 SDK。 */
    public WsSdkBridge(Context context, WsV2Codec.Endpoint endpoint, SdkLog log) {
        this(endpoint, message -> log.log(LOG_TAG, message), () -> true,
                () -> {
                    if (!LocalSdkEndpoint.isLocal(endpoint.getHost())) return null;
                    if (android.os.Build.VERSION.SDK_INT >= 27) return new AidlSdkBridge(context, log);
                    // 低版本不创建含 SharedMemory 契约的 AIDL 实现；显式启动通讯服务拉起 WS。
                    try {
                        context.startService(new android.content.Intent().setClassName(
                                "cc.huoyan", "cc.huoyan.app.comm.SdkCommService"));
                    } catch (RuntimeException e) {
                        log.log(LOG_TAG, "SDK 服务启动失败：" + e.getMessage());
                    }
                    return null;
                });
    }

    WsSdkBridge() {
        this(WsV2Codec.Endpoint.def(), message -> { });
    }

    WsSdkBridge(WsV2Codec.Endpoint endpoint) {
        this(endpoint, message -> { });
    }

    WsSdkBridge(WsV2Codec.Endpoint endpoint, EventLog eventLog) {
        this(endpoint, eventLog, () -> false, () -> null);
    }

    /** 注入应用就绪条件，便于通过回环服务验证真实 WS 请求。 */
    WsSdkBridge(WsV2Codec.Endpoint endpoint, EventLog eventLog, BooleanSupplier applicationReady) {
        this(endpoint, eventLog, applicationReady, () -> null);
    }

    /** 注入本机服务拉起器；远端 WS 的工厂返回 null，不绑定本机 SDK。 */
    WsSdkBridge(WsV2Codec.Endpoint endpoint, EventLog eventLog, BooleanSupplier applicationReady,
            java.util.function.Supplier<SdkChannelBridge> localServiceFactory) {
        this.endpoint = endpoint;
        this.eventLog = eventLog;
        this.applicationReady = applicationReady;
        this.localServiceFactory = localServiceFactory;
    }

    /** [Biz] 打点出口：生产为 android Log（由调用方控制调试日志），单测注入记录实现。 */
    interface EventLog {

        /** 打一条业务打点（消息形如 {@code [Biz] key=value}）。 */
        void biz(String message);
    }

    @Override
    public synchronized void connect() {
        if (!applicationReady.getAsBoolean()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        localService = localServiceFactory.get();
        if (localService != null) {
            localService.connect();
            eventLog.biz("[Biz] ws_sdk_start=bind_service");
        }
        reconnectDelay = RECONNECT_INITIAL_MS;
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ws-sdk-reconnect");
            thread.setDaemon(true);
            return thread;
        });
        openConnection();
    }

    @Override
    public synchronized void disconnect() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        if (localService != null) {
            localService.disconnect();
            localService = null;
        }
        WebSocketClient current;
        synchronized (sendLock) {
            current = client;
            client = null;
        }
        connected = false;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
                // 已断链路上 close 异常无需处理
            }
        }
        ScheduledExecutorService executor = reconnectExecutor;
        reconnectExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        int dropped = pending.failAll("通道已断开（disconnect）");
        eventLog.biz("[Biz] ws_disconnect manual pending_dropped=" + dropped);
    }

    @Override
    public boolean isConnected() {
        return started.get() && connected;
    }

    /** 显式发送携带宿主版本的初始化命令，传输等待时间不作为协议参数。 */
    @Override
    public BaseResultParcel init(String appVersion) {
        JSONObject params = new JSONObject();
        if (appVersion != null) params.put("app_version", appVersion);
        WsV2Codec.Response response = request(WsV2Codec.CMD_INIT,
                params, null, INIT_TIMEOUT_MS);
        return response == null
                ? new BaseResultParcel(LOCAL_DISCONNECTED, "本地未连接（WS 未建立或已断开）")
                : WsParcelMapper.base(response);
    }

    @Override
    public CapabilityParcel getCapabilities() {
        WsV2Codec.Response response =
                request(WsV2Codec.CMD_GET_CAPABILITIES, null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.capability(response);
    }

    @Override
    public StatusSnapshotParcel getStatus() {
        WsV2Codec.Response response =
                request(WsV2Codec.CMD_GET_STATUS, null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.status(response);
    }

    @Override
    public CertInfoParcel getCertInfo() {
        WsV2Codec.Response response =
                request(WsV2Codec.CMD_GET_CERT_INFO, null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.certInfo(response);
    }

    @Override
    public DownloadProgressParcel getDownloadProgress() {
        WsV2Codec.Response response =
                request(WsV2Codec.CMD_GET_DOWNLOAD_PROGRESS, null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.downloadProgress(response);
    }

    /** 无参查询当前设备支持的授权模型。 */
    @Override public cc.huoyan.app.aidl.LicenseModelsParcel queryDeviceModels() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_QUERY_DEVICE_MODELS,
                null, null, INIT_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.licenseModels(response);
    }

    /** 按激活码查询支持模型，适用于正常激活。 */
    @Override public cc.huoyan.app.aidl.LicenseModelsParcel queryLicenseModels(String activationCode) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_QUERY_LICENSE_MODELS,
                WsParcelMapper.buildLicenseModelsParams(activationCode), null, INIT_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.licenseModels(response);
    }

    @Override
    public BaseResultParcel activate(String activationCode, String modelCode) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_ACTIVATE,
                WsParcelMapper.buildActivateParams(activationCode, modelCode), null, INIT_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    @Override
    public BaseResultParcel setBaseUrl(String url) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_SET_BASE_URL,
                WsParcelMapper.buildUrlParams(url), null, REQUEST_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    /** 识别和检测默认从裁剪位图直接编码 JPEG90，减少 WS 传输字节数。 */
    @Override
    public ImageRequestParcel createImageRequest(android.graphics.Bitmap bitmap) {
        return createJpegImageRequest(bitmap);
    }

    /** 构造 WS 图像载体：保留 RGB888 兼容契约。 */
    @Override
    public ImageRequestParcel createImageRequest(byte[] rgbFrame, int width, int height) {
        if (width <= 0 || height <= 0 || rgbFrame == null
                || (long) width * height > Integer.MAX_VALUE / 3
                || rgbFrame.length != (long) width * height * 3) {
            throw new IllegalArgumentException("RGB888 帧长度必须等于正数 width*height*3");
        }
        return new ImageRequestParcel(null, rgbFrame, rgbFrame.length, width, height,
                ImageRequestParcel.FORMAT_RGB888);
    }

    @Override
    public DetectResultParcel detect(ImageRequestParcel image) {
        if (!isConnected()) {
            return null;
        }
        ImagePayload payload = toImagePayload(image);
        if (payload.errorReason != null) {
            return WsParcelMapper.errorDetect(WsParcelMapper.ERR_ARGS, payload.errorReason);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_DETECT,
                imageParams(image, payload, null),
                payload.bytes, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.detect(response);
    }

    @Override
    public RecognizeResultParcel recognize(ImageRequestParcel image, int rotate) {
        if (!isConnected()) {
            return null;
        }
        long startedAt = System.nanoTime();
        String requestId = nextRequestId();
        ImagePayload payload = toImagePayload(image);
        timing(requestId, "image_prepare", startedAt,
                "imageBytes=" + (payload.bytes == null ? 0 : payload.bytes.length)
                        + " width=" + payload.width + " height=" + payload.height
                        + " format=" + (image != null && image.getFormat() == ImageRequestParcel.FORMAT_JPEG ? "jpeg90" : "rgb"));
        if (payload.errorReason != null) {
            timing(requestId, "end", startedAt, "outcome=invalid_image");
            return WsParcelMapper.errorRecognize(WsParcelMapper.ERR_ARGS, payload.errorReason);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_RECOGNIZE,
                imageParams(image, payload, rotate),
                payload.bytes, REQUEST_TIMEOUT_MS, requestId, startedAt);
        long mapStart = System.nanoTime();
        try {
            RecognizeResultParcel result = response == null ? null : WsParcelMapper.recognize(response);
            timing(requestId, "end", startedAt, "resultMapMs=" + elapsedMs(mapStart)
                    + " outcome=" + (result == null ? "no_response" : "code_" + result.getCode()));
            return result;
        } catch (RuntimeException e) {
            timing(requestId, "end", startedAt, "outcome=result_map_exception");
            throw e;
        }
    }

    @Override
    public LearnResultParcel itemLearn(ImageRequestParcel image, String clazz) {
        if (!isConnected()) {
            return new LearnResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", 0L);
        }
        ImagePayload payload = toRgbPayload(image);
        if (payload.errorReason != null) {
            return new LearnResultParcel(WsParcelMapper.ERR_ARGS, payload.errorReason, 0L);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_ITEM_LEARN,
                WsParcelMapper.buildLearnParams(clazz, payload.width, payload.height),
                payload.bytes, REQUEST_TIMEOUT_MS);
        return response == null ? new LearnResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", 0L) : WsParcelMapper.learn(response);
    }

    @Override
    public ItemLearnBatchParcel batchLearn(ImageRequestParcel image, String clazz) {
        if (!isConnected()) {
            return new ItemLearnBatchParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
        }
        ImagePayload payload = toRgbPayload(image);
        if (payload.errorReason != null) {
            return WsParcelMapper.errorBatchLearn(WsParcelMapper.ERR_ARGS, payload.errorReason);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_BATCH_LEARN,
                WsParcelMapper.buildLearnParams(clazz, payload.width, payload.height),
                payload.bytes, INIT_TIMEOUT_MS);
        return response == null ? new ItemLearnBatchParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList()) : WsParcelMapper.batchLearn(response);
    }

    @Override
    public BaseResultParcel itemForget(String clazz) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_ITEM_FORGET,
                WsParcelMapper.buildClazzParam(clazz), null, REQUEST_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    @Override
    public BaseResultParcel itemForgetByUid(long uid) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_ITEM_FORGET_BY_UID,
                WsParcelMapper.buildUidParam(uid), null, REQUEST_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    @Override
    public ItemClazzListParcel getItemClazzList(int page, int size) {
        WsV2Codec.Response response =
                request(WsV2Codec.CMD_GET_ITEM_CLAZZ_LIST,
                        WsParcelMapper.buildPageParams(page, size), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.clazzList(response);
    }

    @Override
    public BaseResultParcel submitFeedback(FeedbackParcel feedback) {
        if (!isConnected()) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        if (feedback == null) {
            return new BaseResultParcel(WsParcelMapper.ERR_ARGS, "反馈载体不能为空");
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_SUBMIT_FEEDBACK,
                WsParcelMapper.buildFeedbackParams(feedback), null, FEEDBACK_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    /** 先发送 uploadPhoto JSON 声明，再发送同 requestId 的 JPEG/PNG 二进制帧。 */
    @Override
    public PhotoUploadResultParcel uploadPhoto(ImageRequestParcel image) {
        if (!isConnected()) return new PhotoUploadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        if (image == null || image.getBytes() == null || image.getBytes().length == 0
                || image.getSharedMemory() != null || image.getShmKey() != null
                || (image.getFormat() != ImageRequestParcel.FORMAT_JPEG
                    && image.getFormat() != ImageRequestParcel.FORMAT_PNG)) {
            return new PhotoUploadResultParcel(-7, "需 JPEG/PNG 字节载体", null);
        }
        int size = image.getSize() == 0 ? image.getBytes().length : image.getSize();
        if (size <= 0 || size > image.getBytes().length || size > 8 * 1024 * 1024) {
            return new PhotoUploadResultParcel(-8, "图片字节长度无效或超过 8MB", null);
        }
        JSONObject params = new JSONObject();
        params.put("format", image.getFormat() == ImageRequestParcel.FORMAT_JPEG ? "jpeg" : "png");
        WsV2Codec.Response response = request(WsV2Codec.CMD_UPLOAD_PHOTO, params,
                Arrays.copyOf(image.getBytes(), size), FEEDBACK_TIMEOUT_MS);
        return response == null ? new PhotoUploadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null) : WsParcelMapper.photoUpload(response);
    }

    /** 调用 SDK 的 getItemClazzImages，保留服务端回执。 */
    @Override
    public ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size) {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_ITEM_CLAZZ_IMAGES,
                WsParcelMapper.buildClazzPageParams(clazz, page, size), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.clazzImages(response);
    }

    /** 调用 SDK 的 getFailedImages，保留服务端回执。 */
    @Override
    public FailedImagesResultParcel getFailedImages() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_FAILED_IMAGES,
                null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.failedImages(response);
    }

    /** 查询 SDK 持久化的云端服务开关。 */
    @Override
    public CloudStatusParcel getCloudStatus() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_CLOUD_STATUS,
                null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.cloudStatus(response);
    }

    /** 设置云端服务开关，保留 SDK 返回的终态或拒绝原因。 */
    @Override
    public CloudStatusParcel setCloudStatus(boolean enabled) {
        JSONObject params = new JSONObject();
        params.put("enabled", enabled);
        WsV2Codec.Response response = request(WsV2Codec.CMD_SET_CLOUD_STATUS,
                params, null, REQUEST_TIMEOUT_MS);
        return response == null ? new CloudStatusParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false) : WsParcelMapper.cloudStatus(response);
    }

    /** 调用 SDK 的 backupRecognitionData，保留服务端回执。 */
    @Override
    public BaseResultParcel backupRecognitionData() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_BACKUP_RECOGNITION_DATA,
                null, null, INIT_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    /** 调用 SDK 的 getRecognitionDataInfo，保留服务端回执。 */
    @Override
    public RecognitionDataInfoParcel getRecognitionDataInfo() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_RECOGNITION_DATA_INFO,
                null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.recognitionDataInfo(response);
    }

    /** 调用 SDK 的 restoreRecognitionData，保留服务端回执。 */
    @Override
    public BaseResultParcel restoreRecognitionData() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_RESTORE_RECOGNITION_DATA,
                null, null, INIT_TIMEOUT_MS);
        return response == null ? new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送") : WsParcelMapper.base(response);
    }

    /** 调用小图包下载学习（长任务独立超时，失败不重发）。 */
    @Override
    public ImageFlowProgressParcel downloadImagesAndLearn() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_DOWNLOAD_IMAGES_AND_LEARN,
                null, null, TimeUnit.MINUTES.toMillis(30));
        return response == null ? new ImageFlowProgressParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false, null, 0, 0, 0, 0, null, 0, null) : WsParcelMapper.imageFlow(response, true);
    }

    /** 调用小图包阶段进度查询。 */
    @Override
    public ImageFlowProgressParcel getImageDownloadProgress() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_IMAGE_DOWNLOAD_PROGRESS,
                null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.imageFlow(response, false);
    }

    /** 调用小图包阶段进度查询。 */
    @Override
    public ImageFlowProgressParcel getImageLearnProgress() {
        WsV2Codec.Response response = request(WsV2Codec.CMD_GET_IMAGE_LEARN_PROGRESS,
                null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.imageFlow(response, false);
    }

    // ================= 绑定与商品 CRUD（SDK 尾插命令，纯 text 帧无图） =================

    @Override
    public BindingResultParcel bindDevice(BindDeviceParcel req) {
        if (!isConnected()) {
            return new BindingResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, null, null, null, null, null, null);
        }
        if (req == null) {
            return new BindingResultParcel(WsParcelMapper.ERR_ARGS, "绑定载体不能为空",
                    null, null, null, null, null, null, null, null);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_BIND_DEVICE,
                WsParcelMapper.buildBindDeviceParams(req), null, BIND_TIMEOUT_MS);
        if (response == null) {
            eventLog.biz("[Biz] ws_bind_device result=not_executed reason=disconnected");
            return new BindingResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, null, null, null, null, null, null);
        }
        eventLog.biz("[Biz] ws_bind_device code=" + response.getCode());
        return WsParcelMapper.binding(response);
    }

    @Override
    public GoodsResultParcel createGoods(GoodsSaveParcel req) {
        if (!isConnected()) {
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        if (req == null) {
            return new GoodsResultParcel(WsParcelMapper.ERR_ARGS, "商品载体不能为空", null);
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_CREATE_GOODS,
                WsParcelMapper.buildGoodsSaveParams(req), null, REQUEST_TIMEOUT_MS);
        if (response == null) {
            eventLog.biz("[Biz] ws_create_goods result=not_executed reason=disconnected");
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        eventLog.biz("[Biz] ws_create_goods code=" + response.getCode());
        return WsParcelMapper.goods(response);
    }

    @Override
    public GoodsResultParcel updateGoods(String goodsId, GoodsSaveParcel req) {
        if (!isConnected()) {
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        if (goodsId == null || goodsId.trim().isEmpty()) {
            return new GoodsResultParcel(WsParcelMapper.ERR_ARGS, "goodsId 不能为空", null);
        }
        if (req == null) {
            return new GoodsResultParcel(WsParcelMapper.ERR_ARGS, "商品载体不能为空", null);
        }
        JSONObject params = WsParcelMapper.buildGoodsIdParam(goodsId);
        params.putAll(WsParcelMapper.buildGoodsSaveParams(req));
        WsV2Codec.Response response = request(WsV2Codec.CMD_UPDATE_GOODS,
                params, null, REQUEST_TIMEOUT_MS);
        if (response == null) {
            eventLog.biz("[Biz] ws_update_goods result=not_executed reason=disconnected");
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        eventLog.biz("[Biz] ws_update_goods code=" + response.getCode()
                + " goodsId=" + goodsId);
        return WsParcelMapper.goods(response);
    }

    @Override
    public BaseResultParcel deleteGoods(String goodsId) {
        if (!isConnected()) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        if (goodsId == null || goodsId.trim().isEmpty()) {
            return new BaseResultParcel(WsParcelMapper.ERR_ARGS, "goodsId 不能为空");
        }
        WsV2Codec.Response response = request(WsV2Codec.CMD_DELETE_GOODS,
                WsParcelMapper.buildGoodsIdParam(goodsId), null, REQUEST_TIMEOUT_MS);
        if (response == null) {
            eventLog.biz("[Biz] ws_delete_goods result=not_executed reason=disconnected");
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        eventLog.biz("[Biz] ws_delete_goods code=" + response.getCode()
                + " goodsId=" + goodsId);
        return WsParcelMapper.base(response);
    }

    // ================= 连接与重连 =================

    /** 建立新连接（每次重连新建客户端实例；连接失败经 onClose/onError 进退避）。 */
    private void openConnection() {
        if (!started.get()) {
            return;
        }
        final WebSocketClient fresh;
        try {
            fresh = new WebSocketClient(new URI(endpoint.toUrl())) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    reconnectDelay = RECONNECT_INITIAL_MS;
                    eventLog.biz("[Biz] ws_connect endpoint=" + endpoint.toUrl());
                }

                @Override
                public void onMessage(String message) {
                    long receivedAt = System.nanoTime();
                    WsV2Codec.Response response = WsV2Codec.parseResponse(message);
                    if (response != null && response.getRequestId() != null) {
                        Long startedAt = recognitionStarts.get(response.getRequestId());
                        if (startedAt != null) {
                            timing(response.getRequestId(), "response_received", startedAt,
                                    "responseDecodeMs=" + elapsedMs(receivedAt)
                                            + " responseChars=" + message.length()
                                            + " code=" + response.getCode());
                        }
                        pending.complete(response.getRequestId(), response);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    onTransportLost("连接关闭 code=" + code
                            + (reason == null || reason.isEmpty() ? "" : " reason=" + reason));
                }

                @Override
                public void onError(Exception ex) {
                    onTransportLost("连接异常："
                            + (ex == null ? "unknown" : ex.getClass().getSimpleName()));
                }
            };
        } catch (URISyntaxException e) {
            connected = false;
            scheduleReconnect();
            return;
        }
        synchronized (sendLock) {
            client = fresh;
        }
        try {
            fresh.connect();
        } catch (Exception e) {
            onTransportLost("connect 发起失败：" + e.getMessage());
        }
    }

    /** 传输层失守（onClose/onError 均达）：断开态落定 + 在途作废 + 退避重连。 */
    private void onTransportLost(String detail) {
        connected = false;
        int dropped = pending.failAll(detail);
        eventLog.biz("[Biz] ws_disconnect detail=" + detail + " pending_dropped=" + dropped);
        scheduleReconnect();
    }

    /** 指数退避重连调度（CAS 防 onClose/onError 双达重复调度）。 */
    private void scheduleReconnect() {
        if (!started.get()) {
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = reconnectDelay;
        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS);
        ScheduledExecutorService executor = reconnectExecutor;
        if (executor == null) {
            reconnectScheduled.set(false);
            return;
        }
        try {
            executor.schedule(() -> {
                reconnectScheduled.set(false);
                if (started.get() && !connected) {
                    openConnection();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            reconnectScheduled.set(false);
        }
    }

    // ================= 请求发送与 requestId 路由 =================

    /**
     * 发送一条 v2 命令并等待响应（阻塞调用线程）。
     *
     * @param cmd      命令名
     * @param params   命令参数；null=无参
     * @param image    图像字节；非 null 时 text 声明后紧随 binary 帧（需图命令）
     * @param timeoutMs 等待响应超时
     * @return 响应；未连接/发送失败返回 null；超时/在途断线返回
     *         code=RESULT_UNKNOWN 的本地错误响应
     */
    private WsV2Codec.Response request(String cmd, JSONObject params, byte[] image, long timeoutMs) {
        return request(cmd, params, image, timeoutMs, nextRequestId(), System.nanoTime());
    }

    private WsV2Codec.Response request(String cmd, JSONObject params, byte[] image, long timeoutMs,
            String requestId, long startedAt) {
        boolean trace = WsV2Codec.CMD_RECOGNIZE.equals(cmd);
        if (!isConnected()) {
            if (trace) timing(requestId, "transport_end", startedAt, "outcome=disconnected");
            return null;
        }
        CompletableFuture<WsV2Codec.Response> future = pending.register(requestId);
        if (trace) recognitionStarts.put(requestId, startedAt);
        String outcome = "send_exception";
        try {
            long encodeStart = System.nanoTime();
            String text = WsV2Codec.encodeRequest(requestId, cmd, params);
            long textEncodeMs = elapsedMs(encodeStart);
            long lockStart = System.nanoTime();
            synchronized (sendLock) {
                long lockWaitMs = elapsedMs(lockStart);
                WebSocketClient current = client;
                if (current == null || !current.isOpen()) {
                    outcome = "disconnected";
                    return null;
                }
                try {
                    long sendStart = System.nanoTime();
                    current.send(text);
                    long frameEncodeMs = 0L;
                    long binarySendMs = 0L;
                    if (image != null) {
                        long frameStart = System.nanoTime();
                        byte[] frame = WsV2Codec.encodeBinaryFrame(requestId, image);
                        frameEncodeMs = elapsedMs(frameStart);
                        long binaryStart = System.nanoTime();
                        current.send(frame);
                        binarySendMs = elapsedMs(binaryStart);
                    }
                    if (trace) timing(requestId, "send_enqueued", startedAt,
                            "textEncodeMs=" + textEncodeMs + " sendLockMs=" + lockWaitMs
                                    + " frameEncodeMs=" + frameEncodeMs + " binarySendEnqueueMs=" + binarySendMs
                                    + " sendEnqueueMs=" + elapsedMs(sendStart));
                } catch (Exception e) {
                    return new WsV2Codec.Response(requestId, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                            "发送中断，结果未知", null);
                }
            }
            try {
                WsV2Codec.Response response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                outcome = "code_" + response.getCode();
                return response;
            } catch (TimeoutException e) {
                outcome = "timeout";
                eventLog.biz("[Biz] ws_request_timeout requestId=" + requestId
                        + " cmd=" + cmd + " timeoutMs=" + timeoutMs);
                return new WsV2Codec.Response(requestId, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                        "请求超时（" + timeoutMs + "ms，cmd=" + cmd + "）", null);
            } catch (InterruptedException e) {
                outcome = "interrupted";
                Thread.currentThread().interrupt();
                return new WsV2Codec.Response(requestId, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                        "等待响应被中断（cmd=" + cmd + "）", null);
            } catch (ExecutionException e) {
                outcome = "disconnected";
                return new WsV2Codec.Response(requestId, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                        "连接断开，结果未知（cmd=" + cmd + "）", null);
            }
        } finally {
            pending.remove(requestId);
            recognitionStarts.remove(requestId);
            if (trace) timing(requestId, "transport_end", startedAt, "outcome=" + outcome);
        }
    }

    private void timing(String requestId, String event, long startedAt, String detail) {
        eventLog.biz("[Biz] [Perf] side=client requestId=" + requestId + " event=" + event
                + " totalMs=" + elapsedMs(startedAt) + " " + detail);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static JSONObject imageParams(ImageRequestParcel image, ImagePayload payload, Integer rotate) {
        JSONObject params = WsParcelMapper.buildImageParams(payload.width, payload.height, rotate);
        if (image.getFormat() == ImageRequestParcel.FORMAT_JPEG) params.put("format", "jpeg");
        return params;
    }

    static ImagePayload toImagePayload(ImageRequestParcel image) {
        if (image == null || image.getFormat() != ImageRequestParcel.FORMAT_JPEG) return toRgbPayload(image);
        byte[] bytes = image.getBytes();
        if (bytes == null || image.getSharedMemory() != null || image.getShmKey() != null) {
            return ImagePayload.error("WS JPEG 只支持 bytes 载体");
        }
        int size = image.getSize() == 0 ? bytes.length : image.getSize();
        if (size <= 0 || size > bytes.length || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return ImagePayload.error("JPEG 字节长度或尺寸无效");
        }
        return ImagePayload.ok(size == bytes.length ? bytes : Arrays.copyOf(bytes, size),
                image.getWidth(), image.getHeight());
    }

    /** 连接内唯一 requestId：会话纪元 + 单调序号（已回执 id 不复用，规避在途冲突）。 */
    private String nextRequestId() {
        return sessionEpoch.incrementAndGet() + "-" + requestSeq.incrementAndGet();
    }

    // ================= 图像载体映射 =================

    /** WS 通道图像载荷：bytes+几何（errorReason=null 可上线）或不可映射原因。 */
    static final class ImagePayload {

        final byte[] bytes;
        final int width;
        final int height;
        final String errorReason;

        private ImagePayload(byte[] bytes, int width, int height, String errorReason) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.errorReason = errorReason;
        }

        static ImagePayload ok(byte[] bytes, int width, int height) {
            return new ImagePayload(bytes, width, height, null);
        }

        static ImagePayload error(String reason) {
            return new ImagePayload(null, 0, 0, reason);
        }
    }

    /** WS 只接收 RGB 字节；独立校验几何和有效长度，AIDL 的 1MB 内联限制不适用。 */
    static ImagePayload toRgbPayload(ImageRequestParcel image) {
        if (image == null) {
            return ImagePayload.error("图像载体不能为空");
        }
        byte[] bytes = image.getBytes();
        int present = (image.getShmKey() != null ? 1 : 0)
                + (image.getSharedMemory() != null ? 1 : 0) + (bytes != null ? 1 : 0);
        if (present != 1) {
            return ImagePayload.error("图像载体必须为 shmKey/sharedMemory/bytes 恰选一");
        }
        if (bytes == null) {
            return ImagePayload.error("WS 不支持 SharedMemory/shmKey，请使用 RGB 字节载体");
        }
        if (image.getFormat() != ImageRequestParcel.FORMAT_RGB888
                || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return ImagePayload.error("WS 图像需携带 RGB888 格式及正数 width/height");
        }
        int size = image.getSize();
        if (bytes.length == 0 || size < 0 || size > bytes.length) {
            return ImagePayload.error("RGB 字节不能为空且 size 必须在 0..bytes.length");
        }
        int effective = size == 0 ? bytes.length : size;
        if ((long) image.getWidth() * image.getHeight() > effective / 3L) {
            return ImagePayload.error("width*height*3 超出有效字节数");
        }
        return ImagePayload.ok(effective < bytes.length ? Arrays.copyOf(bytes, effective) : bytes,
                image.getWidth(), image.getHeight());
    }
    private static JSONObject params(Object... pairs) {
        JSONObject result = new JSONObject(true);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    /** 调用 SDK listGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsListResultParcel listGoods(long syncTimestamp, int current, int size) {
        if (!isConnected()) return null;
        WsV2Codec.Response response = request("listGoods", params("sync_timestamp", syncTimestamp, "current", current, "size", size), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.goodsList(response);
    }

    /** 调用 SDK downloadGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsDownloadResultParcel downloadGoods(int size) {
        if (!isConnected()) return new GoodsDownloadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, 0, 0, 0, 0, 0, 0, 0, false, 0, null);
        WsV2Codec.Response response = request("downloadGoods", params("size", size), null, REQUEST_TIMEOUT_MS);
        return response == null ? new GoodsDownloadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, 0, 0, 0, 0, 0, 0, 0, false, 0, null) : WsParcelMapper.goodsDownload(response);
    }

    /** 调用 SDK getDownloadGoodsProgress，保留参数和回执，不自动重试。 */
    @Override
    public GoodsDownloadProgressParcel getDownloadGoodsProgress() {
        if (!isConnected()) return null;
        WsV2Codec.Response response = request("getDownloadGoodsProgress", null, null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.goodsDownloadProgress(response);
    }

    /** 调用 SDK downloadImagesAndLearnByGoodsId，保留参数和回执，不自动重试。 */
    @Override
    public ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(long[] goodsIds) {
        if (!isConnected()) return new ImageFlowProgressParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false, null, 0, 0, 0, 0, null, 0, null);
        WsV2Codec.Response response = request("downloadImagesAndLearnByGoodsId", params("goods_ids", goodsIds), null, TimeUnit.MINUTES.toMillis(30));
        return response == null ? new ImageFlowProgressParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false, null, 0, 0, 0, 0, null, 0, null) : WsParcelMapper.imageFlow(response, true);
    }

    /** 调用 SDK getGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsResultParcel getGoods(String goodsId) {
        if (!isConnected()) return null;
        WsV2Codec.Response response = request("getGoods", params("goods_id", goodsId), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.goods(response);
    }

    /** 调用 SDK queryGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsQueryResultParcel queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder) {
        if (!isConnected()) return null;
        WsV2Codec.Response response = request("queryGoods", params("keyword", keyword, "name", name, "plu", plu, "is_deleted", isDeleted, "current", current, "size", size, "sort_by", sortBy, "sort_order", sortOrder), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.goodsQuery(response);
    }

    /** 调用 SDK getGoodsCount，保留参数和回执，不自动重试。 */
    @Override
    public GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu, int isDeleted) {
        if (!isConnected()) return null;
        WsV2Codec.Response response = request("getGoodsCount", params("keyword", keyword, "name", name, "plu", plu, "is_deleted", isDeleted), null, REQUEST_TIMEOUT_MS);
        return response == null ? null : WsParcelMapper.goodsCount(response);
    }

    /** 调用 SDK batchUpdateGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsBatchResultParcel batchUpdateGoods(String[] goodsIds, GoodsSaveParcel changes) {
        if (!isConnected()) return new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
        WsV2Codec.Response response = request("batchUpdateGoods", params("goods_ids", goodsIds, "changes", changes == null ? null : WsParcelMapper.buildGoodsSaveParams(changes)), null, REQUEST_TIMEOUT_MS);
        return response == null ? new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList()) : WsParcelMapper.goodsBatch(response);
    }

    /** 调用 SDK batchDeleteGoods，保留参数和回执，不自动重试。 */
    @Override
    public GoodsBatchResultParcel batchDeleteGoods(String[] goodsIds) {
        if (!isConnected()) return new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
        WsV2Codec.Response response = request("batchDeleteGoods", params("goods_ids", goodsIds), null, REQUEST_TIMEOUT_MS);
        return response == null ? new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList()) : WsParcelMapper.goodsBatch(response);
    }

}
