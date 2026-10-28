package cc.huoyan.retail.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.CheckoutDialog;
import cc.huoyan.retail.checkout.NumericKeypad;
import cc.huoyan.retail.common.ui.ToastEx;

/** 本地退出密码验证及设置，共用自定义九宫格数字键盘。 */
public final class ExitPasswordDialog extends CheckoutDialog {
    /** 密码计算与视图解耦，旋转时不重复执行或阻塞主线程。 */
    public static final class Operation extends androidx.lifecycle.ViewModel {
        /** null 表示尚无结果；回执由当前视图消费一次。 */
        final androidx.lifecycle.MutableLiveData<Boolean> result = new androidx.lifecycle.MutableLiveData<>();
        /** 主线程提交互斥标志。 */
        boolean busy;
        /** 专用密码计算队列，不持有 Dialog 或 Activity。 */
        private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        /** 投递回执到主线程。 */
        private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        /** 后台验证或保存，不改变派生强度。 */
        void submit(android.content.Context context, String password, boolean setup, boolean enable) {
            if (busy) return;
            busy = true;
            android.content.Context app = context.getApplicationContext();
            worker.execute(() -> {
                boolean success = setup ? ExitPasswordStore.save(app, password, enable) : ExitPasswordStore.verify(app, password);
                ui.post(() -> { busy = false; result.setValue(success); });
            });
        }
        /** 已提交保存继续完成，页面释放后关闭队列。 */
        @Override protected void onCleared() { worker.shutdown(); }
    }
    /** 当前密码任务，生命周期由 Fragment 管理。 */
    private Operation operation;
    /** 当前输入只保留在弹窗内存，不写入实例状态或日志。 */
    private final String[] values = {"", ""};
    /** 当前激活输入框。 */
    private int selected;
    /** 设置模式要求两次确认；验证模式只展示一个输入框。 */
    private boolean setup;
    /** 当前视图的两个输入框。 */
    private TextView first, second;
    /** 打开退出验证，成功通过 FragmentResult 通知宿主。 */
    public static void showVerify(FragmentManager manager) { new ExitPasswordDialog().show(manager, "exitPassword"); }
    /** 打开设置弹窗，首次开启只有密码保存成功后才启用。 */
    public static void showSetup(FragmentManager manager, boolean enable) {
        if (manager.findFragmentByTag("exitPassword") != null) return;
        ExitPasswordDialog dialog = new ExitPasswordDialog(); Bundle args = new Bundle();
        args.putBoolean("setup", true); args.putBoolean("enable", enable); dialog.setArguments(args);
        dialog.show(manager, "exitPassword");
    }
    /** 创建掩码输入框和数字键盘，点击输入框切换编辑目标。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.dialog_exit_password, container, false);
        operation = new androidx.lifecycle.ViewModelProvider(this).get(Operation.class);
        setup = getArguments() != null && getArguments().getBoolean("setup");
        if (!setup) {
            // 验证模式的单输入框不参与键盘等高计算，避免按键被压缩。
            View keyboard = view.findViewById(R.id.passwordKeyboard);
            ViewGroup.LayoutParams params = keyboard.getLayoutParams();
            params.height = getResources().getDimensionPixelSize(R.dimen.ui_password_verify_keyboard_height);
            keyboard.setLayoutParams(params);
        }
        first = view.findViewById(R.id.passwordInput); second = view.findViewById(R.id.passwordRepeat);
        second.setVisibility(setup ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.passwordRepeatLabel).setVisibility(setup ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.passwordTitle)).setText(setup
                ? R.string.checkout_password_setup_title : R.string.checkout_password_verify_title);
        ((TextView) view.findViewById(R.id.passwordPurpose)).setText(setup
                ? R.string.checkout_password_purpose : R.string.checkout_password_verify_hint);
        ((TextView) view.findViewById(R.id.passwordLabel)).setText(setup
                ? R.string.checkout_new_password : R.string.checkout_local_password);
        ((TextView) view.findViewById(R.id.confirmPassword)).setText(setup
                ? R.string.checkout_save_password : R.string.checkout_verify_exit);
        first.setOnClickListener(v -> { selected = 0; render(); }); second.setOnClickListener(v -> { selected = 1; render(); });
        ((TextView) view.findViewById(R.id.dot)).setText(R.string.checkout_clear);
        NumericKeypad.bind(view, this::key);
        view.findViewById(R.id.closeDialog).setOnClickListener(v -> { if (!operation.busy) dismiss(); });
        view.findViewById(R.id.confirmPassword).setOnClickListener(v -> confirm()); render(); return view;
    }
    /** 当前输入框接收数字、清空或退格。 */
    private void key(String key) {
        if (operation.busy) return;
        if (key.equals(".")) values[selected] = "";
        else if (key.equals("backspace")) values[selected] = values[selected].isEmpty() ? "" : values[selected].substring(0, values[selected].length() - 1);
        else if (values[selected].length() < 12) values[selected] += key;
        render();
    }
    /** 用掩码展示密码并强调当前编辑字段。 */
    private void render() {
        TextView[] fields = {first, second};
        for (int i = 0; i < 2; i++) {
            fields[i].setText(values[i].isEmpty() ? getString(i == 0 ? R.string.checkout_password : R.string.checkout_password_repeat)
                    : new String(new char[values[i].length()]).replace('\0', '●'));
            fields[i].setSelected(i == selected);
            fields[i].setTextColor(values[i].isEmpty() ? 0xFF6E7C90 : 0xFF293A51);
        }
    }
    /** 按生命周期消费计算结果；等待遮罩随旋转恢复。 */
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        operation.result.observe(getViewLifecycleOwner(), success -> {
            showBusy(operation.busy, R.string.password_processing);
            if (success == null) return;
            operation.result.setValue(null);
            if (!success) {
                ToastEx.showFailureToast(requireContext(), getString(setup
                        ? R.string.checkout_password_save_failed : R.string.checkout_password_wrong));
                return;
            }
            dismissAllowingStateLoss();
            getParentFragmentManager().setFragmentResult(setup ? "exitPasswordSaved" : "exitVerified", new Bundle());
            if (setup) ToastEx.showSuccessToast(requireContext(), R.string.checkout_password_saved);
        });
        showBusy(operation.busy, R.string.password_processing);
    }

    /** 本地输入校验后提交后台派生，避免确认按钮冻结主线程。 */
    private void confirm() {
        if (operation.busy) return;
        if (setup && (values[0].isEmpty() || !values[0].equals(values[1]))) {
            ToastEx.showFailureToast(requireContext(), getString(R.string.checkout_password_mismatch)); return;
        }
        operation.submit(requireContext(), values[0], setup, setup && requireArguments().getBoolean("enable"));
        values[0] = ""; values[1] = "";
        showBusy(true, R.string.password_processing);
    }
    /** 关闭时清除所有明文及控件引用。 */
    @Override public void onDestroyView() { values[0] = ""; values[1] = ""; first = null; second = null; super.onDestroyView(); }
}
