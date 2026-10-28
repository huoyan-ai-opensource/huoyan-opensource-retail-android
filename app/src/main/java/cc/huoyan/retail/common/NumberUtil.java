package cc.huoyan.retail.common;

import java.text.DecimalFormat;

public class NumberUtil {

    private static final DecimalFormat df2 = new DecimalFormat("#.##");

    public static String decimalFormat(float number) {
        return df2.format(number);
    }

    public static boolean isInteger(String str) {
        if (str == null || str.isEmpty()) return false;
        int i = 0;
        // 处理负号
        if (str.charAt(0) == '-') {
            if (str.length() == 1) return false; // 不能只有负号
            i = 1;
        }
        // 检查剩余字符是否为数字
        for (; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isFloat(String str) {
        // 正则表达式：匹配整数或小数
        String regex = "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
        return str.matches(regex);
    }
}
