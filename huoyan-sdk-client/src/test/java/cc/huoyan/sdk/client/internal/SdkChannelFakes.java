package cc.huoyan.sdk.client.internal;

import android.content.ServiceConnection;

import java.util.ArrayList;
import java.util.List;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.FailedImageParcel;
import cc.huoyan.app.aidl.FailedImagesResultParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

/**
 * 通道层测试替身集：绑定操作记录 / 主线程调度捕获 / 打点记录，
 * 供 {@link SdkCommBindingTest} 与 {@link AidlSdkBridgeTest} 共用；
 * 阶段 D 增可编程桥替身（状态/证书查询透传验证）。
 */
final class SdkChannelFakes {

    /** 记录式绑定操作：可设定 bind 成败，计数 bind/unbind。 */
    static final class RecordingBindOps implements SdkCommBinding.BindOps {

        boolean bindAccepted = true;
        int bindCount;
        int unbindCount;

        @Override
        public boolean bind(ServiceConnection connection) {
            bindCount++;
            return bindAccepted;
        }

        @Override
        public void unbind(ServiceConnection connection) {
            unbindCount++;
        }
    }

    /** 捕获式主线程调度：记录 post/postDelayed 任务与延迟，由测试手动推进（模拟主线程时序）。 */
    static final class CapturingScheduler implements SdkCommBinding.Scheduler {

        final List<Runnable> posted = new ArrayList<>();
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override
        public void post(Runnable task) {
            posted.add(task);
        }

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            delayed.add(task);
            delays.add(delayMs);
        }

        @Override
        public void cancelPending() {
            posted.clear();
            delayed.clear();
            delays.clear();
        }

        /** 执行并清空全部即时任务（模拟主线程消化消息队列）。 */
        void runPosted() {
            List<Runnable> snapshot = new ArrayList<>(posted);
            posted.clear();
            for (Runnable task : snapshot) {
                task.run();
            }
        }

        /** 执行最近一个延迟任务（模拟退避到期重连）。 */
        void runLastDelayed() {
            Runnable task = delayed.remove(delayed.size() - 1);
            delays.remove(delays.size() - 1);
            task.run();
        }
    }

    /** 记录式打点：收集 [Biz] 消息。 */
    static final class RecordingEventLog implements SdkCommBinding.EventLog {

        final List<String> messages = new ArrayList<>();

        @Override
        public void biz(String message) {
            messages.add(message);
        }
    }

    /**
     * 可编程桥替身：连接标志 / 就绪码 / 状态与证书回执可设定，
     * 供 {@link SdkChannelManagerTest} 验证便捷查询的透传语义；其余业务方法占位
     * （本替身只服务状态查询路径，不模拟识别/学习行为）。
     */
    static class FakeSdkChannelBridge implements SdkChannelBridge {
        @Override public cc.huoyan.app.aidl.GoodsListResultParcel listGoods(long syncTimestamp, int current, int size) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsDownloadResultParcel downloadGoods(int size) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsDownloadProgressParcel getDownloadGoodsProgress() { return null; }
        @Override public cc.huoyan.app.aidl.ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(long[] goodsIds) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsResultParcel getGoods(String goodsId) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsQueryResultParcel queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu, int isDeleted) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsBatchResultParcel batchUpdateGoods(String[] goodsIds, GoodsSaveParcel changes) { return null; }
        @Override public cc.huoyan.app.aidl.GoodsBatchResultParcel batchDeleteGoods(String[] goodsIds) { return null; }
        @Override public cc.huoyan.app.aidl.ImageFlowProgressParcel downloadImagesAndLearn() { return null; }
        @Override public cc.huoyan.app.aidl.ImageFlowProgressParcel getImageDownloadProgress() { return null; }
        @Override public cc.huoyan.app.aidl.ImageFlowProgressParcel getImageLearnProgress() { return null; }

        /** 查询云端开关的测试占位。 */
        @Override
        public cc.huoyan.app.aidl.CloudStatusParcel getCloudStatus() { return null; }

        /** 设置云端开关的测试占位。 */
        @Override
        public cc.huoyan.app.aidl.CloudStatusParcel setCloudStatus(boolean enabled) { return null; }


        boolean connected;
        int readyCode = SdkChannelBridge.LOCAL_DISCONNECTED;
        String readyReason;
        StatusSnapshotParcel status;
        CertInfoParcel certInfo;
        int connectCount;

        @Override
        public void connect() {
            connectCount++;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public BaseResultParcel init(String appVersion) {
            return new BaseResultParcel(readyCode, readyReason);
        }

        @Override
        public CapabilityParcel getCapabilities() {
            return null;
        }

        @Override
        public StatusSnapshotParcel getStatus() {
            return status;
        }

        @Override
        public CertInfoParcel getCertInfo() {
            return certInfo;
        }

        @Override
        public DownloadProgressParcel getDownloadProgress() {
            return null;
        }

        @Override
        public cc.huoyan.app.aidl.LicenseModelsParcel queryDeviceModels() { return null; }
        public cc.huoyan.app.aidl.LicenseModelsParcel queryLicenseModels(String activationCode) { return null; }

    public BaseResultParcel activate(String activationCode, String modelCode) {
            return null;
        }

        /** 构造测试桥使用的 RGB 字节载体。 */
        @Override
        public ImageRequestParcel createImageRequest(byte[] rgbFrame, int width, int height) {
            return new ImageRequestParcel(null, rgbFrame, rgbFrame.length, width, height,
                    ImageRequestParcel.FORMAT_RGB888);
        }

        @Override
        public DetectResultParcel detect(ImageRequestParcel image) {
            return null;
        }

        @Override
        public RecognizeResultParcel recognize(ImageRequestParcel image, int rotate) {
            return null;
        }

        @Override
        public LearnResultParcel itemLearn(ImageRequestParcel image, String clazz) {
            return null;
        }

        @Override
        public ItemLearnBatchParcel batchLearn(ImageRequestParcel image, String clazz) {
            return null;
        }

        @Override
        public BaseResultParcel itemForget(String clazz) {
            return null;
        }

        @Override
        public BaseResultParcel itemForgetByUid(long uid) {
            return null;
        }

        @Override
        public ItemClazzListParcel getItemClazzList(int page, int size) {
            return null;
        }

        @Override
        public BaseResultParcel setBaseUrl(String url) {
            return null;
        }

        @Override
        public ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size) {
            return null;
        }

        @Override
        public FailedImagesResultParcel getFailedImages() {
            return null;
        }

        @Override
        public BaseResultParcel backupRecognitionData() {
            return null;
        }

        @Override
        public RecognitionDataInfoParcel getRecognitionDataInfo() {
            return null;
        }

        @Override
        public BaseResultParcel restoreRecognitionData() {
            return null;
        }

        @Override
    public cc.huoyan.app.aidl.PhotoUploadResultParcel uploadPhoto(cc.huoyan.app.aidl.ImageRequestParcel image) {
        return null;
    }

    @Override
        public BaseResultParcel submitFeedback(FeedbackParcel feedback) {
            return null;
        }

        // ---- 绑定/商品 CRUD（本替身只服务状态查询路径，占位可预设） ----

        /** bindDevice 预设回执。 */
        BindingResultParcel bindDeviceResult;

        /** createGoods 预设回执。 */
        GoodsResultParcel createGoodsResult;

        /** updateGoods 预设回执。 */
        GoodsResultParcel updateGoodsResult;

        /** deleteGoods 预设回执。 */
        BaseResultParcel deleteGoodsResult;

        @Override
        public BindingResultParcel bindDevice(BindDeviceParcel req) {
            return bindDeviceResult;
        }

        @Override
        public GoodsResultParcel createGoods(GoodsSaveParcel req) {
            return createGoodsResult;
        }

        @Override
        public GoodsResultParcel updateGoods(String goodsId, GoodsSaveParcel req) {
            return updateGoodsResult;
        }

        @Override
        public BaseResultParcel deleteGoods(String goodsId) {
            return deleteGoodsResult;
        }

        /** 固定返回本替身的桥工厂（配合 {@link SdkChannelManager#createForTest}）。 */
    }

    private SdkChannelFakes() {
    }
}
