package cc.huoyan.retail.settings;

import static cc.huoyan.retail.data.SPManager.SP_DEVICES_ID;
import android.text.TextUtils;
import android.util.Log;
import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;

/** 授权页 SDK 访问，各查询独立处理失败并保留调试日志。 */
final class PanelSdkSource implements PanelPresenter.Data {
    /** 与既有授权页一致的日志标签。 */
    private static final String LOG_TAG = "PanelFragment";
    /** 写入失败后读取真实开关，避免 UI 停在乐观值。 */
    @Override public PanelPresenter.CloudResult cloud(Boolean requested) {
        CloudStatus result;
        CloudStatus actual = null;
        try {
            HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
            result = requested == null ? bridge.getCloudStatus() : bridge.setCloudStatus(requested);
            if (requested != null && (result == null || result.getCode() != 0)) {
                actual = bridge.getCloudStatus();
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, e);
            result = new CloudStatus(-1, e.getMessage(), false);
        }
        CloudStatus snapshot = result != null && result.getCode() == 0 ? result : actual;
        return new PanelPresenter.CloudResult(result, snapshot);
    }
    /** 查询当前通道面板状态。 */
    @Override public PanelPresenter.PanelResult panel() {
        StatusSnapshot status = null;
        CertInfo certInfo = null;
        Capability capability = null;
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        if (client.isConnected()) {
            // 三个查询独立处理失败，证书不可用不能阻止 SDK 版本回显。
            try { capability = client.getCapabilities(); }
            catch (RuntimeException error) { Logger.logError(LOG_TAG, error); }
            try { status = client.getStatus(); }
            catch (RuntimeException error) { Logger.logError(LOG_TAG, error); }
            try { certInfo = client.getCertInfo(); }
            catch (RuntimeException error) { Logger.logError(LOG_TAG, error); }
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] panel_query status="
                    + (status != null ? status.getCode() : "na")
                    + " cert=" + (certInfo != null ? certInfo.getCode() : "na"));
        }
        if (status != null && status.getCode() == 0 && !TextUtils.isEmpty(status.getDeviceId())) {
            try {
                SPManager.getInstance().putLong(SP_DEVICES_ID, Long.parseLong(status.getDeviceId()));
            } catch (NumberFormatException e) {
                Logger.logError(LOG_TAG, "设备号解析失败: " + status.getDeviceId());
            }
        }
        return new PanelPresenter.PanelResult(status, certInfo, capability);
    }
}
