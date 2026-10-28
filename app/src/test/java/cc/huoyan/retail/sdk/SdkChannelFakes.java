package cc.huoyan.retail.sdk;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import android.content.ServiceConnection;

import java.util.ArrayList;
import java.util.List;

import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.BindingResult;
import cc.huoyan.sdk.client.model.Capability;
import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.DetectResult;
import cc.huoyan.sdk.client.model.DownloadProgress;
import cc.huoyan.sdk.client.model.Feedback;
import cc.huoyan.sdk.client.model.GoodsResult;
import cc.huoyan.sdk.client.model.GoodsSave;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.model.ItemClazzImagePage;
import cc.huoyan.sdk.client.model.FailedImage;
import cc.huoyan.sdk.client.model.FailedImagesResult;
import cc.huoyan.sdk.client.model.RecognitionDataInfo;
import cc.huoyan.sdk.client.model.ItemClazzList;
import cc.huoyan.sdk.client.model.ItemLearnBatch;
import cc.huoyan.sdk.client.model.LearnResult;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/**
 * 通道层测试替身集：绑定操作记录 / 主线程调度捕获 / 打点记录，
 * 供 {@link SdkCommBindingTest} 与 {@link AidlSdkBridgeTest} 共用；
 * 阶段 D 增可编程桥替身（状态/证书查询透传验证）。
 */
final class SdkChannelFakes {

    static class FakeSdkChannelBridge implements HuoyanSdkClient {
        @Override public cc.huoyan.sdk.client.model.GoodsListResult listGoods(long syncTimestamp, int current, int size) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsDownloadResult downloadGoods(int size) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsDownloadProgress getDownloadGoodsProgress() { return null; }
        @Override public cc.huoyan.sdk.client.model.ImageFlowProgress downloadImagesAndLearnByGoodsId(long[] goodsIds) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsResult getGoods(String goodsId) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsQueryResult queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsCountResult getGoodsCount(String keyword, String name, String plu, int isDeleted) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsBatchResult batchUpdateGoods(String[] goodsIds, GoodsSave changes) { return null; }
        @Override public cc.huoyan.sdk.client.model.GoodsBatchResult batchDeleteGoods(String[] goodsIds) { return null; }
        @Override public SdkImage image(android.graphics.Bitmap bitmap) { return SdkImage.rgb888(bitmap); }
        @Override public cc.huoyan.sdk.client.model.ImageFlowProgress downloadImagesAndLearn() { return null; }
        @Override public cc.huoyan.sdk.client.model.ImageFlowProgress getImageDownloadProgress() { return null; }
        @Override public cc.huoyan.sdk.client.model.ImageFlowProgress getImageLearnProgress() { return null; }

        /** 查询云端开关的测试占位。 */
        @Override
        public cc.huoyan.sdk.client.model.CloudStatus getCloudStatus() { return null; }

        /** 设置云端开关的测试占位。 */
        @Override
        public cc.huoyan.sdk.client.model.CloudStatus setCloudStatus(boolean enabled) { return null; }


        boolean connected;
        int readyCode = HuoyanSdkClient.LOCAL_DISCONNECTED;
        String readyReason;
        StatusSnapshot status;
        CertInfo certInfo;
        int connectCount;

        @Override
        public void connect() {
            connectCount++;
        }

        @Override
        public void close() {
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public BaseResult initialize() {
            return new BaseResult(readyCode, readyReason);
        }

        @Override
        public Capability getCapabilities() {
            return null;
        }

        @Override
        public StatusSnapshot getStatus() {
            return status;
        }

        @Override
        public CertInfo getCertInfo() {
            return certInfo;
        }

        @Override
        public DownloadProgress getDownloadProgress() {
            return null;
        }

        @Override
        public cc.huoyan.sdk.client.model.LicenseModels queryDeviceModels() { return null; }
        public cc.huoyan.sdk.client.model.LicenseModels queryLicenseModels(String activationCode) { return null; }

        public BaseResult activate(String activationCode, String modelCode) {
            return null;
        }

        /** 构造测试桥使用的 RGB 字节载体。 */
        @Override
        public SdkImage image(byte[] rgbFrame, int width, int height) {
            return SdkImage.rgb888(rgbFrame, width, height);
        }

        @Override
        public DetectResult detect(SdkImage image) {
            return null;
        }

        @Override
        public RecognizeResult recognize(SdkImage image, int rotate) {
            return null;
        }

        @Override
        public LearnResult learn(SdkImage image, String clazz) {
            return null;
        }

        @Override
        public ItemLearnBatch batchLearn(SdkImage image, String clazz) {
            return null;
        }

        @Override
        public BaseResult forgetClass(String clazz) {
            return null;
        }

        @Override
        public BaseResult forgetSample(long uid) {
            return null;
        }

        @Override
        public ItemClazzList listClasses(int page, int size) {
            return null;
        }

        @Override
        public BaseResult setBaseUrl(String url) {
            return null;
        }

        @Override
        public ItemClazzImagePage listImages(String clazz, int page, int size) {
            return null;
        }

        @Override
        public FailedImagesResult getFailedImages() {
            return null;
        }

        @Override
        public BaseResult backupRecognitionData() {
            return null;
        }

        @Override
        public RecognitionDataInfo getRecognitionDataInfo() {
            return null;
        }

        @Override
        public BaseResult restoreRecognitionData() {
            return null;
        }

        @Override
    public cc.huoyan.sdk.client.model.PhotoUploadResult uploadPhoto(cc.huoyan.sdk.client.SdkImage image) {
        return null;
    }

    @Override
        public BaseResult submitFeedback(Feedback feedback) {
            return null;
        }

        // ---- 绑定/商品 CRUD（本替身只服务状态查询路径，占位可预设） ----

        /** bindDevice 预设回执。 */
        BindingResult bindDeviceResult;

        /** createGoods 预设回执。 */
        GoodsResult createGoodsResult;

        /** updateGoods 预设回执。 */
        GoodsResult updateGoodsResult;

        /** deleteGoods 预设回执。 */
        BaseResult deleteGoodsResult;

        @Override
        public BindingResult bindDevice(String shopId, String shopName, String shopNo) {
            return bindDeviceResult;
        }

        @Override
        public GoodsResult createGoods(GoodsSave req) {
            return createGoodsResult;
        }

        @Override
        public GoodsResult updateGoods(String goodsId, GoodsSave req) {
            return updateGoodsResult;
        }

        @Override
        public BaseResult deleteGoods(String goodsId) {
            return deleteGoodsResult;
        }

        /** 固定返回本替身的桥工厂（配合 {@link SdkChannelManager#createForTest}）。 */
        static SdkChannelManager.BridgeFactory factory(FakeSdkChannelBridge bridge) {
            return channel -> bridge;
        }
    }

    private SdkChannelFakes() {
    }
}
