package cc.huoyan.retail.common;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

/** 系统语言映射的纯 Java 测试，不依赖 Android 配置桩。 */
public class LocalePreferenceUtilTest {
    /** 首次使用或无效偏好采用跟随系统，显式选择的中英文仍保留。 */
    @Test public void preferenceDefaultsToSystem() {
        for (String mode : new String[]{null, "", "system", "fr"}) {
            assertEquals("system", LocalePreferenceUtil.normalizePreference(mode));
        }
        assertEquals("zh", LocalePreferenceUtil.normalizePreference("zh"));
        assertEquals("en", LocalePreferenceUtil.normalizePreference("en"));
    }

    /** 手动选择优先于系统语言，包括系统改变之后。 */
    @Test public void manualSelectionOverridesSystem() {
        for (String system : new String[]{"zh-CN", "zh-Hant-TW", "en-US", "fr-FR", "ar", null}) {
            assertEquals(Locale.ENGLISH, LocalePreferenceUtil.resolveLocale("en", system));
            assertEquals(Locale.SIMPLIFIED_CHINESE, LocalePreferenceUtil.resolveLocale("zh", system));
        }
    }

    /** 切回跟随系统后，按每次传入的系统语言重新决定显示语言。 */
    @Test public void systemModeTracksSystemLanguage() {
        assertEquals(Locale.ENGLISH, LocalePreferenceUtil.resolveLocale("system", "fr-FR"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, LocalePreferenceUtil.resolveLocale("system", "zh-TW"));
        assertEquals(Locale.ENGLISH, LocalePreferenceUtil.resolveLocale("system", "en-US"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, LocalePreferenceUtil.resolveLocale(null, "zh-CN"));
    }

    /** 简体、繁体、地区和大小写变体均显示中文。 */
    @Test public void chineseVariantsUseChinese() {
        for (String tag : new String[]{"zh", "zh-CN", "zh-TW", "zh-HK", "zh-Hans", "zh-Hant-TW", "ZH_hant", " zh "}) {
            assertEquals(tag, "zh", LocalePreferenceUtil.normalizeLanguageCode(tag));
            assertEquals(tag, Locale.SIMPLIFIED_CHINESE, LocalePreferenceUtil.codeToLocale(tag));
        }
    }

    /** 英文及所有未支持的非中文语言均显示英文，避免落回中文。 */
    @Test public void nonChineseLanguagesUseEnglish() {
        for (String tag : new String[]{"en", "en-US", "EN_gb", "fr-FR", "ja-JP", "de", "ar", "ko", "es", "zho"}) {
            assertEquals(tag, "en", LocalePreferenceUtil.normalizeLanguageCode(tag));
            assertEquals(tag, Locale.ENGLISH, LocalePreferenceUtil.codeToLocale(tag));
        }
    }

    /** 缺失或未知语言使用英文默认值。 */
    @Test public void missingLanguagesUseEnglish() {
        for (String tag : new String[]{null, "", "   ", "und"}) {
            assertEquals("en", LocalePreferenceUtil.normalizeLanguageCode(tag));
            assertEquals(Locale.ENGLISH, LocalePreferenceUtil.codeToLocale(tag));
        }
    }
}
