package cc.huoyan.retail.common.ui;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.fragment.app.DialogFragment;

/** 商品搜索弹窗的窗口约束，横竖屏共用可压缩列表的居中布局。 */
public final class SearchDialogWindow {
    /** 仅提供窗口配置，不创建实例。 */
    private SearchDialogWindow() { }

    /** 在弹窗 onStart 时按宿主可用区域设置尺寸，中文输入法出现时压缩列表。 */
    public static void apply(DialogFragment fragment) {
        fragment.requireDialog().setCanceledOnTouchOutside(true);
        Window window = fragment.requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        Rect available = new Rect();
        fragment.requireActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        float density = fragment.getResources().getDisplayMetrics().density;
        window.setGravity(Gravity.CENTER);
        window.setLayout(Math.min(Math.round(900 * density), available.width() - Math.round(32 * density)),
                Math.min(Math.round(690 * density), available.height() - Math.round(48 * density)));
    }
}
