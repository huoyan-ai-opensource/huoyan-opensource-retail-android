package cc.huoyan.retail.config;

/**
 * 门店场景对应的模块开关默认值表（Issue 决策 8）。
 * <p>纯静态工具类，不依赖 Android {@code Context}，可直接单元测试。
 * <ul>
 *     <li>称重模块（weighing）默认关（2026-08-27 调整）：首次启动不开启，
 *     避免无秤设备启动即报错；有秤门店可在设置中手动开启并持久化；</li>
 *     <li>打印模块（print）默认关（2026-08-27 调整）：同上，避免无打印机设备报错。</li>
 * </ul>
 */
public final class SceneDefaults {

    private SceneDefaults() {
    }

    /**
     * 称重模块（weighing）在该场景下的默认开关。
     * <p>2026-08-27 调整为统一默认关：首次启动不开启，避免无秤设备启动即报错；
     * 有秤门店可在设置中手动开启（经 {@code ModuleConfig} 持久化后不再取本默认值）。
     *
     * @param scene 门店场景（当前不影响返回值，保留入参以便门店级配置演进）
     * @return 统一返回 false
     */
    public static boolean defaultWeighingEnabled(Scene scene) {
        return false;
    }

    /**
     * 打印模块（print）在该场景下的默认开关。
     * <p>2026-08-27 调整为统一默认关：首次启动不开启，避免无打印机设备启动即报错；
     * 有打印机门店可在设置中手动开启（经 {@code ModuleConfig} 持久化后不再取本默认值）。
     *
     * @param scene 门店场景（当前不影响返回值，保留入参以便门店级配置演进）
     * @return 统一返回 false
     */
    public static boolean defaultPrintEnabled(Scene scene) {
        return false;
    }

    /**
     * 默认门店场景。
     * <p>返回 {@link Scene#SNACKS}（零食）：与当前通用客户端（源自零食业态）的现状兼容，
     * 场景未写入 SP 前按零食业态取默认开关。
     *
     * @return {@link Scene#SNACKS}
     */
    public static Scene defaultScene() {
        return Scene.SNACKS;
    }
}
