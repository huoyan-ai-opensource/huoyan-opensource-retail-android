package cc.huoyan.retail.device.print;

import java.util.Locale;

/** 旧称重条码格式不可表达的商品仍打印明细，不伪造或截断编码。 */
final class ReceiptBarcode {
    static String build(String plu, long amountCent) {
        if (plu == null || !plu.matches("[0-9]{1,5}") || amountCent < 0 || amountCent > 99999) {
            return null;
        }
        String body = "20" + String.format(Locale.US, "%05d", Integer.parseInt(plu))
                + String.format(Locale.US, "%05d", amountCent);
        return body + EAN13Util.calculateENA13Checksum(body + "0");
    }
}
