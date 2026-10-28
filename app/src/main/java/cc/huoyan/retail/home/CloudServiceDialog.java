package cc.huoyan.retail.home;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import cc.huoyan.retail.R;
import cc.huoyan.retail.RetailConstants;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.settings.ScanWechatQRCodeDialog;

/** 云端服务弹窗：未登录展示咨询与扫码入口，登录后展示店铺信息和云端操作。 */
public final class CloudServiceDialog extends DialogFragment implements CloudServicePresenter.View {
    /** 当前弹窗布局，销毁时释放。 */
    private View content;
    /** 首页持有的协调器，弹窗重开不会重复提交。 */
    private CloudServicePresenter presenter;
    /** 创建弹窗并绑定已有扫码登录、真实开关和同步入口。 */
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cloud_service, null);
        presenter = new ViewModelProvider(requireActivity()).get(CloudServiceModel.class).presenter;
        content.findViewById(R.id.close).setOnClickListener(v -> dismiss());
        content.findViewById(R.id.cloudLogin).setOnClickListener(v -> login());
        content.findViewById(R.id.cloudEnable).setOnClickListener(v -> DialogUtil.showAlertDialog(getChildFragmentManager(),
                getString(R.string.cloud_title), getString(R.string.panel_cloud_enable_confirm),
                getString(R.string.button_confirm), getString(R.string.button_cancel),
                new AlertConfirmDialogListener() {
                    /** 用户确认后才提交开启操作。 */
                    @Override public void onConfirm() { presenter.run(CloudServicePresenter.Action.ENABLE, null); }
                    /** 取消时保留当前云状态。 */
                    @Override public void onCancel() { }
                }));
        content.findViewById(R.id.cloudRefresh).setOnClickListener(v -> presenter.refresh());
        content.findViewById(R.id.cloudSyncGoods).setOnClickListener(v -> {
            if (checkSyncBinding()) presenter.run(CloudServicePresenter.Action.GOODS, null);
        });
        content.findViewById(R.id.cloudSyncImages).setOnClickListener(v -> {
            if (!checkSyncBinding()) return;
            DialogUtil.showAlertDialog(
                getChildFragmentManager(), getString(R.string.cloud_sync_images),
                getString(R.string.cloud_sync_images_confirm), getString(R.string.button_confirm),
                getString(R.string.button_cancel), new AlertConfirmDialogListener() {
                    /** 确认后提交识别库同步。 */
                    @Override public void onConfirm() { presenter.run(CloudServicePresenter.Action.IMAGES, null); }
                    /** 取消时不提交同步。 */
                    @Override public void onCancel() { }
                });
        });
        presenter.attach(this);
        return new AlertDialog.Builder(requireContext()).setView(content).create();
    }
    /** 未绑定时直接说明同步前提，不显示会被拒绝的同步确认或等待框。 */
    private boolean checkSyncBinding() {
        if (SPManager.getInstance().hasBoundShop()) return true;
        ToastEx.showFailureToast(requireContext(), getString(R.string.cloud_sync_bind_shop_first));
        return false;
    }

    /** 使用现有门店扫码通道，成功回执由协调器持久化。 */
    private void login() {
        if (presenter.busy || getChildFragmentManager().findFragmentByTag("cloudLogin") != null) return;
        ScanWechatQRCodeDialog scan = ScanWechatQRCodeDialog.create(RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_LOGIN);
        CloudServicePresenter session = presenter;
        scan.setListener(new ScanWechatQRCodeDialog.ScanWechatQRCodeDialogListener() {
            /** 扫码结果仅提交一次，不在相机线程操作店铺控件。 */
            @Override public void onScan(String result) { session.run(CloudServicePresenter.Action.BIND, result); }
            /** 相机清理由扫码弹窗负责。 */
            @Override public void onDismiss() { }
        });
        scan.show(getChildFragmentManager(), "cloudLogin");
    }
    /** 按绑定与真实状态切换内容；未知时提供重试，不显示已关闭。 */
    @Override public void render() {
        if (content == null) return;
        SPManager store = SPManager.getInstance();
        boolean bound = store.hasBoundShop();
        content.findViewById(R.id.cloudMarketing).setVisibility(bound ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.cloudAccount).setVisibility(bound ? View.VISIBLE : View.GONE);
        ((TextView) content.findViewById(R.id.cloudShopInfo)).setText(localShopValue(
                SPManager.SP_SHOP_NAME, R.string.default_shop_name));
        ((TextView) content.findViewById(R.id.cloudShopAddress)).setText(getString(R.string.cloud_shop_address,
                localShopValue(SPManager.SP_SHOP_ADDRESS, R.string.cloud_not_filled)));
        ((TextView) content.findViewById(R.id.cloudShopPhone)).setText(getString(R.string.cloud_shop_phone,
                localShopValue(SPManager.SP_SHOP_PHONE, R.string.cloud_not_filled)));
        ((TextView) content.findViewById(R.id.cloudState)).setText(presenter.enabled == null
                ? R.string.cloud_state_retry : presenter.enabled ? R.string.cloud_enabled : R.string.cloud_disabled);
        content.findViewById(R.id.cloudEnable).setVisibility(Boolean.FALSE.equals(presenter.enabled) ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.cloudRefresh).setVisibility(presenter.enabled == null ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.cloudSyncActions).setVisibility(Boolean.TRUE.equals(presenter.enabled) ? View.VISIBLE : View.GONE);
        TextView hint = content.findViewById(R.id.cloudSyncHint);
        hint.setText(Boolean.TRUE.equals(presenter.enabled) ? R.string.cloud_sync_hint : R.string.cloud_enable_hint);
        hint.setVisibility(presenter.enabled == null ? View.GONE : View.VISIBLE);
        for (int id : new int[]{R.id.cloudLogin, R.id.cloudEnable, R.id.cloudRefresh, R.id.cloudSyncGoods, R.id.cloudSyncImages}) {
            content.findViewById(id).setEnabled(!presenter.busy);
            content.findViewById(id).setAlpha(presenter.busy ? 0.5f : 1f);
        }
        content.post(this::renderLoading);
        updateWindowSize();
    }
    /** 使用项目统一 LoadingDialog 展示同步中的忙碌状态，不在业务弹窗底部回显。 */
    private void renderLoading() {
        if (content == null || !isResumed() || getChildFragmentManager().isStateSaved()) return;
        LoadingDialog loading = (LoadingDialog) getChildFragmentManager().findFragmentByTag("cloudLoading");
        boolean processing = presenter.busy && presenter.action != null
                && presenter.action != CloudServicePresenter.Action.BIND;
        if (processing && loading == null) {
            loading = new LoadingDialog(getString(R.string.cloud_working));
            loading.setCancelable(false);
            loading.showNow(getChildFragmentManager(), "cloudLoading");
        } else if (!processing && loading != null) loading.dismissAllowingStateLoss();
    }
    /** 恢复弹窗时同步加载窗口状态。 */
    @Override public void onResume() {
        super.onResume();
        if (content != null) content.post(this::renderLoading);
    }
    /** 登录及开启服务的回执只展示一次，不占用弹窗底部空间。 */
    @Override public void notifyResult(CloudServicePresenter.Result receipt) {
        if (Boolean.TRUE.equals(receipt.success)) ToastEx.showSuccessToast(requireContext(), receipt.message);
        else ToastEx.createToast(requireContext(), receipt.message);
    }
    /** 仅展示本地店铺资料，空值回退默认文案，不用绑定账号或店铺 ID 代替。 */
    private String localShopValue(String key, int fallback) {
        String value = SPManager.getInstance().getString(key);
        return value == null || value.trim().isEmpty() ? getString(fallback) : value;
    }
    /** 限制弹窗宽高，内容过长时内部滚动，关闭入口保持固定。 */
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        updateWindowSize();
        presenter.refresh();
    }
    /** 主线程按当前内容调整窗口；横屏登录后收窄，登录完成后立即重新计算。 */
    void updateWindowSize() {
        Dialog dialog = getDialog();
        if (content == null || dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        float density = getResources().getDisplayMetrics().density;
        View decor = requireActivity().getWindow().getDecorView();
        boolean compact = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && content.findViewById(R.id.cloudAccount).getVisibility() == View.VISIBLE;
        ((cc.huoyan.retail.common.ui.BoundedDialogLayout) content)
                .setMaxHeight(decor.getHeight() - Math.round(32 * density));
        window.setLayout(Math.min(Math.round((compact ? 672 : 840) * density), decor.getWidth() - Math.round(32 * density)),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }
    /** 释放控件及页面引用，后台写操作仍由首页模型完成。 */
    @Override public void onDestroyView() {
        presenter.detach(); content = null; super.onDestroyView();
    }
}
