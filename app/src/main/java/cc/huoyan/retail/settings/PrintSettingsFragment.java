package cc.huoyan.retail.settings;

import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.device.print.PrinterManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

/** 打印设置：选择标准 USB 打印设备，使用 ESC/POS 连接测试和真实小票模板试打。 */
public class PrintSettingsFragment extends BaseFragment implements PrintSettingsPresenter.View {
    /** 本页等待框标识，由子 FragmentManager 管理。 */
    private static final String PROGRESS_TAG = "printerProgress";
    /** 打印模块开关，程序化刷新不得写入配置。 */
    private Switch printModule;
    /** 开关程序化更新守卫。 */
    private boolean suppressModuleSwitchWrite;
    /** 当前设备选择文案。 */
    private TextView printerModelTv;
    /** 跨视图重建保留的任务协调器。 */
    private PrintSettingsPresenter presenter;

    /** 按宿主选择已有横竖屏布局。 */
    @Override protected int getLayoutResId() {
        return getArguments() != null && getArguments().getBoolean("standalone")
                ? R.layout.page_print : R.layout.fragment_print_settings;
    }

    /** 绑定配置与一次性操作，隐藏原有模块内的结果状态。 */
    @Override protected void initView(View view) {
        presenter = new ViewModelProvider(this).get(PrintSettingsModel.class).presenter;
        printModule = view.findViewById(R.id.printModule);
        printModule.setChecked(ModuleConfig.isPrintEnabled(requireContext()));
        printModule.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressModuleSwitchWrite) ModuleConfig.setPrintEnabled(requireContext(), checked);
        });
        printerModelTv = view.findViewById(R.id.printerModel);
        ((TextView) view.findViewById(R.id.printerInitName)).setText("ESC/POS");
        ((TextView) view.findViewById(R.id.printerTypeName)).setText(R.string.printer_escpos);
        view.findViewById(R.id.printInitNameLayout).setOnClickListener(v -> {
            if (presenter.isBusy()) return;
            List<String> types = Collections.singletonList(getString(R.string.printer_escpos));
            SelectOptionDialog.show(getChildFragmentManager(), "printerType",
                    getString(R.string.settings_system_printer_type), types, types.get(0));
        });
        view.findViewById(R.id.printerModelLayout).setOnClickListener(v -> presenter.refresh(true));
        refreshPaperWidth(view);
        view.findViewById(R.id.printerPaperLayout).setOnClickListener(v -> {
            if (presenter.isBusy()) return;
            SelectOptionDialog.show(getChildFragmentManager(), "printerPaper",
                    getString(R.string.printer_paper_width),
                    Arrays.asList(getString(R.string.printer_paper_58), getString(R.string.printer_paper_80)),
                    Arrays.asList("58", "80"), String.valueOf(SPManager.getInstance().getPrinterPaperWidthMm()));
        });
        SelectOptionDialog.listen(getChildFragmentManager(), this, "printerPaper", (position, value) -> {
            if (presenter.isBusy() || !("58".equals(value) || "80".equals(value))) return;
            SPManager.getInstance().putPrinterPaperWidthMm(Integer.parseInt(value));
            refreshPaperWidth(requireView());
        });
        SelectOptionDialog.listen(getChildFragmentManager(), this, "printerDevice", (position, value) -> {
            if (presenter.isBusy()) return;
            if (!presenter.devices().contains(value)) {
                ToastEx.createToast(requireContext(), getString(R.string.select_option_unavailable));
                return;
            }
            SPManager.getInstance().putPrinterDevice(value);
            presenter.selectionChanged(value);
        });
        view.findViewById(R.id.communicationTest).setOnClickListener(v -> presenter.testConnection());
        view.findViewById(R.id.printTest).setOnClickListener(v -> presenter.print(SPManager.getInstance().getPrinterPaperWidthMm()));
        View status = view.findViewById(R.id.printerStatus);
        if (status != null) status.setVisibility(View.GONE);
    }

    /** 回显纸张配置，重建后从持久化值恢复。 */
    private void refreshPaperWidth(View view) {
        ((TextView) view.findViewById(R.id.printerPaperWidth)).setText(
                SPManager.getInstance().getPrinterPaperWidthMm() == 80
                        ? R.string.printer_paper_80 : R.string.printer_paper_58);
    }

    /** 可见时刷新开关和设备；不会自动请求授权或打印。 */
    @Override public void onResume() {
        super.onResume();
        suppressModuleSwitchWrite = true;
        printModule.setChecked(ModuleConfig.isPrintEnabled(requireContext()));
        suppressModuleSwitchWrite = false;
        refreshPaperWidth(requireView());
        presenter.attach(this);
        if (presenter.isBusy()) showProgress();
        else dismissProgress();
        presenter.refresh(false);
    }

    /** 枚举由 onResume 和选择按钮触发。 */
    @Override protected void loadData() { }

    /** 展示可用选择；未配置或设备不可用时提示选择设备。 */
    @Override public void showSelection(String device) {
        printerModelTv.setTextColor(android.graphics.Color.parseColor(device == null ? "#C93636" : "#293A51"));
        printerModelTv.setText(device == null ? getString(R.string.settings_system_printer_model_pick)
                : PrinterManager.displayName(device));
    }

    /** 使用项目统一选项框展示本次设备枚举。 */
    @Override public void showDevices(List<String> devices, String selected) {
        if (devices.isEmpty()) { noPrinter(); return; }
        List<String> names = new ArrayList<>(devices.size());
        for (String device : devices) names.add(PrinterManager.displayName(device));
        SelectOptionDialog.show(getChildFragmentManager(), "printerDevice",
                getString(R.string.settings_system_printer_model), names, devices, selected);
    }

    /** 枚举失败使用一次性提示，不在模块内残留错误状态。 */
    @Override public void showError(String error) { ToastEx.createToast(requireContext(), error); }

    /** 未选中设备时提示先完成选择。 */
    @Override public void noPrinter() {
        ToastEx.createToast(requireContext(), getString(R.string.printer_select_device));
    }

    /** 连接与授权期间显示本页等待框。 */
    @Override public void connectionStarted() { showProgress(); }

    /** 关闭等待并按真实连接回执提示结果。 */
    @Override public void connectionFinished(String error) {
        dismissProgress();
        if (error == null) ToastEx.showSuccessToast(requireContext(), getString(R.string.toast_printer_connected));
        else ToastEx.createToast(requireContext(), error);
    }

    /** 试打期间显示本页等待框。 */
    @Override public void printStarted() { showProgress(); }

    /** 关闭等待；发送成功不宣称已经完成机械出纸。 */
    @Override public void printFinished(String error) {
        dismissProgress();
        if (error == null) ToastEx.showSuccessToast(requireContext(), getString(R.string.toast_test_print_done));
        else ToastEx.createToast(requireContext(), getString(R.string.toast_test_print_failed, error));
    }

    /** 恢复已有等待框或创建一个；提交事务后立即可按 tag 查找，避免重复。 */
    private void showProgress() {
        setControlsEnabled(false);
        if (getChildFragmentManager().isStateSaved()) return;
        if (getChildFragmentManager().findFragmentByTag(PROGRESS_TAG) != null) return;
        LoadingDialog dialog = new LoadingDialog(getString(R.string.printer_working));
        dialog.setCancelable(false);
        dialog.showNow(getChildFragmentManager(), PROGRESS_TAG);
    }

    /** 只移除本页拥有的等待框；异步完成及视图重建均可安全调用。 */
    private void dismissProgress() {
        setControlsEnabled(true);
        DialogFragment dialog = (DialogFragment) getChildFragmentManager().findFragmentByTag(PROGRESS_TAG);
        if (dialog != null) dialog.dismissAllowingStateLoss();
    }

    /** 在途操作期间禁用会改变目标设备或触发重复打印的入口。 */
    private void setControlsEnabled(boolean enabled) {
        View view = getView();
        if (view == null) return;
        for (int id : new int[]{R.id.printInitNameLayout, R.id.printerModelLayout,
                R.id.communicationTest, R.id.printTest, R.id.printModule, R.id.printerPaperLayout}) {
            view.findViewById(id).setEnabled(enabled);
        }
    }

    /** 视图退出仅解除展示引用，ViewModel 中的 USB 操作继续完成。 */
    @Override public void onDestroyView() {
        presenter.detach();
        dismissProgress();
        printerModelTv = null;
        printModule = null;
        super.onDestroyView();
    }
}
