package cc.huoyan.retail.common.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import androidx.fragment.app.DialogFragment;

/** 横竖屏弹窗由各自 XML 布局绘制同色遮罩，窗口使用完整的应用可用区域。 */
public final class LandscapeDialogWindow {
    private LandscapeDialogWindow() { }

    /** 按屏幕方向配置全屏弹窗的遮罩、尺寸和输入法行为。 */
    public static boolean apply(DialogFragment fragment) {
        if (fragment.getDialog() == null) return false;
        Window window = fragment.getDialog().getWindow();
        if (window == null) return false;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | (fragment.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING));
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        return true;
    }
}
