package cc.huoyan.retail.common;

import java.util.UUID;

/**
 * Created by feng
 * Created on 2020/7/29
 */
public class UUIDUtils {
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static String getNo() {
        String s = "";
        String string = TimeUtils.ymd2String(System.currentTimeMillis());
        String s1 = String.valueOf(System.currentTimeMillis());
        if (s1.length() > 6) {
            string = string + s1.substring(s1.length() - 6, s1.length());
        }
        for (int i = 0; i < 4; i++) {
            s += String.valueOf((int) (Math.random() * 10));
        }
        return string + s;
    }

    public static String getMemberNo() {
        String s = "";
        String string = TimeUtils.ymd2String(System.currentTimeMillis());
        String s1 = String.valueOf(System.currentTimeMillis());
        if (s1.length() > 4) {
            string = string + s1.substring(s1.length() - 4, s1.length());
        }
        for (int i = 0; i < 2; i++) {
            s += String.valueOf((int) (Math.random() * 10));
        }
        return string + s;
    }

}
