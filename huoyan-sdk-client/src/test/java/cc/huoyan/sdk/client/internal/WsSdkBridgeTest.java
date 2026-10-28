package cc.huoyan.sdk.client.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.BaseResultParcel;

import org.junit.Test;

/**
 * {@link WsSdkBridge} 单测：未连接哨兵语义（18 方法面）
 * 与图像载体 → WS rgb 载体映射纯逻辑。连接/重连/在线请求路由属真机链路
 * （需 SDK WS 服务端），不在此覆盖。
 */
public class WsSdkBridgeTest {

    // ================= 未连接哨兵（接口契约） =================

    @Test
    public void 未连接_生命周期与就绪哨兵() {
        WsSdkBridge bridge = new WsSdkBridge();
        assertFalse(bridge.isConnected());
        BaseResultParcel ready = bridge.init(null);
        assertNotNull("Issue #66 占位为哨兵快照（永非 null）", ready);
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, ready.getCode());
        assertNotNull("哨兵快照 reason 带诊断", ready.getReason());
    }

    @Test
    public void 未连接_查询与图像方法返回null或0() {
        WsSdkBridge bridge = new WsSdkBridge();
        assertNull(bridge.getFailedImages());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.backupRecognitionData().getCode());
        assertNull(bridge.getRecognitionDataInfo());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.restoreRecognitionData().getCode());
        assertNull(bridge.getCapabilities());
        assertNull(bridge.getStatus());
        assertNull(bridge.getCertInfo());
        assertNull(bridge.getDownloadProgress());
        assertNull(bridge.detect(new ImageRequestParcel("key", null)));
        assertNull(bridge.recognize(new ImageRequestParcel("key", null), 0));
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.itemLearn(new ImageRequestParcel("key", null), "100").getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.batchLearn(new ImageRequestParcel("key", null), "100").getCode());
        assertNull(bridge.getItemClazzList(1, 100));
        assertNull(bridge.getItemClazzImages("100", 1, 100));
    }

    @Test
    public void 未连接_动作方法返回未发送回执() {
        WsSdkBridge bridge = new WsSdkBridge();
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.activate("ACT", "model-x").getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.setBaseUrl("https://example.com").getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.itemForget("100").getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.itemForgetByUid(1L).getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.submitFeedback(null).getCode());
    }

    @Test
    public void 未连接_绑定与商品CRUD返回未发送回执() {
        WsSdkBridge bridge = new WsSdkBridge();
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.bindDevice(new BindDeviceParcel(42L, "10086", null, null)).getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.createGoods(new GoodsSaveParcel("可颂", "3001", null,
                null, null, null, null, null, null, null, null, Long.valueOf(1200L),
                null, null)).getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.updateGoods("1770001", new GoodsSaveParcel("可颂2", null,
                null, null, null, null, null, null, null, null, null, null, null, null)).getCode());
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, bridge.deleteGoods("1770001").getCode());
    }

    @Test
    public void disconnect幂等_未启动时不抛异常() {
        WsSdkBridge bridge = new WsSdkBridge();
        bridge.disconnect();
        bridge.disconnect();
        assertFalse(bridge.isConnected());
    }

    // ================= 图像载体 → WS rgb 载体映射 =================

    @Test
    public void 载体映射_bytes带几何_rgb直传() {
        byte[] rgb = new byte[6];   // 1x1 像素 * 3 字节，双倍余量
        ImageRequestParcel image = new ImageRequestParcel(
                null, rgb, rgb.length, 1, 1, ImageRequestParcel.FORMAT_RGB888);

        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNull(payload.errorReason);
        assertSame(rgb, payload.bytes);
        assertEquals(1, payload.width);
        assertEquals(1, payload.height);
    }

    @Test
    public void 载体映射_size前缀截取有效字节() {
        byte[] rgb = new byte[12];
        ImageRequestParcel image = new ImageRequestParcel(
                null, rgb, 6, 2, 1, ImageRequestParcel.FORMAT_RGB888);

        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNull(payload.errorReason);
        assertEquals(6, payload.bytes.length);
    }

    @Test
    public void 载体映射_legacy无几何bytes_不可映射() {
        ImageRequestParcel image = new ImageRequestParcel(null, new byte[6]);

        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNotNull(payload.errorReason);
        assertNull(payload.bytes);
    }

    @Test
    public void 载体映射_shmKey不支持() {
        ImageRequestParcel image = new ImageRequestParcel("shm-key", null);

        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNotNull(payload.errorReason);
        assertTrue(payload.errorReason.contains("shmKey"));
    }

    @Test
    public void 载体映射_null载体报错() {
        assertNotNull(WsSdkBridge.toRgbPayload(null).errorReason);
    }

    @Test
    public void 载体映射_契约校验失败原因透传() {
        // 双载体（shmKey + bytes）违反恰选一
        ImageRequestParcel image = new ImageRequestParcel("key", new byte[6]);

        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNotNull(payload.errorReason);
        assertTrue(payload.errorReason.contains("恰选一"));
    }

    @Test
    public void 载体映射_几何不符字节数报错() {
        // 4x4 像素需 48 字节，仅给 6 字节
        ImageRequestParcel image = new ImageRequestParcel(
                null, new byte[6], 6, 4, 4, ImageRequestParcel.FORMAT_RGB888);

        assertNotNull(WsSdkBridge.toRgbPayload(image).errorReason);
    }
    @Test
    public void wsLargeRgbRetainsOriginalBytesWithoutSharedMemory() {
        byte[] rgb = new byte[1076 * 756 * 3];
        ImageRequestParcel image = new WsSdkBridge().createImageRequest(rgb, 1076, 756);
        assertNull(image.getSharedMemory());
        assertSame(rgb, image.getBytes());
        WsSdkBridge.ImagePayload payload = WsSdkBridge.toRgbPayload(image);
        assertNull(payload.errorReason);
        assertSame(rgb, payload.bytes);
        // 同一个大数组如果作为 Binder 内联载体仍应被 AIDL 契约拒绝。
        assertNotNull(image.validate());
    }

    @Test
    public void wsRgbRejectsInvalidSizeFormatAndOverflowGeometry() {
        for (ImageRequestParcel image : new ImageRequestParcel[]{
                new ImageRequestParcel(null, new byte[6], -1, 2, 1, 1),
                new ImageRequestParcel(null, new byte[6], 7, 2, 1, 1),
                new ImageRequestParcel(null, new byte[6], 6, 2, 1, 0),
                new ImageRequestParcel(null, new byte[6], 6, Integer.MAX_VALUE, Integer.MAX_VALUE, 1)}) {
            assertNotNull(WsSdkBridge.toRgbPayload(image).errorReason);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void wsFactoryRejectsMismatchedGeometry() {
        new WsSdkBridge().createImageRequest(new byte[6], 3, 3);
    }

}
