package cc.huoyan.retail.settings;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.sdk.client.SdkDiscovery;
import cc.huoyan.retail.common.ui.ToastEx;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WS 地址填写、连接测试和局域网选择，查询不改变当前连接。 */
public final class SdkConnectionFragment extends androidx.fragment.app.Fragment implements SdkConnectionPresenter.View, PendingSettings {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText address;
    private TextView status;
    private TextView probe;
    private TextView discover;
    private ListView results;
    /** 当前 View 生命周期内的连接操作协调者。 */
    private SdkConnectionPresenter presenter;
    private View content;

    /** 创建独立 SDK 连接页，测试和探测不会修改正在使用的连接。 */
    @NonNull @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        presenter = new SdkConnectionPresenter(this, worker, ui::post);
        content = inflater.inflate(R.layout.page_sdk, container, false);
        address = content.findViewById(R.id.wsAddress);
        status = content.findViewById(R.id.wsStatus);
        probe = content.findViewById(R.id.wsProbe);
        discover = content.findViewById(R.id.wsDiscover);
        results = content.findViewById(R.id.wsResults);
        address.setText(state == null ? SdkDiscovery.ipFromEndpoint(SPManager.getInstance().getString(
                SPManager.SP_WS_ENDPOINT)) : state.getString("address"));
        content.findViewById(R.id.sdkAidl).setOnClickListener(v -> presenter.save(cc.huoyan.sdk.client.SdkConfig.Channel.AIDL, address.getText().toString()));
        content.findViewById(R.id.sdkWs).setOnClickListener(v -> presenter.selectLan());
        presenter.showCurrent();
        if (state != null && state.getBoolean("lanSelected")) presenter.selectLan();
        probe.setOnClickListener(v -> presenter.probe(address.getText().toString()));
        discover.setOnClickListener(v -> presenter.query(true, address.getText().toString()));
        content.findViewById(R.id.confirm).setOnClickListener(v -> savePendingSettings());
        return content;
    }

    /** 通道变更后重置状态和按钮样式。 */
    @Override public void showChannel(cc.huoyan.sdk.client.SdkConfig.Channel channel) {
        status.setText(R.string.land_connection_untested);
        results.setAdapter(null);
        results.setVisibility(View.GONE);
        content.findViewById(R.id.sdkDiscoveryEmpty).setVisibility(View.VISIBLE);
        setBusy(false);
        for (int id : new int[]{R.id.sdkAidl, R.id.sdkWs}) {
            TextView button = content.findViewById(id);
            boolean active = (id == R.id.sdkAidl) == (channel == cc.huoyan.sdk.client.SdkConfig.Channel.AIDL);
            button.setSelected(active);
            button.setBackgroundResource(R.drawable.sdk_tab);
            button.setTextColor(Color.parseColor(active ? "#3F3EED" : "#6E7C90"));
        }
        boolean lan = channel == cc.huoyan.sdk.client.SdkConfig.Channel.WS;
        content.findViewById(R.id.sdkLanContent).setVisibility(lan ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.sdkLocalContent).setVisibility(lan ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.confirm).setVisibility(lan ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.sdkActions).setVisibility(lan ? View.VISIBLE : View.GONE);
        if (!lan) aidlStatus(cc.huoyan.retail.sdk.SdkChannelManager.getInstance().getClient().isConnected());
        address.setEnabled(lan);
        discover.setEnabled(channel == cc.huoyan.sdk.client.SdkConfig.Channel.WS);
    }

    /** 释放后台探测线程。 */
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }

    /** 地址校验失败统一提示，保留输入以便修正。 */
    @Override public void invalidAddress() { ToastEx.showFailureToast(requireContext(), getString(R.string.ws_invalid)); }
    /** 显示已保存通道。 */
    @Override public void saved(String channel) { ToastEx.showSuccessToast(requireContext(), getString(R.string.sdk_connection_saved, getString("AIDL".equals(channel) ? R.string.land_sdk_aidl : R.string.land_sdk_ws))); }
    /** 展示本机 AIDL 的即时连接状态。 */
    @Override public void aidlStatus(boolean connected) { ((TextView) content.findViewById(R.id.sdkLocalStatus)).setText(connected ? R.string.land_sdk_connected : R.string.land_sdk_save_first); }
    /** 启动测试时清空旧发现列表并禁用重复操作。 */
    @Override public void querying(boolean scan, String ip) {
        setBusy(true); results.setAdapter(null); results.setVisibility(View.GONE);
        content.findViewById(R.id.sdkDiscoveryEmpty).setVisibility(View.VISIBLE);
        status.setText(scan ? getString(R.string.ws_scanning) : getString(R.string.ws_testing, ip));
    }
    /** 将探测结果转换为状态文案及可选择地址。 */
    @Override public void queryResult(boolean scan, String ip, boolean connected, List<String> found) {
        if (!isAdded()) return;
        setBusy(false);
        if (!scan) { status.setText(getString(connected ? R.string.ws_test_success : R.string.ws_test_failed, ip)); return; }
        status.setText(found.isEmpty() ? getString(R.string.ws_not_found) : getString(R.string.ws_found, found.size()));
        results.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.sdkDiscoveryEmpty).setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
        results.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_ws_address, found));
        results.setOnItemClickListener((parent, view, position, id) -> {
            address.setText(found.get(position));
            results.setItemChecked(position, true);
        });
    }

    private void setBusy(boolean busy) {
        probe.setEnabled(!busy);
        discover.setEnabled(!busy && presenter.channel() == cc.huoyan.sdk.client.SdkConfig.Channel.WS);
        probe.setAlpha(busy ? 0.5f : 1f);
        discover.setAlpha(busy ? 0.5f : 1f);
    }

    /** 旋转重建时保留尚未保存的地址输入。 */
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("address", address.getText().toString());
        state.putBoolean("lanSelected", presenter.channel() == cc.huoyan.sdk.client.SdkConfig.Channel.WS);
    }

    /** 保存当前通道与地址，校验失败时不允许设置宿主退出。 */
    @Override public boolean savePendingSettings() {
        return presenter != null && address != null && presenter.save(presenter.channel(), address.getText().toString());
    }

    /** 通道与地址草稿未提交时，同样纳入设置页退出保护。 */
    @Override public boolean hasUnsavedSettings() {
        if (address == null || presenter == null) return false;
        String saved = SdkDiscovery.ipFromEndpoint(SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT));
        return presenter.channel() != cc.huoyan.retail.sdk.SdkChannelManager.getInstance().getChannel()
                || !address.getText().toString().trim().equals(saved == null ? "" : saved);
    }

    /** 离开页面时取消探测，阻止回调继续更新视图。 */
    @Override public void onDestroyView() {
        presenter.detach();
        ui.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
