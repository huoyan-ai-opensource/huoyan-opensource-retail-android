package cc.huoyan.sdk.client.internal.ws;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

/**
 * {@link WsV2Codec} 纯逻辑单测：请求帧构造、响应解析、
 * binary 组帧/解帧与命令面路由判定。线格式断言以 SDK
 * {@code cc.huoyan.shared.ws.WsV2Codec} 契约为准（requestId/cmd/params 与
 * [4B 大端][requestId][图像]）。
 */
public class WsV2CodecTest {

    // ================= text 请求帧构造 =================

    @Test
    public void encodeRequest_含参数_三键齐全() {
        JSONObject params = new JSONObject(true);
        params.put("clazz", 123456789012345L);
        String text = WsV2Codec.encodeRequest("r-1", "itemLearn", params);

        JSONObject root = JSON.parseObject(text);
        assertEquals("r-1", root.getString("requestId"));
        assertEquals("itemLearn", root.getString("cmd"));
        JSONObject parsedParams = root.getJSONObject("params");
        assertNotNull(parsedParams);
        assertEquals(123456789012345L, parsedParams.getLongValue("clazz"));
    }

    @Test
    public void encodeRequest_无参数_省略params键() {
        String text = WsV2Codec.encodeRequest("r-2", "getStatus", null);
        JSONObject root = JSON.parseObject(text);
        assertEquals("r-2", root.getString("requestId"));
        assertEquals("getStatus", root.getString("cmd"));
        assertFalse(root.containsKey("params"));
    }

    @Test
    public void encodeRequest_空参数对象_同样省略params键() {
        String text = WsV2Codec.encodeRequest("r-3", "renewCert", new JSONObject(true));
        assertFalse(JSON.parseObject(text).containsKey("params"));
    }

    @Test
    public void encodeRequest_数值参数以数字上线不为字符串() {
        JSONObject params = new JSONObject(true);
        params.put("rotate", 90);
        String text = WsV2Codec.encodeRequest("r-4", "recognize", params);
        Object rotate = JSON.parseObject(text).getJSONObject("params").get("rotate");
        assertTrue("rotate 应为数字（SDK 侧按 Integer/Long 解析）", rotate instanceof Number);
    }

    // ================= text 响应解析 =================

    @Test
    public void parseResponse_成功回执_code0携带data() {
        WsV2Codec.Response response = WsV2Codec.parseResponse(
                "{\"requestId\":\"r-1\",\"code\":0,\"data\":{\"api_version\":2}}");
        assertNotNull(response);
        assertEquals("r-1", response.getRequestId());
        assertEquals(0, response.getCode());
        assertTrue(response.isOk());
        assertEquals(2, response.getData().getIntValue("api_version"));
        assertNull(response.getReason());
    }

    @Test
    public void parseResponse_成功回执缺失data_归一为空对象() {
        WsV2Codec.Response response = WsV2Codec.parseResponse(
                "{\"requestId\":\"r-2\",\"code\":0}");
        assertNotNull(response);
        assertTrue(response.isOk());
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    public void parseResponse_错误回执_携带code与reason() {
        WsV2Codec.Response response = WsV2Codec.parseResponse(
                "{\"requestId\":\"r-3\",\"code\":-1,\"reason\":\"参数错误\"}");
        assertNotNull(response);
        assertEquals(-1, response.getCode());
        assertFalse(response.isOk());
        assertEquals("参数错误", response.getReason());
        assertNull(response.getData());
    }

    @Test
    public void parseResponse_协议段错误码原样透传() {
        WsV2Codec.Response response = WsV2Codec.parseResponse(
                "{\"requestId\":\"r-4\",\"code\":40005,\"reason\":\"requestId 在途重复\"}");
        assertNotNull(response);
        assertEquals(40005, response.getCode());
    }

    @Test
    public void parseResponse_非JSON文本返回null() {
        assertNull(WsV2Codec.parseResponse("not json"));
    }

    @Test
    public void parseResponse_JSON数组返回null() {
        assertNull(WsV2Codec.parseResponse("[1,2,3]"));
    }

    @Test
    public void parseResponse_缺code键返回null() {
        assertNull(WsV2Codec.parseResponse("{\"requestId\":\"r-5\",\"data\":{}}"));
    }

    @Test
    public void parseResponse_空文本返回null() {
        assertNull(WsV2Codec.parseResponse(""));
        assertNull(WsV2Codec.parseResponse(null));
    }

    @Test
    public void parseResponse_无requestId的事件文本_不进路由() {
        // 事件广播（无 requestId）不应误路由：requestId 解析为 null 由调用方丢弃
        WsV2Codec.Response response = WsV2Codec.parseResponse(
                "{\"event\":\"model_ready\",\"payload\":{}}");
        assertNull(response);
    }

    // ================= binary 帧 =================

    @Test
    public void binaryFrame_组帧解帧回环_asciiRequestId() {
        byte[] image = new byte[]{1, 2, 3, 4, 5};
        byte[] frame = WsV2Codec.encodeBinaryFrame("r-9", image);

        WsV2Codec.Frame decoded = WsV2Codec.decodeBinaryFrame(frame);
        assertNotNull(decoded);
        assertEquals("r-9", decoded.getRequestId());
        assertEquals(5, decoded.getImage().length);
        assertEquals(3, decoded.getImage()[2]);
    }

    @Test
    public void binaryFrame_组帧解帧回环_utf8多字节RequestId() {
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xAB};
        String requestId = "会话-1";
        byte[] frame = WsV2Codec.encodeBinaryFrame(requestId, image);

        WsV2Codec.Frame decoded = WsV2Codec.decodeBinaryFrame(frame);
        assertNotNull(decoded);
        assertEquals(requestId, decoded.getRequestId());
        assertEquals(2, decoded.getImage().length);
    }

    @Test
    public void binaryFrame_帧头为大端长度() {
        byte[] frame = WsV2Codec.encodeBinaryFrame("abcd", new byte[2]);
        // "abcd" 4 字节：大端 4B 头应为 00 00 00 04
        assertEquals(0, frame[0]);
        assertEquals(0, frame[1]);
        assertEquals(0, frame[2]);
        assertEquals(4, frame[3]);
        assertEquals('a', frame[4]);
    }

    @Test
    public void binaryFrame_帧长不足返回null() {
        assertNull(WsV2Codec.decodeBinaryFrame(new byte[]{0, 0, 0}));
        assertNull(WsV2Codec.decodeBinaryFrame(new byte[0]));
        assertNull(WsV2Codec.decodeBinaryFrame(null));
    }

    @Test
    public void binaryFrame_声明越界返回null() {
        // 头声明 10 字节但实际 requestId 只有 2 字节且无图像
        byte[] frame = new byte[6];
        frame[3] = 10;
        assertNull(WsV2Codec.decodeBinaryFrame(frame));
    }

    @Test
    public void binaryFrame_图像为空返回null() {
        // requestId 完整但图像零字节：4B 头 + 4B id 无余量 → 非法
        byte[] frame = new byte[8];
        frame[3] = 4;
        frame[4] = 'a';
        assertNull(WsV2Codec.decodeBinaryFrame(frame));
    }

    // ================= 命令面 =================

    @Test
    public void requiresImage_四需图命令() {
        assertTrue(WsV2Codec.requiresImage(WsV2Codec.CMD_DETECT));
        assertTrue(WsV2Codec.requiresImage(WsV2Codec.CMD_RECOGNIZE));
        assertTrue(WsV2Codec.requiresImage(WsV2Codec.CMD_ITEM_LEARN));
        assertTrue(WsV2Codec.requiresImage(WsV2Codec.CMD_BATCH_LEARN));
    }

    @Test
    public void requiresImage_非需图命令为false() {
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_GET_STATUS));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_ITEM_FORGET));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_SUBMIT_FEEDBACK));
        assertTrue(WsV2Codec.requiresImage(WsV2Codec.CMD_UPLOAD_PHOTO));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_INIT));
        assertFalse("Issue #9 绑定/商品 CRUD 均为纯 text 命令",
                WsV2Codec.requiresImage(WsV2Codec.CMD_BIND_DEVICE));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_CREATE_GOODS));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_UPDATE_GOODS));
        assertFalse(WsV2Codec.requiresImage(WsV2Codec.CMD_DELETE_GOODS));
        assertFalse(WsV2Codec.requiresImage("unknown_cmd"));
    }

    @Test
    public void 命令名_与SDKStaticConfig镜像() {
        // 命令面命令名逐一锁定（漂移即测试失败）；
        // SDK 统一小驼峰（与 AIDL 一致），旧 snake_case 名废弃
        assertEquals("getStatus", WsV2Codec.CMD_GET_STATUS);
        assertEquals("recognize", WsV2Codec.CMD_RECOGNIZE);
        assertEquals("getCapabilities", WsV2Codec.CMD_GET_CAPABILITIES);
        assertEquals("getCertInfo", WsV2Codec.CMD_GET_CERT_INFO);
        assertEquals("getDownloadProgress", WsV2Codec.CMD_GET_DOWNLOAD_PROGRESS);
        assertEquals("activate", WsV2Codec.CMD_ACTIVATE);
        assertEquals("setBaseUrl", WsV2Codec.CMD_SET_BASE_URL);
        assertEquals("detect", WsV2Codec.CMD_DETECT);
        assertEquals("itemLearn", WsV2Codec.CMD_ITEM_LEARN);
        assertEquals("batchLearn", WsV2Codec.CMD_BATCH_LEARN);
        assertEquals("itemForget", WsV2Codec.CMD_ITEM_FORGET);
        assertEquals("itemForgetByUid", WsV2Codec.CMD_ITEM_FORGET_BY_UID);
        assertEquals("getItemClazzList", WsV2Codec.CMD_GET_ITEM_CLAZZ_LIST);
        assertEquals("getItemClazzImages", WsV2Codec.CMD_GET_ITEM_CLAZZ_IMAGES);
        assertEquals("submitFeedback", WsV2Codec.CMD_SUBMIT_FEEDBACK);
        assertEquals("init", WsV2Codec.CMD_INIT);
        // SDK 尾插命令（SDK StaticConfig CMD_BIND_DEVICE/
        // CMD_CREATE_CATALOG/CMD_UPDATE_CATALOG/CMD_DELETE_CATALOG 线名）
        assertEquals("bindDevice", WsV2Codec.CMD_BIND_DEVICE);
        assertEquals("createGoods", WsV2Codec.CMD_CREATE_GOODS);
        assertEquals("updateGoods", WsV2Codec.CMD_UPDATE_GOODS);
        assertEquals("deleteGoods", WsV2Codec.CMD_DELETE_GOODS);
    }

    @Test
    public void 缺省端点_回环18086() {
        assertEquals("127.0.0.1", WsV2Codec.DEFAULT_HOST);
        assertEquals(18086, WsV2Codec.DEFAULT_PORT);
        assertEquals("/ws", WsV2Codec.DEFAULT_PATH);
        assertEquals("ws://127.0.0.1:18086/ws", WsV2Codec.DEFAULT_ENDPOINT);
    }

    // ================= 端点解析（阶段 H SP_WS_ENDPOINT 覆盖） =================

    @Test
    public void parseEndpoint_空与null_回退缺省() {
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint(null));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint(""));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("   "));
    }

    @Test
    public void parseEndpoint_非ws前缀_回退缺省() {
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("http://192.168.1.115:18086"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("wss://192.168.1.115:18086/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("192.168.1.115:18086"));
    }

    @Test
    public void parseEndpoint_空host_回退缺省() {
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://:18086/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws:///ws"));
    }

    @Test
    public void parseEndpoint_C50实测地址_含路径() {
        WsV2Codec.Endpoint endpoint = WsV2Codec.parseEndpoint("ws://192.168.1.115:18086/ws");
        assertEquals("192.168.1.115", endpoint.getHost());
        assertEquals(18086, endpoint.getPort());
        assertEquals("/ws", endpoint.getPath());
        assertEquals("ws://192.168.1.115:18086/ws", endpoint.toUrl());
    }

    @Test
    public void parseEndpoint_无路径_补缺省路径() {
        WsV2Codec.Endpoint endpoint = WsV2Codec.parseEndpoint("ws://192.168.1.115:18086");
        assertEquals("192.168.1.115", endpoint.getHost());
        assertEquals(18086, endpoint.getPort());
        assertEquals("/ws", endpoint.getPath());
    }

    @Test
    public void parseEndpoint_根路径_补缺省路径() {
        assertEquals("/ws", WsV2Codec.parseEndpoint("ws://192.168.1.115:18086/").getPath());
    }

    @Test
    public void parseEndpoint_自定义路径_原样保留() {
        WsV2Codec.Endpoint endpoint = WsV2Codec.parseEndpoint("ws://10.0.0.8:9000/sdk-ws");
        assertEquals("10.0.0.8", endpoint.getHost());
        assertEquals(9000, endpoint.getPort());
        assertEquals("/sdk-ws", endpoint.getPath());
        assertEquals("ws://10.0.0.8:9000/sdk-ws", endpoint.toUrl());
    }

    @Test
    public void parseEndpoint_无端口_补缺省端口保留host() {
        // 端口缺省：只补默认端口（host 合法保留，便于免写端口配置）
        WsV2Codec.Endpoint endpoint = WsV2Codec.parseEndpoint("ws://192.168.1.115/ws");
        assertEquals("192.168.1.115", endpoint.getHost());
        assertEquals(18086, endpoint.getPort());
    }

    @Test
    public void parseEndpoint_端口越界或不可解析_整体回退缺省端点() {
        // 收尾 P3：显式携带非法端口（0/负/超 65535/非数字/空）产出非法 URI
        // 会导致连接失败无限退避重连——整体回退缺省端点
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:0/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:-1/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:65536/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:99999/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:abc/ws"));
        assertEquals(WsV2Codec.Endpoint.def(), WsV2Codec.parseEndpoint("ws://192.168.1.115:/ws"));
    }

    @Test
    public void parseEndpoint_端口边界值_合法保留() {
        assertEquals(1, WsV2Codec.parseEndpoint("ws://192.168.1.115:1/ws").getPort());
        assertEquals(65535, WsV2Codec.parseEndpoint("ws://192.168.1.115:65535/ws").getPort());
    }

    @Test
    public void parseEndpoint_带空白_先trim再解析() {
        WsV2Codec.Endpoint endpoint = WsV2Codec.parseEndpoint("  ws://192.168.1.115:18086/ws  ");
        assertEquals("192.168.1.115", endpoint.getHost());
        assertEquals(18086, endpoint.getPort());
    }

    @Test
    public void utf8字节数_与帧头一致() {
        String requestId = "节点-42";
        byte[] frame = WsV2Codec.encodeBinaryFrame(requestId, new byte[1]);
        int declared = ((frame[0] & 0xFF) << 24) | ((frame[1] & 0xFF) << 16)
                | ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        assertEquals(requestId.getBytes(StandardCharsets.UTF_8).length, declared);
    }
}
