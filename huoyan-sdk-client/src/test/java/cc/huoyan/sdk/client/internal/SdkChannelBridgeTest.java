package cc.huoyan.sdk.client.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cc.huoyan.app.aidl.BaseResultParcel;

/** 守护桥方法签名、SDK 完整 Binder transaction 顺序和断连返回约定。 */
public class SdkChannelBridgeTest {

    /** 通道本地方法（生命周期、图像构造，不属于 SDK 契约业务方法）。 */
    private static final Set<String> LOCAL_METHODS =
            new HashSet<>(Arrays.asList("connect", "disconnect", "isConnected", "createImageRequest", "createJpegImageRequest", "createEncodedImageRequest", "createPhotoRequest"));

    /** 桥已暴露的业务方法数（初始化、识别、分页、备份及商品写入）。 */
    private static final int EXPECTED_BUSINESS_METHOD_COUNT = 41;

    private Class<?> loadAidlContract() {
        try {
            return Class.forName("cc.huoyan.app.aidl.ISdkCommService");
        } catch (ClassNotFoundException e) {
            fail("AIDL 契约类不在单测 classpath: " + e);
            return null;
        }
    }

    @Test
    public void businessMethodNames_mirrorAidlContract() {
        Class<?> contract = loadAidlContract();
        Set<String> aidlNames = new HashSet<>();
        for (Method m : contract.getDeclaredMethods()) {
            aidlNames.add(m.getName());
        }
        assertEquals("SDK 契约业务方法数应为 41（同步 SDK Issue #113）", 41, aidlNames.size());

        Set<String> bridgeBusinessNames = new HashSet<>();
        for (Method m : SdkChannelBridge.class.getDeclaredMethods()) {
            if (!LOCAL_METHODS.contains(m.getName())) {
                bridgeBusinessNames.add(m.getName());
            }
        }
        assertEquals("Bridge 业务方法数应为 41", EXPECTED_BUSINESS_METHOD_COUNT, bridgeBusinessNames.size());
        assertEquals("JAR 必须覆盖全部 SDK 方法", aidlNames, bridgeBusinessNames);
    }

    @Test
    public void businessMethodSignatures_alignWithAidlContract() {
        Class<?> contract = loadAidlContract();
        for (Method bridge : SdkChannelBridge.class.getDeclaredMethods()) {
            if (LOCAL_METHODS.contains(bridge.getName())) {
                continue;
            }
            Method aidl = null;
            for (Method candidate : contract.getDeclaredMethods()) {
                if (candidate.getName().equals(bridge.getName())) {
                    aidl = candidate;
                    break;
                }
            }
            assertNotNull("SDK 契约缺少桥方法: " + bridge.getName(), aidl);
            assertEquals("返回类型不一致: " + bridge.getName(),
                    aidl.getReturnType(), bridge.getReturnType());
            assertTrue("参数类型不一致: " + bridge.getName(),
                    Arrays.equals(aidl.getParameterTypes(), bridge.getParameterTypes()));
        }
    }

    @Test
    public void shellBridges_allMethods_invokeWithoutException() throws Exception {
        for (SdkChannelBridge bridge : Arrays.asList(new AidlSdkBridge(), new WsSdkBridge())) {
            bridge.connect();
            assertFalse(bridge.isConnected());
            for (Method m : SdkChannelBridge.class.getDeclaredMethods()) {
                if (m.getName().equals("createImageRequest") || m.getName().equals("createJpegImageRequest") || m.getName().equals("createEncodedImageRequest") || m.getName().equals("createPhotoRequest")) continue; // 工厂入参和边界另有专测。
                Object result = m.invoke(bridge, defaultArgs(m.getParameterTypes()));
                Class<?> returnType = m.getReturnType();
                if (returnType == void.class) {
                    continue;
                }
                if (returnType == int.class) {
                    int value = (Integer) result;
                    assertTrue("int 占位应为 0 或本地未连接哨兵: " + m.getName(),
                            value == 0 || value == SdkChannelBridge.LOCAL_DISCONNECTED);
                } else if (returnType == boolean.class) {
                    assertFalse("boolean 占位应为 false: " + m.getName(), (Boolean) result);
                } else if ("init".equals(m.getName())) {
                    assertNotNull("Issue #66 就绪占位为哨兵快照（永非 null）: " + m.getName(), result);
                    int code = ((BaseResultParcel) result).getCode();
                    assertTrue("就绪占位码应为本地未连接哨兵: " + m.getName(),
                            code == SdkChannelBridge.LOCAL_DISCONNECTED);
                } else if (result != null) {
                    assertEquals(SdkChannelBridge.LOCAL_DISCONNECTED, ((Integer) result.getClass().getMethod("getCode").invoke(result)).intValue());
                } else {
                    assertNull("查询没有回执: " + m.getName(), result);
                }
            }
            bridge.disconnect();
        }
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
    @Test
    public void binderTransactionOrder_matchesCurrentShell() throws Exception {
        String[] methods = {
                "getCapabilities",
                "getStatus",
                "getCertInfo",
                "getDownloadProgress",
                "activate",
                "setBaseUrl",
                "detect",
                "recognize",
                "itemLearn",
                "itemForget",
                "itemForgetByUid",
                "getItemClazzList",
                "getItemClazzImages",
                "getFailedImages",
                "submitFeedback",
                "uploadPhoto",
                "init",
                "batchLearn",
                "bindDevice",
                "downloadImagesAndLearn",
                "getImageDownloadProgress",
                "getImageLearnProgress",
                "listGoods",
                "createGoods",
                "updateGoods",
                "deleteGoods",
                "setCloudStatus",
                "getCloudStatus",
                "downloadGoods",
                "getDownloadGoodsProgress",
                "downloadImagesAndLearnByGoodsId",
                "getGoods",
                "queryGoods",
                "getGoodsCount",
                "batchUpdateGoods",
                "batchDeleteGoods",
                "backupRecognitionData",
                "getRecognitionDataInfo",
                "restoreRecognitionData"};
        Class<?> stub = Class.forName("cc.huoyan.app.aidl.ISdkCommService$Stub");
        for (int i = 0; i < methods.length; i++) {
            java.lang.reflect.Field transaction = stub.getDeclaredField("TRANSACTION_" + methods[i]);
            transaction.setAccessible(true);
            assertEquals(methods[i], i + 1, transaction.getInt(null));
        }
    }

}
