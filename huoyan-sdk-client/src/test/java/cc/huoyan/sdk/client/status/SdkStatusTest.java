package cc.huoyan.sdk.client.status;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import org.junit.Test;
import static org.junit.Assert.*;
/** READY 与激活/模型可用是独立维度。 */
public class SdkStatusTest {
    private StatusSnapshot snapshot(int code, String state, Boolean ready) {
        return new StatusSnapshot(code,null,null,null,null,null,null,null,state,ready,null,null,null,null);
    }
    @Test public void shellReadyDoesNotConfirmModelOrActivation() {
        for (Boolean ready : new Boolean[]{null,false}) {
            StatusSnapshot status = snapshot(0,"READY",ready);
            assertTrue(SdkStatus.shellStarted(status)); assertFalse(SdkStatus.modelReady(status));
            assertEquals(SdkIssue.MODEL_NOT_READY,SdkStatus.issue(0,status));
        }
    }
    @Test public void successfulModelFlagDoesNotRequireReadyState() {
        for (String state : new String[]{null,"MODEL_PREPARING","READY"}) {
            StatusSnapshot status = snapshot(0,state,true);
            assertTrue(SdkStatus.modelReady(status)); assertEquals(SdkIssue.NONE,SdkStatus.issue(0,status));
            assertEquals(SdkIssue.NOT_CONNECTED,SdkStatus.issue(HuoyanSdkClient.LOCAL_DISCONNECTED,status));
        }
    }
    @Test public void failedReceiptAndMissingDataCannotConfirmReadiness() {
        assertFalse(SdkStatus.modelReady(snapshot(-1,"READY",true)));
        assertEquals(SdkIssue.STARTUP_FAILED,SdkStatus.issue(-111,null));
        assertEquals(SdkIssue.STATUS_UNAVAILABLE,SdkStatus.issue(0,snapshot(0,null,null)));
    }

    /** OFFLINE 是已启动的本地可用态，联网校准恢复不改变有效本地能力。 */
    @Test public void offlineLocalReadinessSurvivesOnlineRecovery() {
        StatusSnapshot offline = offline(0, true, true, false);
        assertTrue(SdkStatus.shellStarted(offline));
        assertTrue(SdkStatus.modelReady(offline));
        assertEquals(SdkIssue.NONE, SdkStatus.issue(0, offline));
        assertEquals(SdkIssue.NOT_CONNECTED, SdkStatus.issue(HuoyanSdkClient.LOCAL_DISCONNECTED, offline));
        assertTrue(SdkStatus.modelReady(snapshot(0, "READY", true)));
    }

    /** 离线状态名不能放宽授权、模型与密钥要求，失败回执也不沿用就绪值。 */
    @Test public void offlineStillRequiresLocalCertificateModelAndKey() {
        for (Boolean ready : new Boolean[]{null, false}) {
            StatusSnapshot status = offline(0, ready, true, false);
            assertTrue(SdkStatus.shellStarted(status));
            assertFalse(SdkStatus.modelReady(status));
            assertEquals(SdkIssue.MODEL_NOT_READY, SdkStatus.issue(0, status));
        }
        for (Boolean valid : new Boolean[]{null, false}) {
            StatusSnapshot status = offline(0, true, valid, false);
            assertFalse(SdkStatus.modelReady(status));
            assertEquals(valid == null ? SdkIssue.STATUS_UNAVAILABLE : SdkIssue.CERTIFICATE_INVALID,
                    SdkStatus.issue(0, status));
        }
        assertFalse(SdkStatus.modelReady(offline(0, true, true, true)));
        assertEquals(SdkIssue.STATUS_UNAVAILABLE, SdkStatus.issue(0, offline(0, true, true, true)));
        assertFalse(SdkStatus.shellStarted(offline(-1, true, true, false)));
        assertFalse(SdkStatus.modelReady(offline(-1, true, true, false)));
    }

    /** 构造本地可用与错误事实，不依赖真实网络或 Android 环境。 */
    private StatusSnapshot offline(int code, Boolean ready, Boolean valid, Boolean lost) {
        return new StatusSnapshot(code, null, null, null, null, null, lost, false,
                "OFFLINE", ready, false, null, null, valid);
    }
}
