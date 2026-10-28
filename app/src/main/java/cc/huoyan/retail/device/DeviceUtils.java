/*
 * Copyright 2017 JessYan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cc.huoyan.retail.device;

import android.annotation.SuppressLint;
import android.os.Build;

import java.lang.reflect.Method;

/** 提供设备授权和首页展示需要的型号、硬件序列号。 */
public class DeviceUtils {
    private DeviceUtils() {
        throw new IllegalStateException("you can't instantiate me!");
    }

    /** 返回系统报告的设备型号。 */
    public static String getPhoneType() {
        return Build.MODEL;
    }

    /**
     * 依次读取设备序列号属性和系统接口；系统均未提供时返回 unknown。
     *
     * @return 手机序列号
     */
    @SuppressLint({"NewApi", "MissingPermission"})
    public static String getSerialNumber() {
        String serial = readSystemProperty("ro.serialno");
        if (isUsableSerial(serial)) return serial;
        serial = readSystemProperty("ro.boot.serialno");
        if (isUsableSerial(serial)) return serial;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                serial = Build.getSerial();
                if (isUsableSerial(serial)) return serial;
            } catch (SecurityException ignored) {
                // Android 10 及以上可能限制普通应用读取硬件序列号。
            }
        }
        return isUsableSerial(Build.SERIAL) ? Build.SERIAL : "unknown";
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class);
            get.setAccessible(true);
            return (String) get.invoke(null, key);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static boolean isUsableSerial(String serial) {
        return serial != null && !serial.trim().isEmpty()
                && !"unknown".equalsIgnoreCase(serial.trim())
                && !"null".equalsIgnoreCase(serial.trim());
    }
}
