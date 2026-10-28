package cc.huoyan.retail.common;
import cc.huoyan.retail.goods.SaleModeMapper;

import android.content.Context;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.R;

/** 为后台服务和单例按当前语言读取文案，避免沿用应用启动时的语言。 */
public final class LocalizedText {
    private LocalizedText() { }

    /** 按当前保存语言获取资源文案及格式参数。 */
    public static String get(int id, Object... args) {
        return LocalePreferenceUtil.wrap(RetailApplication.getInstance()).getString(id, args);
    }

    /** 返回界面上的计价单位，上传协议继续使用 SaleModeMapper 的固定单位。 */
    public static String unit(Context context, int localUnit) {
        return context.getString(localUnit == 5 ? R.string.display_unit_count : R.string.display_unit_weight);
    }
}
