package cc.huoyan.retail.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import cc.huoyan.retail.RetailConstants;
import cc.huoyan.retail.common.GsonUtil;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.LicenseModel;
import java.util.ArrayList;

/** 首页、授权页和识别设置共用的激活弹窗宿主；旋转只恢复状态，不重发写请求。 */
public final class ActivationFlowFragment extends Fragment implements ModelActivationPresenter.View {
    /** 当前 Activity 唯一模型会话宿主。 */
    private static final String TAG = "modelActivation";
    /** 激活结束后通知页面刷新真实模型，不传乐观状态。 */
    static final String RESULT = "modelActivationFinished";
    /** 模型列表选择结果标识。 */
    private static final String CHOICE = "activationModelChoice";
    /** ViewModel 持有的会话，不持有旧页面引用。 */
    private ModelActivationPresenter session;
    /** 延后渲染，避开输入弹窗回调内的 Fragment 事务重入。 */
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 单次待渲染任务，页面不可见时移除。 */
    private final Runnable draw = this::renderNow;

    /** 打开激活码输入流程。 */
    public static void open(FragmentActivity activity) { open(activity, false); }
    /** 打开当前设备授权范围内的模型切换。 */
    public static void switchModel(FragmentActivity activity) { open(activity, true); }
    /** 查找唯一会话，重复点击不会生成第二个请求。 */
    private static void open(FragmentActivity activity, boolean switching) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        ActivationFlowFragment flow = (ActivationFlowFragment) manager.findFragmentByTag(TAG);
        if (flow == null) {
            flow = new ActivationFlowFragment();
            manager.beginTransaction().add(flow, TAG).commitNow();
        }
        flow.session.open(switching);
    }
    /** 创建可跨旋转保留的会话并注册恢复后的弹窗结果。 */
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        session = new ViewModelProvider(this).get(ModelActivationViewModel.class).session;
        SelectOptionDialog.listen(getChildFragmentManager(), this, CHOICE, (position, value) -> {
            if (position < 0) session.cancel(); else session.select(value);
        });
        getChildFragmentManager().setFragmentResultListener("activationInputCancelled", this,
                (key, result) -> session.cancel());
    }
    /** 页面可见后恢复遮罩和选择状态；进程重建的空会话关闭残留弹窗。 */
    @Override public void onStart() { super.onStart(); session.attach(this); }
    /** 页面不可见后停止持有视图，回执留给恢复后的宿主消费。 */
    @Override public void onStop() {
        session.detach(); main.removeCallbacks(draw); super.onStop();
    }
    /** 将多次状态变化合并到主线程下一帧。 */
    @Override public void render() { main.removeCallbacks(draw); main.post(draw); }
    /** 根据会话阶段显示唯一业务弹窗。 */
    private void renderNow() {
        if (!isAdded() || !isResumed() || getChildFragmentManager().isStateSaved()) return;
        ModelActivationPresenter.Stage stage = session.stage;
        if (stage != ModelActivationPresenter.Stage.INPUT) close("activateCode");
        if (stage == ModelActivationPresenter.Stage.IDLE) close("scanWechatQRCode");
        if (stage != ModelActivationPresenter.Stage.CHOOSING) close("selectOption");
        boolean loading = stage == ModelActivationPresenter.Stage.QUERYING || stage == ModelActivationPresenter.Stage.ACTIVATING;
        if (!loading) close("modelLoading");
        if (loading) {
            String text = stage == ModelActivationPresenter.Stage.QUERYING ? "正在读取模型列表…"
                    : session.switching ? "正在切换模型…" : "正在激活…";
            LoadingDialog dialog = (LoadingDialog) getChildFragmentManager().findFragmentByTag("modelLoading");
            if (dialog == null) {
                dialog = new LoadingDialog(text); dialog.setCancelable(false);
                dialog.showNow(getChildFragmentManager(), "modelLoading");
            } else dialog.setText(text);
        } else if (stage == ModelActivationPresenter.Stage.INPUT) {
            Fragment scanning = getChildFragmentManager().findFragmentByTag("scanWechatQRCode");
            if (scanning instanceof ScanWechatQRCodeDialog) { bindScanner((ScanWechatQRCodeDialog) scanning); return; }
            ActivateCodeDialog dialog = (ActivateCodeDialog) getChildFragmentManager().findFragmentByTag("activateCode");
            if (dialog == null) { dialog = new ActivateCodeDialog(); bindInput(dialog); dialog.showNow(getChildFragmentManager(), "activateCode"); }
            else bindInput(dialog);
        } else if (stage == ModelActivationPresenter.Stage.CHOOSING) {
            ArrayList<String> labels = new ArrayList<>(), codes = new ArrayList<>();
            String current = null;
            for (LicenseModel model : session.models) {
                labels.add(model.getModelName()); codes.add(model.getModelCode());
                if (session.switching && model.isCurrent()) current = model.getModelCode();
            }
            SelectOptionDialog.show(getChildFragmentManager(), CHOICE, session.switching ? "切换模型" : "选择模型",
                    session.switching ? "请选择要切换的模型" : "请选择本次使用的识别模型",
                    labels, codes, current, true);
        } else if (stage == ModelActivationPresenter.Stage.FINISHED) {
            BaseResult result = session.result;
            if (result.getCode() == 0) ToastEx.showSuccessToast(requireContext(), session.switching ? "模型切换成功" : "激活成功");
            else ToastEx.createToast(requireContext(), result.getReason() == null || result.getReason().trim().isEmpty()
                    ? "操作失败（" + result.getCode() + "）" : result.getReason());
            if (session.submitted) getParentFragmentManager().setFragmentResult(RESULT, new Bundle());
            session.consumed();
        }
        Fragment scanner = getChildFragmentManager().findFragmentByTag("scanWechatQRCode");
        if (scanner instanceof ScanWechatQRCodeDialog) bindScanner((ScanWechatQRCodeDialog) scanner);
    }
    /** 恢复可见状态后确保上一帧未因生命周期尚未 RESUMED 而漏绘。 */
    @Override public void onResume() { super.onResume(); render(); }
    /** 绑定手输与扫码入口。 */
    private void bindInput(ActivateCodeDialog dialog) {
        dialog.setActivateCodeListener(new ActivateCodeDialog.ActivateCodeListener() {
            /** 初次授权进入选模；切换补码时提交已选模型。 */
            @Override public void onActivate(String code) { accept(code, false); }
            /** 使用现有扫码界面，返回值交付同一会话。 */
            @Override public void onScan() {
                ScanWechatQRCodeDialog scanner = ScanWechatQRCodeDialog.create(RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_SERIAL);
                bindScanner(scanner); scanner.show(getChildFragmentManager(), "scanWechatQRCode");
            }
        });
    }
    /** 重新绑定恢复的扫码弹窗。 */
    private void bindScanner(ScanWechatQRCodeDialog scanner) {
        scanner.setListener(new ScanWechatQRCodeDialog.ScanWechatQRCodeDialogListener() {
            /** 仅提取 serial，交给当前选模或补码阶段。 */
            @Override public void onScan(String result) { accept(result, true); }
            /** 取消扫码返回输入；已进入查询则保持当前阶段。 */
            @Override public void onDismiss() { render(); }
        });
    }
    /** 输入错误通过 Toast 提示，不触发模型查询或写操作。 */
    private void accept(String input, boolean scanned) {
        try {
            String code = input;
            if (scanned) {
                ScanSerialEntity payload = GsonUtil.fromJson(input, ScanSerialEntity.class);
                code = payload == null ? null : payload.getSerial();
            }
            if (code == null || code.trim().isEmpty()) throw new IllegalArgumentException();
            session.input(code);
        } catch (RuntimeException error) {
            ToastEx.createToast(requireContext(), scanned ? "二维码缺少有效激活信息" : "激活码读取失败，请重试");
            render();
        }
    }
    /** 同步关闭过期弹窗，下一阶段可使用相同标签。 */
    private void close(String tag) {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).dismiss();
            getChildFragmentManager().executePendingTransactions();
        }
    }
}
