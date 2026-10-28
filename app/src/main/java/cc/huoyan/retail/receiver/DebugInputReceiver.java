package cc.huoyan.retail.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.data.SPManager;

/**
 * 物理介入注入接收器（android-test-lab 平台，场景录制/回放，debug-only）。
 *
 * 监听外部广播（action: com.adtc.debug.INPUT），extra "data" 格式：
 *   [debug] weigh 0.385              → 称重注入（读 lastWeight()）
 *   [debug] camera /sdcard/f1.png    → 摄像头喂帧（feedFrame）
 *   [debug] scan 690102831111        → 扫码注入（发 com.adtc.debug.SCAN 广播，
 *                                      宿主 Activity/输入层接听并入扫码路径）
 *   [debug] recognize 1001,1002      → 识别结果注入（发 com.adtc.debug.RECOGNIZE 广播，
 *                                      宿主 MainActivity 接听并走与真实识别结果
 *                                      相同的分发/前置校验链路）
 *   [debug] goods 1001 测试面包 250 1 → 通过 SDK 新建商品（后台等待真实回执，
 *                                      参数：PLU 名称(无空格) 单价分 unit，5=计件 其余=称重）
 *   [debug] config weighing on|off    → 模块开关写入（ModuleConfig.setWeighingEnabled）
 *   [debug] config print on|off       → 打印模块开关写入（ModuleConfig.setPrintEnabled，
 *                                      真机无打印机场景结算前关闭，避免打印异常阻断提交）
 *   [debug] config recognize_area reset → 识别区域重置（清除 SP_RECOGNIZE_AREA，
 *                                      下次 getRecognizeArea 重建默认 15%~85% 区域，
 *                                      自动化场景开始前隔离上一场景残留）
 *
 * 接入点（按真实实现接线，P5 再接）：
 *   - WeightSource：真实电子秤读数源；默认 Mock 存注入值（lastWeight()）
 *   - CameraSource：feedFrame(路径) 喂本地图片给识别模块
 *   - scan：宿主接听 SCAN 广播 → 走现有扫码输入
 *   - recognize：宿主接听 RECOGNIZE 广播 → 模拟识别到指定 PLU 商品
 *
 * 仅 debug 构建有行为（BuildConfig.DEBUG 守卫，release 零开销）。
 */
public final class DebugInputReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.adtc.debug.INPUT";
    public static final String ACTION_SCAN = "com.adtc.debug.SCAN";
    public static final String ACTION_RECOGNIZE = "com.adtc.debug.RECOGNIZE";
    public static final String EXTRA_RECOGNIZE_PLU = "plu";
    private static final String TAG = "DebugInput";

    // ── 可替换输入源接口（项目在 Application/入口 attach 真实实现）──
    public interface WeightSource { double weight(); }
    public interface CameraSource { void feedFrame(String path); }

    private static volatile double lastWeight = 0.0;
    private static volatile CameraSource cameraSource = path ->
            Log.d(TAG, "camera feed 未接线（接入 CameraSource 后生效）: " + path);

    /** 项目入口注册真实输入源；返回可替换的注入读数 */
    public static void attach(CameraSource camera) {
        if (camera != null) cameraSource = camera;
    }
    public static double lastWeight() { return lastWeight; }
    // ──────────────────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) return;                 // release 零行为
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String data = intent.getStringExtra("data");
        if (data == null) data = intent.getDataString();
        if (data == null || !data.startsWith("[debug] ")) return;
        String cmd = data.substring("[debug] ".length()).trim();
        Log.d(TAG, "[debug] " + cmd);

        if (cmd.startsWith("weigh ")) {
            try {
                lastWeight = Double.parseDouble(cmd.substring("weigh ".length()).trim());
                Log.d(TAG, "[Biz] weight=" + lastWeight);
            } catch (NumberFormatException e) {
                Log.w(TAG, "weigh 参数非法: " + cmd);
            }
        } else if (cmd.startsWith("camera ")) {
            cameraSource.feedFrame(cmd.substring("camera ".length()).trim());
        } else if (cmd.startsWith("scan ")) {
            String code = cmd.substring("scan ".length()).trim();
            Intent scan = new Intent(ACTION_SCAN);
            scan.putExtra("code", code);
            context.sendBroadcast(scan);                 // 宿主接听并入扫码路径
            Log.d(TAG, "[Biz] scan_code=" + code);
        } else if (cmd.startsWith("recognize ")) {
            String[] rawPlus = cmd.substring("recognize ".length()).trim().split(",");
            StringBuilder pluListBuilder = new StringBuilder();
            for (String rawPlu : rawPlus) {
                String plu = rawPlu.trim();
                if (plu.isEmpty()) {
                    continue;
                }
                if (pluListBuilder.length() > 0) {
                    pluListBuilder.append(',');
                }
                pluListBuilder.append(plu);
            }
            String pluList = pluListBuilder.toString();
            if (pluList.isEmpty()) {
                Log.w(TAG, "recognize 参数为空: " + cmd);
                return;
            }
            Intent recognize = new Intent(ACTION_RECOGNIZE);
            recognize.putExtra(EXTRA_RECOGNIZE_PLU, pluList);
            context.sendBroadcast(recognize);            // 宿主接听并入识别确认路径
            Log.d(TAG, "[Biz] recognize_plu=" + pluList);
        } else if (cmd.startsWith("goods ")) {
            // 参数：PLU 名称(无空格) 单价分 unit（5=计件，其余=称重）
            String[] parts = cmd.substring("goods ".length()).trim().split("\\s+");
            if (parts.length < 4) {
                Log.w(TAG, "goods 参数不足（需 PLU 名称 单价分 unit）: " + cmd);
                return;
            }
            try {
                GoodsEntity goods = new GoodsEntity();
                goods.setPlu(parts[0]);
                goods.setName(parts[1]);
                goods.setPrice(Long.parseLong(parts[2]));
                goods.setUnit(Integer.parseInt(parts[3]));
                PendingResult pending = goAsync();
                cc.huoyan.retail.common.IOThreadHelper.getInstance().post(() -> {
                    try {
                        SdkGoodsSource.getInstance().save(goods, false);
                        Log.d(TAG, "[Biz] goods_injected plu=" + goods.getPlu()
                                + " name=" + goods.getName() + " price=" + goods.getPrice() + " unit=" + goods.getUnit());
                    } catch (RuntimeException error) {
                        Log.w(TAG, "SDK 商品创建失败", error);
                    } finally { pending.finish(); }
                });
            } catch (NumberFormatException e) {
                Log.w(TAG, "goods 参数非法（单价分/unit 须为整数）: " + cmd);
            }
        } else if (cmd.startsWith("config ")) {
            // 参数：weighing on|off / print on|off / recognize_area reset
            String[] parts = cmd.substring("config ".length()).trim().split("\\s+");
            if (parts.length == 2 && "recognize_area".equals(parts[0]) && "reset".equals(parts[1])) {
                // 写空串等同清除（getRecognizeArea 对空串走默认 15%~85% 重建并回写）
                SPManager.getInstance().putString(SPManager.SP_RECOGNIZE_AREA, "");
                Log.d(TAG, "[Biz] recognize_area=reset");
            } else if (parts.length == 2 && ("weighing".equals(parts[0]) || "print".equals(parts[0]))
                    && ("on".equals(parts[1]) || "off".equals(parts[1]))) {
                boolean enabled = "on".equals(parts[1]);
                if ("weighing".equals(parts[0])) {
                    ModuleConfig.setWeighingEnabled(context, enabled);
                    Log.d(TAG, "[Biz] config_weighing=" + enabled);
                } else {
                    ModuleConfig.setPrintEnabled(context, enabled);
                    Log.d(TAG, "[Biz] config_print=" + enabled);
                }
            } else {
                Log.w(TAG, "config 参数非法（支持 weighing on|off / print on|off / recognize_area reset）: " + cmd);
            }
        } else {
            Log.w(TAG, "未识别注入命令: " + cmd);
        }
    }
}
