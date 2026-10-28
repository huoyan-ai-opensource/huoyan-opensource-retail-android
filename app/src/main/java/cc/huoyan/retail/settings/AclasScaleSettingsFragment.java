package cc.huoyan.retail.settings;

import static cc.huoyan.retail.data.SPManager.SP_ACLAS_SCALE_PORT;

import android.text.TextUtils;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.checkout.RecognizeCountKeyboardDialog;
import cc.huoyan.retail.device.scale.AclasWeightSnapshot;
import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.device.scale.AclasScaleManager;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.ui.ToastEx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import aclasdriver.AclasScale;

/** Settings for the Aclas scale used by the Walmart weighted-sale flow. */
public class AclasScaleSettingsFragment extends BaseFragment implements ScaleSettingsPresenter.View {
    /** 串口配置及测试会话协调者，每个视图独立创建。 */
    private ScaleSettingsPresenter presenter;
    private TextView selectedPortView;
    private TextView statusView;
    private TextView testButton;
    private Switch weighingModule;
    private boolean suppressWeighingSwitchWrite;
    /** 用户请求打开列表时，等待当前枚举完成后展示新快照。 */
    private boolean selectingPort;
    /** 页面所属的串口读取遮罩，视图销毁时关闭。 */
    private cc.huoyan.retail.common.ui.LoadingDialog portsLoading;

    @Override
    protected int getLayoutResId() {
        if (getArguments() != null && getArguments().getBoolean("standalone")) return R.layout.page_scale;
        return R.layout.fragment_aclas_scale_settings;
    }

    @Override
    protected void initView(View view) {
        selectedPortView = view.findViewById(R.id.selectedScalePort);
        statusView = view.findViewById(R.id.scaleTestStatus);
        presenter = new ScaleSettingsPresenter(this, new AclasScaleManager(), IOThreadHelper.getInstance()::post,
                new android.os.Handler(android.os.Looper.getMainLooper())::post, AclasScale::getAvailableUartList,
                port -> SPManager.getInstance().putString(SP_ACLAS_SCALE_PORT, port),
                SPManager.getInstance().getString(SP_ACLAS_SCALE_PORT));
        renderSelectedPort();
        SelectOptionDialog.listen(getChildFragmentManager(), this, "scalePort", (position, value) -> presenter.select(value));
        view.findViewById(R.id.selectScalePort).setOnClickListener(v -> { selectingPort = true; presenter.refresh(); });
        testButton = view.findViewById(R.id.testScalePort);
        testButton.setOnClickListener(v -> presenter.toggle());
        view.findViewById(R.id.zeroScale).setOnClickListener(v -> presenter.zero());
        view.findViewById(R.id.tareScale).setOnClickListener(v -> showTareKeyboard());
        weighingModule = view.findViewById(R.id.weighingModule);
        weighingModule.setChecked(ModuleConfig.isWeighingEnabled(requireContext()));
        weighingModule.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressWeighingSwitchWrite) {
                ModuleConfig.setWeighingEnabled(requireContext(), isChecked);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWeighingModuleSwitch();
        presenter.refresh();
    }

    /**
     * 可见时刷新称重模块开关回显：门店场景在系统设置页切换后会改未显式写入项的默认值。
     * <p>程序化置位不写 SP，保持「未显式写入跟随场景默认」语义。
     */
    private void refreshWeighingModuleSwitch() {
        suppressWeighingSwitchWrite = true;
        weighingModule.setChecked(ModuleConfig.isWeighingEnabled(requireContext()));
        suppressWeighingSwitchWrite = false;
    }

    private void showTareKeyboard() {
        if (!presenter.running()) {
            ToastEx.createToast(getContextSafe(), getString(R.string.toast_start_test_first));
            return;
        }
        RecognizeCountKeyboardDialog dialog = new RecognizeCountKeyboardDialog();
        dialog.setTitle(getString(R.string.dialog_tare_title));
        dialog.setListener(presenter::tare);
        dialog.show(getParentFragmentManager(), "scaleTareKeyboard");
    }

    /** 展示当前发现的串口选项。 */
    private void showPortSelect() {
        SelectOptionDialog.show(getChildFragmentManager(), "scalePort", getString(R.string.settings_scale_select_port),
                presenter.available(), presenter.selected());
    }
    /** 串口读取期间复用恢复的遮罩，异步提交避免嵌套 Fragment 事务。 */
    @Override public void readingPorts() {
        if (portsLoading != null) return;
        portsLoading = (cc.huoyan.retail.common.ui.LoadingDialog)
                getChildFragmentManager().findFragmentByTag("portsLoading");
        if (portsLoading == null) {
            portsLoading = new cc.huoyan.retail.common.ui.LoadingDialog(getString(R.string.status_reading_ports));
            portsLoading.setCancelable(false);
            portsLoading.show(getChildFragmentManager(), "portsLoading");
        }
    }
    /** 展示可用数量、已保存串口丢失或枚举错误。 */
    @Override public void portsFound(List<String> ports, String selected, String error) {
        if (portsLoading != null) { portsLoading.dismissAllowingStateLoss(); portsLoading = null; }
        boolean open = selectingPort;
        selectingPort = false;
        if (!TextUtils.isEmpty(error)) { ToastEx.showFailureToast(requireContext(), getString(R.string.status_read_ports_failed, error)); return; }
        if (open && isResumed() && !getChildFragmentManager().isStateSaved()) showPortSelect();
    }
    /** 保存后同步当前端口并提示。 */
    @Override public void portSaved(String port) {
        renderSelectedPort();
        ToastEx.showSuccessToast(requireContext(), getString(R.string.toast_scale_config_saved));
    }
    /** 选择结果已过期，要求用户重新选择。 */
    @Override public void unavailableSelection() { ToastEx.createToast(requireContext(), getString(R.string.select_option_unavailable)); }
    /** 未选端口或端口不可用时提示。 */
    @Override public void cannotStart(boolean missing) { ToastEx.createToast(requireContext(), getString(missing ? R.string.toast_select_port_first : R.string.toast_selected_port_unavailable)); }
    /** 置零去皮依赖活动测试连接。 */
    @Override public void startFirst() { ToastEx.createToast(requireContext(), getString(R.string.toast_start_test_first)); }
    /** 展示连接中并切换为关闭按钮。 */
    @Override public void connecting(String port) { testButton.setText(R.string.button_close_test); statusView.setText(getString(R.string.status_connecting, port)); }
    /** 连接成功，等待实时读数。 */
    @Override public void connected(String port) { statusView.setText(getString(R.string.status_connected_waiting, port)); }
    /** 根据布局展示三位小数的读数及稳定标记。 */
    @Override public void weight(String port, AclasWeightSnapshot snapshot) {
        TextView weight = requireView().findViewById(R.id.scaleWeight);
        TextView stable = requireView().findViewById(R.id.scaleStable);
        if (weight == null) statusView.setText(getString(R.string.status_weight_read, port, formatWeight(snapshot.getWeightKg()), getString(snapshot.isStable() ? R.string.text_stable : R.string.text_unstable)));
        else {
            statusView.setText(R.string.land_scale_connected);
            weight.setText(formatWeight(snapshot.getWeightKg()));
            stable.setText(snapshot.isStable() ? R.string.land_scale_stable : R.string.land_scale_unstable);
        }
    }
    /** 测试结束后恢复启动按钮。 */
    @Override public void stopped(String error) {
        testButton.setText(R.string.settings_scale_test_btn);
        statusView.setText(error == null ? getString(R.string.status_test_stopped) : getString(R.string.status_test_failed, error));
    }
    /** 显示置零或去皮命令回执。 */
    @Override public void commandResult(boolean tare, boolean success, String message) {
        if (success) ToastEx.showSuccessToast(requireContext(), getString(tare ? R.string.toast_tare_sent : R.string.toast_zero_sent));
        else ToastEx.createToast(requireContext(), message);
    }

    private void renderSelectedPort() {
        selectedPortView.setTextColor(android.graphics.Color.parseColor(
                TextUtils.isEmpty(presenter.selected()) ? "#C93636" : "#293A51"));
        selectedPortView.setText(TextUtils.isEmpty(presenter.selected()) ? getString(R.string.status_no_port_selected) : presenter.selected());
    }

    private String formatWeight(double weightKg) {
        return BigDecimal.valueOf(weightKg).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public void onPause() {
        if (presenter.running()) {
            presenter.stop();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (portsLoading != null) { portsLoading.dismissAllowingStateLoss(); portsLoading = null; }
        presenter.detach();
        super.onDestroyView();
    }

    @Override
    protected void loadData() {
    }
}
