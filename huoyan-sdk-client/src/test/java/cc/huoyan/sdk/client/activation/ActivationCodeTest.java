package cc.huoyan.sdk.client.activation;
import org.junit.Test;
import static org.junit.Assert.*;
/** 手输兼容格式不应改变其他长度的扫码 serial。 */
public class ActivationCodeTest {
    @Test public void normalizeIsIdempotentAndPreservesOtherSerials() {
        assertEquals("ABCD-1234-EFGH-5678",ActivationCode.normalize("ABCD1234EFGH5678"));
        assertEquals("ABCD-1234-EFGH-5678",ActivationCode.normalize("ABCD-1234-EFGH-5678"));
        assertEquals("HY2-87IA-VY86-QZXY",ActivationCode.normalize("HY2-87IA-VY86-QZXY"));
        assertTrue(ActivationCode.validManual("ABCD1234EFGH5678"));
        assertFalse(ActivationCode.validManual("HY2-87IA-VY86-QZXY"));
    }
}
