package cc.huoyan.sdk.client.internal;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import cc.huoyan.sdk.client.internal.ws.WsV2Codec;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import cc.huoyan.app.aidl.ImageRequestParcel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** 回环 WS 服务验证真实请求命令、参数和响应路由，不依赖现场 SDK。 */
public class WsSdkBridgeWireTest {
    @Test(timeout = 15000)
    public void newContract_roundTripsThroughWebSocket() throws Exception {
        RecordingServer server = new RecordingServer();
        CountDownLatch connected = new CountDownLatch(1);
        WsSdkBridge bridge = null;
        server.start();
        try {
            assertTrue(server.started.await(3, TimeUnit.SECONDS));
            bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                    message -> {
                        if (message.startsWith("[Biz] ws_connect ")) {
                            connected.countDown();
                        }
                    }, () -> true);
            bridge.connect();
            assertTrue(connected.await(3, TimeUnit.SECONDS));
            for (int code : new int[]{0, -110, -111, -1}) {
                server.code = code;
                assertEquals(code, bridge.init("1.2.3").getCode());
                JSONObject params = server.take("init").getJSONObject("params");
                assertEquals("1.2.3", params.getString("app_version"));
                assertEquals(1, params.size());
            }
            server.code = 0;
            server.data = JSON.parseObject("{\"sdk_state\":\"NOT_ACTIVATED\",\"model_ready\":false}");
            assertEquals("NOT_ACTIVATED", bridge.getStatus().getSdkState());
            assertNoParams(server.take("getStatus"));

            server.data = JSON.parseObject("{\"models\":[{\"model_code\":\"fresh\",\"model_name\":\"生鲜\",\"chip\":\"rk3576\",\"current\":false}]}");
            assertEquals("fresh", bridge.queryLicenseModels("INPUT-CODE").getModels().get(0).getModelCode());
            JSONObject queryLicense = server.take("queryLicenseModels").getJSONObject("params");
            assertEquals("INPUT-CODE", queryLicense.getString("activation_code"));
            assertEquals(1, queryLicense.size());
            assertEquals("fresh", bridge.queryDeviceModels().getModels().get(0).getModelCode());
            assertNoParams(server.take("queryDeviceModels"));

            server.data = JSON.parseObject("{\"enabled\":true}");
            assertTrue(bridge.getCloudStatus().isEnabled());
            assertNoParams(server.take("getCloudStatus"));
            assertTrue(bridge.setCloudStatus(true).isEnabled());
            assertEquals(Boolean.TRUE, server.take("setCloudStatus").getJSONObject("params").get("enabled"));
            server.code = -1;
            assertEquals(-1, bridge.setCloudStatus(false).getCode());
            assertEquals(Boolean.FALSE, server.take("setCloudStatus").getJSONObject("params").get("enabled"));
            server.code = 0;
            assertTrue(bridge.getCloudStatus().isEnabled());
            assertNoParams(server.take("getCloudStatus"));
            server.data = new JSONObject();
            assertTrue(bridge.getCloudStatus().getCode() != 0);
            assertNoParams(server.take("getCloudStatus"));

            server.data = JSON.parseObject("{\"clazz\":[\"101\"],\"uid_count\":[2],\"total\":101,"
                    + "\"current\":2,\"size\":100,\"pages\":2,\"thumbnails\":[\"/images/9\"]}");
            assertEquals(101, bridge.getItemClazzList(2, 100).getTotal());
            JSONObject params = server.take("getItemClazzList").getJSONObject("params");
            assertEquals(2, params.getIntValue("page"));
            assertEquals(100, params.getIntValue("size"));

            server.data = JSON.parseObject("{\"total\":0,\"current\":3,\"size\":20,\"pages\":0,\"items\":[]}");
            assertTrue(bridge.getItemClazzImages("9007199254740993", 3, 20).getItems().isEmpty());
            params = server.take("getItemClazzImages").getJSONObject("params");
            assertEquals(9007199254740993L, params.getLongValue("clazz"));
            assertEquals(3, params.getIntValue("page"));
            assertEquals(20, params.getIntValue("size"));
            assertTrue(bridge.getFailedImages().getItems().isEmpty());
            assertNoParams(server.take("getFailedImages"));
            assertEquals(0, bridge.backupRecognitionData().getCode());
            assertNoParams(server.take("backupRecognitionData"));
            assertEquals(0, bridge.getRecognitionDataInfo().getBackupTimeEpochMs());
            assertNoParams(server.take("getRecognitionDataInfo"));
            assertEquals(0, bridge.restoreRecognitionData().getCode());
            assertNoParams(server.take("restoreRecognitionData"));
        } finally {
            if (bridge != null) {
                bridge.disconnect();
            }
            server.stop(1000);
        }
    }

    @Test(timeout = 15000)
    public void publicGoodsAndBackupApiRoundTripsThroughRealSocket() throws Exception {
        RecordingServer server = new RecordingServer();
        CountDownLatch connected = new CountDownLatch(1);
        server.start();
        assertTrue(server.started.await(3, TimeUnit.SECONDS));
        WsSdkBridge transport = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                message -> { if (message.startsWith("[Biz] ws_connect ")) connected.countDown(); }, () -> true);
        cc.huoyan.sdk.client.HuoyanSdkClient client = new DefaultSdkClient(transport, true, "1.2.3");
        try {
            client.connect(); assertTrue(connected.await(3, TimeUnit.SECONDS));
            server.data = JSON.parseObject("{\"request\":{\"account_id\":2115027529298350203,"
                    + "\"shop_id\":\"external-user:007\"},\"response\":{\"account_id\":\"2115027529298350203\","
                    + "\"shop_id\":\"internal-shop-9\"}}");
            cc.huoyan.sdk.client.model.BindingResult binding = client.bindDevice("external-user:007");
            JSONObject bindParams = server.take("bindDevice").getJSONObject("params");
            assertEquals(2115027529298350203L, bindParams.getLongValue("account_id"));
            assertEquals("external-user:007", bindParams.getString("shop_id"));
            assertFalse(bindParams.containsKey("shop_name"));
            assertFalse(bindParams.containsKey("shop_no"));
            assertEquals("external-user:007", binding.getRequest().getShopId());
            assertEquals("internal-shop-9", binding.getShopId());
            server.data = JSON.parseObject("{\"records\":[{\"id\":\"9007199254740993\",\"price\":9007199254740993,\"is_deleted\":1}],\"total\":1,\"pages\":1,\"sync_timestamp\":999}");
            cc.huoyan.sdk.client.model.GoodsListResult list = client.listGoods(123, 2, 50);
            assertEquals(999, list.getSyncTimestamp());
            assertEquals(9007199254740993L, list.getRecords().get(0).getPrice());
            assertEquals(1, list.getRecords().get(0).getIsDeleted());
            assertEquals(123, server.take("listGoods").getJSONObject("params").getLongValue("sync_timestamp"));
            assertEquals(1, client.queryGoods("面包", null, null, 1, 2, 50, "name", "desc").getTotal());
            JSONObject query = server.take("queryGoods").getJSONObject("params");
            assertEquals("面包", query.getString("keyword")); assertEquals("desc", query.getString("sort_order"));
            assertFalse(query.containsKey("name"));
            server.data = JSON.parseObject("{\"count\":7,\"goods\":{\"id\":\"9\"}}");
            assertEquals(7, client.getGoodsCount(null, null, null, -1).getCount()); server.take("getGoodsCount");
            assertEquals("9", client.getGoods("9").getRecord().getGoodsId()); server.take("getGoods");
            server.data = JSON.parseObject("{\"phase\":\"SUCCESS\",\"sync_timestamp\":321,\"added\":2,\"fail_count\":1}");
            assertEquals(2, client.downloadGoods(100).getAdded());
            assertEquals(100, server.take("downloadGoods").getJSONObject("params").getIntValue("size"));
            assertEquals(321, client.getDownloadGoodsProgress().getSyncTimestamp()); server.take("getDownloadGoodsProgress");
            server.data = JSON.parseObject("{\"items\":[{\"goods_id\":\"9\",\"code\":0},{\"goods_id\":\"10\",\"code\":404,\"reason\":\"不存在\"}]}");
            cc.huoyan.sdk.client.model.GoodsSave changes = new cc.huoyan.sdk.client.model.GoodsSave(
                    null,null,null,null,null,null,null,null,null,null,null,0L,null,null);
            assertEquals(404, client.batchUpdateGoods(new String[]{"9","10"}, changes).getItems().get(1).getCode());
            JSONObject update = server.take("batchUpdateGoods").getJSONObject("params");
            assertEquals(0, update.getJSONObject("changes").getLongValue("price"));
            assertFalse(update.getJSONObject("changes").containsKey("sale_mode"));
            assertEquals(2, client.batchDeleteGoods(new String[]{"9","10"}).getItems().size()); server.take("batchDeleteGoods");
            server.data = JSON.parseObject("{\"phase\":\"SUCCESS\",\"learn_success\":2,\"learn_fail\":1,\"skipped_count\":3}");
            assertEquals(3, client.downloadImagesAndLearnByGoodsId(new long[]{9007199254740993L}).getSkippedCount());
            assertEquals(9007199254740993L, server.take("downloadImagesAndLearnByGoodsId").getJSONObject("params").getJSONArray("goods_ids").getLongValue(0));
            server.data = JSON.parseObject("{\"backup_time_epoch_ms\":456,\"schema_version\":3,\"file_format_version\":4}");
            assertEquals(0, client.backupRecognitionData().getCode()); server.take("backupRecognitionData");
            assertEquals(456, client.getRecognitionDataInfo().getBackupTimeEpochMs()); server.take("getRecognitionDataInfo");
            assertEquals(0, client.restoreRecognitionData().getCode()); server.take("restoreRecognitionData");
            server.data = JSON.parseObject("{\"photo_file_url\":\"https://files.example/photo.jpg\"}");
            server.imageMode = true;
            String url = client.uploadPhoto(cc.huoyan.sdk.client.SdkImage.encoded(
                    new byte[]{(byte)255,(byte)216,(byte)255},0,0,2)).getPhotoFileUrl();
            assertEquals("https://files.example/photo.jpg", url); server.take("uploadPhoto");
            server.imageMode = false;
            cc.huoyan.sdk.client.model.Feedback feedback = new cc.huoyan.sdk.client.model.Feedback(
                    "request-1", url, 1, java.util.Collections.singletonList(
                    new cc.huoyan.sdk.client.model.FeedbackItem(0,java.util.Collections.emptyList(),new cc.huoyan.sdk.client.model.FeedbackResult("42","面包","SDK识别"),10.0,20.0,30.0,40.0)));
            assertEquals(0, client.submitFeedback(feedback).getCode());
            JSONObject sent = server.take("submitFeedback").getJSONObject("params");
            assertEquals(url, sent.getString("photo_file_url"));
            assertEquals("request-1", sent.getString("request_id"));
            assertEquals(10.0, sent.getJSONArray("items").getJSONObject(0).getDoubleValue("x"), 0);
            server.code = 409;
            assertEquals(409, client.downloadGoods(100).getCode()); server.take("downloadGoods");
        } finally { client.close(); server.stop(1000); }
    }

    /** 验证坐标实际进入 WS 请求，且同一条目顺序与商品、反馈类型一致。 */
    @Test(timeout = 15000)
    public void feedbackCoordinatesTravelInGoodsForDetectAndStudy() throws Exception {
        RecordingServer server = new RecordingServer();
        CountDownLatch connected = new CountDownLatch(1);
        WsSdkBridge bridge = null;
        server.start();
        try {
            assertTrue(server.started.await(3, TimeUnit.SECONDS));
            bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                    message -> {
                        if (message.startsWith("[Biz] ws_connect ")) connected.countDown();
                    }, () -> true);
            bridge.connect();
            assertTrue(connected.await(3, TimeUnit.SECONDS));
            for (int type : new int[]{0, 1}) {
                cc.huoyan.app.aidl.FeedbackParcel feedback = new cc.huoyan.app.aidl.FeedbackParcel(
                        "feedback-" + type, "https://files/frame.jpg", type,
                        java.util.Arrays.asList(
                                new cc.huoyan.app.aidl.FeedbackItemParcel(0,java.util.Collections.emptyList(),new cc.huoyan.app.aidl.FeedbackResultParcel( "1001", "面包", "SDK识别"), 10D, 20D, 30D, 40D),
                                new cc.huoyan.app.aidl.FeedbackItemParcel(0,java.util.Collections.emptyList(),new cc.huoyan.app.aidl.FeedbackResultParcel( "1002", "可颂", "手动修改"), 50D, 60D, 70D, 80D)));
                server.code = type == 1 ? 0 : -9;
                assertEquals(server.code, bridge.submitFeedback(feedback).getCode());
                JSONObject params = server.take("submitFeedback").getJSONObject("params");
                assertEquals(feedback.getPhotoFileUrl(), params.getString("photo_file_url"));
                assertFalse(params.containsKey("shop_id"));
                assertEquals(type, params.getIntValue("feedback_type"));
                assertFalse(params.containsKey("token"));
                assertFalse(params.containsKey("device_id"));
                for (int i = 0; i < 2; i++) {
                    JSONObject goods = params.getJSONArray("items").getJSONObject(i);
                    assertEquals(10 + 40 * i, goods.getIntValue("x"));
                    assertEquals(20 + 40 * i, goods.getIntValue("y"));
                    assertEquals(30 + 40 * i, goods.getIntValue("width"));
                    assertEquals(40 + 40 * i, goods.getIntValue("height"));
                    assertEquals(0, goods.getIntValue("item_type"));
                    assertFalse(goods.containsKey("selected"));
                }
            }
        } finally {
            if (bridge != null) bridge.disconnect();
            server.stop(1000);
        }
    }

    @Test(timeout = 15000)
    public void largeRgbAllImageCommandsSendBinaryWithoutSharedMemory() throws Exception {
        RecordingServer server = new RecordingServer();
        server.imageMode = true;
        CountDownLatch connected = new CountDownLatch(1);
        WsSdkBridge bridge = null;
        server.start();
        try {
            assertTrue(server.started.await(3, TimeUnit.SECONDS));
            bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                    message -> {
                        if (message.startsWith("[Biz] ws_connect ")) connected.countDown();
                    }, () -> true);
            bridge.connect();
            assertTrue(connected.await(3, TimeUnit.SECONDS));
            for (int format : new int[]{ImageRequestParcel.FORMAT_JPEG, ImageRequestParcel.FORMAT_PNG}) {
                byte[] photo = new byte[2 * 1024 * 1024];
                photo[0] = 42;
                server.data = JSON.parseObject("{\"photo_file_url\":\"https://files/photo.jpg\"}");
                assertEquals("https://files/photo.jpg", bridge.uploadPhoto(bridge.createPhotoRequest(photo, format)).getPhotoFileUrl());
                JSONObject request = server.take("uploadPhoto");
                assertEquals(format == ImageRequestParcel.FORMAT_JPEG ? "jpeg" : "png",
                        request.getJSONObject("params").getString("format"));
                WsV2Codec.Frame frame = server.images.poll(2, TimeUnit.SECONDS);
                assertNotNull(frame);
                assertEquals(request.getString("requestId"), frame.getRequestId());
                assertArrayEquals(photo, frame.getImage());
            }
            server.data = new JSONObject();
            byte[] rgb = new byte[1076 * 756 * 3];
            for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) (i * 31);
            for (String cmd : new String[]{"detect", "recognize", "itemLearn", "batchLearn"}) {
                ImageRequestParcel image = bridge.createImageRequest(rgb, 1076, 756);
                assertNull(image.getSharedMemory());
                switch (cmd) {
                    case "detect": assertEquals(0, bridge.detect(image).getCode()); break;
                    case "recognize": assertEquals(0, bridge.recognize(image, 0).getCode()); break;
                    case "itemLearn": assertEquals(0, bridge.itemLearn(image, "101").getCode()); break;
                    default: assertEquals(0, bridge.batchLearn(image, "101").getCode()); break;
                }
                JSONObject request = server.take(cmd);
                JSONObject params = request.getJSONObject("params");
                assertEquals(1076, params.getIntValue("width"));
                assertEquals(756, params.getIntValue("height"));
                WsV2Codec.Frame binary = server.images.poll(2, TimeUnit.SECONDS);
                assertNotNull(binary);
                assertEquals(request.getString("requestId"), binary.getRequestId());
                assertArrayEquals(rgb, binary.getImage());
            }
        } finally {
            if (bridge != null) bridge.disconnect();
            server.stop(1000);
        }
    }

    @Test(timeout = 15000)
    public void jpegRecognitionAndDetectionDeclareFormatAndKeepOriginalBytes() throws Exception {
        RecordingServer server = new RecordingServer();
        server.imageMode = true;
        server.data = new JSONObject();
        CountDownLatch connected = new CountDownLatch(1);
        WsSdkBridge bridge = null;
        server.start();
        try {
            assertTrue(server.started.await(3, TimeUnit.SECONDS));
            bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                    message -> { if (message.startsWith("[Biz] ws_connect ")) connected.countDown(); },
                    () -> true);
            bridge.connect();
            assertTrue(connected.await(3, TimeUnit.SECONDS));
            byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 12, 34, (byte) 0xff, (byte) 0xd9};
            // 此处只验证传输；真实 JPEG 编解码由 BitmapJpegDeviceTest 覆盖。
            ImageRequestParcel image = new ImageRequestParcel(null, jpeg, jpeg.length,
                    1076, 756, ImageRequestParcel.FORMAT_JPEG);
            for (String cmd : new String[]{"recognize", "detect"}) {
                if ("recognize".equals(cmd)) assertEquals(0, bridge.recognize(image, 90).getCode());
                else assertEquals(0, bridge.detect(image).getCode());
                JSONObject request = server.take(cmd);
                assertEquals("jpeg", request.getJSONObject("params").getString("format"));
                if ("recognize".equals(cmd)) {
                    assertEquals(90, request.getJSONObject("params").getIntValue("rotate"));
                }
                WsV2Codec.Frame frame = server.images.poll(2, TimeUnit.SECONDS);
                assertNotNull(frame);
                assertEquals(request.getString("requestId"), frame.getRequestId());
                assertArrayEquals(jpeg, frame.getImage());
            }
        } finally {
            if (bridge != null) bridge.disconnect();
            server.stop(1000);
        }
    }

    private static void assertNoParams(JSONObject request) {
        JSONObject params = request.getJSONObject("params");
        assertTrue(params == null || params.isEmpty());
        assertFalse(request.containsKey("timeout_ms"));
        assertFalse(request.containsKey("app_version"));
    }

    /** 同机 WS 只拉起一次服务，断开释放绑定，再连接时创建新的绑定实例。 */
    @Test(timeout = 15000)
    public void localServiceFollowsWsLifecycleWithoutDuplicateBindings() throws Exception {
        RecordingServer server = new RecordingServer();
        java.util.concurrent.atomic.AtomicInteger created = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger connects = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger disconnects = new java.util.concurrent.atomic.AtomicInteger();
        server.start();
        assertTrue(server.started.await(3, TimeUnit.SECONDS));
        WsSdkBridge bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                message -> {}, () -> true, () -> {
                    created.incrementAndGet();
                    return (SdkChannelBridge) java.lang.reflect.Proxy.newProxyInstance(
                            SdkChannelBridge.class.getClassLoader(), new Class[]{SdkChannelBridge.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("connect")) connects.incrementAndGet();
                                else if (method.getName().equals("disconnect")) disconnects.incrementAndGet();
                                else throw new AssertionError("拉起服务不应调用业务方法：" + method.getName());
                                return null;
                            });
                });
        try {
            bridge.connect();
            bridge.connect();
            assertEquals(1, created.get());
            assertEquals(1, connects.get());
            bridge.disconnect();
            bridge.disconnect();
            assertEquals(1, disconnects.get());
            bridge.connect();
            assertEquals(2, created.get());
            assertEquals(2, connects.get());
        } finally {
            bridge.disconnect();
            server.stop(1000);
        }
        assertEquals(2, disconnects.get());
    }

    /** 长任务未返回时，下载与学习进度仍可独立查询，终态按学习统计映射。 */
    @Test(timeout = 15000)
    public void imagePackageProgressWhileDownloadIsInFlight() throws Exception {
        RecordingServer server = new RecordingServer();
        server.holdDownload = true;
        server.start();
        WsSdkBridge bridge = null;
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertTrue(server.started.await(3, TimeUnit.SECONDS));
            bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                    message -> {}, () -> true);
            bridge.connect();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!bridge.isConnected() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(bridge.isConnected());
            WsSdkBridge connected = bridge;
            java.util.concurrent.Future<cc.huoyan.app.aidl.ImageFlowProgressParcel> task =
                    worker.submit(connected::downloadImagesAndLearn);
            assertNoParams(server.take("downloadImagesAndLearn"));
            server.data = JSON.parseObject("{\"phase\":\"DOWNLOADING\",\"current\":4,\"total\":10,"
                    + "\"success_count\":3,\"fail_count\":1,\"fail_stats\":\"网络错误=1\"}");
            cc.huoyan.app.aidl.ImageFlowProgressParcel progress = bridge.getImageDownloadProgress();
            assertNoParams(server.take("getImageDownloadProgress"));
            assertTrue(progress.isActive());
            assertEquals(4, progress.getCurrent());
            assertEquals(1, progress.getFailCount());
            assertEquals("网络错误=1", progress.getFailStats());
            server.data = JSON.parseObject("{\"phase\":\"LEARNING\",\"current\":2,\"total\":9,"
                    + "\"success_count\":2,\"fail_count\":0}");
            assertEquals(2, bridge.getImageLearnProgress().getSuccessCount());
            assertNoParams(server.take("getImageLearnProgress"));
            assertFalse(task.isDone());
            server.data = JSON.parseObject("{\"phase\":\"SUCCESS\",\"download_success\":9,"
                    + "\"download_fail\":1,\"learn_success\":8,\"learn_fail\":1,"
                    + "\"learn_fail_stats\":\"学习失败=1\",\"public_dir\":\"/sdcard/huoyan/goods\"}");
            server.reply(server.downloadSocket, server.downloadRequest);
            progress = task.get(2, TimeUnit.SECONDS);
            assertEquals(0, progress.getCode());
            assertFalse(progress.isActive());
            assertEquals(9, progress.getTotal());
            assertEquals(8, progress.getSuccessCount());
            assertEquals("学习失败=1", progress.getFailStats());
            server.code = -1;
            assertEquals(-1, bridge.getImageLearnProgress().getCode());
            assertNoParams(server.take("getImageLearnProgress"));
        } finally {
            if (bridge != null) bridge.disconnect();
            worker.shutdownNow();
            server.stop(1000);
        }
    }

    @Test(timeout = 10000)
    public void acceptedWriteDisconnectAndTimeoutRemainUnknownWithoutReplay() throws Exception {
        RecordingServer server = new RecordingServer();
        server.holdDownload = true;
        server.start();
        assertTrue(server.started.await(2, TimeUnit.SECONDS));
        CountDownLatch connected = new CountDownLatch(1);
        WsSdkBridge bridge = new WsSdkBridge(new WsV2Codec.Endpoint("127.0.0.1", server.getPort(), "/ws"),
                message -> { if (message.startsWith("[Biz] ws_connect ")) connected.countDown(); }, () -> true);
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            bridge.connect(); assertTrue(connected.await(2, TimeUnit.SECONDS));
            java.lang.reflect.Method request = WsSdkBridge.class.getDeclaredMethod("request", String.class, JSONObject.class, byte[].class, long.class);
            request.setAccessible(true);
            WsV2Codec.Response timeout = (WsV2Codec.Response) request.invoke(bridge, "downloadImagesAndLearn", null, null, 50L);
            server.take("downloadImagesAndLearn");
            assertEquals(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, timeout.getCode());
            java.util.concurrent.Future<cc.huoyan.app.aidl.ImageFlowProgressParcel> result = worker.submit(bridge::downloadImagesAndLearn);
            server.take("downloadImagesAndLearn");
            bridge.disconnect();
            assertEquals(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, result.get(2,TimeUnit.SECONDS).getCode());
            assertTrue(server.requests.isEmpty());
        } finally { bridge.disconnect(); worker.shutdownNow(); server.stop(1000); }
    }

    private static final class RecordingServer extends WebSocketServer {
        final CountDownLatch started = new CountDownLatch(1);
        final BlockingQueue<JSONObject> requests = new LinkedBlockingQueue<>();
        final BlockingQueue<WsV2Codec.Frame> images = new LinkedBlockingQueue<>();
        volatile boolean imageMode;
        volatile boolean holdDownload;
        volatile WebSocket downloadSocket;
        volatile JSONObject downloadRequest;
        volatile JSONObject imageRequest;
        volatile int code;
        volatile JSONObject data = new JSONObject();

        RecordingServer() {
            super(new InetSocketAddress("127.0.0.1", 0));
        }

        JSONObject take(String cmd) throws InterruptedException {
            JSONObject request = requests.poll(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals(cmd, request.getString("cmd"));
            return request;
        }

        @Override
        public void onOpen(WebSocket socket, ClientHandshake handshake) { }

        @Override
        public void onClose(WebSocket socket, int code, String reason, boolean remote) { }

        @Override
        public void onMessage(WebSocket socket, String message) {
            JSONObject request = JSON.parseObject(message);
            if (holdDownload && "downloadImagesAndLearn".equals(request.getString("cmd"))) {
                downloadSocket = socket;
                downloadRequest = request;
                requests.add(request);
                return;
            }
            requests.add(request);
            if (imageMode) {
                imageRequest = request;
                return;
            }
            reply(socket, request);
        }

        /** 收到完整二进制图像后才回执，验证实际发出的字节。 */
        @Override
        public void onMessage(WebSocket socket, ByteBuffer message) {
            byte[] bytes = new byte[message.remaining()];
            message.get(bytes);
            WsV2Codec.Frame frame = WsV2Codec.decodeBinaryFrame(bytes);
            if (frame != null) images.add(frame);
            reply(socket, imageRequest);
        }

        private void reply(WebSocket socket, JSONObject request) {
            JSONObject response = new JSONObject();
            response.put("requestId", request.getString("requestId"));
            response.put("code", code);
            response.put("reason", code == 0 ? null : "初始化动作结果");
            response.put("data", data);
            socket.send(response.toJSONString());
        }

        @Override
        public void onError(WebSocket socket, Exception error) { }

        @Override
        public void onStart() {
            started.countDown();
        }
    }
}
