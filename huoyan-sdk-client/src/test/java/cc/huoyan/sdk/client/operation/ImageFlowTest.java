package cc.huoyan.sdk.client.operation;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.ImageFlowProgress;
import org.junit.Test;
import static org.junit.Assert.*;
/** 终态组合与执行确定性不能由 phase 单独决定。 */
public class ImageFlowTest {
    private ImageFlowProgress result(int code,String phase,long failures,int failCode) {
        return new ImageFlowProgress(code,null,false,phase,1,1,1,failures,null,failCode,null);
    }
    @Test public void combinedOutcomePreservesPartialFailureAndUncertainty() {
        assertEquals(ImageFlowProgress.Outcome.SUCCESS,result(0,"SUCCESS",0,0).outcome());
        assertEquals(ImageFlowProgress.Outcome.PARTIAL_SUCCESS,result(0,"SUCCESS",1,0).outcome());
        assertEquals(ImageFlowProgress.Outcome.FAILURE,result(0,"SUCCESS",0,-1).outcome());
        assertEquals(ImageFlowProgress.Outcome.FAILURE,result(0,"FAILED",0,0).outcome());
        assertEquals(ImageFlowProgress.Outcome.UNKNOWN,result(HuoyanSdkClient.RESULT_UNKNOWN,null,0,0).outcome());
        assertEquals(ImageFlowProgress.Outcome.NOT_EXECUTED,result(HuoyanSdkClient.LOCAL_DISCONNECTED,null,0,0).outcome());
    }
}
