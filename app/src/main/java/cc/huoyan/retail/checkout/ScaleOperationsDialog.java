package cc.huoyan.retail.checkout;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.device.scale.AclasWeightSnapshot;
import java.math.BigDecimal;

/** 顶部称重卡片的去皮、置零弹窗，实时读数与输入值分离。 */
public final class ScaleOperationsDialog extends CheckoutDialog {
    /** 在途状态不持有 Activity；旋转后继续显示 LoadingDialog。 */
    public static final class Operation extends ViewModel {
        /** null 为正在等待，非空为已结束的设备回执。 */
        final MutableLiveData<Result> result = new MutableLiveData<>();
        /** 主线程控制提交互斥。 */
        boolean busy;
        /** 主线程当前皮重输入，视图重建时保留。 */
        String input;
        /** 是否已按下第一个编辑键。 */
        boolean editing;
    }
    /** 设备回执，不含控件引用。 */
    static final class Result {
        /** 设备是否确认成功。 */
        final boolean success;
        /** 一次性结果提示。 */
        final String message;
        /** 固定设备回执。 */
        Result(boolean success, String message) { this.success = success; this.message = message; }
    }
    /** 当前弹窗的操作模型。 */
    private Operation operation;
    /** 当前输入控件，销毁视图时清理。 */
    private TextView input;
    /** 当前读数控件，销毁视图时清理。 */
    private TextView net, tare, gross;
    /** 创建键盘及回执观察，不另开串口。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.dialog_scale_operations, container, false);
        operation = new ViewModelProvider(this).get(Operation.class);
        AclasWeightSnapshot current = ((MainActivity) requireActivity()).scaleSnapshot();
        if (operation.input == null) operation.input = state != null ? state.getString("input", "0")
                : current != null && current.tareKg() != null ? current.tareKg().movePointRight(3).toBigIntegerExact().toString() : "0";
        input = root.findViewById(R.id.tareInput); net = root.findViewById(R.id.dialogNetWeight);
        tare = root.findViewById(R.id.dialogTareWeight); gross = root.findViewById(R.id.dialogGrossWeight);
        input.setText(operation.input); render(current);
        NumericKeypad.bind(root, this::key);
        root.findViewById(R.id.clearInput).setOnClickListener(v -> { if (operation.busy) return; operation.input = "0"; operation.editing = false; input.setText("0"); });
        root.findViewById(R.id.closeDialog).setOnClickListener(v -> { if (!operation.busy) dismiss(); });
        root.findViewById(R.id.zeroScale).setOnClickListener(v -> submit(null));
        root.findViewById(R.id.confirmTare).setOnClickListener(v -> {
            try {
                BigDecimal tare = new BigDecimal(operation.input);
                if (tare.signum() < 0) throw new ArithmeticException();
                tare.intValueExact();
                submit(tare.movePointLeft(3));
            } catch (NumberFormatException | ArithmeticException error) { ToastEx.showFailureToast(requireContext(), getString(R.string.checkout_tare_invalid)); }
        });
        return root;
    }
    /** 在视图有效时观察结果，成功后关闭，失败时保留输入。 */
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        operation.result.observe(getViewLifecycleOwner(), result -> {
            showBusy(operation.busy, R.string.checkout_scale_working);
            if (result == null) return;
            operation.result.setValue(null);
            if (result.success) { ToastEx.showSuccessToast(requireContext(), result.message); dismissAllowingStateLoss(); }
            else ToastEx.showFailureToast(requireContext(), result.message);
        });
        showBusy(operation.busy, R.string.checkout_scale_working);
    }
    /** 输入按整数克限制，第一键替换预填皮重。 */
    private void key(String key) {
        if (operation.busy) return;
        if (!operation.editing) { operation.input = ""; operation.editing = true; }
        if (key.equals("backspace")) operation.input = WeightInputDialog.backspace(operation.input);
        else if (key.equals(".")) return;
        else operation.input = WeightInputDialog.appendDigit(operation.input, key);
        input.setText(operation.input);
    }
    /** 发出操作后由设备管理器超时或真实回执结束，回调仅引用模型。 */
    private void submit(BigDecimal tare) {
        if (operation.busy) return;
        operation.busy = true; showBusy(true, R.string.checkout_scale_working);
        Operation model = operation;
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        ((MainActivity) requireActivity()).commandScale(tare, (success, message) -> ui.post(() -> {
            model.busy = false; model.result.setValue(new Result(success, message));
        }));
    }
    /** 主线程实时刷新读数，绝不覆盖用户正在输入的皮重。 */
    public void render(AclasWeightSnapshot snapshot) {
        if (net == null) return;
        net.setText(snapshot == null ? "—" : snapshot.netKg().toPlainString());
        tare.setText(getString(R.string.checkout_dialog_tare,
                snapshot == null || snapshot.tareKg() == null ? "—" : snapshot.tareKg().toPlainString()));
        gross.setText(getString(R.string.checkout_dialog_gross,
                snapshot == null || snapshot.grossKg() == null ? "—" : snapshot.grossKg().toPlainString()));
    }
    /** 保存输入以支持进程重建；在途设备操作不重发。 */
    @Override public void onSaveInstanceState(@NonNull Bundle state) { super.onSaveInstanceState(state); state.putString("input", operation.input); }
    /** 释放当前视图引用。 */
    @Override public void onDestroyView() { input = null; net = null; tare = null; gross = null; super.onDestroyView(); }
}
