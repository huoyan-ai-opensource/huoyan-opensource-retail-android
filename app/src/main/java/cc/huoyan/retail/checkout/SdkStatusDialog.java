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
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 读取 SDK 原始状态的自检弹窗，不初始化或修改 SDK。 */
public final class SdkStatusDialog extends CheckoutDialog {
    /** 后台查询模型，不持有页面；查询结果在主线程发布。 */
    public static final class StatusModel extends ViewModel {
        /** 本次状态与连接回执。 */
        final MutableLiveData<StatusSnapshot> snapshot = new MutableLiveData<>();
        /** 是否正在查询。 */
        boolean busy;
        /** 查询时实际连接状态。 */
        boolean connected;
        /** 查询异常，仅用于真实错误展示。 */
        String failure;
        /** 最近一次查询完成的本机时间，视图重建不改变。 */
        long checkedAt;
        /** 查询代次，销毁后丢弃结果。 */
        int generation;
        /** 后台刷新一次状态，正在查询时忽略重复点击。 */
        void refresh() {
            if (busy) return;
            busy = true; int request = ++generation;
            android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
            IOThreadHelper.getInstance().post(() -> {
                StatusSnapshot result = null; String error = null;
                HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
                boolean live = client.isConnected();
                try { if (live) result = client.getStatus(); } catch (Exception problem) { error = problem.getMessage(); }
                StatusSnapshot value = result; String message = error;
                ui.post(() -> { if (generation == request) { busy = false; connected = live; failure = message; checkedAt = System.currentTimeMillis(); snapshot.setValue(value); } });
            });
        }
        /** 视图模型销毁后使后台结果失效。 */
        @Override protected void onCleared() { generation++; }
    }
    /** 当前查询模型。 */
    private StatusModel model;
    /** 当前详情控件。 */
    private TextView details;
    /** 创建滚动状态详情和固定操作入口。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.dialog_sdk_status, container, false);
        details = view.findViewById(R.id.sdkDetails);
        model = new ViewModelProvider(this).get(StatusModel.class);
        view.findViewById(R.id.closeDialog).setOnClickListener(v -> { if (!model.busy) dismiss(); });
        view.findViewById(R.id.closeSdkIcon).setOnClickListener(v -> { if (!model.busy) dismiss(); });
        view.findViewById(R.id.refreshSdk).setOnClickListener(v -> refresh());
        return view;
    }
    /** 打开即读取最新状态，重建时沿用仍在途查询。 */
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        model.snapshot.observe(getViewLifecycleOwner(), status -> {
            showBusy(model.busy, R.string.checkout_loading);
            renderStatus(view, status);
        });
        if (model.generation == 0) refresh(); else showBusy(model.busy, R.string.checkout_loading);
    }
    /** 通过 LoadingDialog 提示查询，内容区不追加进度。 */
    private void refresh() { model.refresh(); showBusy(model.busy, R.string.checkout_loading); }
    /** 依据实际回执绘制状态条及分区字段；未知项不推断为正常。 */
    private void renderStatus(View view, StatusSnapshot status) {
        boolean ready = model.connected && cc.huoyan.sdk.client.status.SdkStatus.modelReady(status);
        boolean error = !model.connected || model.failure != null || (status != null && status.getCode() != 0);
        TextView health = view.findViewById(R.id.sdkHealth);
        health.setText(error ? R.string.checkout_sdk_abnormal : ready
                ? R.string.checkout_sdk_healthy : status == null ? R.string.checkout_sdk_unavailable : R.string.checkout_sdk_not_ready);
        health.setTextColor(error ? 0xFFB42318 : ready ? 0xFF27835B : 0xFF9A6700);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(error ? 0xFFFEF3F2 : ready ? 0xFFEAF3ED : 0xFFFFF7E8);
        background.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        health.setBackground(background);
        ((TextView) view.findViewById(R.id.sdkConnection)).setText(getString(R.string.checkout_sdk_local_connection,
                getString(model.connected ? R.string.checkout_connected : R.string.checkout_disconnected)));
        Boolean authorized = status == null ? null : status.getCertificateValid();
        ((TextView) view.findViewById(R.id.sdkAuthorization)).setText(authorized == null ? "—"
                : getString(authorized ? R.string.checkout_sdk_active : R.string.checkout_sdk_inactive));
        Boolean modelReady = status == null ? null : status.getModelReady();
        String initialization = status == null ? "—" : value(status.getSdkState()) + " / "
                + (modelReady == null ? "—" : getString(modelReady ? R.string.checkout_sdk_ready : R.string.checkout_sdk_waiting));
        if (status != null && status.getModelVersion() != null && !status.getModelVersion().isEmpty())
            initialization += "\n" + status.getModelVersion();
        ((TextView) view.findViewById(R.id.sdkInitialization)).setText(initialization);
        ((TextView) view.findViewById(R.id.sdkCheckedAt)).setText(model.checkedAt == 0 ? "—"
                : new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", getResources().getConfiguration().getLocales().get(0))
                        .format(new java.util.Date(model.checkedAt)));
        boolean noError = status != null && status.getCode() == 0 && value(status.getReason()).equals("—")
                && value(status.getRecentError()).equals("—") && model.failure == null;
        details.setText(noError ? getString(R.string.checkout_sdk_no_error) : getString(R.string.checkout_sdk_raw_error,
                status == null ? "—" : String.valueOf(status.getCode()),
                status == null ? getString(R.string.checkout_sdk_unavailable) : value(status.getReason()),
                model.failure != null ? model.failure : status == null ? "—" : value(status.getRecentError())));
    }
    /** 缺少原始字段时保留占位符。 */
    private String value(String text) { return text == null || text.isEmpty() ? "—" : text; }
    /** 释放详情控件引用。 */
    @Override public void onDestroyView() { details = null; super.onDestroyView(); }
}
