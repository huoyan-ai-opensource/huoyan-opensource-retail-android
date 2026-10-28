package cc.huoyan.retail.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 模块开关与门店场景的本地配置读写（Issue 决策 7）。
 * <p>门店配置（场景 + 模块开关）纯本地 {@link SharedPreferences} 存储，不经过服务端；
 * 服务端下发为未来演进方向，届时仅替换本层读写实现，业务调用方不变。
 * <p>读写规则：
 * <ul>
 *     <li>开关未显式写入时，按当前场景查 {@link SceneDefaults} 默认值表取默认；</li>
 *     <li>切换场景（{@link #setScene}）不联动改写已显式写入的开关，仅影响未写入项的默认值。</li>
 * </ul>
 * 本期仅建立配置层能力，业务接线在后续任务完成。
 */
public final class ModuleConfig {

    /** SP 文件名：与 {@code SPManager} 共用同一存储文件（"HuoYanApplication"）。 */
    static final String SP_FILE_NAME = "HuoYanApplication";

    /** 门店场景 key，值为 {@link Scene} 枚举名。 */
    public static final String SP_SCENE = "SP_SCENE";
    /** 称重模块开关 key。 */
    public static final String SP_WEIGHING_ENABLED = "SP_WEIGHING_ENABLED";
    /** 打印模块开关 key。 */
    public static final String SP_PRINT_ENABLED = "SP_PRINT_ENABLED";
    /** 外传模块开关 key（M4 预留）。 */
    public static final String SP_TRANSFER_ENABLED = "SP_TRANSFER_ENABLED";

    private ModuleConfig() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取门店场景。
     *
     * @param context 上下文
     * @return 已写入且合法的场景；无记录或值非法时返回 {@link SceneDefaults#defaultScene()}
     */
    public static Scene getScene(Context context) {
        Scene scene = Scene.fromName(sp(context).getString(SP_SCENE, null));
        return scene != null ? scene : SceneDefaults.defaultScene();
    }

    /**
     * 写入门店场景。不联动改写已显式写入的模块开关。
     *
     * @param context 上下文
     * @param scene   门店场景
     */
    public static void setScene(Context context, Scene scene) {
        sp(context).edit().putString(SP_SCENE, scene.name()).apply();
    }

    /**
     * 称重模块（weighing）开关。
     *
     * @param context 上下文
     * @return 未显式写入时返回 {@link SceneDefaults#defaultWeighingEnabled(Scene)} 按当前场景取默认
     */
    public static boolean isWeighingEnabled(Context context) {
        boolean def = SceneDefaults.defaultWeighingEnabled(getScene(context));
        return sp(context).getBoolean(SP_WEIGHING_ENABLED, def);
    }

    /**
     * 显式写入称重模块（weighing）开关。
     *
     * @param context 上下文
     * @param enabled 开关值
     */
    public static void setWeighingEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(SP_WEIGHING_ENABLED, enabled).apply();
    }

    /**
     * 打印模块（print）开关。
     *
     * @param context 上下文
     * @return 未显式写入时返回 {@link SceneDefaults#defaultPrintEnabled(Scene)} 按当前场景取默认
     */
    public static boolean isPrintEnabled(Context context) {
        boolean def = SceneDefaults.defaultPrintEnabled(getScene(context));
        return sp(context).getBoolean(SP_PRINT_ENABLED, def);
    }

    /**
     * 显式写入打印模块（print）开关。
     *
     * @param context 上下文
     * @param enabled 开关值
     */
    public static void setPrintEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(SP_PRINT_ENABLED, enabled).apply();
    }

    /**
     * 外传模块（transfer）开关，M4 外传预留。
     *
     * @param context 上下文
     * @return 未显式写入时返回 false（M4 接入前外传一律不启用）
     */
    public static boolean isTransferEnabled(Context context) {
        return sp(context).getBoolean(SP_TRANSFER_ENABLED, false);
    }

    /**
     * 显式写入外传模块（transfer）开关，M4 外传预留。
     *
     * @param context 上下文
     * @param enabled 开关值
     */
    public static void setTransferEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(SP_TRANSFER_ENABLED, enabled).apply();
    }
}
