package cc.huoyan.sdk.client.internal.ws;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

/**
 * WS 协议 v2 客户端编解码：线格式以 SDK
 * {@code cc.huoyan.shared.ws.WsV2Codec}（SDK 侧同名实现）为契约来源——
 *
 * <ul>
 *   <li>text 请求 {@code {"requestId": 非空, "cmd": 非空, "params"?: 对象}}；</li>
 *   <li>text 响应 {@code {"requestId": 原样带回, "code": 0, "data": {...}}}，
 *       {@code code≠0} 时为 {@code {"requestId", "code", "reason"}}；</li>
 *   <li>binary 帧 = [4B 大端 requestId 长度][requestId UTF-8 字节][图像原始字节]，
 *       需图命令（detect/recognize/itemLearn/batchLearn）先 text 声明后 binary 传图
 *       （服务端声明后 5s 窗口，超时作废须以新 requestId 重发）。</li>
 * </ul>
 *
 * <p>命令名镜像 SDK {@code StaticConfig.Comm.CMD_*}（SDK 统一小驼峰、与 AIDL
 * 方法名一致，旧 snake_case 名废弃不再被 SDK 识别）；JSON 走 fastjson（纯 JVM 可测，
 * 与 SDK 侧 org.json 线格式互通：双方只约定 JSON 文本与字段名）。纯静态无状态。
 */
public final class WsV2Codec {

    /** WS 服务端点缺省主机（SDK 仅绑定回环；局域网开放端点随后续阶段）。 */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** WS 服务端点缺省端口（SDK {@code StaticConfig.Comm.WS_LOOPBACK_PORT}）。 */
    public static final int DEFAULT_PORT = 18086;

    /**
     * WS 服务端点缺省路径：SDK {@code SdkWsServer} 为裸
     * {@code WebSocketServer}，不校验资源描述符（任意路径可连），取 {@code /ws}
     * 对齐 C50 实测地址 {@code ws://192.168.1.115:18086/ws}。
     */
    public static final String DEFAULT_PATH = "/ws";

    /** WS 服务端点缺省整串（回环 + {@link #DEFAULT_PATH}）。 */
    public static final String DEFAULT_ENDPOINT =
            "ws://" + DEFAULT_HOST + ":" + DEFAULT_PORT + DEFAULT_PATH;

    /** 云端服务开关读写命令名。 */
    public static final String CMD_GET_CLOUD_STATUS = "getCloudStatus";
    public static final String CMD_SET_CLOUD_STATUS = "setCloudStatus";

    /** 状态查询命令名。 */
    public static final String CMD_GET_STATUS = "getStatus";
    /** 识别命令名（需图）。 */
    public static final String CMD_RECOGNIZE = "recognize";
    /** 能力快照查询命令名。 */
    public static final String CMD_GET_CAPABILITIES = "getCapabilities";
    /** 按输入激活码查询授权模型。 */
    public static final String CMD_QUERY_LICENSE_MODELS = "queryLicenseModels";
    /** 当前设备授权模型无参查询命令。 */
    public static final String CMD_QUERY_DEVICE_MODELS = "queryDeviceModels";
    /** 证书视图查询命令名。 */
    public static final String CMD_GET_CERT_INFO = "getCertInfo";
    /** 模型下载进度查询命令名。 */
    public static final String CMD_GET_DOWNLOAD_PROGRESS = "getDownloadProgress";
    /** 激活命令名。 */
    public static final String CMD_ACTIVATE = "activate";
    /** 业务上报域基址配置命令名。 */
    public static final String CMD_SET_BASE_URL = "setBaseUrl";
    /** 纯检测命令名（需图）。 */
    public static final String CMD_DETECT = "detect";
    /** 学习样本命令名（需图）。 */
    public static final String CMD_ITEM_LEARN = "itemLearn";
    /**
     * 批量学习命令名（需图）：镜像 AIDL batchLearn(image, clazz)——
     * SDK 组合一次 detect + 全框逐框 itemLearn，响应 data.boxes[] 每框独立结果
     * （x/y/w/h/score/uid/code/reason，与 ItemLearnBatchParcel 同构）。
     */
    public static final String CMD_BATCH_LEARN = "batchLearn";
    /** 按分类遗忘命令名。 */
    public static final String CMD_ITEM_FORGET = "itemForget";
    /** 按 UID 遗忘命令名。 */
    public static final String CMD_ITEM_FORGET_BY_UID = "itemForgetByUid";
    /** 已学类别列表查询命令名。 */
    public static final String CMD_GET_ITEM_CLAZZ_LIST = "getItemClazzList";
    /** 识别反馈提交命令名。 */
    public static final String CMD_UPLOAD_PHOTO = "uploadPhoto";
    public static final String CMD_SUBMIT_FEEDBACK = "submitFeedback";
    /**
     * 设备绑定命令名：镜像 AIDL bindDevice(BindDeviceParcel)
     * ——params.account_id/shop_id 必填，shop_name/shop_no 可选；
     * 响应 data.request（传入回显）+ data.response（后端落库终态），与
     * BindingResultParcel 同构（snake 键）。
     */
    public static final String CMD_BIND_DEVICE = "bindDevice";
    /** 商品创建命令名：params.name/plu 必填，响应 data.goods。 */
    public static final String CMD_CREATE_GOODS = "createGoods";
    /** 商品更新命令名：params.goods_id 必填 + 同创建字段，响应 data.goods。 */
    public static final String CMD_UPDATE_GOODS = "updateGoods";
    /** 商品软删命令名：params.goods_id 必填，响应无载荷。 */
    public static final String CMD_DELETE_GOODS = "deleteGoods";

    /** 携带宿主客户端版本的初始化命令名。 */
    public static final String CMD_INIT = "init";

    /** 类别图片分页查询命令名。 */
    public static final String CMD_GET_ITEM_CLAZZ_IMAGES = "getItemClazzImages";

    /** 失败图片查询命令名。 */
    public static final String CMD_GET_FAILED_IMAGES = "getFailedImages";

    /** 备份识别数据命令名。 */
    public static final String CMD_BACKUP_RECOGNITION_DATA = "backupRecognitionData";

    /** 查询识别数据备份信息命令名。 */
    public static final String CMD_GET_RECOGNITION_DATA_INFO = "getRecognitionDataInfo";

    /** 恢复识别数据命令名。 */
    public static final String CMD_RESTORE_RECOGNITION_DATA = "restoreRecognitionData";
    public static final String CMD_DOWNLOAD_IMAGES_AND_LEARN = "downloadImagesAndLearn";
    public static final String CMD_GET_IMAGE_DOWNLOAD_PROGRESS = "getImageDownloadProgress";
    public static final String CMD_GET_IMAGE_LEARN_PROGRESS = "getImageLearnProgress";

    private WsV2Codec() {
    }

    /** v2 响应值对象：requestId 原样带回 + 回执码 + data/reason 恰一携带。 */
    public static final class Response {

        private final String requestId;
        private final int code;
        private final String reason;
        private final JSONObject data;

        public Response(String requestId, int code, String reason, JSONObject data) {
            this.requestId = requestId;
            this.code = code;
            this.reason = reason;
            this.data = data;
        }

        /** 响应归属的在途请求；SDK 侧尽力提取时可为 null。 */
        public String getRequestId() {
            return requestId;
        }

        /** 回执码（0=成功；负值/4000x 为 SDK 侧错误码段）。 */
        public int getCode() {
            return code;
        }

        /** 成功载荷；code≠0 时为 null。 */
        public JSONObject getData() {
            return data;
        }

        /** 错误说明；code=0 时为 null。 */
        public String getReason() {
            return reason;
        }

        /** 是否成功回执。 */
        public boolean isOk() {
            return code == 0;
        }
    }

    /** binary 帧解码产物：requestId + 图像原始字节。 */
    public static final class Frame {

        private final String requestId;
        private final byte[] image;

        public Frame(String requestId, byte[] image) {
            this.requestId = Objects.requireNonNull(requestId, "requestId 不能为空");
            this.image = Objects.requireNonNull(image, "image 不能为空");
        }

        public String getRequestId() {
            return requestId;
        }

        public byte[] getImage() {
            return image;
        }
    }

    /** 解析后的 WS 端点值对象（scheme 固定 ws，host + port + 以 / 起的 path）。 */
    public static final class Endpoint {

        private final String host;
        private final int port;
        private final String path;

        /** 缺省端点（回环 18086 + /ws）。 */
        public static Endpoint def() {
            return new Endpoint(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_PATH);
        }

        public Endpoint(String host, int port, String path) {
            this.host = host;
            this.port = port;
            this.path = path;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPath() {
            return path;
        }

        /** 连接 URL 整串（{@code ws://host:port/path}）。 */
        public String toUrl() {
            return "ws://" + host + ":" + port + path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Endpoint)) {
                return false;
            }
            Endpoint that = (Endpoint) other;
            return port == that.port && host.equals(that.host) && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port, path);
        }

        @Override
        public String toString() {
            return "Endpoint(" + toUrl() + ")";
        }
    }

    /**
     * 解析 SP 覆盖端点串（真机连 C50 SDK 用）：格式
     * {@code ws://host:port[/path]}——scheme 仅 ws；host 非空必填（IPv4/主机名，
     * IPv6 字面量不支持）；port 缺省补 {@link #DEFAULT_PORT}，显式携带但不可解析
     * 或不在 1-65535（0/负/越界）时<b>整体回退缺省端点</b>（Codex 收尾 P3：非法
     * 端口产出非法 URI 会导致连接失败无限退避重连）；path 缺省
     * {@link #DEFAULT_PATH}，非 {@code /} 起头按缺省处理。null/空/非 ws:// 前缀/
     * host 为空一律回退缺省端点（解析失败不阻断连接，宁连回环不崩）。
     *
     * @param raw SP 覆盖端点串
     * @return 解析后的端点（永不返回 null）
     */
    public static Endpoint parseEndpoint(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Endpoint.def();
        }
        String rest = raw.trim();
        if (!rest.startsWith("ws://")) {
            return Endpoint.def();
        }
        rest = rest.substring("ws://".length());
        int pathStart = rest.indexOf('/');
        String authority = pathStart >= 0 ? rest.substring(0, pathStart) : rest;
        String path = pathStart >= 0 ? rest.substring(pathStart) : "";
        int colon = authority.indexOf(':');
        String host = colon >= 0 ? authority.substring(0, colon) : authority;
        if (host.isEmpty()) {
            return Endpoint.def();
        }
        int port = DEFAULT_PORT;
        if (colon >= 0) {
            try {
                port = Integer.parseInt(authority.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                return Endpoint.def();
            }
            if (port < 1 || port > 65535) {
                return Endpoint.def();
            }
        }
        if (path.isEmpty() || !path.startsWith("/") || "/".equals(path)) {
            path = DEFAULT_PATH;
        }
        return new Endpoint(host, port, path);
    }

    /**
     * 组装 text 请求帧（JSON 文本）。
     *
     * @param requestId 连接内唯一请求标识（非空）
     * @param cmd       命令名（非空）
     * @param params    命令参数；null/空对象时省略 params 键（无参命令）
     * @return 线格式 JSON 文本
     */
    public static String encodeRequest(String requestId, String cmd, JSONObject params) {
        JSONObject root = new JSONObject(true);
        root.put("requestId", Objects.requireNonNull(requestId, "requestId 不能为空"));
        root.put("cmd", Objects.requireNonNull(cmd, "cmd 不能为空"));
        if (params != null && !params.isEmpty()) {
            root.put("params", params);
        }
        return root.toJSONString();
    }

    /**
     * 解析 text 响应帧。
     *
     * @param text 服务端推送的文本消息
     * @return 响应值对象；非 JSON/非对象/缺 code 返回 null（调用方静默丢弃）
     */
    public static Response parseResponse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Object parsed;
        try {
            parsed = JSON.parse(text);
        } catch (JSONException e) {
            return null;
        }
        if (!(parsed instanceof JSONObject)) {
            return null;
        }
        JSONObject root = (JSONObject) parsed;
        if (!root.containsKey("code")) {
            return null;
        }
        String requestId = root.getString("requestId");
        int code = root.getIntValue("code");
        if (code == 0) {
            JSONObject data = root.getJSONObject("data");
            return new Response(requestId, code, null, data != null ? data : new JSONObject(true));
        }
        return new Response(requestId, code, root.getString("reason"), null);
    }

    /**
     * 组装 binary 传图帧：[4B 大端 requestId 长度][requestId UTF-8][图像字节]。
     *
     * @param requestId 与 text 声明一致的请求标识（非空）
     * @param image     图像原始字节（非空；rgb 紧凑帧，服务端按 params 几何解析）
     * @return binary 帧字节
     */
    public static byte[] encodeBinaryFrame(String requestId, byte[] image) {
        Objects.requireNonNull(requestId, "requestId 不能为空");
        Objects.requireNonNull(image, "image 不能为空");
        byte[] idBytes = requestId.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[4 + idBytes.length + image.length];
        writeIntBigEndian(frame, 0, idBytes.length);
        System.arraycopy(idBytes, 0, frame, 4, idBytes.length);
        System.arraycopy(image, 0, frame, 4 + idBytes.length, image.length);
        return frame;
    }

    /**
     * 解析 binary 帧（与 SDK 侧解帧同规，供测试回环断言）。
     *
     * @param frame 帧字节
     * @return 帧值对象；结构非法（长度不足/声明越界/图像空）返回 null
     */
    public static Frame decodeBinaryFrame(byte[] frame) {
        if (frame == null || frame.length < 4) {
            return null;
        }
        int idLength = readIntBigEndian(frame, 0);
        if (idLength <= 0 || 4L + idLength >= frame.length) {
            return null;
        }
        String requestId = new String(frame, 4, idLength, StandardCharsets.UTF_8).trim();
        if (requestId.isEmpty()) {
            return null;
        }
        int imageOffset = 4 + idLength;
        byte[] image = new byte[frame.length - imageOffset];
        System.arraycopy(frame, imageOffset, image, 0, image.length);
        return new Frame(requestId, image);
    }

    /**
     * 命令是否需要 binary 传图（镜像 SDK {@code WsV2Dispatcher.requiresImage}，
     * 包含 batchLearn）。
     *
     * @param cmd 命令名
     * @return true=detect/recognize/itemLearn/batchLearn（先 text 声明后 binary 传图）
     */
    public static boolean requiresImage(String cmd) {
        return CMD_DETECT.equals(cmd) || CMD_RECOGNIZE.equals(cmd)
                || CMD_ITEM_LEARN.equals(cmd) || CMD_BATCH_LEARN.equals(cmd) || CMD_UPLOAD_PHOTO.equals(cmd);
    }

    private static void writeIntBigEndian(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int readIntBigEndian(byte[] source, int offset) {
        return ((source[offset] & 0xFF) << 24)
                | ((source[offset + 1] & 0xFF) << 16)
                | ((source[offset + 2] & 0xFF) << 8)
                | (source[offset + 3] & 0xFF);
    }
}
