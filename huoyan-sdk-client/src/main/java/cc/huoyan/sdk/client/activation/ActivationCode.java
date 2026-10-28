package cc.huoyan.sdk.client.activation;
/** 激活码兼容格式：十六位码按四位分段，其他扫码 serial 原样提交。 */
public final class ActivationCode {
    /** 静态输入工具。 */
    private ActivationCode() {}
    /** 规范化已有十六位激活码，不猜测其他长度的编码。 */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String code = input.replace("-", "");
        if (code.length() == 16) {
            return code.substring(0, 4) + "-" + code.substring(4, 8) + "-"
                    + code.substring(8, 12) + "-" + code.substring(12, 16);
        }
        return input;
    }

    /** 现有手输入口的十六位格式；不是对所有扫码 serial 的长度限制。 */
    public static boolean validManual(String input) { return input != null && input.replace("-", "").length() == 16; }
}
