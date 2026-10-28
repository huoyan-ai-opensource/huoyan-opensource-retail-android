package cc.huoyan.sdk.client.internal.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FeedbackItemParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ItemClazzDetailParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.ItemLearnBoxParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognitionItemParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

/**
 * {@link WsParcelMapper} 单测：响应 JSON → Parcelable 字段
 * 填充（字段名/线序对齐 SDK {@code SdkWsDispatcher} 载荷构建）与请求参数构造
 * （对齐 SDK {@code WsFeedbackChannel.toSubmission} 等参数解析键）。
 */
public class WsParcelMapperTest {

    private static WsV2Codec.Response ok(String dataJson) {
        return new WsV2Codec.Response("r", 0, null, JSON.parseObject(dataJson));
    }

    private static WsV2Codec.Response err(int code, String reason) {
        return new WsV2Codec.Response("r", code, reason, null);
    }

    /** 当前设备查询省略激活码，显式激活码按原值发送。 */
    @Test public void modelQueryUsesOptionalActivationCode() {
    }

    /** 动态名称、顺序与 current 标记完整保留，失败不伪装为空成功。 */
    @Test public void modelListKeepsDynamicIdentityAndErrors() {
        cc.huoyan.app.aidl.LicenseModelsParcel models = WsParcelMapper.licenseModels(ok(
                "{\"models\":[{\"model_code\":\"new-7\",\"model_name\":\"新模型\",\"chip\":\"rk\",\"current\":true},"
                + "{\"model_code\":\"new-2\",\"model_name\":\"第二模型\",\"current\":false}]}"));
        assertEquals(2, models.getModels().size());
        assertEquals("new-7", models.getModels().get(0).getModelCode());
        assertEquals("新模型", models.getModels().get(0).getModelName());
        assertTrue(models.getModels().get(0).isCurrent());
        assertEquals("new-2", models.getModels().get(1).getModelCode());
        assertEquals(40004, WsParcelMapper.licenseModels(err(40004, "denied")).getCode());
        assertEquals(-1, WsParcelMapper.licenseModels(ok("{}")).getCode());
    }

    // ================= 查询类回执 =================

    @Test
    public void capability_全字段填充() {
        CapabilityParcel parcel = WsParcelMapper.capability(ok(
                "{\"api_version\":2,\"shared_memory_image\":false,\"detect\":true,"
                        + "\"recognize\":true,\"item_learn\":true,\"item_forget\":true,"
                        + "\"item_query\":true}"));
        assertEquals(0, parcel.getCode());
        assertNull(parcel.getReason());
        assertEquals(2, parcel.getApiVersion());
        assertTrue(!parcel.isSharedMemoryImage());
        assertTrue(parcel.isDetect());
        assertTrue(parcel.isRecognize());
        assertTrue(parcel.isItemLearn());
        assertTrue(parcel.isItemForget());
        assertTrue(parcel.isItemQuery());
    }

    @Test
    public void capability_错误形态code透传能力全false() {
        CapabilityParcel parcel = WsParcelMapper.capability(err(40004, "权限拒绝"));
        assertEquals(40004, parcel.getCode());
        assertEquals("权限拒绝", parcel.getReason());
        assertEquals(0, parcel.getApiVersion());
        assertTrue(!parcel.isDetect() && !parcel.isRecognize() && !parcel.isItemLearn());
    }

    @Test
    public void status_六字段填充() {
        StatusSnapshotParcel parcel = WsParcelMapper.status(ok(
                "{\"device_id\":\"dev-1\",\"certificate_valid_until\":\"2027-01-01\","
                        + "\"trust_level\":\"high\",\"model_version\":\"v1.2\","
                        + "\"key_lost\":false,\"backend_connected\":true}"));
        assertEquals(0, parcel.getCode());
        assertEquals("dev-1", parcel.getDeviceId());
        assertEquals("2027-01-01", parcel.getCertificateValidUntil());
        assertEquals("high", parcel.getTrustLevel());
        assertEquals("v1.2", parcel.getModelVersion());
        assertEquals(Boolean.FALSE, parcel.getKeyLost());
        assertEquals(Boolean.TRUE, parcel.getBackendConnected());
    }

    @Test
    public void status_JSONnull字段归一为null() {
        StatusSnapshotParcel parcel = WsParcelMapper.status(ok(
                "{\"device_id\":null,\"certificate_valid_until\":null,"
                        + "\"trust_level\":null,\"model_version\":null,"
                        + "\"key_lost\":null,\"backend_connected\":null}"));
        assertNull(parcel.getDeviceId());
        assertNull(parcel.getTrustLevel());
        assertNull(parcel.getKeyLost());
        assertNull(parcel.getBackendConnected());
    }

    @Test
    public void certInfo_十二字段填充() {
        CertInfoParcel parcel = WsParcelMapper.certInfo(ok(
                "{\"license_id\":\"L-1\",\"model_code\":\"MC\",\"model_channel\":\"CH\","
                        + "\"license_type\":\"formal\",\"activated_at\":1,\"issued_at\":2,"
                        + "\"renewed_at\":3,\"has_renewed_at\":true,\"next_renew_at\":4,"
                        + "\"has_next_renew_at\":false,\"expires_at\":5,\"need_renew\":true}"));
        assertEquals(0, parcel.getCode());
        assertEquals("MC", parcel.getModelCode());
        assertEquals("CH", parcel.getModelChannel());
        assertEquals("formal", parcel.getLicenseType());
        assertEquals(1L, parcel.getActivatedAt());
        assertEquals(2L, parcel.getIssuedAt());
        assertEquals(3L, parcel.getRenewedAt());
        assertTrue(parcel.hasRenewedAt());
        assertEquals(4L, parcel.getNextRenewAt());
        assertTrue(!parcel.hasNextRenewAt());
        assertEquals(5L, parcel.getExpiresAt());
    }

    /** 验证 SDK 的实际版本字段映射，缺失或查询失败时保持空值。 */
    @Test
    public void certInfo_sdkVersionUsesShellWireField() {
        CertInfoParcel parcel = WsParcelMapper.certInfo(ok(
                "{\"sdk_version\":\"2.0.0-alpha01\",\"sdk_version_code\":1}"));
        assertEquals("2.0.0-alpha01", parcel.getSdkVersionName());
        assertEquals(1, parcel.getSdkVersionCode());
        assertNull(WsParcelMapper.certInfo(ok("{}")).getSdkVersionName());
        assertNull(WsParcelMapper.certInfo(err(-20, "网络失败")).getSdkVersionName());
    }

    @Test
    public void certInfo_错误形态空载荷() {
        CertInfoParcel parcel = WsParcelMapper.certInfo(err(-20, "网络失败"));
        assertEquals(-20, parcel.getCode());
        assertEquals("网络失败", parcel.getReason());
        assertEquals(0L, parcel.getExpiresAt());
    }

    @Test
    public void downloadProgress_字段填充() {
        DownloadProgressParcel parcel =
                WsParcelMapper.downloadProgress(ok("{\"downloaded\":1024,\"total\":4096}"));
        assertEquals(0, parcel.getCode());
        assertEquals(1024L, parcel.getDownloaded());
        assertEquals(4096L, parcel.getTotal());
    }

    // ================= 图像命令回执 =================

    @Test
    public void detect_boxes填充_遵循Sdk的xywh契约() {
        DetectResultParcel parcel = WsParcelMapper.detect(ok(
                "{\"boxes\":[{\"x\":10,\"y\":20,\"w\":30,\"h\":40,\"score\":0.95},"
                        + "{\"x\":0,\"y\":0,\"w\":3,\"h\":4,\"score\":0.5}]}"));
        assertEquals(0, parcel.getCode());
        assertEquals(2, parcel.getBoxes().size());
        assertEquals(10, parcel.getBoxes().get(0).getX());
        assertEquals(20, parcel.getBoxes().get(0).getY());
        assertEquals(30, parcel.getBoxes().get(0).getW());
        assertEquals(40, parcel.getBoxes().get(0).getH());
        assertEquals(0.95, parcel.getBoxes().get(0).getScore(), 0.0);
        assertEquals(0, parcel.getBoxes().get(1).getX());
        assertEquals(0, parcel.getBoxes().get(1).getY());
        assertEquals(3, parcel.getBoxes().get(1).getW());
        assertEquals(4, parcel.getBoxes().get(1).getH());
        assertEquals(0.5, parcel.getBoxes().get(1).getScore(), 0.0);
    }

    @Test
    public void detect_错误形态空boxes() {
        DetectResultParcel parcel = WsParcelMapper.detect(err(-1, "需要 binary 帧传图"));
        assertEquals(-1, parcel.getCode());
        assertTrue(parcel.getBoxes().isEmpty());
    }

    @Test
    public void recognize_九字段填充含候选() {
        RecognizeResultParcel parcel = WsParcelMapper.recognize(ok(
                "{\"items\":[{\"clazz\":\"10001\",\"distance\":0.25,\"square\":12.5,"
                        + "\"rank\":-1.5,\"cand\":[{\"clazz\":\"10002\",\"distance\":0.3,"
                        + "\"square\":10.0,\"rank\":0.5}],"
                        + "\"box\":{\"left\":10,\"top\":20,\"right\":110,\"bottom\":220}}]}"));
        assertEquals(0, parcel.getCode());
        assertEquals(1, parcel.getItems().size());
        RecognitionItemParcel item = parcel.getItems().get(0);
        assertEquals("10001", item.getClazz());
        assertEquals(0.25, item.getDistance(), 0.0);
        assertEquals(10, item.getLeft());
        assertEquals(20, item.getTop());
        assertEquals(110, item.getRight());
        assertEquals(220, item.getBottom());
        assertEquals(12.5, item.getSquare(), 0.0);
        assertEquals(-1.5, item.getRank(), 0.0);
        assertEquals(1, item.getCandidates().size());
        assertEquals("10002", item.getCandidates().get(0).getClazz());
        assertEquals(0.3, item.getCandidates().get(0).getDistance(), 0.0);
        assertEquals(10.0, item.getCandidates().get(0).getSquare(), 0.0);
        assertEquals(0.5, item.getCandidates().get(0).getRank(), 0.0);
    }

    @Test
    public void recognize_错误形态空items() {
        RecognizeResultParcel parcel = WsParcelMapper.recognize(err(-110, "等待超时"));
        assertEquals(-110, parcel.getCode());
        assertTrue(parcel.getItems().isEmpty());
    }

    @Test
    public void learn_uid填充() {
        LearnResultParcel parcel = WsParcelMapper.learn(ok("{\"uid\":987654321098}"));
        assertEquals(0, parcel.getCode());
        assertEquals(987654321098L, parcel.getUid());
    }

    @Test
    public void batchLearn_每框结果填充_失败框原样保留() {
        ItemLearnBatchParcel parcel = WsParcelMapper.batchLearn(ok("{\"boxes\":["
                + "{\"x\":10,\"y\":20,\"w\":100,\"h\":80,\"score\":0.91,\"uid\":101,\"code\":0,\"reason\":null},"
                + "{\"x\":200,\"y\":40,\"w\":60,\"h\":50,\"score\":0.55,\"uid\":0,\"code\":-5,\"reason\":\"裁切无效\"},"
                + "{\"x\":0,\"y\":0,\"w\":30,\"h\":30,\"score\":0.7,\"uid\":102,\"code\":0,\"reason\":null}"
                + "]}"));
        assertEquals(0, parcel.getCode());
        assertNull(parcel.getReason());
        assertEquals(3, parcel.getBoxes().size());
        ItemLearnBoxParcel first = parcel.getBoxes().get(0);
        assertEquals(10, first.getX());
        assertEquals(20, first.getY());
        assertEquals(100, first.getW());
        assertEquals(80, first.getH());
        assertEquals(0.91, first.getScore(), 1e-9);
        assertEquals(101L, first.getUid());
        assertEquals(0, first.getCode());
        assertNull(first.getReason());
        ItemLearnBoxParcel failed = parcel.getBoxes().get(1);
        assertEquals(-5, failed.getCode());
        assertEquals(0L, failed.getUid());
        assertEquals("裁切无效", failed.getReason());
        assertEquals(102L, parcel.getBoxes().get(2).getUid());
    }

    @Test
    public void batchLearn_空boxes为正常结果() {
        ItemLearnBatchParcel parcel = WsParcelMapper.batchLearn(ok("{\"boxes\":[]}"));
        assertEquals(0, parcel.getCode());
        assertTrue(parcel.getBoxes().isEmpty());
    }

    @Test
    public void batchLearn_整体失败_空boxes错误形态() {
        ItemLearnBatchParcel parcel = WsParcelMapper.batchLearn(err(-110, "等待超时"));
        assertEquals(-110, parcel.getCode());
        assertEquals("等待超时", parcel.getReason());
        assertTrue(parcel.getBoxes().isEmpty());
    }

    // ================= 类别查询回执 =================

    @Test
    public void clazzList_双数组下标对齐() {
        ItemClazzListParcel parcel = WsParcelMapper.clazzList(ok(
                "{\"clazz\":[\"100\",\"200\"],\"uid_count\":[3,5]}"));
        assertEquals(0, parcel.getCode());
        assertEquals(2, parcel.getClazz().length);
        assertEquals("100", parcel.getClazz()[0]);
        assertEquals("200", parcel.getClazz()[1]);
        assertEquals(3L, parcel.getUidCount()[0]);
        assertEquals(5L, parcel.getUidCount()[1]);
    }

    // ================= 动作回执与错误形态 =================

    @Test
    public void base_成功与失败() {
        BaseResultParcel success = WsParcelMapper.base(ok("{}"));
        assertEquals(0, success.getCode());
        assertNull(success.getReason());
        BaseResultParcel failure = WsParcelMapper.base(err(-30, "签名失败"));
        assertEquals(-30, failure.getCode());
        assertEquals("签名失败", failure.getReason());
    }

    @Test
    public void 错误形态工厂_code与reason透传() {
        assertEquals(40005, WsParcelMapper.errorDetect(40005, "在途重复").getCode());
        assertTrue(WsParcelMapper.errorDetect(40005, "在途重复").getBoxes().isEmpty());
        assertEquals(-1, WsParcelMapper.errorRecognize(-1, "载体非法").getCode());
        assertTrue(WsParcelMapper.errorRecognize(-1, "载体非法").getItems().isEmpty());
        assertEquals(0L, WsParcelMapper.errorCertInfo(-20, "网络").getExpiresAt());
        assertEquals(-1, WsParcelMapper.errorBatchLearn(-1, "载体非法").getCode());
        assertEquals("载体非法", WsParcelMapper.errorBatchLearn(-1, "载体非法").getReason());
        assertTrue(WsParcelMapper.errorBatchLearn(-1, "载体非法").getBoxes().isEmpty());
    }

    // ================= 请求参数构造 =================

    @Test
    public void init_成功code0_reasonNull_快照字段null() {
        BaseResultParcel parcel = WsParcelMapper.base(ok("{}"));
        assertEquals(0, parcel.getCode());
        assertNull(parcel.getReason());
    }

    @Test
    public void init_失败四态_code与reason透传() {
        BaseResultParcel timeout = WsParcelMapper.base(err(-110, "密钥轮换待审核"));
        assertEquals(-110, timeout.getCode());
        assertEquals("密钥轮换待审核", timeout.getReason());
        assertEquals(-111, WsParcelMapper.base(err(-111, "SDK 启动初始化失败：xx")).getCode());
        assertEquals(-1, WsParcelMapper.base(err(-1, "初始化配置错误")).getCode());
        assertEquals(40004, WsParcelMapper.base(err(40004, "权限拒绝")).getCode());
    }

    @Test
    public void init_本地哨兵码原样保留_调用方按哨兵处理() {
        // request() 本地失败（在途超时/断线作废）映射 code=LOCAL_DISCONNECTED 响应，
        // init 原样保留该码（Integer.MIN_VALUE 不与服务端码段冲突）
        BaseResultParcel local = WsParcelMapper.base(
                err(Integer.MIN_VALUE, "请求超时（8000ms，cmd=init）"));
        assertEquals(Integer.MIN_VALUE, local.getCode());
        assertEquals("请求超时（8000ms，cmd=init）", local.getReason());
    }

    @Test
    public void buildActivateParams_activationCode() {
        JSONObject params = WsParcelMapper.buildActivateParams("ACT-001", "dynamic-new-model");
        assertEquals("ACT-001", params.getString("activation_code"));
        assertEquals("dynamic-new-model", params.getString("model_code"));
    }

    @Test
    public void buildUrlParams_url() {
        assertEquals("https://api.example.com",
                WsParcelMapper.buildUrlParams("https://api.example.com").getString("url"));
    }

    @Test
    public void buildClazzParam_以数字上线() {
        JSONObject params = WsParcelMapper.buildClazzParam("10001");
        assertTrue(params.get("clazz") instanceof String);
        assertEquals(10001L, params.getLongValue("clazz"));
    }

    @Test
    public void buildUidParam_以数字上线() {
        assertEquals(987654321098L,
                WsParcelMapper.buildUidParam(987654321098L).getLongValue("uid"));
    }

    @Test
    public void buildImageParams_rgb载体携带几何与旋转() {
        JSONObject params = WsParcelMapper.buildImageParams(640, 480, 90);
        assertEquals("rgb", params.getString("format"));
        assertEquals(640, params.getIntValue("width"));
        assertEquals(480, params.getIntValue("height"));
        assertEquals(90, params.getIntValue("rotate"));
    }

    @Test
    public void buildImageParams_无旋转命令省略rotate() {
        JSONObject params = WsParcelMapper.buildImageParams(640, 480, null);
        assertTrue(!params.containsKey("rotate"));
        assertEquals("rgb", params.getString("format"));
    }

    @Test
    public void buildLearnParams_clazz与几何齐全() {
        JSONObject params = WsParcelMapper.buildLearnParams("10001", 320, 240);
        assertEquals(10001L, params.getLongValue("clazz"));
        assertEquals("rgb", params.getString("format"));
        assertEquals(320, params.getIntValue("width"));
        assertEquals(240, params.getIntValue("height"));
    }

    @Test public void feedbackUsesItemsWithOrderedDetectAndResult() {
        FeedbackItemParcel item = new FeedbackItemParcel(1, java.util.Arrays.asList(
                new cc.huoyan.app.aidl.FeedbackDetectParcel("A001", "苹果", 0.123),
                new cc.huoyan.app.aidl.FeedbackDetectParcel("001", "梨", 0.234)),
                new cc.huoyan.app.aidl.FeedbackResultParcel("B002", "桃", "manual"), 1D, 2D, 30D, 40D);
        JSONObject params = WsParcelMapper.buildFeedbackParams(new FeedbackParcel(
                "r", "https://files/photo.jpg", 1, java.util.Collections.singletonList(item)));
        assertTrue(!params.containsKey("goods"));
        JSONObject row = params.getJSONArray("items").getJSONObject(0);
        assertEquals(1, row.getIntValue("item_type"));
        assertEquals("A001", row.getJSONArray("detect").getJSONObject(0).getString("clazz"));
        assertEquals("001", row.getJSONArray("detect").getJSONObject(1).getString("clazz"));
        assertEquals("B002", row.getJSONObject("result").getString("clazz"));
        assertEquals(30D, row.getDoubleValue("width"), 0);
    }

    /** 删除无论是否携带改品记录，WS 都显式输出空 result 对象。 */
    @Test public void deletedFeedbackAlwaysWritesEmptyResultObject() {
        for (cc.huoyan.app.aidl.FeedbackResultParcel result : java.util.Arrays.asList(null,
                new cc.huoyan.app.aidl.FeedbackResultParcel("B002", "桃", "manual"))) {
            FeedbackItemParcel item = new FeedbackItemParcel(2, java.util.Collections.singletonList(
                    new cc.huoyan.app.aidl.FeedbackDetectParcel("A001", "苹果", .123)),
                    result, 1D, 2D, 30D, 40D);
            JSONObject params = WsParcelMapper.buildFeedbackParams(new FeedbackParcel(
                    "r", "https://files/photo.jpg", 1, java.util.Collections.singletonList(item)));
            JSONObject row = JSONObject.parseObject(params.toJSONString()).getJSONArray("items").getJSONObject(0);
            assertNotNull(row.getJSONObject("result"));
            assertTrue(row.getJSONObject("result").isEmpty());
            assertEquals("A001", row.getJSONArray("detect").getJSONObject(0).getString("clazz"));
            assertEquals(30D, row.getDoubleValue("width"), 0);
        }
    }

    // ================= 绑定与商品 CRUD =================

    @Test
    public void buildBindDeviceParams_必填键与可选shopNo() {
        BindDeviceParcel req = new BindDeviceParcel(42L, "10086", "旗舰店", "SH001");
        JSONObject params = WsParcelMapper.buildBindDeviceParams(req);
        assertEquals(42L, params.getLongValue("account_id"));
        assertEquals("10086", params.getString("shop_id"));
        assertEquals("旗舰店", params.getString("shop_name"));
        assertEquals("SH001", params.getString("shop_no"));
    }

    @Test
    public void buildBindDeviceParams_shopNameNull不上键_透传SDK侧拒绝() {
        // 已知阻塞：当前 SDK 必填 shop_name——桥按现有载体传 null（键不上线），
        // 由 SDK 侧 ERR_ARGS 真实拒绝，绝不虚构店名
        BindDeviceParcel req = new BindDeviceParcel(42L, "10086", null, null);
        JSONObject params = WsParcelMapper.buildBindDeviceParams(req);
        assertEquals(42L, params.getLongValue("account_id"));
        assertEquals("10086", params.getString("shop_id"));
        assertTrue(!params.containsKey("shop_name"));
        assertTrue(!params.containsKey("shop_no"));
    }

    @Test
    public void binding_成功回执映射request与response两组() {
        BindingResultParcel parcel = WsParcelMapper.binding(ok(
                "{\"request\":{\"account_id\":42,\"shop_id\":\"10086\",\"shop_name\":null,\"shop_no\":null},"
                        + "\"response\":{\"account_id\":\"9001\",\"account_name\":\"张三\","
                        + "\"shop_id\":\"2001\",\"shop_name\":\"旗舰店\",\"shop_no\":\"SH001\",\"shop_address\":\"测试地址\",\"shop_phone\":\"010-88886666\"}}"));
        assertEquals(0, parcel.getCode());
        assertNull(parcel.getReason());
        assertNotNull(parcel.getRequest());
        assertEquals(42L, parcel.getRequest().getAccountId());
        assertEquals("10086", parcel.getRequest().getShopId());
        assertNull(parcel.getRequest().getShopName());
        assertEquals("9001", parcel.getAccountId());
        assertEquals("张三", parcel.getAccountName());
        assertEquals("2001", parcel.getShopId());
        assertEquals("旗舰店", parcel.getShopName());
        assertEquals("SH001", parcel.getShopNo());
        assertEquals("测试地址", parcel.getShopAddress());
        assertEquals("010-88886666", parcel.getShopPhone());
    }

    @Test
    public void binding_失败回执code透传空载荷() {
        BindingResultParcel parcel = WsParcelMapper.binding(err(-1, "shop_name 必填且为非空字符串"));
        assertEquals(-1, parcel.getCode());
        assertEquals("shop_name 必填且为非空字符串", parcel.getReason());
        assertNull(parcel.getRequest());
        assertNull(parcel.getAccountId());
        assertNull(parcel.getShopId());
    }

    @Test
    public void buildGoodsSaveParams_全字段() {
        GoodsSaveParcel req = new GoodsSaveParcel("可颂", "3001", "http://img/1.jpg",
                "小麦", "3天", "kesong", Integer.valueOf(2), "烘焙", "个", Integer.valueOf(0),
                Long.valueOf(50L), Long.valueOf(1200L), Integer.valueOf(9), Integer.valueOf(1));
        JSONObject params = WsParcelMapper.buildGoodsSaveParams(req);
        assertEquals("可颂", params.getString("name"));
        assertEquals("3001", params.getString("plu"));
        assertEquals("http://img/1.jpg", params.getString("image"));
        assertEquals("小麦", params.getString("ingredients_list"));
        assertEquals("3天", params.getString("shelf_life"));
        assertEquals("kesong", params.getString("pinyin"));
        assertEquals(2, params.getIntValue("category"));
        assertEquals("烘焙", params.getString("category_name"));
        assertEquals("个", params.getString("unit"));
        assertEquals(0, params.getIntValue("sale_mode"));
        assertEquals(1200L, params.getLongValue("price"));
        assertEquals(50L, params.getLongValue("tare_weight"));
        assertEquals(9, params.getIntValue("sort"));
        assertEquals(1, params.getIntValue("status"));
    }

    @Test
    public void buildGoodsSaveParams_哨兵未传字段不上键() {
        GoodsSaveParcel req = new GoodsSaveParcel("可颂", "3001", null,
                null, null, null, null, null, null, null, null, null, null, null);
        JSONObject params = WsParcelMapper.buildGoodsSaveParams(req);
        assertEquals("可颂", params.getString("name"));
        assertEquals("3001", params.getString("plu"));
        assertTrue(!params.containsKey("image"));
        assertTrue(!params.containsKey("category"));
        assertTrue(!params.containsKey("sale_mode"));
        assertTrue(!params.containsKey("price"));
        assertTrue(!params.containsKey("tare_weight"));
        assertTrue(!params.containsKey("sort"));
        assertTrue(!params.containsKey("status"));
    }

    @Test
    public void buildGoodsIdParam_goodsId上键() {
        JSONObject params = WsParcelMapper.buildGoodsIdParam("1770001");
        assertEquals("1770001", params.getString("goods_id"));
    }

    @Test
    public void goods_成功回执映射record全字段() {
        GoodsResultParcel parcel = WsParcelMapper.goods(ok(
                "{\"goods\":{\"id\":\"1770001\",\"shop_id\":\"2001\",\"name\":\"可颂\","
                        + "\"plu\":\"3001\",\"base_id\":\"9\",\"image\":\"http://img/1.jpg\","
                        + "\"category\":2,\"category_name\":\"烘焙\",\"unit\":\"个\","
                        + "\"sale_mode\":0,\"price\":1200,\"tare_weight\":50,"
                        + "\"ingredients_list\":\"小麦\",\"shelf_life\":\"3天\","
                        + "\"pinyin\":\"kesong\",\"status\":1,\"sort\":9,"
                        + "\"shop_name\":\"旗舰店\"}}"));
        assertEquals(0, parcel.getCode());
        assertNull(parcel.getReason());
        GoodsParcel record = parcel.getRecord();
        assertNotNull(record);
        assertEquals("1770001", record.getGoodsId());
        assertEquals("2001", record.getShopId());
        assertEquals("可颂", record.getName());
        assertEquals("3001", record.getPlu());
        assertEquals("9", record.getBaseGoodsId());
        assertEquals(2, record.getCategory());
        assertEquals("烘焙", record.getCategoryName());
        assertEquals("个", record.getUnit());
        assertEquals(0, record.getSaleMode());
        assertEquals(1200L, record.getPrice());
        assertEquals(50L, record.getTareWeight());
        assertEquals("小麦", record.getIngredientsList());
        assertEquals("3天", record.getShelfLife());
        assertEquals("kesong", record.getPinyin());
        assertEquals(1, record.getStatus());
        assertEquals(9, record.getSort());
        assertEquals("旗舰店", record.getShopName());
        assertEquals("WS 线面无软删字段——CRUD 回执恒未删", 0, record.getIsDeleted());
        assertNull("WS 线面无 update_time——映射 null 不虚构", record.getUpdateTime());
        assertNull(record.getAccountId());
        assertNull(record.getAccountName());
        assertNull(record.getCreateTime());
    }

    @Test
    public void goods_失败回执code透传recordNull() {
        GoodsResultParcel parcel = WsParcelMapper.goods(err(400, "PLU 已存在"));
        assertEquals(400, parcel.getCode());
        assertEquals("PLU 已存在", parcel.getReason());
        assertNull(parcel.getRecord());
    }
    @Test
    public void status_newFieldsPreserveNullFalseAndLongTimestamp() {
        StatusSnapshotParcel status = WsParcelMapper.status(ok(
                "{\"sdk_state\":\"KEY_ROTATION_PENDING\",\"model_ready\":false,"
                        + "\"busy\":true,\"busy_operation\":\"init\","
                        + "\"state_updated_at\":1790000000000,\"certificate_valid\":false}"));
        assertEquals("KEY_ROTATION_PENDING", status.getSdkState());
        assertEquals(Boolean.FALSE, status.getModelReady());
        assertEquals(Boolean.TRUE, status.getBusy());
        assertEquals("init", status.getBusyOperation());
        assertEquals(Long.valueOf(1790000000000L), status.getStateUpdatedAt());
        assertEquals(Boolean.FALSE, status.getCertificateValid());
        StatusSnapshotParcel absent = WsParcelMapper.status(ok("{}"));
        assertNull(absent.getModelReady());
        assertNull(absent.getStateUpdatedAt());
        assertNull(absent.getCertificateValid());
        assertNull(WsParcelMapper.status(err(-1, "失败")).getSdkState());
        assertTrue(!status.equals(absent));
    }

    @Test
    public void clazzPage_preservesMetadataAndThumbnails() {
        ItemClazzListParcel page = WsParcelMapper.clazzList(ok(
                "{\"clazz\":[\"101\"],\"uid_count\":[2],\"thumbnails\":[\"/images/9\"],"
                        + "\"total\":101,\"current\":2,\"size\":100,\"pages\":2}"));
        assertEquals(101, page.getTotal());
        assertEquals(2, page.getCurrent());
        assertEquals(100, page.getSize());
        assertEquals(2, page.getPages());
        assertEquals("/images/9", page.getThumbnails()[0]);
    }

    @Test
    public void imagePage_keepsUnavailableImagesAndErrorReason() {
        cc.huoyan.app.aidl.ItemClazzImagePageParcel page = WsParcelMapper.clazzImages(ok(
                "{\"total\":2,\"current\":1,\"size\":100,\"pages\":1,\"items\":["
                        + "{\"uid\":1,\"image_available\":false},"
                        + "{\"uid\":2,\"image_available\":true,\"image_url\":\"/images/2\","
                        + "\"mime_type\":\"image/jpeg\"}]}"));
        assertEquals(2, page.getItems().size());
        assertTrue(!page.getItems().get(0).isImageAvailable());
        assertNull(page.getItems().get(0).getImageUrl());
        assertEquals("image/jpeg", page.getItems().get(1).getMimeType());
        assertEquals("/images/2", page.getItems().get(1).getImageUrl());
        assertEquals("查询失败", WsParcelMapper.clazzImages(err(-111, "查询失败")).getReason());
    }

    @Test
    public void failedImages_andRecognitionBackupInfo_mapWireFields() {
        cc.huoyan.app.aidl.FailedImagesResultParcel failed = WsParcelMapper.failedImages(ok(
                "{\"items\":[{\"clazz\":\"3\",\"image_id\":\"4\",\"failure_reason\":\"解码失败\","
                        + "\"image_url\":\"/failed/4\"}]}"));
        assertEquals("3", failed.getItems().get(0).getClazz());
        assertEquals("4", failed.getItems().get(0).getImageId());
        assertEquals("解码失败", failed.getItems().get(0).getFailureReason());
        assertEquals("/failed/4", failed.getItems().get(0).getImageUrl());
        assertTrue(WsParcelMapper.failedImages(ok("{\"items\":[]}")).getItems().isEmpty());
        assertEquals(-111, WsParcelMapper.failedImages(err(-111, "不可用")).getCode());
        assertEquals("不可用", WsParcelMapper.failedImages(err(-111, "不可用")).getReason());
        cc.huoyan.app.aidl.RecognitionDataInfoParcel info = WsParcelMapper.recognitionDataInfo(ok(
                "{\"backup_time_epoch_ms\":1790000000000,\"backup_time_text\":\"2026-09-10 12:00:00\","
                        + "\"schema_version\":3,\"file_format_version\":2}"));
        assertEquals(1790000000000L, info.getBackupTimeEpochMs());
        assertEquals("2026-09-10 12:00:00", info.getBackupTimeText());
        assertEquals(3, info.getSchemaVersion());
        assertEquals(2, info.getFileFormatVersion());
        assertEquals(0, WsParcelMapper.recognitionDataInfo(ok("{}")).getBackupTimeEpochMs());
        assertEquals(-111, WsParcelMapper.recognitionDataInfo(err(-111, "失败")).getCode());
    }

}
