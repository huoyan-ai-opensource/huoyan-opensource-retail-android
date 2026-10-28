package cc.huoyan.retail.sdk;

import android.os.SystemClock;
import android.util.Log;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 等待 SDK 启动清理与模型准备收敛；不触发激活、绑定或重复写入。 */
final class SdkDeviceReadiness {
    private SdkDeviceReadiness() { }

    static StatusSnapshot await(HuoyanSdkClient client) {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        StatusSnapshot status;
        do {
            status = client.getStatus();
            if (status == null || status.getCode() != 0) break;
            String state = status.getSdkState();
            if (!"CLEANUP_PENDING".equals(state) && !"INITIALIZING".equals(state)
                    && !"MODEL_PREPARING".equals(state)) break;
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < deadline);
        Log.i("SdkDeviceReadiness", "[Biz] sdk_test_state=" + (status == null ? "no_response" : status.getSdkState())
                + " recent_error=" + (status == null ? "" : status.getRecentError()));
        return status;
    }
}
