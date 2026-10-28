package cc.huoyan.sdk.client.internal.ws;

import cc.huoyan.app.aidl.PhotoUploadResultParcel;
import cc.huoyan.app.aidl.ImageFlowProgressParcel;
import java.util.ArrayList;
import java.util.List;
import cc.huoyan.app.aidl.*;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.CertInfoParcel;
import cc.huoyan.app.aidl.CloudStatusParcel;
import cc.huoyan.app.aidl.DetectBoxParcel;
import cc.huoyan.app.aidl.DetectResultParcel;
import cc.huoyan.app.aidl.DownloadProgressParcel;
import cc.huoyan.app.aidl.FeedbackItemParcel;
import cc.huoyan.app.aidl.FeedbackDetectParcel;
import cc.huoyan.app.aidl.FeedbackResultParcel;
import cc.huoyan.app.aidl.FeedbackParcel;
import cc.huoyan.app.aidl.GoodsParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.FailedImageParcel;
import cc.huoyan.app.aidl.ItemClazzImagePageParcel;
import cc.huoyan.app.aidl.RecognitionDataInfoParcel;
import cc.huoyan.app.aidl.ItemClazzImageParcel;
import cc.huoyan.app.aidl.ItemClazzListParcel;
import cc.huoyan.app.aidl.ItemLearnBatchParcel;
import cc.huoyan.app.aidl.ItemLearnBoxParcel;
import cc.huoyan.app.aidl.LearnResultParcel;
import cc.huoyan.app.aidl.RecognitionCandidateParcel;
import cc.huoyan.app.aidl.RecognitionItemParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.app.aidl.StatusSnapshotParcel;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * WS v2 响应 JSON → SDK Parcelable 字段映射 + 请求参数构造。
 * 字段名/线序以 SDK {@code SdkWsDispatcher} 各 payload 构建方法为契约来源
 * （get_status 六字段、cert_info 十二字段、detect 的 x/y/w/h/score、
 * recognize 条目 square/rank/cand、clazz 数组等），错误回执映射对齐 SDK
 * {@code SdkAidlAdapter} 的 error 形态（code + reason + 空载荷）。
 * 纯 fastjson 逻辑，JVM 可测。
 */
public final class WsParcelMapper {

    /** 入参非法错误码（对齐 SDK huoyan ERR_ARGS）。 */
    public static final int ERR_ARGS = -1;

    private WsParcelMapper() {
    }

    /** 映射云端开关回执；错误载荷中的 enabled 不得作为实际状态。 */
    public static CloudStatusParcel cloudStatus(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new CloudStatusParcel(response.getCode(), response.getReason(), false);
        }
        Object enabled = response.getData().get("enabled");
        if (!(enabled instanceof Boolean)) {
            return new CloudStatusParcel(ERR_ARGS, "云端服务状态回执缺少 enabled", false);
        }
        return new CloudStatusParcel(0, null, (Boolean) enabled);
    }

    // ================= 响应 → Parcelable =================

    /**
     * 能力快照回执。
     *
     * @param response get_capabilities 响应
     * @return 能力快照（失败时 code/reason + 空载荷）
     */
    public static CapabilityParcel capability(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new CapabilityParcel(response.getCode(), response.getReason(),
                    0, false, false, false, false, false, false, null, -1);
        }
        JSONObject data = response.getData();
        return new CapabilityParcel(0, null,
                data.getIntValue("api_version"),
                data.getBooleanValue("shared_memory_image"),
                data.getBooleanValue("detect"),
                data.getBooleanValue("recognize"),
                data.getBooleanValue("item_learn"),
                data.getBooleanValue("item_forget"),
                data.getBooleanValue("item_query"),
                data.getString("sdk_version"),
                data.getIntValue("sdk_version_code"));
    }

    /**
     * 状态快照回执（含 sdk_state 等状态字段，可空字段 JSON null → null）。
     *
     * @param response get_status 响应
     * @return 状态快照（失败时 code/reason + 全 null）
     */
    public static StatusSnapshotParcel status(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new StatusSnapshotParcel(response.getCode(), response.getReason(),
                    null, null, null, null, null, null);
        }
        JSONObject data = response.getData();
        return new StatusSnapshotParcel(0, null,
                data.getString("device_id"),
                data.getString("certificate_valid_until"),
                data.getString("trust_level"),
                data.getString("model_version"),
                data.getBoolean("key_lost"),
                data.getBoolean("backend_connected"),
                data.getString("sdk_state"),
                data.getBoolean("model_ready"),
                data.getBoolean("busy"),
                data.getString("busy_operation"),
                data.getLong("state_updated_at"),
                data.getBoolean("certificate_valid"), data.getString("recent_error"));
    }

    /**
     * 证书视图回执（十二字段）。
     *
     * @param response get_cert_info 响应
     * @return 证书视图（失败时 code/reason + 空载荷形态）
     */
    public static CertInfoParcel certInfo(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return errorCertInfo(response.getCode(), response.getReason());
        }
        JSONObject data = response.getData();
        return new CertInfoParcel(0, null,
                data.getString("model_code"),
                data.getString("model_channel"),
                data.getString("license_type"),
                data.getLongValue("activated_at"),
                data.getLongValue("issued_at"),
                data.getLongValue("renewed_at"),
                data.getBooleanValue("has_renewed_at"),
                data.getLongValue("next_renew_at"),
                data.getBooleanValue("has_next_renew_at"),
                data.getLongValue("expires_at"),
                // SDK 协议字段名为 sdk_version（与 SdkWsDispatcher.certInfoPayload 对齐）。
                data.getString("sdk_version"),
                data.getIntValue("sdk_version_code"),
                data.getString("activation_serial"));
    }

    /**
     * 下载进度回执。
     *
     * @param response get_download_progress 响应
     * @return 下载进度（失败时 code/reason + 0/0）
     */
    public static DownloadProgressParcel downloadProgress(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new DownloadProgressParcel(response.getCode(), response.getReason(), 0L, 0L);
        }
        JSONObject data = response.getData();
        return new DownloadProgressParcel(0, null,
                data.getLongValue("downloaded"), data.getLongValue("total"));
    }

    /**
     * 检测回执（框字段 x/y/w/h/score 与 SDK SdkWsDispatcher.detectPayload 一致）。
     *
     * @param response detect 响应
     * @return 检测回执（失败时 code/reason + 空 boxes）
     */
    public static DetectResultParcel detect(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return errorDetect(response.getCode(), response.getReason());
        }
        JSONArray boxes = response.getData().getJSONArray("boxes");
        List<DetectBoxParcel> parsed = new ArrayList<>();
        if (boxes != null) {
            for (int i = 0; i < boxes.size(); i++) {
                JSONObject box = boxes.getJSONObject(i);
                if (box == null) {
                    continue;
                }
                parsed.add(new DetectBoxParcel(
                        box.getIntValue("x"),
                        box.getIntValue("y"),
                        box.getIntValue("w"),
                        box.getIntValue("h"),
                        box.getDoubleValue("score")));
            }
        }
        return new DetectResultParcel(0, null, parsed);
    }

    /**
     * 识别回执（SDK 门面合并后唯一格式）：条目填全九字段
     * （clazz/distance/box/square/rank/cand）。
     *
     * @param response recognize 响应
     * @return 识别回执（失败时 code/reason + 空 items）
     */
    public static RecognizeResultParcel recognize(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return errorRecognize(response.getCode(), response.getReason());
        }
        JSONArray items = response.getData().getJSONArray("items");
        List<RecognitionItemParcel> parsed = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                parsed.add(toItem(item));
            }
        }
        return new RecognizeResultParcel(0, null, parsed);
    }

    private static RecognitionItemParcel toItem(JSONObject item) {
        JSONObject box = item.getJSONObject("box");
        int left = box != null ? box.getIntValue("left") : 0;
        int top = box != null ? box.getIntValue("top") : 0;
        int right = box != null ? box.getIntValue("right") : 0;
        int bottom = box != null ? box.getIntValue("bottom") : 0;
        List<RecognitionCandidateParcel> candidates = new ArrayList<>();
        JSONArray cand = item.getJSONArray("cand");
        if (cand != null) {
            for (int i = 0; i < cand.size(); i++) {
                JSONObject candidate = cand.getJSONObject(i);
                if (candidate == null) {
                    continue;
                }
                candidates.add(new RecognitionCandidateParcel(
                        requireClazz(candidate.get("clazz")),
                        candidate.getDoubleValue("distance"),
                        candidate.getDoubleValue("square"),
                        candidate.getDoubleValue("rank")));
            }
        }
        return new RecognitionItemParcel(requireClazz(item.get("clazz")),
                item.getDoubleValue("distance"), left, top, right, bottom,
                item.getDoubleValue("square"), item.getDoubleValue("rank"), candidates);
    }

    /**
     * 学习回执（成功携带新 uid）。
     *
     * @param response item_learn 响应
     * @return 学习回执（失败时 code/reason + uid=0）
     */
    public static LearnResultParcel learn(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new LearnResultParcel(response.getCode(), response.getReason(), 0L);
        }
        return new LearnResultParcel(0, null, response.getData().getLongValue("uid"));
    }

    /**
     * 批量学习回执：data.boxes[] 每框平铺记录（键序 x/y/w/h/score/
     * uid/code/reason，对齐 SDK {@code SdkWsDispatcher.batchLearnPayload} 与 AIDL
     * ItemLearnBoxParcel 同构）；失败框原样保留（code≠0 + reason），过滤由业务侧
     * 裁决（单框失败不阻断其他框）。
     *
     * @param response batch_learn 响应
     * @return 批量学习回执（整体失败时 code/reason + 空 boxes）
     */
    public static ItemLearnBatchParcel batchLearn(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return errorBatchLearn(response.getCode(), response.getReason());
        }
        JSONArray boxes = response.getData().getJSONArray("boxes");
        List<ItemLearnBoxParcel> parsed = new ArrayList<>();
        if (boxes != null) {
            for (int i = 0; i < boxes.size(); i++) {
                JSONObject box = boxes.getJSONObject(i);
                if (box == null) {
                    continue;
                }
                parsed.add(new ItemLearnBoxParcel(
                        box.getIntValue("x"),
                        box.getIntValue("y"),
                        box.getIntValue("w"),
                        box.getIntValue("h"),
                        box.getDoubleValue("score"),
                        box.getLongValue("uid"),
                        box.getIntValue("code"),
                        box.getString("reason")));
            }
        }
        return new ItemLearnBatchParcel(0, null, parsed);
    }

    /**
     * 已学类别列表回执（clazz[] 与 uid_count[] 下标一一对应）。
     *
     * @param response get_item_clazz_list 响应
     * @return 类别列表（失败时 code/reason + 空数组）
     */
    private static String requireClazz(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException("clazz 必须为字符串");
        return cc.huoyan.sdk.client.operation.SdkInputs.clazz((String) value);
    }

    private static String[] toClazzArray(JSONArray values) {
        if (values == null) throw new IllegalArgumentException("缺少 clazz 列表");
        String[] result = new String[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = requireClazz(values.get(i));
        return result;
    }

    public static ItemClazzListParcel clazzList(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new ItemClazzListParcel(response.getCode(), response.getReason(),
                    new String[0], new long[0]);
        }
        JSONObject data = response.getData();
        return new ItemClazzListParcel(0, null,
                toClazzArray(data.getJSONArray("clazz")),
                toLongArray(data.getJSONArray("uid_count")),
                data.getLongValue("total"), data.getIntValue("current"),
                data.getIntValue("size"), data.getIntValue("pages"),
                data.getJSONArray("thumbnails") == null ? null
                        : data.getJSONArray("thumbnails").toArray(new String[0]));
    }

    /**
     * 无载荷动作回执（init/activate/setBaseUrl/itemForget 族/
     * submit_feedback）：成功 code=0，失败透传 code/reason。
     *
     * @param response 动作命令响应
     * @return 通用动作回执
     */
    public static BaseResultParcel base(WsV2Codec.Response response) {
        return response.isOk()
                ? new BaseResultParcel(0, null)
                : new BaseResultParcel(response.getCode(), response.getReason());
    }

    private static long[] toLongArray(JSONArray array) {
        if (array == null) {
            return new long[0];
        }
        long[] values = new long[array.size()];
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            values[i] = value instanceof Number ? ((Number) value).longValue() : 0L;
        }
        return values;
    }

    // ================= 错误形态（对齐 SDK SdkAidlAdapter） =================

    /**
     * 检测回执错误形态。
     *
     * @param code   错误码
     * @param reason 错误说明
     * @return 空 boxes 错误回执
     */
    public static DetectResultParcel errorDetect(int code, String reason) {
        return new DetectResultParcel(code, reason, new ArrayList<DetectBoxParcel>());
    }

    /**
     * 识别回执错误形态。
     *
     * @param code   错误码
     * @param reason 错误说明
     * @return 空 items 错误回执
     */
    public static RecognizeResultParcel errorRecognize(int code, String reason) {
        return new RecognizeResultParcel(code, reason, new ArrayList<RecognitionItemParcel>());
    }

    /**
     * 批量学习回执错误形态（整体失败：一次都没学）。
     *
     * @param code   错误码
     * @param reason 错误说明
     * @return 空 boxes 错误回执
     */
    public static ItemLearnBatchParcel errorBatchLearn(int code, String reason) {
        return new ItemLearnBatchParcel(code, reason, new ArrayList<ItemLearnBoxParcel>());
    }

    /**
     * 证书视图错误形态。
     *
     * @param code   错误码
     * @param reason 错误说明
     * @return 空载荷错误回执
     */
    public static CertInfoParcel errorCertInfo(int code, String reason) {
        return new CertInfoParcel(code, reason,
                null, null, null, 0L, 0L, 0L, false, 0L, false, 0L);
    }

    // ================= 请求参数构造 =================

    /**
     * activate 命令参数。
     *
     * @param activationCode 激活码
     * @param modelCode 用户选择的模型代码
     * @return 激活码与模型代码参数
     */
    public static JSONObject buildActivateParams(String activationCode, String modelCode) {
        JSONObject params = new JSONObject(true);
        params.put("activation_code", activationCode);
        params.put("model_code", modelCode);
        return params;
    }

    /** 按激活码查询模型的请求参数。 */
    public static JSONObject buildLicenseModelsParams(String activationCode) {
        JSONObject params = new JSONObject(true);
        params.put("activation_code", activationCode);
        return params;
    }

    /** 保留模型列表顺序、稳定代码和当前项标记。 */
    public static LicenseModelsParcel licenseModels(WsV2Codec.Response response) {
        List<LicenseModelParcel> models = new ArrayList<>();
        if (!response.isOk()) return new LicenseModelsParcel(response.getCode(), response.getReason(), models);
        JSONArray rows = response.getData().getJSONArray("models");
        if (rows == null) return new LicenseModelsParcel(-1, "模型列表回执缺失", models);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            models.add(new LicenseModelParcel(row.getString("model_code"), row.getString("model_name"),
                    row.getString("chip"), row.getBooleanValue("current")));
        }
        return new LicenseModelsParcel(0, null, models);
    }

    /**
     * set_base_url 命令参数。
     *
     * @param url 基址
     * @return {"url": ...}
     */
    public static JSONObject buildUrlParams(String url) {
        JSONObject params = new JSONObject(true);
        params.put("url", url);
        return params;
    }

    /**
     * 分类编码参数（item_forget / get_item_clazz_detail）。
     *
     * @param clazz 分类编码（int64）
     * @return {"clazz": ...}
     */
    public static JSONObject buildClazzParam(String clazz) {
        JSONObject params = new JSONObject(true);
        params.put("clazz", clazz);
        return params;
    }

    /**
     * 样本 UID 参数（item_forget_by_uid）。
     *
     * @param uid 样本 UID
     * @return {"uid": ...}
     */
    public static JSONObject buildUidParam(long uid) {
        JSONObject params = new JSONObject(true);
        params.put("uid", uid);
        return params;
    }

    /**
     * 需图命令的图像载体参数：rgb 紧凑帧携带几何（SDK rgb 载体必填
     * width/height）；rotate 仅 recognize 携带（∈{0,90,180,270}，SDK 侧校验）。
     *
     * @param width  帧宽
     * @param height 帧高
     * @param rotate 旋转角；null=detect 等无旋转命令
     * @return 图像载体参数对象
     */
    public static JSONObject buildImageParams(int width, int height, Integer rotate) {
        JSONObject params = new JSONObject(true);
        params.put("format", "rgb");
        params.put("width", width);
        params.put("height", height);
        if (rotate != null) {
            params.put("rotate", rotate);
        }
        return params;
    }

    /**
     * item_learn 命令参数（分类编码 + 图像载体几何）。
     *
     * @param clazz 分类编码（int64）
     * @param width 帧宽
     * @param height 帧高
     * @return {"clazz", "format", "width", "height"}
     */
    public static JSONObject buildLearnParams(String clazz, int width, int height) {
        JSONObject params = buildImageParams(width, height, null);
        params.put("clazz", clazz);
        return params;
    }

    /** 将识别目标、候选、最终选择与原图坐标转换为 WS feedback 契约。 */
    public static JSONObject buildFeedbackParams(FeedbackParcel feedback) {
        JSONObject params = new JSONObject(true);
        params.put("feedback_type", feedback.getFeedbackType());
        if (feedback.getPhotoFileUrl() != null) {
            params.put("photo_file_url", feedback.getPhotoFileUrl());
        }
        if (feedback.getRequestId() != null) {
            params.put("request_id", feedback.getRequestId());
        }
        JSONArray items = new JSONArray();
        for (FeedbackItemParcel item : feedback.getItems()) {
            JSONObject row = new JSONObject(true);
            row.put("item_type", item.getItemType());
            JSONArray detect = new JSONArray();
            for (FeedbackDetectParcel candidate : item.getDetect()) {
                JSONObject entry = new JSONObject(true);
                entry.put("clazz", candidate.getClazz());
                entry.put("name", candidate.getName());
                entry.put("distance", candidate.getDistance());
                detect.add(entry);
            }
            row.put("detect", detect);
            if (item.getItemType() == FeedbackItemParcel.DELETED) {
                row.put("result", new JSONObject(true));
            } else if (item.getResult() != null) {
                FeedbackResultParcel result = item.getResult();
                JSONObject selected = new JSONObject(true);
                selected.put("clazz", result.getClazz());
                selected.put("name", result.getName());
                selected.put("source", result.getSource());
                row.put("result", selected);
            }
            if (item.getX() != null) row.put("x", item.getX());
            if (item.getY() != null) row.put("y", item.getY());
            if (item.getWidth() != null) row.put("width", item.getWidth());
            if (item.getHeight() != null) row.put("height", item.getHeight());
            items.add(row);
        }
        params.put("items", items);
        return params;
    }

    /** 解析 uploadPhoto 回执，保留 SDK 业务错误码与线上 URL。 */
    public static PhotoUploadResultParcel photoUpload(WsV2Codec.Response response) {
        BaseResultParcel result = base(response);
        JSONObject data = response.getData();
        return new PhotoUploadResultParcel(result.getCode(), result.getReason(),
                data == null ? null : data.getString("photo_file_url"));
    }

    // ================= 绑定与商品 CRUD =================

    /**
     * bindDevice 命令参数：account_id/shop_id/shop_name 对齐 SDK
     * {@code SdkWsDispatcher.handleBindDevice} 键名（snake）；shop_no 可选。
     * shopName 为 null 时键不上线——SDK 侧以 ERR_ARGS 真实拒绝（当前 SDK
     * shop_name 必填为已知服务端阻塞，桥不虚构店名、不假装成功）。
     *
     * @param req 绑定请求载体
     * @return 绑定参数对象
     */
    public static JSONObject buildBindDeviceParams(BindDeviceParcel req) {
        JSONObject params = new JSONObject(true);
        params.put("account_id", req.getAccountId());
        if (req.getShopId() != null) {
            params.put("shop_id", req.getShopId());
        }
        if (req.getShopName() != null) {
            params.put("shop_name", req.getShopName());
        }
        if (req.getShopNo() != null) {
            params.put("shop_no", req.getShopNo());
        }
        return params;
    }

    /**
     * 绑定回执映射：data.request（传入回显组）+ data.response（后端落库组），
     * 键 snake 对齐 SDK {@code SdkWsDispatcher.bindingPayload}；失败回执
     * code/reason 透传、两组字段全 null（与 AIDL BindingResultParcel 错误形态同构）。
     *
     * @param response bindDevice 响应
     * @return 绑定回执
     */
    public static BindingResultParcel binding(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new BindingResultParcel(response.getCode(), response.getReason(),
                    null, null, null, null, null, null, null, null);
        }
        JSONObject data = response.getData();
        JSONObject request = data.getJSONObject("request");
        BindDeviceParcel echo = request == null ? null : new BindDeviceParcel(
                request.getLongValue("account_id"),
                request.getString("shop_id"),
                request.getString("shop_name"),
                request.getString("shop_no"));
        JSONObject payload = data.getJSONObject("response");
        return new BindingResultParcel(0, null, echo,
                payload != null ? payload.getString("account_id") : null,
                payload != null ? payload.getString("account_name") : null,
                payload != null ? payload.getString("shop_id") : null,
                payload != null ? payload.getString("shop_name") : null,
                payload != null ? payload.getString("shop_no") : null,
                payload != null ? payload.getString("shop_address") : null,
                payload != null ? payload.getString("shop_phone") : null);
    }

    /**
     * createGoods/updateGoods 命令的商品字段参数：键名对齐 SDK
     * {@code SdkWsDispatcher.parseCatalogSaveRequest}（name/plu/image/
     * ingredients_list/shelf_life/pinyin/category/category_name/unit/sale_mode/
     * tare_weight/price/sort/status——注意价键为 price 非 unit_fen）。字符串
     * null 不上线、数值仅在 hasX=true 时上线（哨兵 -1 不出线，SDK 侧 null=未传）。
     *
     * @param req 商品保存载体
     * @return 商品字段参数对象（goods_id 由调用方另行携带）
     */
    public static JSONObject buildGoodsSaveParams(GoodsSaveParcel req) {
        JSONObject params = new JSONObject(true);
        if (req.getName() != null) {
            params.put("name", req.getName());
        }
        if (req.getPlu() != null) {
            params.put("plu", req.getPlu());
        }
        if (req.getImage() != null) {
            params.put("image", req.getImage());
        }
        if (req.getIngredientsList() != null) {
            params.put("ingredients_list", req.getIngredientsList());
        }
        if (req.getShelfLife() != null) {
            params.put("shelf_life", req.getShelfLife());
        }
        if (req.getPinyin() != null) {
            params.put("pinyin", req.getPinyin());
        }
        if (req.hasCategory()) {
            params.put("category", req.getCategory());
        }
        if (req.getCategoryName() != null) {
            params.put("category_name", req.getCategoryName());
        }
        if (req.getUnit() != null) {
            params.put("unit", req.getUnit());
        }
        if (req.hasSaleMode()) {
            params.put("sale_mode", req.getSaleMode());
        }
        if (req.hasTareWeight()) {
            params.put("tare_weight", req.getTareWeight());
        }
        if (req.hasPrice()) {
            params.put("price", req.getPrice());
        }
        if (req.hasSort()) {
            params.put("sort", req.getSort());
        }
        if (req.hasStatus()) {
            params.put("status", req.getStatus());
        }
        return params;
    }

    /**
     * 商品单 ID 参数（updateGoods/deleteGoods 的 goods_id）。
     *
     * @param goodsId SDK 商品 ID（十进制字符串）
     * @return {"goods_id": ...}
     */
    public static JSONObject buildGoodsIdParam(String goodsId) {
        JSONObject params = new JSONObject(true);
        params.put("goods_id", goodsId);
        return params;
    }

    /**
     * 商品单条回执映射：data.goods 键 snake 对齐 SDK
     * {@code SdkWsDispatcher.catalogEntryJson}（id/shop_id/name/plu/base_id/
     * image/category/category_name/unit/sale_mode/price/tare_weight/
     * ingredients_list/shelf_life/pinyin/status/sort/shop_name）。WS 线面无
     * is_deleted/update_time/account_id/account_name/create_time——映射
     * 0/null 不虚构；失败回执 code/reason 透传、record=null。
     *
     * @param response createGoods/updateGoods 响应
     * @return 商品单条回执
     */
    public static GoodsResultParcel goods(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new GoodsResultParcel(response.getCode(), response.getReason(), null);
        }
        JSONObject goods = response.getData().getJSONObject("goods");
        if (goods == null) {
            return new GoodsResultParcel(ERR_ARGS, "成功回执缺失 goods 字段", null);
        }
        return new GoodsResultParcel(0, null, goodsRecord(goods));
    }

    private static GoodsParcel goodsRecord(JSONObject goods) {
        return new GoodsParcel(
                goods.getString("id"),
                goods.getString("shop_id"),
                goods.getString("name"),
                goods.getString("plu"),
                goods.getString("base_id"),
                goods.getString("image"),
                goods.getIntValue("category"),
                goods.getString("category_name"),
                goods.getString("unit"),
                goods.getIntValue("sale_mode"),
                goods.getLongValue("price"),
                goods.getLongValue("tare_weight"),
                goods.getString("ingredients_list"),
                goods.getString("shelf_life"),
                goods.getString("pinyin"),
                goods.getIntValue("status"),
                goods.getIntValue("sort"),
                goods.getIntValue("is_deleted"),
                goods.getString("update_time"),
                goods.getString("shop_name"),
                goods.getString("account_id"),
                goods.getString("account_name"),
                goods.getString("create_time"));
    }
    /** 映射类别图片分页；缺失图片仍保留样本记录。 */
    public static ItemClazzImagePageParcel clazzImages(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new ItemClazzImagePageParcel(response.getCode(), response.getReason(),
                    0, 0, 0, 0, null);
        }
        JSONObject data = response.getData();
        List<ItemClazzImageParcel> items = new ArrayList<>();
        JSONArray records = data.getJSONArray("items");
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                JSONObject item = records.getJSONObject(i);
                items.add(new ItemClazzImageParcel(item.getLongValue("uid"),
                        item.getBooleanValue("image_available"), item.getString("image_url"),
                        item.getString("mime_type")));
            }
        }
        return new ItemClazzImagePageParcel(0, null, data.getLongValue("total"),
                data.getIntValue("current"), data.getIntValue("size"),
                data.getIntValue("pages"), items);
    }

    /** 映射失败图片查询，保留错误码与原因。 */
    public static FailedImagesResultParcel failedImages(WsV2Codec.Response response) {
        return new FailedImagesResultParcel(response.getCode(), response.getReason(),
                failedImageRecords(response.isOk() ? response.getData().getJSONArray("items") : null));
    }

    private static List<FailedImageParcel> failedImageRecords(JSONArray records) {
        List<FailedImageParcel> items = new ArrayList<>();
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                JSONObject item = records.getJSONObject(i);
                items.add(new FailedImageParcel(requireClazz(item.get("clazz")),
                        item.getString("image_id"), item.getString("failure_reason"),
                        item.getString("image_url")));
            }
        }
        return items;
    }

    /** 映射识别数据备份时间及版本，未备份保持零值空态。 */
    public static RecognitionDataInfoParcel recognitionDataInfo(WsV2Codec.Response response) {
        if (!response.isOk()) {
            return new RecognitionDataInfoParcel(response.getCode(), response.getReason(),
                    0, "", 0, 0);
        }
        JSONObject data = response.getData();
        return new RecognitionDataInfoParcel(0, null, data.getLongValue("backup_time_epoch_ms"),
                data.getString("backup_time_text"), data.getLongValue("schema_version"),
                data.getLongValue("file_format_version"));
    }

    /** 将阶段进度或最终学习统计映射为与 AIDL 一致的回执。 */
    public static ImageFlowProgressParcel imageFlow(WsV2Codec.Response response, boolean finalResult) {
        JSONObject data = response.getData();
        if (!response.isOk() || data == null || data.getString("phase") == null) {
            return new ImageFlowProgressParcel(response.isOk() ? ERR_ARGS : response.getCode(),
                    response.isOk() ? "下载学习回执缺少阶段" : response.getReason(),
                    false, null, 0, 0, 0, 0, null, 0, null);
        }
        String phase = data.getString("phase");
        long success = data.getLongValue(finalResult ? "learn_success" : "success_count");
        long fail = data.getLongValue(finalResult ? "learn_fail" : "fail_count");
        // 部分 SDK 版本的 WS 快照未输出 active；按阶段补齐 AIDL 语义。
        boolean active = data.containsKey("active") ? data.getBooleanValue("active")
                : "DOWNLOADING".equals(phase) || "LEARNING".equals(phase);
        return new ImageFlowProgressParcel(0, null, !finalResult && active, phase,
                finalResult ? success + fail : data.getLongValue("current"),
                finalResult ? success + fail : data.getLongValue("total"), success, fail,
                data.getString(finalResult ? "learn_fail_stats" : "fail_stats"),
                data.getIntValue("fail_code"), data.getString("fail_reason"),
                data.getString("public_dir"), data.getLongValue("skipped_count"),
                data.getString("skip_reason_stats"), data.getLongValue("download_success"),
                data.getLongValue("download_fail"), data.getString("download_fail_stats"),
                failedImageRecords(data.getJSONArray("failed_images")));
    }

    /** 构造分页参数，0 交由 SDK 使用默认值。 */
    public static JSONObject buildPageParams(int page, int size) {
        JSONObject params = new JSONObject(true);
        params.put("page", page);
        params.put("size", size);
        return params;
    }

    /** 构造类别图片分页参数。 */
    public static JSONObject buildClazzPageParams(String clazz, int page, int size) {
        JSONObject params = buildPageParams(page, size);
        params.put("clazz", clazz);
        return params;
    }

    private static JSONObject payload(WsV2Codec.Response response) {
        return response.isOk() && response.getData() != null ? response.getData() : new JSONObject();
    }

    private static List<GoodsParcel> goodsRecords(JSONObject data) {
        List<GoodsParcel> result = new ArrayList<>();
        JSONArray records = data.getJSONArray("records");
        if (records != null) for (int i = 0; i < records.size(); i++) result.add(goodsRecord(records.getJSONObject(i)));
        return result;
    }

    /** 解析云端商品分页及同步游标。 */
    public static GoodsListResultParcel goodsList(WsV2Codec.Response response) {
        JSONObject d = payload(response);
        return new GoodsListResultParcel(response.getCode(), response.getReason(), d.getString("shop_id"),
                d.getString("sync_mode"), d.getLongValue("sync_timestamp"), d.getString("server_time"),
                d.getLongValue("total"), d.getLongValue("pages"), d.getIntValue("current"), d.getIntValue("size"), goodsRecords(d));
    }

    /** 解析 SDK 本地商品查询分页，保留软删字段。 */
    public static GoodsQueryResultParcel goodsQuery(WsV2Codec.Response response) {
        JSONObject d = payload(response);
        return new GoodsQueryResultParcel(response.getCode(), response.getReason(), d.getLongValue("total"),
                d.getLongValue("pages"), d.getIntValue("current"), d.getIntValue("size"), goodsRecords(d));
    }

    /** 解析商品计数；错误码不被空统计覆盖。 */
    public static GoodsCountResultParcel goodsCount(WsV2Codec.Response response) {
        return new GoodsCountResultParcel(response.getCode(), response.getReason(), payload(response).getLongValue("count"));
    }

    /** 解析批量操作逐项结果，保留部分成功与原始顺序。 */
    public static GoodsBatchResultParcel goodsBatch(WsV2Codec.Response response) {
        List<GoodsBatchItemParcel> result = new ArrayList<>();
        JSONArray items = payload(response).getJSONArray("items");
        if (items != null) for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            result.add(new GoodsBatchItemParcel(item.getString("goods_id"), item.getIntValue("code"), item.getString("reason")));
        }
        return new GoodsBatchResultParcel(response.getCode(), response.getReason(), result);
    }

    /** 解析商品同步终态及完整统计。 */
    public static GoodsDownloadResultParcel goodsDownload(WsV2Codec.Response response) {
        JSONObject d = payload(response);
        return new GoodsDownloadResultParcel(response.getCode(), response.getReason(), d.getString("phase"),
                d.getString("sync_mode"), d.getLongValue("sync_timestamp"), d.getLongValue("total"),
                d.getLongValue("added"), d.getLongValue("updated"), d.getLongValue("deleted"),
                d.getLongValue("success_count"), d.getLongValue("fail_count"), d.getBooleanValue("has_changes"),
                d.getIntValue("fail_code"), d.getString("fail_reason"));
    }

    /** 解析商品同步进度，保留失败阶段和已提交游标。 */
    public static GoodsDownloadProgressParcel goodsDownloadProgress(WsV2Codec.Response response) {
        JSONObject d = payload(response);
        return new GoodsDownloadProgressParcel(response.getCode(), response.getReason(), d.getBooleanValue("active"),
                d.getString("phase"), d.getLongValue("current"), d.getLongValue("total"),
                d.getLongValue("success_count"), d.getLongValue("fail_count"), d.getLongValue("added_count"),
                d.getLongValue("updated_count"), d.getLongValue("deleted_count"), d.getBooleanValue("has_changes"),
                d.getString("sync_mode"), d.getLongValue("sync_timestamp"), d.getString("fail_reason"));
    }
}
