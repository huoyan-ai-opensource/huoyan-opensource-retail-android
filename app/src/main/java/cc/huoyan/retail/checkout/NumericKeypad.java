package cc.huoyan.retail.checkout;

import android.view.View;
import cc.huoyan.retail.R;
import java.util.function.Consumer;

/** 自定义数字小键盘绑定；不打开系统输入法，不持有页面。 */
public final class NumericKeypad {
    /** 工具类不可实例化。 */
    private NumericKeypad() { }
    /** 将 0–9、小数点及退格回调交给当前弹窗。 */
    public static void bind(View root, Consumer<String> key) {
        int[] ids = {R.id.num0,R.id.num1,R.id.num2,R.id.num3,R.id.num4,R.id.num5,R.id.num6,R.id.num7,R.id.num8,R.id.num9};
        for (int i = 0; i < ids.length; i++) { final String value = String.valueOf(i); root.findViewById(ids[i]).setOnClickListener(v -> key.accept(value)); }
        root.findViewById(R.id.dot).setOnClickListener(v -> key.accept("."));
        root.findViewById(R.id.backspace).setOnClickListener(v -> key.accept("backspace"));
    }
}
