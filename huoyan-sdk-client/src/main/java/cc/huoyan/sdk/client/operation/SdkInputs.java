package cc.huoyan.sdk.client.operation;
/** SDK 样本标识与字符串分类编码的输入约束。 */
public final class SdkInputs {
    /** 静态参数工具。 */
    private SdkInputs() {}
    /** 样本标识必须为正 long。 */
    public static long positiveId(long id) {
        if (id <= 0) throw new IllegalArgumentException("SDK 标识必须为正数");
        return id;
    }
    /** 原样校验分类字符串，区分大小写并保留前导零。 */
    public static String clazz(String value) {
        if (!isClazz(value)) throw new IllegalArgumentException("分类编码须为 1–50 位英文字母或数字");
        return value;
    }
    /** 判断分类字符串是否合法。 */
    public static boolean isClazz(String value) {
        return value != null && value.matches("[A-Za-z0-9]{1,50}");
    }
}
