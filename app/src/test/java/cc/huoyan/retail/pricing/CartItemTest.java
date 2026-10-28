package cc.huoyan.retail.pricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;

import org.junit.Test;

/**
 * {@link CartItem} 模型语义与防御策略（Issue 决策 7）单测。
 */
public class CartItemTest {

    @Test
    public void countItem_gettersReturnValues_weightIgnored() {
        CartItem item = new CartItem("1001", "蛋挞", 1200,
                PricingMode.COUNT, BigDecimal.valueOf(3), null);

        assertEquals("1001", item.getPlu());
        assertEquals("蛋挞", item.getName());
        assertEquals(1200, item.getUnitPriceCent());
        assertEquals(PricingMode.COUNT, item.getPricingMode());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(item.getQuantity()));
        assertNull(item.getWeightKg());
        // 入车未计算时金额为 0，等待 CartCalculator 回填
        assertEquals(0L, item.getAmountCent());
    }

    @Test
    public void weightItem_gettersReturnValues_quantityIgnored() {
        CartItem item = new CartItem("2001", "吐司", 2560,
                PricingMode.WEIGHT, null, new BigDecimal("0.385"));

        assertEquals("2001", item.getPlu());
        assertEquals(PricingMode.WEIGHT, item.getPricingMode());
        assertNull(item.getQuantity());
        assertEquals(0, new BigDecimal("0.385").compareTo(item.getWeightKg()));
        assertEquals(0L, item.getAmountCent());
    }

    @Test
    public void countItem_nullQuantity_rejected() {
        try {
            new CartItem("1001", "蛋挞", 1200, PricingMode.COUNT, null, null);
            throw new AssertionError("期望抛出 IllegalArgumentException，实际未抛出");
        } catch (IllegalArgumentException expected) {
            assertEquals("计件条目 quantity 不能为 null", expected.getMessage());
        }
    }

    @Test
    public void countItem_negativeQuantity_rejected() {
        try {
            new CartItem("1001", "蛋挞", 1200, PricingMode.COUNT, BigDecimal.valueOf(-1), null);
            throw new AssertionError("期望抛出 IllegalArgumentException，实际未抛出");
        } catch (IllegalArgumentException expected) {
            assertEquals("计件条目 quantity 不能为负数: -1", expected.getMessage());
        }
    }

    @Test
    public void weightItem_nullWeightKg_rejected() {
        try {
            new CartItem("2001", "吐司", 2560, PricingMode.WEIGHT, null, null);
            throw new AssertionError("期望抛出 IllegalArgumentException，实际未抛出");
        } catch (IllegalArgumentException expected) {
            assertEquals("称重条目 weightKg 不能为 null", expected.getMessage());
        }
    }

    @Test
    public void weightItem_negativeWeightKg_rejected() {
        try {
            new CartItem("2001", "吐司", 2560, PricingMode.WEIGHT, null, new BigDecimal("-0.5"));
            throw new AssertionError("期望抛出 IllegalArgumentException，实际未抛出");
        } catch (IllegalArgumentException expected) {
            assertEquals("称重条目 weightKg 不能为负数: -0.5", expected.getMessage());
        }
    }

    @Test
    public void nullPricingMode_rejected() {
        try {
            new CartItem("1001", "蛋挞", 1200, null, BigDecimal.ONE, null);
            throw new AssertionError("期望抛出 NullPointerException，实际未抛出");
        } catch (NullPointerException expected) {
            assertEquals("pricingMode 不能为 null", expected.getMessage());
        }
    }

    @Test
    public void zeroQuantityAndWeight_allowed_amountDefaultsZero() {
        // 决策 7：数量/重量为 0 允许构造（由计算器按金额 0 计），负数才拒绝
        CartItem countItem = new CartItem("1001", "蛋挞", 1200, PricingMode.COUNT, BigDecimal.ZERO, null);
        CartItem weightItem = new CartItem("2001", "吐司", 2560, PricingMode.WEIGHT, null, BigDecimal.ZERO);

        assertEquals(0L, countItem.getAmountCent());
        assertEquals(0L, weightItem.getAmountCent());
    }

    @Test
    public void withAmountCent_returnsCopy_originalUntouched() {
        CartItem item = new CartItem("1001", "蛋挞", 1200, PricingMode.COUNT, BigDecimal.valueOf(3), null);

        CartItem backfilled = item.withAmountCent(3600L);

        assertEquals(3600L, backfilled.getAmountCent());
        assertEquals(0L, item.getAmountCent());
        assertEquals("1001", backfilled.getPlu());
        assertEquals("蛋挞", backfilled.getName());
        assertEquals(1200, backfilled.getUnitPriceCent());
        assertEquals(PricingMode.COUNT, backfilled.getPricingMode());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(backfilled.getQuantity()));
    }
}
