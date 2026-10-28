package cc.huoyan.retail.goods;

import cc.huoyan.retail.pricing.PricingMode;

import org.junit.Assert;
import org.junit.Test;

/**
 * sale_mode 计价归一化测试（Issue D13）。
 */
public class SaleModeMapperTest {

    @Test
    public void saleModeCountMapsToLocalUnitCount() {
        Assert.assertEquals(5, SaleModeMapper.toLocalUnit(0));
        Assert.assertEquals(PricingMode.COUNT, PricingMode.fromUnit(SaleModeMapper.toLocalUnit(0)));
    }

    @Test
    public void saleModeWeightMapsToLocalUnitWeight() {
        Assert.assertEquals(0, SaleModeMapper.toLocalUnit(1));
        Assert.assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(SaleModeMapper.toLocalUnit(1)));
    }

    @Test
    public void unknownSaleModeFallsBackToWeight() {
        // 契约枚举外的未知值按称重兜底，不进计件键盘链路
        Assert.assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(SaleModeMapper.toLocalUnit(2)));
        Assert.assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(SaleModeMapper.toLocalUnit(-1)));
    }

    @Test
    public void constantsMatchContract() {
        Assert.assertEquals(0, SaleModeMapper.SALE_MODE_COUNT);
        Assert.assertEquals(1, SaleModeMapper.SALE_MODE_WEIGHT);
        Assert.assertEquals(5, SaleModeMapper.LOCAL_UNIT_COUNT);
    }

    @Test
    public void localUnitCountMapsToSaleModeCount() {
        Assert.assertEquals(0, SaleModeMapper.toSaleMode(5));
    }

    @Test
    public void localUnitWeightMapsToSaleModeWeight() {
        Assert.assertEquals(1, SaleModeMapper.toSaleMode(0));
    }

    @Test
    public void unknownLocalUnitMapsToSaleModeWeight() {
        // 与 PricingMode.fromUnit 口径一致：非 5 一律按称重上传
        Assert.assertEquals(1, SaleModeMapper.toSaleMode(3));
        Assert.assertEquals(1, SaleModeMapper.toSaleMode(-1));
    }

    @Test
    public void toSaleModeAndToLocalUnitRoundTrip() {
        // 双向映射 round-trip：计件 unit 5 ↔ sale_mode 0（按个）、称重 unit 0 ↔ sale_mode 1（按重量）
        Assert.assertEquals(5, SaleModeMapper.toLocalUnit(SaleModeMapper.toSaleMode(5)));
        Assert.assertEquals(0, SaleModeMapper.toLocalUnit(SaleModeMapper.toSaleMode(0)));
        Assert.assertEquals(1, SaleModeMapper.toSaleMode(SaleModeMapper.toLocalUnit(1)));
        Assert.assertEquals(0, SaleModeMapper.toSaleMode(SaleModeMapper.toLocalUnit(0)));
    }

    @Test
    public void localUnitCountMapsToLabelCount() {
        Assert.assertEquals("个", SaleModeMapper.toUnitLabel(5));
    }

    @Test
    public void localUnitWeightMapsToLabelWeight() {
        Assert.assertEquals("kg", SaleModeMapper.toUnitLabel(0));
    }

    @Test
    public void unknownLocalUnitMapsToLabelWeight() {
        // 与 PricingMode.fromUnit 口径一致：非 5 一律按称重标签 kg 展示
        Assert.assertEquals("kg", SaleModeMapper.toUnitLabel(3));
        Assert.assertEquals("kg", SaleModeMapper.toUnitLabel(-1));
    }
}
