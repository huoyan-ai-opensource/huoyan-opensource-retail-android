package cc.huoyan.sdk.client.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


import cc.huoyan.sdk.client.internal.SdkChannelFakes.CapturingScheduler;
import cc.huoyan.sdk.client.internal.SdkChannelFakes.RecordingBindOps;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cc.huoyan.app.aidl.BaseResultParcel;
import cc.huoyan.app.aidl.BindDeviceParcel;
import cc.huoyan.app.aidl.BindingResultParcel;
import cc.huoyan.app.aidl.CapabilityParcel;
import cc.huoyan.app.aidl.GoodsParcel;
import cc.huoyan.app.aidl.GoodsResultParcel;
import cc.huoyan.app.aidl.GoodsSaveParcel;
import cc.huoyan.app.aidl.ImageRequestParcel;

/**
 * {@link AidlSdkBridge} 单测（绑定/商品 CRUD 透传）：
 * 未连接占位（哨兵/null 不抛异常）、24 方法连接态透传、RemoteException 触发
 * 重连标记、图像载体构造工具（SharedMemory 经测试遮蔽类验证，见
 * {@code android.os.SharedMemory} 测试替身）。
 */
public class AidlSdkBridgeTest {

    /** 通道生命周期方法（bridge 自有，不属于 SDK 契约 18 业务方法）。 */
    private static final Set<String> LOCAL_METHODS =
            new HashSet<>(Arrays.asList("connect", "disconnect", "isConnected", "createImageRequest", "createJpegImageRequest", "createEncodedImageRequest", "createPhotoRequest"));

    private final RecordingBindOps ops = new RecordingBindOps();
    private final CapturingScheduler scheduler = new CapturingScheduler();

    private SdkCommBinding newBinding() {
        return new SdkCommBinding(ops, scheduler, message -> { });
    }

    // ---- 未连接占位 ----

    @Test
    public void unconnected_allMethods_returnPlaceholders_withoutException() throws Exception {
        ops.bindAccepted = false;   // SDK 未安装：退避重连永远在途
        AidlSdkBridge bridge = new AidlSdkBridge(newBinding());

        bridge.connect();
        assertFalse(bridge.isConnected());
        assertEquals("connect 已触达绑定层", 1, ops.bindCount);
        assertEquals("退避任务在途", 1, scheduler.delayed.size());

        for (Method method : SdkChannelBridge.class.getDeclaredMethods()) {
            if (method.getName().equals("createImageRequest") || method.getName().equals("createJpegImageRequest") || method.getName().equals("createEncodedImageRequest") || method.getName().equals("createPhotoRequest")) continue; // 本地图像工厂另有专测。
            Object result = method.invoke(bridge, defaultArgs(method.getParameterTypes()));
            assertPlaceholder(method, result, SdkChannelBridge.LOCAL_DISCONNECTED);
        }

        bridge.disconnect();   // 空操作不抛异常
    }

    @Test
    public void productionCtor_jvmWithoutApplication_staysDisconnected() {
        AidlSdkBridge bridge = new AidlSdkBridge();   // RetailApplication 未初始化（JVM）

        bridge.connect();
        assertFalse(bridge.isConnected());
        BaseResultParcel ready = bridge.init(null);
        assertNotNull("Issue #66 占位为哨兵快照（永非 null）", ready);
        assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, ready.getCode());
        assertNull(bridge.getStatus());
        bridge.disconnect();
    }

    // ---- 连接态透传 ----

    @Test
    public void connected_allBusinessMethods_passThroughToService() throws Exception {
        FakeSdkCommService service = new FakeSdkCommService();
        service.initResult = FakeSdkCommService.readyResult(-110, "密钥轮换待审核");
        service.capabilities = new CapabilityParcel(0, null, 2, true, true, true, true, true, true, "2.0.0", 1);
        service.baseResult = new BaseResultParcel(0, null);
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);
        assertTrue(bridge.isConnected());

        BaseResultParcel readyResult = bridge.init("1.2.3");
        assertEquals("1.2.3", service.lastAppVersion);
        assertEquals(-110, readyResult.getCode());
        assertEquals("Issue #66：SDK 回执 reason 透传", "密钥轮换待审核", readyResult.getReason());
        assertSame(service.capabilities, bridge.getCapabilities());
        assertSame(service.baseResult, bridge.activate("code", "model-x"));
        assertEquals("code", service.lastActivationCode);
        assertEquals("model-x", service.lastModelCode);
        service.licenseModels = new cc.huoyan.app.aidl.LicenseModelsParcel(0, null, java.util.Collections.emptyList());
        assertSame(service.licenseModels, bridge.queryDeviceModels());
        assertEquals(1, service.modelQueries);
        assertSame(service.baseResult, bridge.submitFeedback(null));

        // ---- 绑定/商品 CRUD 透传与载体原样送达（请求 null 亦可——SDK 侧裁决） ----
        BindingResultParcel bindResult = new BindingResultParcel(0, null,
                new BindDeviceParcel(42L, "10086", null, null), "9001", "张三",
                "2001", "旗舰店", "SH001", null, null);
        service.bindDeviceResult = bindResult;
        assertSame(bindResult, bridge.bindDevice(
                new BindDeviceParcel(42L, "10086", null, null)));
        assertEquals(42L, service.lastBindDevice.getAccountId());
        assertEquals("10086", service.lastBindDevice.getShopId());
        assertNull("shopName 不由桥虚构填充", service.lastBindDevice.getShopName());

        GoodsResultParcel goodsResult = new GoodsResultParcel(0, null,
                new GoodsParcel("1770001", "2001", "可颂", "3001", null, null,
                        0, null, null, 0, 1200L, 0L, null, null, null, 0, 0,
                        0, null, null, null, null, null));
        service.goodsResult = goodsResult;
        service.deleteGoodsResult = new BaseResultParcel(0, null);
        assertSame(goodsResult, bridge.createGoods(new GoodsSaveParcel(
                "可颂", "3001", null, null, null, null, null, null, null,
                null, null, Long.valueOf(1200L), null, null)));
        assertEquals("3001", service.lastCreateGoods.getPlu());
        assertEquals(1200L, service.lastCreateGoods.getPrice());
        assertSame(goodsResult, bridge.updateGoods("1770001",
                new GoodsSaveParcel("可颂2", null, null, null, null, null, null,
                        null, null, null, null, null, null, null)));
        assertEquals("1770001", service.lastUpdateGoodsId);
        assertEquals("仅传入字段生效——载体原样透传（plu null 不桥侧补）",
                null, service.lastUpdateGoods.getPlu());
        assertEquals(0, bridge.deleteGoods("1770001").getCode());
        assertEquals("1770001", service.lastDeleteGoodsId);

        for (Method method : SdkChannelBridge.class.getDeclaredMethods()) {
            if (LOCAL_METHODS.contains(method.getName())) {
                continue;
            }
            method.invoke(bridge, defaultArgs(method.getParameterTypes()));
        }
        assertEquals("41 业务方法全部透达到服务", 41, service.invoked.size());
        assertTrue(service.invoked.contains("recognize"));
        assertTrue(service.invoked.contains("batchLearn"));
        assertTrue(service.invoked.contains("bindDevice"));
        assertTrue(service.invoked.contains("createGoods"));
        assertTrue(service.invoked.contains("updateGoods"));
        assertTrue(service.invoked.contains("deleteGoods"));
    }

    /** 云开关透传真实终态与关闭拒绝原因，不把错误载荷当作关闭成功。 */
    @Test
    public void cloudStatus_preservesSwitchArgumentAndRejectedResult() {
        FakeSdkCommService service = new FakeSdkCommService();
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);
        assertFalse(bridge.getCloudStatus().isEnabled());
        service.cloudStatus = new cc.huoyan.app.aidl.CloudStatusParcel(0, null, true);
        assertSame(service.cloudStatus, bridge.setCloudStatus(true));
        assertTrue(service.lastCloudEnabled);
        service.cloudStatus = new cc.huoyan.app.aidl.CloudStatusParcel(-1, "不可关闭", false);
        assertSame(service.cloudStatus, bridge.setCloudStatus(false));
        assertFalse(service.lastCloudEnabled);
        assertEquals("不可关闭", bridge.getCloudStatus().getReason());
    }

    // ---- RemoteException 路径 ----

    @Test
    public void remoteException_allBusinessMethods_returnPlaceholders_andTriggerReconnect()
            throws Exception {
        FakeSdkCommService service = new FakeSdkCommService();
        service.throwRemote = true;
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        // 打点经注入缝出口（JVM 无 android.util.Log）：记录式收集，顺带断言
        // 新增绑定/商品调用的事务失败诊断如实出口（不吞掉、不落 android Log）
        List<String> bizLogs = new ArrayList<>();
        AidlSdkBridge bridge = new AidlSdkBridge(binding, bizLogs::add);
        assertTrue(bridge.isConnected());

        for (Method method : SdkChannelBridge.class.getDeclaredMethods()) {
            if (LOCAL_METHODS.contains(method.getName())) {
                continue;
            }
            Object result = method.invoke(bridge, defaultArgs(method.getParameterTypes()));
            assertPlaceholder(method, result, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN);
        }
        assertEquals("每个方法各触发一次重连标记", 41, scheduler.posted.size());
        assertEquals(41, service.invoked.size());
        assertEquals("四个新增绑定/商品方法各有一条事务失败打点", 4, bizLogs.size());
        assertTrue("打点形如 [Biz] aidl_<method>_remote_error result=unknown",
                bizLogs.contains("[Biz] aidl_bindDevice_remote_error result=unknown")
                        && bizLogs.contains("[Biz] aidl_createGoods_remote_error result=unknown")
                        && bizLogs.contains("[Biz] aidl_updateGoods_remote_error result=unknown")
                        && bizLogs.contains("[Biz] aidl_deleteGoods_remote_error result=unknown"));
    }

    // ---- 图像载体构造 ----

    @Test
    public void bytesRequest_carriesGeometry_andPassesContractValidate() {
        byte[] frame = ramp(2 * 3 * 3);
        ImageRequestParcel parcel = AidlSdkBridge.newBytesRequest(frame, 2, 3);

        assertNull(parcel.validate());
        assertEquals(2, parcel.getWidth());
        assertEquals(3, parcel.getHeight());
        assertEquals(frame.length, parcel.getSize());
        assertEquals(ImageRequestParcel.FORMAT_RGB888, parcel.getFormat());
        assertArrayEquals(frame, parcel.getBytes());
    }

    @Test
    public void bytesRequest_overInlineLimit_flaggedByContractValidate() {
        int width = 1;
        int height = (ImageRequestParcel.MAX_INLINE_BYTES + 3) / 3;
        ImageRequestParcel parcel = AidlSdkBridge.newBytesRequest(new byte[width * height * 3], width, height);
        assertNotNull("内联上限由对端契约校验裁决（非构造期抛异常）", parcel.validate());
    }

    @Test
    public void sharedMemoryRequest_writesFrameIntoRegion() throws Exception {
        byte[] frame = ramp(4 * 5 * 3);
        ImageRequestParcel parcel = AidlSdkBridge.newSharedMemoryRequest(frame, 4, 5);

        assertNull(parcel.validate());
        assertEquals(4, parcel.getWidth());
        assertEquals(5, parcel.getHeight());
        assertEquals(frame.length, parcel.getSize());
        assertNull(parcel.getBytes());

        ByteBuffer mapped = parcel.getSharedMemory().mapReadOnly();
        byte[] readBack = new byte[frame.length];
        mapped.get(readBack);
        assertArrayEquals("帧内容须完整写入共享内存区", frame, readBack);
    }

    @Test
    public void carrierUtils_geometryMismatch_failFast() throws Exception {
        byte[] valid = ramp(2 * 3 * 3);
        assertIllegalArgumentException(() -> AidlSdkBridge.newBytesRequest(valid, 3, 3));
        assertIllegalArgumentException(() -> AidlSdkBridge.newBytesRequest(valid, 0, 3));
        assertIllegalArgumentException(() -> AidlSdkBridge.newBytesRequest(null, 2, 3));
        assertIllegalArgumentException(() -> AidlSdkBridge.newSharedMemoryRequest(valid, 2, 4));
    }

    // ---- SharedMemory 载体释放 ----

    @Test
    public void imageMethods_releaseSharedMemoryCarrier_afterCallReturns() throws Exception {
        FakeSdkCommService service = new FakeSdkCommService();
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);

        ImageRequestParcel detectCarrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        bridge.detect(detectCarrier);
        assertTrue("detect 调用返回后须 close 载体", isCarrierClosed(detectCarrier));

        ImageRequestParcel recognizeCarrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        bridge.recognize(recognizeCarrier, 0);
        assertTrue("recognize 调用返回后须 close 载体", isCarrierClosed(recognizeCarrier));

        ImageRequestParcel learnCarrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        bridge.itemLearn(learnCarrier, "1");
        assertTrue("itemLearn 调用返回后须 close 载体", isCarrierClosed(learnCarrier));

        ImageRequestParcel batchLearnCarrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        bridge.batchLearn(batchLearnCarrier, "1");
        assertTrue("batchLearn 调用返回后须 close 载体", isCarrierClosed(batchLearnCarrier));
    }

    /** 新反馈契约保留框几何，图片上传回执原样返回且释放共享内存。 */
    @Test
    public void photoUploadAndFeedbackUseNewAidlContract() throws Exception {
        FakeSdkCommService service = new FakeSdkCommService();
        service.photoResult = new cc.huoyan.app.aidl.PhotoUploadResultParcel(0, null, "https://files/photo.jpg");
        service.baseResult = new BaseResultParcel(0, null);
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);
        android.os.SharedMemory memory = android.os.SharedMemory.create("photo", 2 * 1024 * 1024);
        ImageRequestParcel image = new ImageRequestParcel(memory, null, memory.getSize(), 0, 0, ImageRequestParcel.FORMAT_JPEG);
        assertSame(service.photoResult, bridge.uploadPhoto(image));
        assertSame(image, service.lastPhoto);
        assertTrue(isCarrierClosed(image));
        cc.huoyan.app.aidl.FeedbackParcel feedback = new cc.huoyan.app.aidl.FeedbackParcel("request-1",
                service.photoResult.getPhotoFileUrl(), 1, java.util.Collections.singletonList(
                        new cc.huoyan.app.aidl.FeedbackItemParcel(0,java.util.Collections.emptyList(),new cc.huoyan.app.aidl.FeedbackResultParcel( "1001", "面包", "SDK识别"), 100D, 200D, 300D, 400D)));
        assertSame(service.baseResult, bridge.submitFeedback(feedback));
        assertSame(feedback, service.lastFeedback);
        assertEquals(300D, service.lastFeedback.getItems().get(0).getWidth(), 0);
        service.throwRemote = true;
        memory = android.os.SharedMemory.create("photo-failure", 32);
        image = new ImageRequestParcel(memory, null, 32, 0, 0, ImageRequestParcel.FORMAT_PNG);
        assertEquals(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, bridge.uploadPhoto(image).getCode());
        assertTrue(isCarrierClosed(image));
    }

    @Test
    public void imageMethod_remoteException_stillReleasesCarrier() throws Exception {
        FakeSdkCommService service = new FakeSdkCommService();
        service.throwRemote = true;
        SdkCommBinding binding = newBinding();
        binding.bind();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);

        ImageRequestParcel carrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        assertNull(bridge.recognize(carrier, 0));
        assertTrue("事务失败路径同样须 close（无人再读该区）", isCarrierClosed(carrier));
    }

    @Test
    public void imageMethod_unconnectedEarlyReturn_stillReleasesCarrier() throws Exception {
        ops.bindAccepted = false;   // 未连接：早退占位路径
        AidlSdkBridge bridge = new AidlSdkBridge(newBinding());
        bridge.connect();

        ImageRequestParcel carrier = AidlSdkBridge.newSharedMemoryRequest(ramp(2 * 3 * 3), 2, 3);
        assertNull(bridge.recognize(carrier, 0));
        assertTrue("未连接早退路径同样须 close（载体未送出）", isCarrierClosed(carrier));
    }

    @Test
    public void releaseImageCarrier_bytesOrNull_isNoOp() {
        AidlSdkBridge.releaseImageCarrier(null);
        ImageRequestParcel bytesCarrier = AidlSdkBridge.newBytesRequest(ramp(2 * 3 * 3), 2, 3);
        AidlSdkBridge.releaseImageCarrier(bytesCarrier);
        assertNotNull("bytes 载体无 native 资源，释放后仍可读", bytesCarrier.getBytes());
    }

    @Test
    public void pagedQueries_forwardPageSizeAndInt64Clazz() {
        FakeSdkCommService service = new FakeSdkCommService();
        SdkCommBinding binding = newBinding();
        binding.connected(service);
        AidlSdkBridge bridge = new AidlSdkBridge(binding);
        bridge.getItemClazzList(2, 100);
        assertEquals(2, service.lastPage);
        assertEquals(100, service.lastSize);
        bridge.getItemClazzImages("9007199254740993", 3, 20);
        assertEquals("9007199254740993", service.lastClazz);
        assertEquals(3, service.lastPage);
        assertEquals(20, service.lastSize);
    }

    // ---- 工具 ----

    // 用反射读取测试替身标记，避免 javac 将预编译返回类型绑定到 Android SDK 类。
    private static boolean isCarrierClosed(ImageRequestParcel carrier) throws Exception {
        Object memory = carrier.getSharedMemory();
        return (Boolean) memory.getClass().getMethod("isClosed").invoke(memory);
    }

    private static byte[] ramp(int length) {
        byte[] frame = new byte[length];
        for (int i = 0; i < length; i++) {
            frame[i] = (byte) (i * 7 + 1);
        }
        return frame;
    }

    /** 按参数类型构造占位实参：基本类型取零值，引用类型取 null。 */
    private static Object[] defaultArgs(Class<?>[] parameterTypes) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (type == long.class) {
                args[i] = 0L;
            } else if (type == int.class) {
                args[i] = 0;
            } else if (type == float.class) {
                args[i] = 0f;
            } else if (type == double.class) {
                args[i] = 0d;
            }
        }
        return args;
    }

    /** 占位断言：int 为 0（getApiVersion）或本地哨兵；init 为哨兵快照（永非 null）；其余对象为 null。 */
    private static void assertPlaceholder(Method method, Object result, int expectedCode) throws Exception {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return;
        }
        if (returnType == int.class) {
            int value = (Integer) result;
            assertTrue("int 占位应为 0 或本地未连接哨兵: " + method.getName(),
                    value == 0 || value == SdkChannelBridge.LOCAL_DISCONNECTED);
        } else if (returnType == boolean.class) {
            assertFalse("boolean 占位应为 false: " + method.getName(), (Boolean) result);
        } else if ("init".equals(method.getName())) {
            assertNotNull("Issue #66 就绪占位为哨兵快照（永非 null）", result);
            int code = ((BaseResultParcel) result).getCode();
            assertTrue("就绪占位码应为 0 或本地未连接哨兵: " + method.getName(),
                    code == 0 || code == expectedCode);
        } else if (result != null) {
            assertEquals(expectedCode, ((Integer) result.getClass().getMethod("getCode").invoke(result)).intValue());
        } else {
            assertNull("只读查询无回执: " + method.getName(), result);
        }
    }

    private static void assertIllegalArgumentException(ThrowingAction action) {
        try {
            action.run();
            fail("期望抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期：帧长度与几何不符
        } catch (Exception other) {
            fail("非预期异常: " + other);
        }
    }

    /** 可抛受检异常的动作（lambda 调用声明 throws 的静态工具）。 */
    private interface ThrowingAction {
        void run() throws Exception;
    }
    @Test
    public void imageFactoryWithoutSharedMemoryRetainsBinderInlineLimit() {
        // JVM 的 SDK_INT=0，覆盖低版本内联降级路径。
        AidlSdkBridge bridge = new AidlSdkBridge();
        byte[] small = new byte[6];
        ImageRequestParcel image = bridge.createImageRequest(small, 2, 1);
        assertSame(small, image.getBytes());
        assertNull(image.validate());
        assertNull(bridge.createImageRequest(new byte[1076 * 756 * 3], 1076, 756));
    }

}
