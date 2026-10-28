package cc.huoyan.retail.settings;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.SdkDiscovery;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.common.ui.WindowUtil;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WS 地址填写、连接测试和局域网选择，查询不改变当前连接。 */
public final class WsConnectionDialog extends DialogFragment implements WsConnectionPresenter.View {
    /** 后台查询队列，随当前视图创建和释放。 */
    private ExecutorService worker;
    /** 当前视图的连接查询协调器，重建时重新创建。 */
    private WsConnectionPresenter presenter;
    /** 主线程投递器，不通过闭包保留页面。 */
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** 仅填写 IP 的地址输入框。 */
    private EditText address;
    /** 探测进度与结果文案。 */
    private TextView status;
    /** 单地址连接测试按钮。 */
    private TextView probe;
    /** 局域网发现按钮。 */
    private TextView discover;
    /** 发现地址列表，点击只回填输入。 */
    private ListView results;

    /** 使用自定义布局绘制标题、输入区及按钮。 */
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setStyle(STYLE_NO_TITLE, 0);
    }

    /** 创建仅填写 IP 的连接设置视图，沿用设置页表单和按钮样式。 */
    @NonNull @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View content = inflater.inflate(R.layout.dialog_ws_connection, container, false);
        address = content.findViewById(R.id.wsAddress);
        status = content.findViewById(R.id.wsStatus);
        probe = content.findViewById(R.id.wsProbe);
        discover = content.findViewById(R.id.wsDiscover);
        results = content.findViewById(R.id.wsResults);
        address.setText(state == null ? SdkDiscovery.ipFromEndpoint(SPManager.getInstance().getString(
                SPManager.SP_WS_ENDPOINT)) : state.getString("address"));
        worker = Executors.newSingleThreadExecutor();
        presenter = new WsConnectionPresenter(this, worker, ui::post);
        probe.setOnClickListener(v -> presenter.query(false, address.getText().toString()));
        discover.setOnClickListener(v -> presenter.query(true, address.getText().toString()));
        content.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        content.findViewById(R.id.confirm).setOnClickListener(v -> {
            if (!presenter.saveAddress(address.getText().toString(),
                    SdkChannelManager.getInstance()::setWsEndpoint)) return;
            ToastEx.showSuccessToast(requireContext(), R.string.ws_saved);
            dismiss();
        });
        return content;
    }

    /** 限制弹窗宽度并在输入法弹出时允许内容滚动。 */
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        int margin = getResources().getDimensionPixelSize(R.dimen.dp_12);
        int width = Math.min(getResources().getDimensionPixelSize(R.dimen.dp_340),
                getResources().getDisplayMetrics().widthPixels - margin * 2);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** 在输入框显示地址错误。 */
    @Override public void invalidAddress() { address.setError(getString(R.string.ws_invalid)); }

    /** 展示查询进度并清除旧发现列表。 */
    @Override public void querying(boolean scan, String ip) {
        setBusy(true);
        results.setAdapter(null);
        results.setVisibility(View.GONE);
        status.setText(scan ? getString(R.string.ws_scanning) : getString(R.string.ws_testing, ip));
    }

    /** 只渲染当前查询回执，点击发现项仍仅填入输入框。 */
    @Override public void queryResult(boolean scan, String ip, boolean connected, List<String> found) {
        setBusy(false);
        if (!scan) {
            status.setText(getString(connected ? R.string.ws_test_success : R.string.ws_test_failed, ip));
            return;
        }
        status.setText(found.isEmpty() ? getString(R.string.ws_not_found) : getString(R.string.ws_found, found.size()));
        results.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
        results.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_ws_address, found));
        results.setOnItemClickListener((parent, view, position, id) -> address.setText(found.get(position)));
    }

    /** 查询期间禁用按钮，避免重复操作。 */
    private void setBusy(boolean busy) {
        probe.setEnabled(!busy);
        discover.setEnabled(!busy);
        probe.setAlpha(busy ? 0.5f : 1f);
        discover.setAlpha(busy ? 0.5f : 1f);
    }

    /** 旋转重建时保留尚未保存的地址输入。 */
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("address", address.getText().toString());
    }

    /** 关闭弹窗时取消探测，阻止回调继续更新视图。 */
    @Override public void onDestroyView() {
        presenter.detach();
        worker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
