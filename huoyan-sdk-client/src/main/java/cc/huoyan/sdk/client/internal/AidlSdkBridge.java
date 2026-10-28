package cc.huoyan.sdk.client.internal;

import cc.huoyan.app.aidl.PhotoUploadResultParcel;
import android.os.Build;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;

import androidx.annotation.RequiresApi;

import android.content.Context;
import cc.huoyan.sdk.client.SdkLog;

import java.nio.ByteBuffer;
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
import cc.huoyan.app.aidl.ISdkCommService;
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

/**
 * AIDL 通道实现（绑定/商品 CRUD）：内部持
 * {@link SdkCommBinding}（绑定/退避重连/close 语义见该类），桥方法按 SDK 契约
 * {@code cc.huoyan.app.aidl.ISdkCommService} 封装为同步 Binder 调用——
 * <b>调用方须在后台线程执行</b>（Binder 事务同步阻塞）。调用前检查连接：
 * 未连接写操作返回未发送回执，查询可返回 null；{@link RemoteException}（服务进程死亡）触发
 * 标记断开 + 退避重连；写操作返回 RESULT_UNKNOWN，查询可返回 null，不向调用方抛异常（对齐 SDK 错误模型：
 * 错误经回执 code/reason，服务端回执原样透传）。
 *
 * <p>图像载体构造经 {@link #newSharedMemoryRequest}（API 27+ 主路径）/
 * {@link #newBytesRequest}（小图降级）静态工具就绪，帧来源（相机帧/识别区
 * 裁剪）随阶段 F 接入识别调度。
 */
public final class AidlSdkBridge implements SdkChannelBridge {

    private static final String LOG_TAG = "AidlSdkBridge";

    private final Object lock = new Object();
    private final EventLog eventLog;
    private Context context;
    private SdkCommBinding binding;

    /** [Biz] 打点出口：生产为 android Log（由调用方控制调试日志），单测注入记录实现（同 {@link WsSdkBridge.EventLog} 缝——JVM 单测 android.util.Log not mocked）。 */
    interface EventLog {

        /** 打一条业务打点（消息形如 {@code [Biz] key=value}）。 */
        void biz(String message);
    }

    /** 生产构造：绑定懒建（Application 就绪后以 App 上下文建，见 {@link #obtainBinding}）。 */
    AidlSdkBridge() {
        this.eventLog = message -> { };
    }

    /** 使用调用方的应用上下文与日志出口创建 AIDL 通道。 */
    public AidlSdkBridge(Context context, SdkLog log) {
        this.context = context.getApplicationContext();
        this.eventLog = message -> log.log(LOG_TAG, message);
    }

    /**
     * 注入式构造（单测：直连注入绑定替身/未连接绑定；打点走生产出口）。
     *
     * @param binding 底层绑定
     */
    AidlSdkBridge(SdkCommBinding binding) {
        this(binding, message -> { });
    }

    /**
     * 全量注入构造（单测：JVM 无 android.util.Log，打点缝注入记录实现）。
     *
     * @param binding  底层绑定
     * @param eventLog 打点出口
     */
    AidlSdkBridge(SdkCommBinding binding, EventLog eventLog) {
        this.binding = binding;
        this.eventLog = eventLog;
    }

    @Override
    public void connect() {
        try {
            SdkCommBinding current = obtainBinding();
            if (current != null) {
                current.bind();
            }
        } catch (RuntimeException e) {
            // 同步防御（2026-08-26 C50 真机实测）：绑定链路异常不上抛调用方——
            // checkSdkChannelStatus 的 IO 线程 execute() 无兜底（未捕获即进程
            // 崩溃）、StudyActivity onResume 主线程直调；静默吞掉，由退避重连承载
            eventLog.biz("[Biz] aidl_connect_error=" + e.getClass().getSimpleName());
        }
    }

    @Override
    public void disconnect() {
        SdkCommBinding current = currentBinding();
        if (current != null) {
            current.close();
        }
    }

    @Override
    public boolean isConnected() {
        SdkCommBinding current = currentBinding();
        return current != null && current.isConnected();
    }

    /** 在后台线程显式初始化；动作结果与机器状态分开读取。 */
    @Override
    public BaseResultParcel init(String appVersion) {
        SdkCommBinding current = currentBinding();
        return current != null ? current.init(appVersion)
                : new BaseResultParcel(LOCAL_DISCONNECTED, "本地未连接（绑定尚未建立）");
    }

    // ---- 桥方法封装（错误哨兵见 SdkChannelBridge 契约 Javadoc） ----

    @Override
    public CapabilityParcel getCapabilities() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getCapabilities();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    @Override
    public StatusSnapshotParcel getStatus() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getStatus();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    @Override
    public CertInfoParcel getCertInfo() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getCertInfo();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    @Override
    public DownloadProgressParcel getDownloadProgress() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getDownloadProgress();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 查询失败不产生写操作，不重试激活。 */
    @Override public cc.huoyan.app.aidl.LicenseModelsParcel queryDeviceModels() {
        ISdkCommService service = service();
        if (service == null) return null;
        try { return service.queryDeviceModels(); }
        catch (RemoteException error) { onRemoteException(); return null; }
    }

    /** 按激活码查询支持模型，适用于正常激活。 */
    @Override public cc.huoyan.app.aidl.LicenseModelsParcel queryLicenseModels(String activationCode) {
        ISdkCommService service = service();
        if (service == null) return null;
        try { return service.queryLicenseModels(activationCode); }
        catch (RemoteException error) { onRemoteException(); return null; }
    }

    @Override
    public BaseResultParcel activate(String activationCode, String modelCode) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.activate(activationCode, modelCode);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    @Override
    public BaseResultParcel setBaseUrl(String url) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.setBaseUrl(url);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    @Override
    public DetectResultParcel detect(ImageRequestParcel image) {
        try {
            ISdkCommService service = service();
            if (service == null) {
                return null;
            }
            try {
                return service.detect(image);
            } catch (RemoteException e) {
                onRemoteException();
                return null;
            }
        } finally {
            releaseImageCarrier(image);
        }
    }

    @Override
    public RecognizeResultParcel recognize(ImageRequestParcel image, int rotate) {
        try {
            ISdkCommService service = service();
            if (service == null) {
                return null;
            }
            try {
                return service.recognize(image, rotate);
            } catch (RemoteException e) {
                onRemoteException();
                return null;
            }
        } finally {
            releaseImageCarrier(image);
        }
    }

    @Override
    public LearnResultParcel itemLearn(ImageRequestParcel image, String clazz) {
        try {
            ISdkCommService service = service();
            if (service == null) {
                return new LearnResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", 0L);
            }
            try {
                LearnResultParcel receipt = service.itemLearn(image, clazz);
            return receipt == null ? new LearnResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", 0L) : receipt;
            } catch (RemoteException e) {
                onRemoteException();
                return new LearnResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", 0L);
            }
        } finally {
            releaseImageCarrier(image);
        }
    }

    @Override
    public ItemLearnBatchParcel batchLearn(ImageRequestParcel image, String clazz) {
        try {
            ISdkCommService service = service();
            if (service == null) {
                return new ItemLearnBatchParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
            }
            try {
                ItemLearnBatchParcel receipt = service.batchLearn(image, clazz);
            return receipt == null ? new ItemLearnBatchParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList()) : receipt;
            } catch (RemoteException e) {
                onRemoteException();
                return new ItemLearnBatchParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList());
            }
        } finally {
            releaseImageCarrier(image);
        }
    }

    @Override
    public BaseResultParcel itemForget(String clazz) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.itemForget(clazz);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    @Override
    public BaseResultParcel itemForgetByUid(long uid) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.itemForgetByUid(uid);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    @Override
    public ItemClazzListParcel getItemClazzList(int page, int size) {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getItemClazzList(page, size);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    @Override
    public BaseResultParcel submitFeedback(FeedbackParcel feedback) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.submitFeedback(feedback);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    /** 经 SDK 上传压缩图片；所有退出路径释放共享内存。 */
    @Override
    public PhotoUploadResultParcel uploadPhoto(ImageRequestParcel image) {
        try {
            ISdkCommService service = service();
            if (service == null) return new PhotoUploadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
            PhotoUploadResultParcel receipt = service.uploadPhoto(image);
            return receipt == null ? new PhotoUploadResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                    "调用已发出，结果未知", null) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new PhotoUploadResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN,
                    "调用已发出，结果未知", null);
        } finally { releaseImageCarrier(image); }
    }

    /** JPEG/PNG 大图使用共享内存，建区失败时仅允许内联上限内的小图。 */
    @Override
    public ImageRequestParcel createPhotoRequest(byte[] bytes, int format) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片字节不能为空");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SharedMemory memory = null;
            try {
                memory = SharedMemory.create("huoyan-photo", bytes.length);
                ByteBuffer mapped = memory.mapReadWrite();
                try {
                    mapped.put(bytes);
                } finally {
                    SharedMemory.unmap(mapped);
                }
                return new ImageRequestParcel(memory, null, bytes.length, 0, 0, format);
            } catch (ErrnoException | RuntimeException e) {
                if (memory != null) memory.close();
                eventLog.biz("[Biz] aidl_photo_carrier=failed");
            }
        }
        return bytes.length <= ImageRequestParcel.MAX_INLINE_BYTES
                ? new ImageRequestParcel(null, bytes, bytes.length, 0, 0, format) : null;
    }

    // ---- 绑定与商品 CRUD（SDK 尾插；错误模型同上——回执 code/reason 透传） ----

    @Override
    public BindingResultParcel bindDevice(BindDeviceParcel req) {
        ISdkCommService service = service();
        if (service == null) {
            return new BindingResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, null, null, null, null, null, null);
        }
        try {
            BindingResultParcel receipt = service.bindDevice(req);
            return receipt == null ? new BindingResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null, null, null, null, null, null, null, null) : receipt;
        } catch (RemoteException e) {
            bizRemoteError("bindDevice");
            onRemoteException();
            return new BindingResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null, null, null, null, null, null, null, null);
        }
    }

    @Override
    public GoodsResultParcel createGoods(GoodsSaveParcel req) {
        ISdkCommService service = service();
        if (service == null) {
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        try {
            GoodsResultParcel receipt = service.createGoods(req);
            return receipt == null ? new GoodsResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null) : receipt;
        } catch (RemoteException e) {
            bizRemoteError("createGoods");
            onRemoteException();
            return new GoodsResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null);
        }
    }

    @Override
    public GoodsResultParcel updateGoods(String goodsId, GoodsSaveParcel req) {
        ISdkCommService service = service();
        if (service == null) {
            return new GoodsResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null);
        }
        try {
            GoodsResultParcel receipt = service.updateGoods(goodsId, req);
            return receipt == null ? new GoodsResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null) : receipt;
        } catch (RemoteException e) {
            bizRemoteError("updateGoods");
            onRemoteException();
            return new GoodsResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null);
        }
    }

    @Override
    public BaseResultParcel deleteGoods(String goodsId) {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.deleteGoods(goodsId);
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            bizRemoteError("deleteGoods");
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    /** 同步下载小图包并学习；后台调用，失败不自动重试。 */
    @Override
    public ImageFlowProgressParcel downloadImagesAndLearn() {
        ISdkCommService service = service();
        if (service == null) return new ImageFlowProgressParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false, null, 0, 0, 0, 0, null, 0, null);
        try {
            ImageFlowProgressParcel receipt = service.downloadImagesAndLearn();
            return receipt == null ? new ImageFlowProgressParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false, null, 0, 0, 0, 0, null, 0, null) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new ImageFlowProgressParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false, null, 0, 0, 0, 0, null, 0, null);
        }
    }

    /** 查询小图包下载进度，事务失败返回 null。 */
    @Override
    public ImageFlowProgressParcel getImageDownloadProgress() {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.getImageDownloadProgress();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 查询小图包学习进度，事务失败返回 null。 */
    @Override
    public ImageFlowProgressParcel getImageLearnProgress() {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.getImageLearnProgress();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 同步调用 SDK 的 getItemClazzImages，失败时触发重连并返回 null。 */
    @Override
    public ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size) {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getItemClazzImages(clazz, page, size);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 同步调用 SDK 的 getFailedImages，失败时触发重连并返回 null。 */
    @Override
    public FailedImagesResultParcel getFailedImages() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getFailedImages();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 调用 SDK 的云端服务开关接口，事务失败时触发重连。 */
    @Override
    public CloudStatusParcel getCloudStatus() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getCloudStatus();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 调用 SDK 的云端服务开关接口，事务失败时触发重连。 */
    @Override
    public CloudStatusParcel setCloudStatus(boolean enabled) {
        ISdkCommService service = service();
        if (service == null) {
            return new CloudStatusParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false);
        }
        try {
            CloudStatusParcel receipt = service.setCloudStatus(enabled);
            return receipt == null ? new CloudStatusParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new CloudStatusParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false);
        }
    }

    /** 同步调用 SDK 的 backupRecognitionData，失败时触发重连并返回 null。 */
    @Override
    public BaseResultParcel backupRecognitionData() {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.backupRecognitionData();
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    /** 同步调用 SDK 的 getRecognitionDataInfo，失败时触发重连并返回 null。 */
    @Override
    public RecognitionDataInfoParcel getRecognitionDataInfo() {
        ISdkCommService service = service();
        if (service == null) {
            return null;
        }
        try {
            return service.getRecognitionDataInfo();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 同步调用 SDK 的 restoreRecognitionData，失败时触发重连并返回 null。 */
    @Override
    public BaseResultParcel restoreRecognitionData() {
        ISdkCommService service = service();
        if (service == null) {
            return new BaseResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        }
        try {
            BaseResultParcel receipt = service.restoreRecognitionData();
            return receipt == null ? new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知") : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知");
        }
    }

    /** 新增绑定/商品调用的事务失败打点（经注入缝出口，DEBUG 门在生产实现内；结果未知由调用方按 null 处置）。 */
    private void bizRemoteError(String method) {
        eventLog.biz("[Biz] aidl_" + method + "_remote_error result=unknown");
    }

    /** 按 AIDL 能力构造图像：API 27+ 优先共享内存，建区失败仅允许 1MB 内联降级。 */
    @Override
    public ImageRequestParcel createImageRequest(byte[] rgbFrame, int width, int height) {
        requireRgb888Frame(rgbFrame, width, height);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                return newSharedMemoryRequest(rgbFrame, width, height);
            } catch (ErrnoException e) {
                eventLog.biz("[Biz] aidl_image_carrier=failed reason=" + e.getMessage());
            }
        }
        return rgbFrame.length <= ImageRequestParcel.MAX_INLINE_BYTES
                ? newBytesRequest(rgbFrame, width, height) : null;
    }

    /** JPEG 90 优先经共享内存传输；建区失败时仅内联不超过 256 KiB 的图像。 */
    @Override
    public ImageRequestParcel createJpegImageRequest(android.graphics.Bitmap bitmap) {
        ImageRequestParcel jpeg = JpegImages.encode(bitmap);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                return newEncodedSharedMemoryRequest(jpeg.getBytes(), jpeg.getWidth(),
                        jpeg.getHeight(), ImageRequestParcel.FORMAT_JPEG);
            } catch (ErrnoException e) {
                eventLog.biz("[Biz] aidl_image_carrier=failed reason=" + e.getMessage());
            }
        }
        // 为 Binder 共享的事务缓冲区及其他在途请求留出空间。
        return jpeg.getSize() <= 256 * 1024 ? jpeg : null;
    }

    /** 编码帧使用共享内存；建区失败仅内联不超过 256 KiB 的数据。 */
    @Override
    public ImageRequestParcel createEncodedImageRequest(byte[] bytes, int width, int height, int format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                return newEncodedSharedMemoryRequest(bytes, width, height, format);
            } catch (ErrnoException e) {
                eventLog.biz("[Biz] aidl_image_carrier=failed reason=" + e.getMessage());
            }
        }
        return bytes.length <= 256 * 1024
                ? new ImageRequestParcel(null, bytes, bytes.length, width, height, format) : null;
    }

    // ---- 图像载体构造（detect/recognize/itemLearn/batchLearn 入参） ----

    /**
     * 构造 sharedMemory 主路径图像载体（API 27+，大图主路径）：
     * {@code SharedMemory.create} 建区后写入 RGB888 紧凑帧，跨进程传帧零拷贝。
     * SharedMemory 本端所有权归调用链持有：载体交给桥方法（detect/recognize/
     * itemLearn/batchLearn）后由桥在调用返回时经 {@link #releaseImageCarrier}
     * 统一释放，调用方无须（也不应）自行 close。
     *
     * @param rgbFrame RGB888 紧凑帧（长度必须等于 width*height*3）
     * @param width    帧宽（像素，&gt;0）
     * @param height   帧高（像素，&gt;0）
     * @return 图像载体（format=FORMAT_RGB888，size=有效字节数）
     * @throws ErrnoException           建区/映射失败（共享内存资源不足等）
     * @throws IllegalArgumentException 帧长度与几何不符或几何非法
     */
    @RequiresApi(27)
    public static ImageRequestParcel newSharedMemoryRequest(byte[] rgbFrame, int width, int height)
            throws ErrnoException {
        requireRgb888Frame(rgbFrame, width, height);
        return newEncodedSharedMemoryRequest(rgbFrame, width, height, ImageRequestParcel.FORMAT_RGB888);
    }

    @RequiresApi(27)
    private static ImageRequestParcel newEncodedSharedMemoryRequest(byte[] bytes, int width,
                                                                    int height, int format)
            throws ErrnoException {
        SharedMemory memory = SharedMemory.create("huoyan-frame", bytes.length);
        try {
            ByteBuffer mapped = memory.mapReadWrite();
            try {
                mapped.put(bytes);
            } finally {
                SharedMemory.unmap(mapped);
            }
            return new ImageRequestParcel(memory, null, bytes.length, width, height, format);
        } catch (ErrnoException | RuntimeException e) {
            memory.close();
            throw e;
        }
    }

    /**
     * 释放图像载体持有的 SharedMemory（防持续识别每帧建区
     * 导致的 FD/native 泄漏）：AIDL 通道以<b>同步 Binder 事务返回为跨进程传参
     * 完成点</b>——代理侧 marshalling 已把 fd 写入出参 Parcel、binder 驱动已向
     * 对端进程复制独立 fd，此后本端 close 不影响对端读取；RemoteException
     * （事务未达/失败）与未连接早退路径同样无人再读该区，close 一律安全。
     * bytes 载体无 native 资源，本方法空操作。
     *
     * @param image 图像载体；null 安全
     */
    public static void releaseImageCarrier(ImageRequestParcel image) {
        if (image == null) {
            return;
        }
        SharedMemory memory = image.getSharedMemory();
        if (memory != null) {
            memory.close();
        }
    }

    /**
     * 构造 bytes 降级图像载体（小图 ≤1MB，仍需 API 27+）：RGB888 紧凑帧内联传递
     * （超限由对端 validate 回执 code=-1 裁决）。
     *
     * @param rgbFrame RGB888 紧凑帧（长度必须等于 width*height*3）
     * @param width    帧宽（像素，&gt;0）
     * @param height   帧高（像素，&gt;0）
     * @return 图像载体（format=FORMAT_RGB888，size=全长）
     * @throws IllegalArgumentException 帧长度与几何不符或几何非法
     */
    public static ImageRequestParcel newBytesRequest(byte[] rgbFrame, int width, int height) {
        requireRgb888Frame(rgbFrame, width, height);
        return new ImageRequestParcel(null, rgbFrame, rgbFrame.length, width, height,
                ImageRequestParcel.FORMAT_RGB888);
    }

    /** 帧几何一致性快速失败（调用方编程错误；契约合法性由对端回执裁决）。 */
    private static void requireRgb888Frame(byte[] rgbFrame, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "width/height 必须为正：width=" + width + ", height=" + height);
        }
        long required = width * (long) height * 3;
        if (rgbFrame == null || rgbFrame.length != required) {
            throw new IllegalArgumentException("RGB888 帧长度必须等于 width*height*3（"
                    + required + "）：length=" + (rgbFrame == null ? "null" : rgbFrame.length));
        }
    }

    // ---- 内部 ----

    /** 当前绑定（null=尚未连接过，未连接占位语义）。 */
    private SdkCommBinding currentBinding() {
        synchronized (lock) {
            return binding;
        }
    }

    /** 懒建绑定：使用调用方传入的应用上下文（无上下文的测试实例保持未连接）。 */
    private SdkCommBinding obtainBinding() {
        synchronized (lock) {
            if (binding == null) {
                Context application = context;
                if (application == null) {
                    return null;
                }
                binding = new SdkCommBinding(application, (tag, message) -> eventLog.biz(message));
            }
            return binding;
        }
    }

    /** 当前服务代理（null=未连接）；Binder 同步事务，调用方须在后台线程。 */
    private ISdkCommService service() {
        SdkCommBinding current = currentBinding();
        return current != null ? current.service() : null;
    }

    /** Binder 调用失败（服务进程死亡等）：标记断开并触发退避重连。 */
    private void onRemoteException() {
        SdkCommBinding current = currentBinding();
        if (current != null) {
            current.onRemoteException();
        }
    }
    /** 分页读取云端商品目录；syncTimestamp=0 全量，增量游标由业务端管理。 */
    @Override
    public GoodsListResultParcel listGoods(long syncTimestamp, int current, int size) {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.listGoods(syncTimestamp, current, size);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 将云端商品同步到 SDK 本地库，返回最终统计；不修改业务端数据库。 */
    @Override
    public GoodsDownloadResultParcel downloadGoods(int size) {
        ISdkCommService service = service();
        if (service == null) return new GoodsDownloadResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", null, null, 0, 0, 0, 0, 0, 0, 0, false, 0, null);
        try {
            GoodsDownloadResultParcel receipt = service.downloadGoods(size);
            return receipt == null ? new GoodsDownloadResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null, null, 0, 0, 0, 0, 0, 0, 0, false, 0, null) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new GoodsDownloadResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", null, null, 0, 0, 0, 0, 0, 0, 0, false, 0, null);
        }
    }

    /** 查询 SDK 商品同步进度；失败时保留上次已提交游标。 */
    @Override
    public GoodsDownloadProgressParcel getDownloadGoodsProgress() {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.getDownloadGoodsProgress();
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 按指定商品 ID 下载图片并重新学习；会替换这些分类的旧样本，不自动重试。 */
    @Override
    public ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(long[] goodsIds) {
        ISdkCommService service = service();
        if (service == null) return new ImageFlowProgressParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", false, null, 0, 0, 0, 0, null, 0, null);
        try {
            ImageFlowProgressParcel receipt = service.downloadImagesAndLearnByGoodsId(goodsIds);
            return receipt == null ? new ImageFlowProgressParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false, null, 0, 0, 0, 0, null, 0, null) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new ImageFlowProgressParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", false, null, 0, 0, 0, 0, null, 0, null);
        }
    }

    /** 按商品 ID 查询 SDK 本地商品；不存在返回 404。 */
    @Override
    public GoodsResultParcel getGoods(String goodsId) {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.getGoods(goodsId);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 分页筛选 SDK 本地商品；isDeleted=-1 默认未删除，current/size=0 使用 SDK 默认值。 */
    @Override
    public GoodsQueryResultParcel queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder) {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.queryGoods(keyword, name, plu, isDeleted, current, size, sortBy, sortOrder);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 按条件统计 SDK 本地商品数量。 */
    @Override
    public GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu, int isDeleted) {
        ISdkCommService service = service();
        if (service == null) return null;
        try {
            return service.getGoodsCount(keyword, name, plu, isDeleted);
        } catch (RemoteException e) {
            onRemoteException();
            return null;
        }
    }

    /** 批量更新 SDK 本地商品；只覆盖提供字段，逐条检查 items 的结果。 */
    @Override
    public GoodsBatchResultParcel batchUpdateGoods(String[] goodsIds, GoodsSaveParcel changes) {
        ISdkCommService service = service();
        if (service == null) return new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
        try {
            GoodsBatchResultParcel receipt = service.batchUpdateGoods(goodsIds, changes);
            return receipt == null ? new GoodsBatchResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList()) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new GoodsBatchResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList());
        }
    }

    /** 批量软删除 SDK 本地商品；整体成功仍须逐条检查 items。 */
    @Override
    public GoodsBatchResultParcel batchDeleteGoods(String[] goodsIds) {
        ISdkCommService service = service();
        if (service == null) return new GoodsBatchResultParcel(LOCAL_DISCONNECTED, "SDK 未连接，请求未发送", java.util.Collections.emptyList());
        try {
            GoodsBatchResultParcel receipt = service.batchDeleteGoods(goodsIds);
            return receipt == null ? new GoodsBatchResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList()) : receipt;
        } catch (RemoteException e) {
            onRemoteException();
            return new GoodsBatchResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "调用已发出，结果未知", java.util.Collections.emptyList());
        }
    }

}
