package cc.huoyan.retail.common.ui;

import android.widget.TextView;
import cc.huoyan.retail.R;

/** 设备测试读取当前一次性提示，不向业务代码暴露可变窗口实例。 */
public final class ToastTestProbe {
    /** 工具类不创建实例。 */
    private ToastTestProbe() { }

    /** 主线程读取当前正在显示的文案；隐藏后返回空文本。 */
    public static String text() {
        if (ToastEx.currentView == null) return "";
        TextView text = ToastEx.currentView.findViewById(R.id.toast_text);
        return text.getText().toString();
    }

    /** 主线程清理上次提示，避免设备测试误读前一个操作的结果。 */
    public static void clear() {
        ToastEx.hideOnMain();
    }
}
