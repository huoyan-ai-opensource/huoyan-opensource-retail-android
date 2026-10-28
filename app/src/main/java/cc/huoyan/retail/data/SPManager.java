package cc.huoyan.retail.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.device.camera.CameraConfigEntity;
import cc.huoyan.retail.device.camera.RecognizeAreaEntity;
import cc.huoyan.retail.common.GsonUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SPManager {
    public static final String SP_JSON_CAMERA_CONFIG = "SP_JSON_CAMERA_CONFIG";
    public static final String SP_JSON_FTP_CONFIG = "SP_JSON_FTP_CONFIG";
    public static final String SP_DEVICE_CAMERA = "SP_DEVICE_CAMERA";
    public static final String SP_ACTIVATE_CODE = "SP_ACTIVATE_CODE";
    public static final String SP_ACTIVATE_USER_TOKEN = "SP_ACTIVATE_USER_TOKEN";
    // SDK 2.0 对接通道：值为 aidl/ws，默认 aidl（读写见 SdkChannelManager）
    public static final String SP_SDK_CHANNEL = "SP_SDK_CHANNEL";
    // SDK 2.0 WS 通道端点覆盖：格式 ws://host:port/ws，
    // 缺省/非法回退 127.0.0.1:18086/ws（解析由 JAR 的 SdkConfig/SdkDiscovery 完成）
    public static final String SP_WS_ENDPOINT = "SP_WS_ENDPOINT";
    public static final String SP_SHOP_NAME = "SP_SHOP_NAME";
    /** 允许删除单条识别结果，缺省 false；识别设置保存后生效。 */
    public static final String SP_DELETE_RECOGNITION_RESULT = "SP_DELETE_RECOGNITION_RESULT";
    public static final String SP_STABLE_RECOGNITION = "SP_STABLE_RECOGNITION";
    /** 触发灵敏度档位 1–5；参数保存后生效，缺省为标准 3 档。 */
    public static final String SP_RECOGNITION_SENSITIVITY_LEVEL = "SP_RECOGNITION_SENSITIVITY_LEVEL";
    public static final String SP_SHOP_ID = "SP_SHOP_ID";
    public static final String SP_DEVICES_ID = "SP_DEVICES_ID";
    public static final String SP_RECOGNIZE_AREA = "SP_RECOGNIZE_AREA";
    public static final String SP_UVC_INIT = "SP_UVC_INIT";
    public static final String SP_FTP_S080_LENGTH = "SP_FTP_S080_LENGTH";
    public static final String SP_FTP_SERVER_IP = "SP_FTP_SERVER_IP";
    public static final String SP_FLOATING_X = "SP_FLOATING_X";
    public static final String SP_FLOATING_Y = "SP_FLOATING_Y";
    public static final String SP_SHOP_ADDRESS = "SP_SHOP_ADDRESS";
    public static final String SP_SHOP_PHONE = "SP_SHOP_PHONE";
    public static final String SP_SHOP_NO = "SP_SHOP_NO";
    public static final String SP_ACLAS_SCALE_PORT = "SP_ACLAS_SCALE_PORT";
    /** 打印设备的完整选择标识，由打印设置页保存。 */
    private static final String SP_PRINTER_DEVICE = "printer_device";
    // 学习图 uid 持久化映射（用户 2026-08-27）：JSON 结构 {plu:[{picId,uid}]}，
    // plu→picId→uid。itemLearn 回执 uid 原仅会话内存（StudyPicEntity 不落库），
    // 跨会话单图删除 itemForgetByUid 不可达——学习成功时写入本映射恢复寻址
    public static final String SP_STUDY_UID_MAP = "SP_STUDY_UID_MAP";
    // SDK 绑定 scope：扫码绑定 code=0 时写入（账号/门店/绑定代），
    // 失败不触碰；门店标识复用 SP_SHOP_ID
    public static final String SP_SDK_BIND_ACCOUNT = "SP_SDK_BIND_ACCOUNT";
    public static final String SP_SDK_BIND_GENERATION = "SP_SDK_BIND_GENERATION";
    private static final String LOG_TAG = "SpHelper";
    private static SPManager instance;
    private static SharedPreferences sharedPreferences;

    // 私有构造函数，防止外部实例化
    private SPManager() {
    }

    public static synchronized SPManager getInstance() {
        if (instance == null) {
            instance = new SPManager();
            if (sharedPreferences == null) {
                sharedPreferences = RetailApplication.getInstance().getSharedPreferences("HuoYanApplication", Context.MODE_PRIVATE);
            }
        }
        return instance;
    }

    /** 返回用户选择的打印设备标识；未选择时为空，不预设设备型号。 */
    public String getPrinterDevice() {
        return sharedPreferences.getString(SP_PRINTER_DEVICE, "");
    }

    /** 保存设置页选择的打印设备，打印任务提交时读取快照。 */
    public void putPrinterDevice(String device) {
        putString(SP_PRINTER_DEVICE, device);
    }

    /** 返回打印纸宽毫米值；未配置或无效值使用默认 58mm。 */
    public int getPrinterPaperWidthMm() {
        return sharedPreferences.getInt("printer_paper_width_mm", 58) == 80 ? 80 : 58;
    }

    /** 保存用户选择的纸张规格；打印任务在提交时读取快照，不跟随中途更改。 */
    public void putPrinterPaperWidthMm(int millimeters) {
        if (millimeters != 58 && millimeters != 80) throw new IllegalArgumentException("Unsupported paper width");
        sharedPreferences.edit().putInt("printer_paper_width_mm", millimeters).apply();
    }

    /** 原生相机按设备隔离识别区与显示配置，保留所有旧 UVC 键。 */
    private String cameraPreferenceKey(String key) {
        if (!"CAMERA2".equals(getString("camera_source", "UVC"))) return key;
        return key + "_CAMERA2_" + getString("camera2_device", "default");
    }

    public CameraConfigEntity getCameraConfig() {
        String string = sharedPreferences.getString(cameraPreferenceKey(SP_JSON_CAMERA_CONFIG), "");
        if (TextUtils.isEmpty(string)) {
            return new CameraConfigEntity();
        } else {
            return GsonUtil.fromJson(string, CameraConfigEntity.class);
        }
    }

    public void putCameraConfig(CameraConfigEntity entity) {
        putString(cameraPreferenceKey(SP_JSON_CAMERA_CONFIG), GsonUtil.toJson(entity));
    }

//    public void putDeviceResolution(UsbDevice usbDevice, List<PreviewSize> sizeList) {
//        putString(SP_DEVICE_CAMERA + usbDevice.getDeviceId(), GsonUtil.toJson(sizeList));
//    }
//
//    public List<PreviewSize> getDeviceResolution(UsbDevice usbDevice) {
//        String json = getString(SP_DEVICE_CAMERA + usbDevice.getDeviceId());
//        if ("".equals(json)) {
//            return new ArrayList<>();
//        }
//        Type type = new TypeToken<List<PreviewSize>>() {
//        }.getType();
//        return GsonUtil.fromJson(json, type);
//    }

    public void putRecognizeArea(RecognizeAreaEntity recognizeAreaEntity) {
        putString(cameraPreferenceKey(SP_RECOGNIZE_AREA), GsonUtil.toJson(recognizeAreaEntity));
    }

    public RecognizeAreaEntity getRecognizeArea() {
        String json = getString(cameraPreferenceKey(SP_RECOGNIZE_AREA));
        if ("".equals(json)) {
            CameraConfigEntity cameraConfig = SPManager.getInstance().getCameraConfig();
            int width = 772;
            int height = 468;
            if (cameraConfig != null) {
                try {
                    String cameraResolution = cameraConfig.getCamera_resolution();
                    String[] split = cameraResolution.split("\\*");
                    width = Integer.parseInt(split[0]);
                    height = Integer.parseInt(split[1]);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "getRecognizeArea: " + e.getMessage());
                }
            }
            RecognizeAreaEntity recognizeArea = createDefaultRecognizeArea(width, height);
            putRecognizeArea(recognizeArea);
            return recognizeArea;
        }
        Type type = new TypeToken<RecognizeAreaEntity>() {
        }.getType();
        return GsonUtil.fromJson(json, type);
    }

    /**
     * 根据实际预览尺寸取得识别区；旧尺寸按比例转换，不缩小已保存的相对范围。
     * 首帧调用时才使用真实分辨率生成默认区域。
     */
    public RecognizeAreaEntity getRecognizeArea(int width, int height) {
        RecognizeAreaEntity area;
        if (getString(cameraPreferenceKey(SP_RECOGNIZE_AREA)).isEmpty()) {
            area = createDefaultRecognizeArea(width, height);
        } else {
            area = getRecognizeArea();
            if (area == null || area.getWidth() <= 0 || area.getHeight() <= 0
                    || area.getPoints() == null || area.getPoints().size() != 4) {
                area = createDefaultRecognizeArea(width, height);
            } else if (area.getWidth() != width || area.getHeight() != height) {
                for (PointF point : area.getPoints()) {
                    point.x *= (float) width / area.getWidth();
                    point.y *= (float) height / area.getHeight();
                }
                area.setWidth(width);
                area.setHeight(height);
            }
        }
        putRecognizeArea(area);
        return area;
    }

    /** 创建以预览中心为基准、宽高各占 50% 的默认识别区，不写入偏好。 */
    public static RecognizeAreaEntity createDefaultRecognizeArea(int width, int height) {
        RecognizeAreaEntity area = new RecognizeAreaEntity();
        area.setWidth(width);
        area.setHeight(height);
        float[] coordinates = computeDefaultRecognizeAreaPoints(width, height);
        List<PointF> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            points.add(new PointF(coordinates[i], coordinates[i + 1]));
        }
        area.setPoints(points);
        return area;
    }

    /** 计算居中且宽高各占预览 50% 的四角，依次为左上、右上、右下、左下。 */
    static float[] computeDefaultRecognizeAreaPoints(int width, int height) {
        float left = width * 0.25f;
        float right = width * 0.75f;
        float top = height * 0.25f;
        float bottom = height * 0.75f;
        return new float[]{left, top, right, top, right, bottom, left, bottom};
    }


    public String getString(String key) {
        return sharedPreferences.getString(key, "");
    }

    public String getString(String key, String defValue) {
        return sharedPreferences.getString(key, defValue);
    }

    public long getLong(String key) {
        return sharedPreferences.getLong(key, 0L);
    }

    public float getFloat(String key, float defaultFloat) {
        return sharedPreferences.getFloat(key, defaultFloat);
    }

    public int getInt(String key) {
        return sharedPreferences.getInt(key, 0);
    }

    public int getInt(String key, int defaultInt) {
        return sharedPreferences.getInt(key, defaultInt);
    }
    public Set<String> getStringSet(String key) {
        Set<String> value = sharedPreferences.getStringSet(key, Collections.emptySet());
        return new HashSet<>(value);
    }

    public void putStringSet(String key, Set<String> value) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putStringSet(key, new HashSet<>(value));
        boolean commit = edit.commit();
        Log.d(LOG_TAG, "SP save result: " + commit + ", key=" + key);
    }
    public boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    public void putString(String key, String str) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putString(key, str);
        boolean commit = edit.commit();
        // log打印保存结果
        Log.d(LOG_TAG, "SP save results：" + commit + str);
    }

    public void putLong(String key, long l) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putLong(key, l);
        boolean commit = edit.commit();
        // log打印保存结果
        Log.d(LOG_TAG, "SP save results：" + commit + l);
    }

    public void putFloat(String key, float l) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putFloat(key, l);
        boolean commit = edit.commit();
        // log打印保存结果
        Log.d(LOG_TAG, "SP save results：" + commit + l);
    }


    public void putInt(String key, int l) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt(key, l);
        boolean commit = edit.commit();
        // log打印保存结果
        Log.d(LOG_TAG, "SP save results：" + commit + l);
    }

    public void putBoolean(String key, boolean l) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean(key, l);
        boolean commit = edit.commit();
        // log打印保存结果
        Log.d(LOG_TAG, "SP save results：" + commit + l);
    }

    /**
     * 学习图 uid 映射条目（{@link #SP_STUDY_UID_MAP} 值结构 {plu:[{picId,uid}]}）。
     */
    public static class StudyUidEntry {
        public int picId;
        public long uid;

        public StudyUidEntry() {
        }

        public StudyUidEntry(int picId, long uid) {
            this.picId = picId;
            this.uid = uid;
        }
    }

    /**
     * 解析学习 uid 映射 JSON 为 Map（纯函数，空/非法 JSON 容错返回空 Map；
     * 不依赖 android 类，供 JVM 单测）。
     *
     * @param json 映射 JSON（{plu:[{picId,uid}]}）
     * @return plu → 条目列表（LinkedHashMap，保持写入序）
     */
    static Map<String, List<StudyUidEntry>> parseStudyUidMap(String json) {
        Map<String, List<StudyUidEntry>> empty = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) {
            return empty;
        }
        try {
            Type type = new TypeToken<LinkedHashMap<String, List<StudyUidEntry>>>() {
            }.getType();
            Map<String, List<StudyUidEntry>> map = GsonUtil.fromJson(json, type);
            return map == null ? empty : map;
        } catch (Exception e) {
            return empty;
        }
    }

    /**
     * 写入学习 uid 映射条目（纯函数）：同 plu 同 picId 已存在时替换（进程重启后
     * PIC_ID_SEQ 复位会复用文件名，新样本覆写旧文件，映射同步覆写以保持一致）。
     *
     * @param json  现有映射 JSON
     * @param plu   商品 PLU
     * @param picId 学习小图编号（文件名主体）
     * @param uid   itemLearn 回执句柄
     * @return 写入后的映射 JSON
     */
    static String putStudyUidEntry(String json, String plu, int picId, long uid) {
        Map<String, List<StudyUidEntry>> map = parseStudyUidMap(json);
        if (plu == null || plu.isEmpty() || picId <= 0 || uid <= 0) {
            return GsonUtil.toJson(map);
        }
        List<StudyUidEntry> entries = map.get(plu);
        if (entries == null) {
            entries = new ArrayList<>();
            map.put(plu, entries);
        }
        StudyUidEntry exist = null;
        for (StudyUidEntry entry : entries) {
            if (entry != null && entry.picId == picId) {
                exist = entry;
                break;
            }
        }
        if (exist != null) {
            exist.uid = uid;
        } else {
            entries.add(new StudyUidEntry(picId, uid));
        }
        return GsonUtil.toJson(map);
    }

    /**
     * 查询学习小图对应的 uid（纯函数）。
     *
     * @param json  映射 JSON
     * @param plu   商品 PLU
     * @param picId 学习小图编号
     * @return uid；无映射返回 -1
     */
    static long findStudyUid(String json, String plu, int picId) {
        List<StudyUidEntry> entries = parseStudyUidMap(json).get(plu);
        if (entries == null) {
            return -1L;
        }
        for (StudyUidEntry entry : entries) {
            if (entry != null && entry.picId == picId) {
                return entry.uid;
            }
        }
        return -1L;
    }

    /**
     * 移除单条学习 uid 映射（纯函数，单图删除成功后调用）。
     *
     * @param json  映射 JSON
     * @param plu   商品 PLU
     * @param picId 学习小图编号
     * @return 移除后的映射 JSON
     */
    static String removeStudyUidEntry(String json, String plu, int picId) {
        Map<String, List<StudyUidEntry>> map = parseStudyUidMap(json);
        List<StudyUidEntry> entries = map.get(plu);
        if (entries != null) {
            entries.removeIf(entry -> entry != null && entry.picId == picId);
            if (entries.isEmpty()) {
                map.remove(plu);
            }
        }
        return GsonUtil.toJson(map);
    }

    /**
     * 移除商品全部学习 uid 映射（纯函数，整组遗忘/删除商品成功后调用）。
     *
     * @param json 映射 JSON
     * @param plu  商品 PLU
     * @return 移除后的映射 JSON
     */
    static String removeStudyUidPlu(String json, String plu) {
        Map<String, List<StudyUidEntry>> map = parseStudyUidMap(json);
        map.remove(plu);
        return GsonUtil.toJson(map);
    }

    /**
     * 持久化学习小图 uid 映射（学习成功时调用，picId 与小图文件名一致）。
     *
     * @param plu   商品 PLU
     * @param picId 学习小图编号（文件名主体）
     * @param uid   itemLearn 回执句柄
     */
    public void saveStudyUid(String plu, int picId, long uid) {
        putString(SP_STUDY_UID_MAP, putStudyUidEntry(getString(SP_STUDY_UID_MAP), plu, picId, uid));
    }

    /**
     * 查询学习小图对应的远端 uid（跨会话单图删除 itemForgetByUid 寻址）。
     *
     * @param plu   商品 PLU
     * @param picId 学习小图编号
     * @return uid；无映射返回 -1
     */
    public long getStudyUid(String plu, int picId) {
        return findStudyUid(getString(SP_STUDY_UID_MAP), plu, picId);
    }

    /**
     * 移除单条学习 uid 映射（单图删除成功后调用）。
     *
     * @param plu   商品 PLU
     * @param picId 学习小图编号
     */
    public void removeStudyUid(String plu, int picId) {
        putString(SP_STUDY_UID_MAP, removeStudyUidEntry(getString(SP_STUDY_UID_MAP), plu, picId));
    }

    /**
     * 移除商品全部学习 uid 映射（整组遗忘/删除商品成功后调用）。
     *
     * @param plu 商品 PLU
     */
    public void removeStudyPlu(String plu) {
        putString(SP_STUDY_UID_MAP, removeStudyUidPlu(getString(SP_STUDY_UID_MAP), plu));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 成功绑定后同次写入归属和门店资料；空资料覆盖旧店值，避免换店后串用。 */
    public void saveBinding(cc.huoyan.sdk.client.model.BindingResult binding, long generation) {
        if (binding == null || !binding.hasBindingScope()) {
            throw new IllegalArgumentException("只允许保存完整的成功绑定回执");
        }
        sharedPreferences.edit()
                .putString(SP_SDK_BIND_ACCOUNT, binding.getAccountId())
                .putString(SP_SHOP_ID, binding.getShopId())
                .putString(SP_SHOP_NAME, binding.getShopName() == null ? "" : binding.getShopName())
                .putString(SP_SHOP_NO, binding.getShopNo() == null ? "" : binding.getShopNo())
                .putString(SP_SHOP_ADDRESS, binding.getShopAddress() == null ? "" : binding.getShopAddress())
                .putString(SP_SHOP_PHONE, binding.getShopPhone() == null ? "" : binding.getShopPhone())
                .putLong(SP_SDK_BIND_GENERATION, generation)
                .apply();
    }

    /**
     * 查询已绑定账号（商品同步 scope 来源）。
     *
     * @return 绑定账号字符串；未绑定返回空串
     */
    public String getBindAccountId() {
        return getString(SP_SDK_BIND_ACCOUNT);
    }

    /** 只有成功绑定后保存的账号和门店均存在时，才允许开启云端服务。 */
    public boolean hasBoundShop() {
        return !isBlank(getBindAccountId()) && !isBlank(getString(SP_SHOP_ID));
    }

    /**
     * 查询当前绑定代。
     *
     * @return 绑定代；未绑定返回 0
     */
    public long getBindGeneration() {
        return getLong(SP_SDK_BIND_GENERATION);
    }

}
