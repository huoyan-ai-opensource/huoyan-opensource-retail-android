package cc.huoyan.retail.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 本机退出验证配置；密码以随机盐派生摘要存储，与 SDK 凭据无关。 */
public final class ExitPasswordStore {
    /** 独立偏好文件，修改不影响业务配置。 */
    private static final String FILE = "checkout_exit_guard";
    /** 工具类不可实例化。 */
    private ExitPasswordStore() { }
    /** 获取应用私有偏好，避免保存页面引用。 */
    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    /** 退出验证默认关闭；开启后即使凭据损坏也不能绕过。 */
    public static boolean isEnabled(Context context) { return prefs(context).getBoolean("enabled", false); }
    /** 判断是否已配置密码。 */
    public static boolean hasPassword(Context context) { return prefs(context).contains("hash"); }
    /** 只允许已设置密码时开启验证。 */
    public static void setEnabled(Context context, boolean enabled) {
        if (enabled && !hasPassword(context)) throw new IllegalStateException("Password required");
        prefs(context).edit().putBoolean("enabled", enabled).apply();
    }
    /** 保存两次输入已确认的密码和开关，任何失败都不启用。 */
    public static boolean save(Context context, String password, boolean enabled) {
        if (!password.matches("[0-9]{1,12}")) return false;
        try {
            byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
            return prefs(context).edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("hash", Base64.encodeToString(derive(password, salt), Base64.NO_WRAP))
                    .putBoolean("enabled", enabled).commit();
        } catch (Exception failure) { return false; }
    }
    /** 常量时间比较摘要，缺少或损坏配置一律拒绝。 */
    public static boolean verify(Context context, String password) {
        try {
            SharedPreferences store = prefs(context);
            byte[] salt = Base64.decode(store.getString("salt", ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(store.getString("hash", ""), Base64.NO_WRAP);
            return salt.length == 16 && expected.length == 32 && MessageDigest.isEqual(expected, derive(password, salt));
        } catch (Exception failure) { return false; }
    }
    /** 派生本地密码摘要，计算后擦除临时字符数组。 */
    private static byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
