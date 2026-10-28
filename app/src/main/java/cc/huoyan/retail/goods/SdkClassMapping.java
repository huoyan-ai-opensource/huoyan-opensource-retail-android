package cc.huoyan.retail.goods;

/** PLU 原样作为 SDK 分类编码，保留大小写及前导零。 */
public final class SdkClassMapping {
    /** 无效编码没有可调用的 SDK 类别。 */
    public static final String CLAZZ_INVALID = null;
    /** 校验后直接返回 PLU，不做数值转换。 */
    public static String clazzFromPlu(String plu) {
        return cc.huoyan.sdk.client.operation.SdkInputs.isClazz(plu) ? plu : CLAZZ_INVALID;
    }
    /** 静态工具。 */
    private SdkClassMapping() {}
}
