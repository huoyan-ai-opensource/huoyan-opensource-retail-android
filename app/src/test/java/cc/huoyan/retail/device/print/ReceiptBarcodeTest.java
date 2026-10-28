package cc.huoyan.retail.device.print;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiptBarcodeTest {
    @Test
    public void legacyBarcodePreservesPluAndAmount() {
        assertEquals("2010999001238", ReceiptBarcode.build("10999", 123));
    }

    @Test
    public void unsupportedPluDoesNotBlockReceiptOrInventBarcode() {
        assertNull(ReceiptBarcode.build("91026001", 123));
        assertNull(ReceiptBarcode.build("ABC123", 123));
        assertNull(ReceiptBarcode.build("", 123));
        assertNull(ReceiptBarcode.build(null, 123));
    }

    @Test
    public void amountOutsideLegacyRangeOmitsBarcode() {
        assertNotNull(ReceiptBarcode.build("1", 99999));
        assertNull(ReceiptBarcode.build("1", 100000));
        assertNull(ReceiptBarcode.build("1", -1));
    }
}
