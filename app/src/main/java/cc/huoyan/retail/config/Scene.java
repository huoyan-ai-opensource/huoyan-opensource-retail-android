package cc.huoyan.retail.config;

/**
 * 门店场景（业务业态）。
 * <p>通用零售客户端面向四类业态（Issue 决策 7/8）：
 * <ul>
 *     <li>{@link #FRESH}：生鲜，散称 + 按个混合；</li>
 *     <li>{@link #SNACKS}：零食，散称 + 按个混合（当前通用客户端的现状基线）；</li>
 *     <li>{@link #BAKERY}：烘焙，按个计价；</li>
 *     <li>{@link #BOWL}：小碗菜，按份计价。</li>
 * </ul>
 * 门店场景决定模块开关默认值，默认值表见 {@link SceneDefaults}，
 * 场景与开关的本地读写见 {@link ModuleConfig}。
 */
public enum Scene {

    /** 生鲜：散称 + 按个混合。 */
    FRESH,

    /** 零食：散称 + 按个混合（当前通用客户端的现状基线）。 */
    SNACKS,

    /** 烘焙：按个计价。 */
    BAKERY,

    /** 小碗菜：按份计价。 */
    BOWL;

    /**
     * 将证书模型编码映射为门店类型，支持 bakery-rk3576 等带平台后缀的编码。
     * 未知模型保留空态，避免误标为默认零食场景。
     */
    public static Scene fromModelCode(String code) {
        if (code == null) return null;
        String type = code.trim().toUpperCase(java.util.Locale.ROOT).split("[-_]", 2)[0];
        return fromName(type);
    }

    /**
     * 由存储名解析场景。
     * <p>{@link ModuleConfig} 以枚举名（{@link #name()}）作为 SP 存储编码。
     *
     * @param name SP 中存储的场景名
     * @return 合法枚举名返回对应场景；null/空串/非法名返回 null（由调用方回退默认场景）
     */
    public static Scene fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Scene.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
