package cc.huoyan.sdk.client.internal;

import cc.huoyan.app.aidl.*;
import cc.huoyan.sdk.client.*;
import cc.huoyan.sdk.client.model.*;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** 验证公共门面的数据保真、图像所有权和不重试写入的边界。 */
public class PublicClientTest {
    @Test public void imageIsSnapshotAndChecksOverflow() {
        byte[] rgb = {1, 2, 3};
        SdkImage image = SdkImage.rgb888(rgb, 1, 1);
        rgb[0] = 9;
        byte[] copy = image.getBytes(); copy[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, image.getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> SdkImage.rgb888(rgb, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> SdkImage.rgb888(rgb, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> SdkImage.encoded(rgb, 1, 1, 9));
        assertThrows(IllegalArgumentException.class,
                () -> SdkConfig.websocket("ws://host:18086/ws?token=value"));
        assertEquals("ws://127.0.0.1:18086/ws",
                SdkConfig.websocket("ws://127.0.0.1:18086/ws").getWsEndpoint());
    }

    @Test public void recognitionPreservesAllCandidatesAndDoesNotMutateInput() {
        byte[] rgb = {1, 2, 3};
        RecognizeResultParcel reply = new RecognizeResultParcel(0, null, Arrays.asList(
                new RecognitionItemParcel("9007199254740993", .123, 1, 2, 30, 40, 999, -.5,
                        Arrays.asList(new RecognitionCandidateParcel("2", .4, 33, -.2)))));
        AtomicInteger calls = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            if (method.equals("createImageRequest")) {
                return new ImageRequestParcel(null, (byte[]) args[0], 3, 1, 1, 1);
            }
            if (method.equals("recognize")) {
                calls.incrementAndGet();
                assertEquals(90, args[1]);
                assertArrayEquals(rgb, ((ImageRequestParcel) args[0]).getBytes());
                return reply;
            }
            return null;
        }), false, "1.2.3");
        RecognizeResult result = client.recognize(SdkImage.rgb888(rgb, 1, 1), 90);
        RecognitionItem item = result.getItems().get(0);
        assertEquals("9007199254740993", item.getClazz());
        assertEquals(.123, item.getDistance(), 0);
        assertEquals(999, item.getSquare(), 0);
        assertEquals(-.5, item.getRank(), 0);
        assertEquals(40, item.getBottom());
        assertEquals(-.2, item.getCandidates().get(0).getRank(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> client.recognize(SdkImage.rgb888(rgb, 1, 1), 12));
        assertEquals(1, calls.get());
    }

    @Test public void optionalGoodsFieldsAndLargeIdsSurviveRoundTrip() {
        GoodsSave partial = new GoodsSave("测试", null, null, null, null, null,
                null, null, null, 0, null, 9007199254740993L, 0, null);
        GoodsSaveParcel wire = ModelMapper.toWire(partial);
        assertFalse(wire.hasCategory());
        assertFalse(wire.hasTareWeight());
        assertTrue(wire.hasSaleMode()); assertEquals(0, wire.getSaleMode());
        assertTrue(wire.hasSort()); assertEquals(0, wire.getSort());
        assertEquals(9007199254740993L, wire.getPrice());
        assertEquals(partial, ModelMapper.fromWire(wire));
        ItemClazzList withoutThumbnails = ModelMapper.fromWire(
                new ItemClazzListParcel(0, null, new String[]{"9"}, new long[]{1}));
        assertEquals("9", withoutThumbnails.getClazz()[0]);
        assertEquals(0, withoutThumbnails.getThumbnails().length);
    }

    @Test public void batchLearningKeepsPartialFailureAndNeverRetries() {
        AtomicInteger calls = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            if (method.equals("createImageRequest")) return new ImageRequestParcel(null, new byte[3], 3, 1, 1, 1);
            if (method.equals("batchLearn")) {
                calls.incrementAndGet();
                assertEquals("9007199254740993", args[1]);
                return new ItemLearnBatchParcel(0, null, Arrays.asList(
                        new ItemLearnBoxParcel(1, 2, 3, 4, .9, 9007199254740994L, 0, null),
                        new ItemLearnBoxParcel(5, 6, 7, 8, .8, 0, -7, "学习失败")));
            }
            return null;
        }), false, "1.2.3");
        ItemLearnBatch result = client.batchLearn(SdkImage.rgb888(new byte[3], 1, 1), "9007199254740993");
        assertEquals(0, result.getCode());
        assertEquals(9007199254740994L, result.getBoxes().get(0).getUid());
        assertEquals(-7, result.getBoxes().get(1).getCode());
        assertEquals("学习失败", result.getBoxes().get(1).getReason());
        assertEquals(1, calls.get());
    }

    @Test public void unknownWriteOutcomeIsNotRetriedAndCloseCannotReconnect() {
        AtomicInteger writes = new AtomicInteger(), connects = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            if (method.equals("connect")) connects.incrementAndGet();
            if (method.equals("itemForgetByUid")) writes.incrementAndGet();
            if (method.equals("isConnected")) return true;
            return null;
        }), false, "1.2.3");
        client.connect();
        assertNull(client.forgetSample(3));
        assertEquals(1, writes.get());
        client.close(); client.connect();
        assertEquals(1, connects.get());
        assertFalse(client.isConnected());
    }

    @Test public void unavailableImageDoesNotSendARequest() {
        AtomicInteger calls = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            if (method.equals("createImageRequest")) return null;
            calls.incrementAndGet(); return null;
        }), false, "1.2.3");
        SdkImage image = SdkImage.rgb888(new byte[3], 1, 1);
        assertEquals(HuoyanSdkClient.IMAGE_UNAVAILABLE, client.recognize(image).getCode());
        assertEquals(HuoyanSdkClient.IMAGE_UNAVAILABLE, client.detect(image).getCode());
        assertEquals(HuoyanSdkClient.IMAGE_UNAVAILABLE, client.learn(image, "1").getCode());
        assertEquals(HuoyanSdkClient.IMAGE_UNAVAILABLE, client.batchLearn(image, "1").getCode());
        assertEquals(0, calls.get());
    }

    @Test public void publicSignaturesNeverExposeWireOrRetailTypes() {
        Set<Class<?>> visited = new HashSet<>();
        checkApi(HuoyanSdkClient.class, visited);
        checkApi(SdkConfig.class, visited);
        assertTrue(visited.size() > 20);
    }

    @Test public void bindingInjectsBundledAccountAndPreservesExternalId() {
        AtomicInteger calls = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            assertEquals("bindDevice", method);
            calls.incrementAndGet();
            BindDeviceParcel request = (BindDeviceParcel) args[0];
            assertEquals(2115027529298350203L, request.getAccountId());
            assertEquals("erp-account:007-A", request.getShopId());
            assertEquals("门店名称", request.getShopName());
            assertEquals("S-007", request.getShopNo());
            return new BindingResultParcel(0, null, request, "2115027529298350203",
                    "内部账号", "internal-shop-99", "门店名称", "S-007", "测试地址", "010-88886666");
        }), false, "1.2.3");
        BindingResult result = client.bindDevice("erp-account:007-A", "门店名称", "S-007");
        assertEquals("erp-account:007-A", result.getRequest().getShopId());
        assertEquals("internal-shop-99", result.getShopId());
        assertEquals("测试地址", result.getShopAddress());
        assertEquals("010-88886666", result.getShopPhone());
        assertEquals(2115027529298350203L, result.getRequest().getAccountId());
        assertEquals(1, calls.get());
    }

    @Test public void bindingRejectsBlankBeforeSendingAndHasNoAccountInput() {
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            fail("无效标识不应发请求"); return null;
        }), false, "1.2.3");
        for (String id : new String[]{null, "", " ", "\t\n"}) {
            assertThrows(IllegalArgumentException.class, () -> client.bindDevice(id));
        }
        for (Method method : HuoyanSdkClient.class.getMethods()) {
            if (method.getName().equals("bindDevice")) {
                for (Class<?> parameter : method.getParameterTypes()) assertEquals(String.class, parameter);
                assertTrue(method.getParameterCount() == 1 || method.getParameterCount() == 3);
            }
        }
    }

    private void checkApi(Type type, Set<Class<?>> visited) {
        String name = type.getTypeName();
        assertFalse(name, name.contains("cc.huoyan.app.aidl"));
        assertFalse(name, name.contains(".internal."));
        assertFalse(name, name.contains(".retail."));
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) checkApi(argument, visited);
        } else if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (!cls.getName().startsWith("cc.huoyan.sdk.client") || !visited.add(cls)) return;
            for (Method method : cls.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers())) {
                checkApi(method.getGenericReturnType(), visited);
                for (Type argument : method.getGenericParameterTypes()) checkApi(argument, visited);
            }
            for (Constructor<?> constructor : cls.getConstructors()) {
                for (Type argument : constructor.getGenericParameterTypes()) checkApi(argument, visited);
            }
        }
    }

    private interface Call { Object invoke(String name, Object[] args); }
    private SdkChannelBridge transport(Call call) {
        return (SdkChannelBridge) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SdkChannelBridge.class}, (proxy, method, args) -> call.invoke(method.getName(), args));
    }
    /** 公共初始化入口每次都携带宿主版本，覆盖启动后的再次初始化。 */
    @Test public void initializePassesHostVersionOnEveryCall() {
        AtomicInteger calls = new AtomicInteger();
        HuoyanSdkClient client = new DefaultSdkClient(transport((method, args) -> {
            assertEquals("init", method);
            assertEquals("1.2.3", args[0]);
            calls.incrementAndGet();
            return new cc.huoyan.app.aidl.BaseResultParcel(0, null);
        }), false, "1.2.3");
        assertEquals(0, client.initialize().getCode());
        assertEquals(0, client.initialize().getCode());
        assertEquals(2, calls.get());
    }

}
