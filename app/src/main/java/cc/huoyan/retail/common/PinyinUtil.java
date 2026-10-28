package cc.huoyan.retail.common;


import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

public class PinyinUtil {

    /*
     * 中文转拼音(大写，全拼)
     */
    public static String getTextToPinyin(String text) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON);
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);

        StringBuilder pinyinBuilder = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                // 如果是空格，直接追加到结果中
                pinyinBuilder.append(c);
            } else {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        // 如果字符可以转化为拼音，则取第一个拼音并追加到结果中
                        pinyinBuilder.append(pinyinArray[0]);
                    } else {
                        // 如果字符无法转化为拼音（比如标点符号等），直接追加原字符
                        pinyinBuilder.append(c);
                    }
                } catch (Exception e) {
                    // 出现异常时，直接追加原字符
                    pinyinBuilder.append(c);
                }
            }
        }

        return pinyinBuilder.toString();
    }

    /*
     * 中文转拼音(大写，字符首字母)
     */
    public static String getTextToPinyinByCharFirst(String text) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON);
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);

        StringBuilder pinyinBuilder = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                // 如果是空格，直接追加到结果中
                pinyinBuilder.append(c);
            } else {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        // 如果字符可以转化为拼音，则取第一个拼音并追加到结果中
                        pinyinBuilder.append(pinyinArray[0].substring(0,1));
                    } else {
                        // 如果字符无法转化为拼音（比如标点符号等），直接追加原字符
                        pinyinBuilder.append(c);
                    }
                } catch (Exception e) {
                    // 出现异常时，直接追加原字符
                    pinyinBuilder.append(c);
                }
            }
        }

        return pinyinBuilder.toString();
    }
}
