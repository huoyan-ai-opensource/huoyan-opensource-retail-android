package cc.huoyan.retail.common;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;

/** 语言偏好与资源包装；默认跟随系统，也可手动选择中文或英文。 */
public final class LocalePreferenceUtil {
    /** 默认语言模式，按系统首选语言映射为中文或英文。 */
    public static final String CODE_SYSTEM = "system";
    /** 中文语言标识，覆盖简体、繁体及各地区变体。 */
    public static final String CODE_ZH = "zh";
    /** 非中文系统统一使用的英文语言标识。 */
    public static final String CODE_EN = "en";
    /** 与应用其他设置共用的偏好文件。 */
    private static final String PREFERENCES_NAME = "HuoYanApplication";
    /** 用户选择的语言模式，不保存系统语言的映射结果。 */
    private static final String PREFERENCE_KEY = "SP_APP_LANGUAGE_CODE";

    /** 工具类不允许实例化。 */
    private LocalePreferenceUtil() { }

    /** 提取语言标签的主语言；未知或缺失的标签也使用英文。 */
    @NonNull
    public static String normalizeLanguageCode(String input) {
        if (input == null) return CODE_EN;
        String primary = input.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return CODE_ZH.equals(primary) ? CODE_ZH : CODE_EN;
    }

    /** 将系统语言标签映射为客户端支持的中文或英文区域。 */
    @NonNull
    public static Locale codeToLocale(String code) {
        return CODE_ZH.equals(normalizeLanguageCode(code))
                ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
    }

    /** 校验持久化语言模式，未设置或未知值统一跟随系统。 */
    @NonNull
    public static String normalizePreference(String code) {
        return CODE_ZH.equals(code) || CODE_EN.equals(code) ? code : CODE_SYSTEM;
    }

    /** 根据用户模式与系统首选语言决定显示语言；手动模式不受系统语言变化影响。 */
    @NonNull
    public static Locale resolveLocale(String preference, String systemLanguage) {
        String mode = normalizePreference(preference);
        return codeToLocale(CODE_SYSTEM.equals(mode) ? systemLanguage : mode);
    }

    /** 读取语言模式；首次使用默认跟随系统。 */
    @NonNull
    public static String getLanguageCode(@NonNull Context context) {
        return normalizePreference(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(PREFERENCE_KEY, CODE_SYSTEM));
    }

    /** 保存语言模式并刷新应用级资源，后台提示和随后创建的页面立即使用新语言。 */
    public static void setLanguageCode(@NonNull Context context, @NonNull String code) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFERENCE_KEY, normalizePreference(code)).apply();
        Resources resources = context.getApplicationContext().getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(getCurrentLocale(context));
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /** 从系统资源读取首选语言，避免应用资源包装导致跟随系统锁定为旧语言。 */
    @NonNull
    public static Locale getCurrentLocale(@NonNull Context context) {
        return resolveLocale(getLanguageCode(context),
                Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage());
    }

    /** 在 Application 和 Activity 创建时包装资源，采用当前偏好与最新系统语言。 */
    @NonNull
    public static Context wrap(@NonNull Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(getCurrentLocale(base));
        return base.createConfigurationContext(config);
    }
}
