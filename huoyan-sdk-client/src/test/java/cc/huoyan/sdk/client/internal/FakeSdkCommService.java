package cc.huoyan.sdk.client.internal;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.CloudStatusParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsBatchResultParcel;
import cc.huoyan.app.aidl.GoodsCountResultParcel;
import cc.huoyan.app.aidl.GoodsDownloadProgressParcel;
import cc.huoyan.app.aidl.GoodsDownloadResultParcel;
import cc.huoyan.app.aidl.GoodsListResultParcel;
import cc.huoyan.app.aidl.GoodsQueryResultParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageFlowProgressParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.ISdkCommService;
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
 * SDK 服务测试替身（同步 SDK 的 38 个方法）：
 * 记录被调方法名（验证桥接层透传），回执可预设，可整体切换为抛
 * {@link RemoteException}（验证断线重连路径）。
 */
final class FakeSdkCommService implements ISdkCommService {

    /** 已触达的方法名集合。 */
    final Set<String> invoked = new HashSet<>();

    /** true=所有方法抛 RemoteException（模拟服务进程死亡）。 */
    boolean throwRemote;

    /** init 预设动作回执（缺省初始化完成）。 */
    BaseResultParcel initResult = readyResult(0, null);

    CapabilityParcel capabilities;
    BaseResultParcel baseResult;
    cc.huoyan.app.aidl.PhotoUploadResultParcel photoResult;
    ImageRequestParcel lastPhoto;
    FeedbackParcel lastFeedback;
    StatusSnapshotParcel status;
    CertInfoParcel certInfo;
    DownloadProgressParcel downloadProgress;
    DetectResultParcel detectResult;
    RecognizeResultParcel recognizeResult;
    LearnResultParcel learnResult;
    ItemLearnBatchParcel batchLearnResult;
    ItemClazzListParcel itemClazzList;
    ItemClazzImagePageParcel itemClazzImages;
    int lastPage;
    int lastSize;
    String lastClazz;
    /** bindDevice 预设回执。 */
    BindingResultParcel bindDeviceResult;
    /** createGoods/updateGoods 预设回执。 */
    GoodsResultParcel goodsResult;
    /** deleteGoods 预设回执。 */
    BaseResultParcel deleteGoodsResult;
    /** 最近一次 bindDevice 请求载体（透传断言）。 */
    BindDeviceParcel lastBindDevice;
    /** 最近一次 createGoods 请求载体（透传断言）。 */
    GoodsSaveParcel lastCreateGoods;
    /** 最近一次 updateGoods 的 goodsId 与载体（透传断言）。 */
    String lastUpdateGoodsId;
    GoodsSaveParcel lastUpdateGoods;
    /** 最近一次 deleteGoods 的 goodsId（透传断言）。 */
    String lastDeleteGoodsId;

    private void touch(String method) throws RemoteException {
        invoked.add(method);
        if (throwRemote) {
            throw new RemoteException("fake remote death: " + method);
        }
    }

    @Override
    public CapabilityParcel getCapabilities() throws RemoteException {
        touch("getCapabilities");
        return capabilities;
    }

    @Override
    public StatusSnapshotParcel getStatus() throws RemoteException {
        touch("getStatus");
        return status;
    }

    @Override
    public CertInfoParcel getCertInfo() throws RemoteException {
        touch("getCertInfo");
        return certInfo;
    }

    @Override
    public DownloadProgressParcel getDownloadProgress() throws RemoteException {
        touch("getDownloadProgress");
        return downloadProgress;
    }

    @Override
    public cc.huoyan.app.aidl.LicenseModelsParcel queryDeviceModels() throws RemoteException {
        touch("queryDeviceModels"); modelQueries++; return licenseModels;
    }

    /** 按激活码查询支持模型，适用于正常激活。 */
    @Override
    public cc.huoyan.app.aidl.LicenseModelsParcel queryLicenseModels(String activationCode) throws RemoteException {
        touch("queryLicenseModels"); modelQueries++; return licenseModels;
    }
    /** 模型接口测试记录。 */
    int modelQueries;
    String lastActivationCode, lastModelCode;
    /** 可配置模型查询回执。 */
    cc.huoyan.app.aidl.LicenseModelsParcel licenseModels;

    public BaseResultParcel activate(String activationCode, String modelCode) throws RemoteException {
        touch("activate"); lastActivationCode = activationCode; lastModelCode = modelCode;
        return baseResult;
    }

    @Override
    public BaseResultParcel setBaseUrl(String url) throws RemoteException {
        touch("setBaseUrl");
        return baseResult;
    }

    @Override
    public DetectResultParcel detect(ImageRequestParcel image) throws RemoteException {
        touch("detect");
        return detectResult;
    }

    @Override
    public RecognizeResultParcel recognize(ImageRequestParcel image, int rotate) throws RemoteException {
        touch("recognize");
        return recognizeResult;
    }

    @Override
    public LearnResultParcel itemLearn(ImageRequestParcel image, String clazz) throws RemoteException {
        touch("itemLearn");
        return learnResult;
    }

    @Override
    public ItemLearnBatchParcel batchLearn(ImageRequestParcel image, String clazz) throws RemoteException {
        touch("batchLearn");
        return batchLearnResult;
    }

    @Override
    public BaseResultParcel itemForget(String clazz) throws RemoteException {
        touch("itemForget");
        return baseResult;
    }

    @Override
    public BaseResultParcel itemForgetByUid(long uid) throws RemoteException {
        touch("itemForgetByUid");
        return baseResult;
    }

    @Override
    public ItemClazzListParcel getItemClazzList(int page, int size) throws RemoteException {
        touch("getItemClazzList");
        lastPage = page;
        lastSize = size;
        return itemClazzList;
    }

    @Override
    public ItemClazzImagePageParcel getItemClazzImages(String clazz, int page, int size) throws RemoteException {
        touch("getItemClazzImages");
        lastClazz = clazz;
        lastPage = page;
        lastSize = size;
        return itemClazzImages;
    }

    @Override
    public FailedImagesResultParcel getFailedImages() throws RemoteException {
        touch("getFailedImages");
        return null;
    }

    @Override
    public BaseResultParcel backupRecognitionData() throws RemoteException {
        touch("backupRecognitionData");
        return null;
    }

    @Override
    public RecognitionDataInfoParcel getRecognitionDataInfo() throws RemoteException {
        touch("getRecognitionDataInfo");
        return null;
    }

    @Override
    public BaseResultParcel restoreRecognitionData() throws RemoteException {
        touch("restoreRecognitionData");
        return null;
    }

    @Override
    public cc.huoyan.app.aidl.PhotoUploadResultParcel uploadPhoto(cc.huoyan.app.aidl.ImageRequestParcel image) throws android.os.RemoteException {
        touch("uploadPhoto");
        lastPhoto = image;
        return photoResult;
    }

    @Override
    public BaseResultParcel submitFeedback(FeedbackParcel feedback) throws RemoteException {
        touch("submitFeedback");
        lastFeedback = feedback;
        return baseResult;
    }

    /** 最近一次初始化收到的宿主版本。 */
    String lastAppVersion;

    @Override
    public BaseResultParcel init(String appVersion) throws RemoteException {
        lastAppVersion = appVersion;
        touch("init");
        return initResult;
    }

    // ---- 同步 SDK 契约尾插方法（桥仅暴露 bindDevice/商品 CRUD 四方法，
    //      其余为接口编译完备性占位，本阶段无业务路径） ----

    @Override
    public BindingResultParcel bindDevice(BindDeviceParcel req) throws RemoteException {
        touch("bindDevice");
        lastBindDevice = req;
        return bindDeviceResult;
    }

    @Override
    public ImageFlowProgressParcel downloadImagesAndLearn() throws RemoteException {
        touch("downloadImagesAndLearn");
        return null;
    }

    @Override
    public ImageFlowProgressParcel getImageDownloadProgress() throws RemoteException {
        touch("getImageDownloadProgress");
        return null;
    }

    @Override
    public ImageFlowProgressParcel getImageLearnProgress() throws RemoteException {
        touch("getImageLearnProgress");
        return null;
    }

    @Override
    public GoodsListResultParcel listGoods(long syncTimestamp, int current, int size) throws RemoteException {
        touch("listGoods");
        return null;
    }

    @Override
    public GoodsResultParcel createGoods(GoodsSaveParcel req) throws RemoteException {
        touch("createGoods");
        lastCreateGoods = req;
        return goodsResult;
    }

    @Override
    public GoodsResultParcel updateGoods(String goodsId, GoodsSaveParcel req) throws RemoteException {
        touch("updateGoods");
        lastUpdateGoodsId = goodsId;
        lastUpdateGoods = req;
        return goodsResult;
    }

    @Override
    public BaseResultParcel deleteGoods(String goodsId) throws RemoteException {
        touch("deleteGoods");
        lastDeleteGoodsId = goodsId;
        return deleteGoodsResult;
    }

    CloudStatusParcel cloudStatus = new CloudStatusParcel(0, null, false);
    boolean lastCloudEnabled;

    @Override
    public CloudStatusParcel setCloudStatus(boolean enabled) throws RemoteException {
        touch("setCloudStatus");
        lastCloudEnabled = enabled;
        return cloudStatus;
    }

    @Override
    public CloudStatusParcel getCloudStatus() throws RemoteException {
        touch("getCloudStatus");
        return cloudStatus;
    }

    @Override
    public GoodsDownloadResultParcel downloadGoods(int size) throws RemoteException {
        touch("downloadGoods");
        return null;
    }

    @Override
    public GoodsDownloadProgressParcel getDownloadGoodsProgress() throws RemoteException {
        touch("getDownloadGoodsProgress");
        return null;
    }

    @Override
    public ImageFlowProgressParcel downloadImagesAndLearnByGoodsId(long[] goodsIds) throws RemoteException {
        touch("downloadImagesAndLearnByGoodsId");
        return null;
    }

    @Override
    public GoodsResultParcel getGoods(String goodsId) throws RemoteException {
        touch("getGoods");
        return null;
    }

    @Override
    public GoodsQueryResultParcel queryGoods(String keyword, String name, String plu,
            int isDeleted, int current, int size, String sortBy, String sortOrder) throws RemoteException {
        touch("queryGoods");
        return null;
    }

    @Override
    public GoodsCountResultParcel getGoodsCount(String keyword, String name, String plu,
            int isDeleted) throws RemoteException {
        touch("getGoodsCount");
        return null;
    }

    @Override
    public GoodsBatchResultParcel batchUpdateGoods(String[] goodsIds, GoodsSaveParcel changes) throws RemoteException {
        touch("batchUpdateGoods");
        return null;
    }

    @Override
    public GoodsBatchResultParcel batchDeleteGoods(String[] goodsIds) throws RemoteException {
        touch("batchDeleteGoods");
        return null;
    }

    /** 构造初始化动作回执（code/reason）。 */
    static BaseResultParcel readyResult(int code, String reason) {
        return new BaseResultParcel(code, reason);
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
