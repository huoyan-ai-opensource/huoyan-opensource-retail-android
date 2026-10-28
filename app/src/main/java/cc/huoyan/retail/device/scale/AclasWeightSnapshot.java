package cc.huoyan.retail.device.scale;

/**
 * A single read from the Aclas scale. Weight is always normalized to kilograms.
 */
public class AclasWeightSnapshot {
    /** 设备回执的净重，kg。 */
    private final double weightKg;
    /** 读取到的皮重，kg；未读取时为 null。 */
    private final java.math.BigDecimal tareKg;
    private final String unit;
    private final boolean stable;
    private final boolean netWeight;

    public AclasWeightSnapshot(double weightKg, String unit, boolean stable, boolean netWeight) {
        this(weightKg, unit, stable, netWeight, netWeight ? null : java.math.BigDecimal.ZERO);
    }

    /** 将重量与同次连接的皮重组成展示快照。 */
    public AclasWeightSnapshot(double weightKg, String unit, boolean stable, boolean netWeight, java.math.BigDecimal tareKg) {
        this.tareKg = tareKg;
        this.weightKg = weightKg;
        this.unit = unit;
        this.stable = stable;
        this.netWeight = netWeight;
    }

    /** 返回三位小数净重。 */
    public java.math.BigDecimal netKg() { return java.math.BigDecimal.valueOf(weightKg).setScale(3, java.math.RoundingMode.HALF_UP); }
    /** 返回设备已确认皮重，未知时返回 null。 */
    public java.math.BigDecimal tareKg() { return tareKg == null ? null : tareKg.setScale(3, java.math.RoundingMode.HALF_UP); }
    /** 净重加设备已确认皮重得到总重量，缺少皮重时保持未知。 */
    public java.math.BigDecimal grossKg() { return tareKg() == null ? null : netKg().add(tareKg()); }

    public double getWeightKg() {
        return weightKg;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isStable() {
        return stable;
    }

    public boolean isNetWeight() {
        return netWeight;
    }
}
