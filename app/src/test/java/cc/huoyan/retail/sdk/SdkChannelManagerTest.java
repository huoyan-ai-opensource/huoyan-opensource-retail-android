package cc.huoyan.retail.sdk;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cc.huoyan.retail.sdk.SdkChannelManager.ChannelStore;
import cc.huoyan.sdk.client.SdkConfig.Channel;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/**
 * {@link SdkChannelManager} 通道选择/默认值/切换写存储/桥分发单测。
 * <p>存储经 {@link ChannelStore} 注入内存实现（生产为 SPManager，依赖 Android
 * 不可纯 JUnit 测，见项目测试约定）；SP 真实读写由设置页人工/平台场景验证。
 */
public class SdkChannelManagerTest {

    /** 内存存储：模拟 SP 行为（get 无记录返回 null，put 覆盖写）。 */
    private static final class MemoryStore implements ChannelStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get() {
            return values.get(SdkChannelManager.class.getName() + ".channel");
        }

        @Override
        public void put(String value) {
            values.put(SdkChannelManager.class.getName() + ".channel", value);
        }
    }

    @Test
    public void getChannel_noRecord_defaultsToAidl() {
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(), channel -> new SdkChannelFakes.FakeSdkChannelBridge());
        assertEquals(Channel.AIDL, manager.getChannel());
    }

    @Test
    public void setChannel_persistsValueAndReadsBack() {
        MemoryStore store = new MemoryStore();
        SdkChannelManager manager = SdkChannelManager.createForTest(store);

        manager.setChannel(Channel.WS);
        assertEquals("ws", store.get());
        assertEquals(Channel.WS, manager.getChannel());

        manager.setChannel(Channel.AIDL);
        assertEquals("aidl", store.get());
        assertEquals(Channel.AIDL, manager.getChannel());
    }

    @Test
    public void fromValue_illegalOrEmpty_fallsBackToAidl() {
        assertEquals(Channel.AIDL, Channel.fromValue(null));
        assertEquals(Channel.AIDL, Channel.fromValue(""));
        assertEquals(Channel.AIDL, Channel.fromValue("websocket"));
        assertEquals(Channel.AIDL, Channel.fromValue("AIDL"));
        assertEquals(Channel.WS, Channel.fromValue("ws"));
    }

    @Test
    public void getChannel_illegalPersistedValue_fallsBackToAidl() {
        MemoryStore store = new MemoryStore();
        store.put("bogus");
        SdkChannelManager manager = SdkChannelManager.createForTest(store);
        assertEquals(Channel.AIDL, manager.getChannel());
    }

    @Test
    public void setChannel_null_isIgnored() {
        MemoryStore store = new MemoryStore();
        SdkChannelManager manager = SdkChannelManager.createForTest(store);
        manager.setChannel(Channel.WS);
        manager.setChannel(null);
        assertEquals("ws", store.get());
        assertEquals(Channel.WS, manager.getChannel());
    }

    @Test
    public void getClientPassesSelectedChannelToFactory() {
        java.util.List<Channel> created = new java.util.ArrayList<>();
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(), channel -> {
            created.add(channel);
            return new SdkChannelFakes.FakeSdkChannelBridge();
        });
        manager.getClient();
        manager.setChannel(Channel.WS);
        manager.getClient();
        assertEquals(java.util.Arrays.asList(Channel.AIDL, Channel.WS), created);
    }

    @Test
    public void getBridge_sameChannelReusesInstance_switchDropsStaleForFresh() {
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(), channel -> new SdkChannelFakes.FakeSdkChannelBridge());

        HuoyanSdkClient firstAidl = manager.getClient();
        HuoyanSdkClient secondAidl = manager.getClient();
        assertSame(firstAidl, secondAidl);

        manager.setChannel(Channel.WS);
        HuoyanSdkClient ws = manager.getClient();

        // 收尾 P2-1：切换时旧桥断开并丢弃（AIDL 的 SdkCommBinding close 后不可
        // 复用），切回须重建新桥而非复用已断开实例
        manager.setChannel(Channel.AIDL);
        HuoyanSdkClient freshAidl = manager.getClient();
        assertNotSame(firstAidl, freshAidl);
        assertSame(freshAidl, manager.getClient());
    }

    // ---- 切换断开旧桥 ----

    @Test
    public void setChannel_switchAway_disconnectsStaleBridge() {
        SdkChannelFakes.FakeSdkChannelBridge aidl = new SdkChannelFakes.FakeSdkChannelBridge();
        SdkChannelFakes.FakeSdkChannelBridge ws = new SdkChannelFakes.FakeSdkChannelBridge();
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                channel -> channel == Channel.WS ? ws : aidl);
        aidl.connected = true;

        assertSame(aidl, manager.getClient());
        manager.setChannel(Channel.WS);
        assertFalse("切换后旧通道桥须断开（AIDL 解绑 / WS 停止退避重连）", aidl.connected);
        assertSame(ws, manager.getClient());

        ws.connected = true;
        manager.setChannel(Channel.AIDL);
        assertFalse("切回时反向同样断开旧桥", ws.connected);
    }

    @Test
    public void setChannel_sameChannel_keepsBridgeConnected() {
        SdkChannelFakes.FakeSdkChannelBridge aidl = new SdkChannelFakes.FakeSdkChannelBridge();
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                SdkChannelFakes.FakeSdkChannelBridge.factory(aidl));
        aidl.connected = true;
        assertSame(aidl, manager.getClient());

        manager.setChannel(Channel.AIDL);
        assertTrue("与当前通道一致的切换是空操作，不断开", aidl.connected);
        assertSame(aidl, manager.getClient());
    }

    @Test
    public void setChannel_staleBridgeNeverBuilt_disconnectIsNoOp() {
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(), channel -> new SdkChannelFakes.FakeSdkChannelBridge());
        // 未 getBridge 过：旧桥从未建连，切换只写 SP 不抛异常
        manager.setChannel(Channel.WS);
        assertEquals(Channel.WS, manager.getChannel());
        assertTrue(manager.getClient() instanceof HuoyanSdkClient);
    }

    @Test
    public void bridgeShell_disconnectedPlaceholders() {
        // 占位语义：未连接、查询 null、就绪返回本地哨兵快照（永非 null）
        HuoyanSdkClient bridge = SdkChannelManager.createForTest(new MemoryStore(), channel -> new SdkChannelFakes.FakeSdkChannelBridge()).getClient();
        assertNull(bridge.getStatus());
        assertNull(bridge.getCertInfo());
        assertNull(bridge.activate("code", "model-x"));
        assertEquals(HuoyanSdkClient.LOCAL_DISCONNECTED, bridge.initialize().getCode());
    }

    // ---- 状态/证书便捷查询（未连接 null / 已连接透传） ----

    @Test
    public void getSdkStatus_disconnectedBridge_returnsNull() {
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge();
        bridge.status = statusSnapshot(0);
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                SdkChannelFakes.FakeSdkChannelBridge.factory(bridge));

        assertNull(manager.getSdkStatus());
    }

    @Test
    public void getSdkStatus_connectedBridge_passesThroughBridgeReceipt() {
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge();
        bridge.connected = true;
        StatusSnapshot snapshot = statusSnapshot(0);
        bridge.status = snapshot;
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                SdkChannelFakes.FakeSdkChannelBridge.factory(bridge));

        assertSame(snapshot, manager.getSdkStatus());
    }

    @Test
    public void getCertInfo_disconnectedBridge_returnsNull() {
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge();
        bridge.certInfo = certInfo(0);
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                SdkChannelFakes.FakeSdkChannelBridge.factory(bridge));

        assertNull(manager.getCertInfo());
    }

    @Test
    public void getCertInfo_connectedBridge_passesThroughBridgeReceipt() {
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge();
        bridge.connected = true;
        CertInfo cert = certInfo(-42);
        bridge.certInfo = cert;
        SdkChannelManager manager = SdkChannelManager.createForTest(new MemoryStore(),
                SdkChannelFakes.FakeSdkChannelBridge.factory(bridge));

        assertSame(cert, manager.getCertInfo());
    }

    /** code=0 成功形态状态快照（其余字段 null=尚未取得，单测只关心 code）。 */
    private static StatusSnapshot statusSnapshot(int code) {
        return new StatusSnapshot(code, null, null, null, null, null, null, null);
    }

    /** 指定 code 的证书视图（时间族全 0，单测只关心 code）。 */
    private static CertInfo certInfo(int code) {
        return new CertInfo(code, null, null, null, null,
                0L, 0L, 0L, false, 0L, false, 0L);
    }
}
