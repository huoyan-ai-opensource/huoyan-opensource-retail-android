package cc.huoyan.retail.checkout;

import static org.junit.Assert.assertEquals;

import cc.huoyan.retail.R;
import cc.huoyan.sdk.client.HuoyanSdkClient;

import org.junit.Test;


import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 收银页 SDK 状态文案映射。 */
public class MainActivityTest {
    // ---- SDK 通道状态 → 提示文案映射 ----

    private static StatusSnapshot statusSnapshot(int code) {
        return new StatusSnapshot(code, null, null, null, null, null, null, null);
    }

    private static CertInfo certInfo(int code) {
        return new CertInfo(code, null, null, null, null,
                0L, 0L, 0L, false, 0L, false, 0L);
    }

    @Test
    public void sdkState_controlsPrompt_withoutParsingReasonOrInitCode() {
        String[] states = {"NOT_INIT", "INITIALIZING", "REGISTERED", "NOT_ACTIVATED", "READY",
                "MODEL_PREPARING", "MODEL_NOT_READY", "KEY_ROTATION_PENDING", "KEY_ROTATION_REJECTED",
                "KEY_ROTATION_RATE_LIMITED", "KEY_ROTATION_RESTORING", "CERTIFICATE_EXPIRED",
                "CERTIFICATE_INVALID", "BACKEND_UNAVAILABLE", "STARTUP_FAILED", "DISABLED", "STOPPED", "CLEANUP_PENDING"};
        int[] prompts = {R.string.status_sdk_uninitialized, R.string.status_sdk_initializing,
                R.string.status_sdk_not_activated, R.string.status_sdk_not_activated, R.string.status_sdk_model_not_ready,
                R.string.status_sdk_model_preparing, R.string.status_sdk_model_not_ready,
                R.string.status_sdk_key_rotation_pending, R.string.status_sdk_key_rotation_rejected,
                R.string.status_sdk_key_rotation_rate_limited, R.string.status_sdk_key_rotation_restoring,
                R.string.status_sdk_certificate_expired, R.string.status_sdk_certificate_invalid,
                R.string.status_sdk_backend_unavailable, R.string.status_sdk_startup_failed,
                R.string.status_sdk_disabled, R.string.status_sdk_stopped, R.string.status_sdk_cleanup_pending};
        for (int i = 0; i < states.length; i++) {
            StatusSnapshot status = new StatusSnapshot(0, "诊断文本不参与状态推断",
                    null, null, null, null, null, null, states[i], null, null, null, null, null);
            assertEquals(states[i], prompts[i], MainActivity.resolveSdkChannelIssueTextRes(0, status));
            assertEquals(states[i], prompts[i], MainActivity.resolveSdkChannelIssueTextRes(-110, status));
        }
    }

    @Test
    public void sdkState_missingOrFailedQuery_doesNotReportReady() {
        assertEquals(R.string.status_sdk_not_started, MainActivity.resolveSdkChannelIssueTextRes(
                HuoyanSdkClient.LOCAL_DISCONNECTED, null));
        assertEquals(R.string.status_sdk_status_unavailable,
                MainActivity.resolveSdkChannelIssueTextRes(0, null));
        assertEquals(R.string.status_sdk_status_unavailable,
                MainActivity.resolveSdkChannelIssueTextRes(-110, statusSnapshot(0)));
        assertEquals(R.string.status_sdk_startup_failed,
                MainActivity.resolveSdkChannelIssueTextRes(-111, null));
        assertEquals(R.string.status_sdk_status_unavailable,
                MainActivity.resolveSdkChannelIssueTextRes(0, statusSnapshot(-1)));
    }
}
