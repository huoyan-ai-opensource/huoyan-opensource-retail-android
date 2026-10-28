package cc.huoyan.retail.settings;

/** 手输键盘专用约束，不改变 SDK 扫码协议。 */
final class ActivationKeyboardInput {
    /** 业务指定的大写 Base58 子集，保留大写 L。 */
    static final String CHARACTERS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 静态规则无需实例。 */
    private ActivationKeyboardInput() { }
    /** 检查完整输入或前缀，禁止静默改写大小写及非法字符。 */
    static boolean accepts(String value) {
        if (value == null || value.length() > 16) return false;
        for (int i = 0; i < value.length(); i++) {
            if (CHARACTERS.indexOf(value.charAt(i)) < 0) return false;
        }
        return true;
    }
    /** 仅回填可手输的已存码，其他协议串号保留在存储中。 */
    static String restore(String value) {
        if (value == null) return "";
        String code = value.replace("-", "");
        return accepts(code) ? code : "";
    }
    /** 输入完整且字符合法才允许手动提交。 */
    static boolean complete(String value) { return accepts(value) && value.length() == 16; }
}
