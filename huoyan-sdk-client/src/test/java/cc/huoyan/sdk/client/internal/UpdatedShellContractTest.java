package cc.huoyan.sdk.client.internal;

import com.alibaba.fastjson.JSON;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.internal.ws.WsParcelMapper;
import cc.huoyan.sdk.client.internal.ws.WsV2Codec;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.app.aidl.*;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** 验证新版 SDK JSON 经线契约到公共 API 的字段保真及错误语义。 */
public class UpdatedShellContractTest {
    /** 公共模型及 AIDL 映射均清除删除前的改品结果，原始检测仍完整传递。 */
    @Test public void deletedFeedbackClearsResultAcrossAidlMapping() {
        FeedbackItem item = new FeedbackItem(FeedbackItem.DELETED,
                java.util.Collections.singletonList(new FeedbackDetect("A", "苹果", .123)),
                new FeedbackResult("B", "梨", "manual"), 1D, 2D, 30D, 40D);
        FeedbackItemParcel wire = ModelMapper.toWire(item);
        assertNull(item.getResult());
        assertNull(wire.getResult());
        assertEquals("A", wire.getDetect().get(0).getClazz());
        assertEquals(item, ModelMapper.fromWire(wire));
    }
    private static WsV2Codec.Response ok(String json) {
        return new WsV2Codec.Response("request", 0, null, JSON.parseObject(json));
    }

    /** 能力接口不需要证书载荷；WS 与 AIDL 公共模型都保留版本。 */
    @Test public void capabilitiesPreserveSdkVersionAcrossTransports() {
        Capability capability = ModelMapper.fromWire(WsParcelMapper.capability(ok(
                "{\"api_version\":2,\"detect\":true,\"sdk_version\":\"2.0.0-alpha01\",\"sdk_version_code\":7}")));
        assertEquals("2.0.0-alpha01", capability.getSdkVersionName());
        assertEquals(7, capability.getSdkVersionCode());
        assertTrue(capability.isDetect());
        CapabilityParcel wire = ModelMapper.toWire(capability);
        assertEquals("2.0.0-alpha01", wire.getSdkVersionName());
        assertEquals(7, wire.getSdkVersionCode());
        assertEquals(capability, ModelMapper.fromWire(wire));
    }

    /** AIDL 查询被拒绝时，SDK 仍可提供独立于能力标志的版本元数据。 */
    @Test public void failedAidlCapabilitiesPreserveVersionMetadata() {
        Capability capability = ModelMapper.fromWire(new CapabilityParcel(-3, "denied", 2,
                false, false, false, false, false, false, "2.0.0", 9));
        assertEquals(-3, capability.getCode());
        assertEquals("denied", capability.getReason());
        assertFalse(capability.isDetect());
        assertEquals("2.0.0", capability.getSdkVersionName());
        assertEquals(9, capability.getSdkVersionCode());
    }

    /** 无载荷错误及 SDK 版本读取失败都不伪造版本。 */
    @Test public void unavailableCapabilityVersionUsesUnknownDefaults() {
        Capability failed = ModelMapper.fromWire(WsParcelMapper.capability(
                new WsV2Codec.Response("request", -3, "denied", null)));
        assertEquals(-3, failed.getCode());
        assertNull(failed.getSdkVersionName());
        assertEquals(-1, failed.getSdkVersionCode());
        Capability unavailable = ModelMapper.fromWire(WsParcelMapper.capability(ok(
                "{\"api_version\":2,\"sdk_version\":\"unknown\",\"sdk_version_code\":-1}")));
        assertEquals("unknown", unavailable.getSdkVersionName());
        assertEquals(-1, unavailable.getSdkVersionCode());
    }

    /** 新证书不依赖已删除字段，时间与版本仍落在正确字段。 */
    @Test public void certificateWithoutRemovedFieldsPreservesVersionAndTimes() {
        CertInfo cert = ModelMapper.fromWire(WsParcelMapper.certInfo(ok(
                "{\"model_code\":\"fresh\",\"model_channel\":\"stable\",\"license_type\":\"annual\","
                        + "\"activated_at\":11,\"issued_at\":12,\"renewed_at\":13,\"has_renewed_at\":true,"
                        + "\"next_renew_at\":14,\"has_next_renew_at\":true,\"expires_at\":15,"
                        + "\"sdk_version\":\"2.0.0-alpha01\",\"sdk_version_code\":1,\"activation_serial\":\"serial\"}")));
        assertEquals("fresh", cert.getModelCode());
        assertEquals("stable", cert.getModelChannel());
        assertEquals("annual", cert.getLicenseType());
        assertEquals(11, cert.getActivatedAt());
        assertEquals(15, cert.getExpiresAt());
        assertTrue(cert.hasNextRenewAt());
        assertEquals("2.0.0-alpha01", cert.getSdkVersionName());
        assertEquals("serial", cert.getActivationSerial());
        assertEquals(cert, ModelMapper.fromWire(ModelMapper.toWire(cert)));
    }

    /** 快照读取成功不能掩盖清理隔离状态或最近错误。 */
    @Test public void cleanupStatePreservesRecentError() {
        StatusSnapshot status = ModelMapper.fromWire(WsParcelMapper.status(ok(
                "{\"sdk_state\":\"CLEANUP_PENDING\",\"model_ready\":false,\"busy\":false,"
                        + "\"recent_error\":\"旧数据清理待完成\"}")));
        assertEquals(0, status.getCode());
        assertEquals("CLEANUP_PENDING", status.getSdkState());
        assertEquals(Boolean.FALSE, status.getModelReady());
        assertEquals("旧数据清理待完成", status.getRecentError());
        assertEquals(status, ModelMapper.fromWire(ModelMapper.toWire(status)));
        assertNull(ModelMapper.fromWire(WsParcelMapper.status(ok("{}"))).getRecentError());
    }

    /** 查询失败保留原因，不能降级为成功空列表。 */
    @Test public void failedImagesDistinguishesRejectionFromEmptySuccess() {
        FailedImagesResult denied = ModelMapper.fromWire(WsParcelMapper.failedImages(
                new WsV2Codec.Response("request", -1003, "索引读取失败", null)));
        assertEquals(-1003, denied.getCode());
        assertEquals("索引读取失败", denied.getReason());
        FailedImagesResult empty = ModelMapper.fromWire(WsParcelMapper.failedImages(ok("{\"items\":[]}")));
        assertEquals(0, empty.getCode());
        assertTrue(empty.getItems().isEmpty());
    }

    /** 终态保留下载与学习独立统计，即使学习全部成功也不能丢掉下载失败。 */
    @Test public void finalLearningResultPreservesBothStagesAndFailures() {
        ImageFlowProgress result = ModelMapper.fromWire(WsParcelMapper.imageFlow(ok(
                "{\"phase\":\"SUCCESS\",\"download_success\":2,\"download_fail\":1,"
                        + "\"download_fail_stats\":\"HTTP 404=1\",\"learn_success\":2,\"learn_fail\":0,"
                        + "\"skipped_count\":3,\"failed_images\":[{\"clazz\":\"42\",\"image_id\":\"missing\","
                        + "\"failure_reason\":\"HTTP 404\",\"image_url\":\"/failed/missing\"}]}"), true));
        assertFalse(result.isActive());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(2, result.getDownloadSuccess());
        assertEquals(1, result.getDownloadFail());
        assertEquals("HTTP 404=1", result.getDownloadFailStats());
        assertEquals(3, result.getSkippedCount());
        assertEquals("missing", result.getFailedImages().get(0).getImageId());
        assertEquals(result, ModelMapper.fromWire(ModelMapper.toWire(result)));
        assertThrows(UnsupportedOperationException.class, () -> result.getFailedImages().clear());
    }

    /** 未知结果和未执行都由调用方决策，门面不自动重放写入。 */
    @Test public void shellOutcomeCodesArePreservedWithoutRetry() {
        for (int code : new int[]{HuoyanSdkClient.RESULT_UNKNOWN, HuoyanSdkClient.NOT_EXECUTED}) {
            AtomicInteger calls = new AtomicInteger();
            SdkChannelBridge transport = (SdkChannelBridge) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SdkChannelBridge.class}, (proxy, method, args) -> {
                        assertEquals("itemForgetByUid", method.getName());
                        calls.incrementAndGet();
                        return new BaseResultParcel(code, "操作状态");
                    });
            BaseResult result = new DefaultSdkClient(transport, false, "1.2.3").forgetSample(42);
            assertEquals(code, result.getCode());
            assertEquals("操作状态", result.getReason());
            assertEquals(1, calls.get());
        }
    }

    /** 超出 SDK 载体上限的图像在复制或发送前拒绝。 */
    @Test public void oversizedImageIsRejectedLocally() {
        byte[] oversized = new byte[32 * 1024 * 1024 + 1];
        assertThrows(IllegalArgumentException.class,
                () -> SdkImage.encoded(oversized, 0, 0, SdkImage.FORMAT_JPEG));
        assertThrows(IllegalArgumentException.class,
                () -> SdkImage.rgb888(oversized, 11184811, 1));
    }
}
