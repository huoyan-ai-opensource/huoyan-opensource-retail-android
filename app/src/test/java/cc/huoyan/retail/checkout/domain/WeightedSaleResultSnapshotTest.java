package cc.huoyan.retail.checkout.domain;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

/** 打印期间重复秤读数不使小票过期，实际数量、重量和会话变化仍使旧快照失效。 */
public class WeightedSaleResultSnapshotTest {
    private WeightedSaleResult receipt(String session,double weight,int count) {
        return new WeightedSaleResult(session,java.math.BigDecimal.valueOf(weight),1000,471,1,count,Collections.singletonList(
                new WeightedSaleResult.Item("1","商品",1000,count,false,"1", cc.huoyan.retail.pricing.PricingMode.WEIGHT)));
    }
    @Test public void repeatedReadingKeepsSameReceipt(){assertTrue(receipt("a",.471,1).isSameReceipt(receipt("a",.471,1)));}
    @Test public void changedWeightOrQuantityInvalidates(){assertFalse(receipt("a",.471,1).isSameReceipt(receipt("a",.472,1)));assertFalse(receipt("a",.471,1).isSameReceipt(receipt("a",.471,2)));}
    @Test public void newSessionCannotCompletePreviousReceipt(){assertFalse(receipt("a",.471,1).isSameReceipt(receipt("b",.471,1)));}
}
