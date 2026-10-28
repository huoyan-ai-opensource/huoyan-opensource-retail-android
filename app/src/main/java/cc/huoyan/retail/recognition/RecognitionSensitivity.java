package cc.huoyan.retail.recognition;

/** 五档触发灵敏度与汉明距离阈值的唯一映射，供设置页和识别线程共用。 */
public final class RecognitionSensitivity {
    /** 标准档保持原默认阈值 4；该常量不依赖 Android。 */
    public static final int DEFAULT_LEVEL = 3;

    /** 仅提供无状态的档位换算。 */
    private RecognitionSensitivity() { }

    /** 校验当前档位配置，缺省或非法值使用标准档。 */
    public static int validLevel(int level) {
        return level >= 1 && level <= 5 ? level : DEFAULT_LEVEL;
    }

    /** 档位越高阈值越低；帧距离仍须严格大于返回的阈值才触发。 */
    public static int threshold(int level) {
        switch (validLevel(level)) {
            case 1: return 8;
            case 2: return 6;
            case 4: return 2;
            case 5: return 1;
            default: return 4;
        }
    }
}
