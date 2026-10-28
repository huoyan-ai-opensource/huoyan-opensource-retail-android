package cc.huoyan.retail.checkout.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 同一次状态发布的不可变快照，可由识别线程读取。 */
public final class CheckoutState {
    public enum Phase { IDLE_EMPTY, DETECTING, WEIGHT_STABILIZING, LOCKED_VALID, LOCKED_PRICE_MISSING, LOCKED_PRICE_CONFLICT, SOLD_OR_REPORTED }
    public final Phase phase;
    public final boolean weighingEnabled;
    public final boolean scaleConnected;
    public final boolean recognitionLocked;
    public final boolean printing;
    public final boolean hasStableWeight;
    public final boolean detecting;
    public final boolean recognitionActive;
    public final boolean canEdit;
    public final boolean canPrint;
    public final int stableReadCount;
    public final BigDecimal currentWeightKg;
    public final BigDecimal saleWeightKg;
    public final WeightedSaleResult receipt;
    public final Set<String> priceConflictPlus;
    /** 自动去皮在途，阻止编辑、重识别与打印。 */
    public final boolean automaticTarePending;
    /** 商品皮重尚未得到实际设备读数确认，禁止使用旧净重结算。 */
    public final boolean automaticTareRequired;

    CheckoutState(Phase phase, boolean weighingEnabled, boolean connected, boolean locked,
            boolean printing, int stableReads, BigDecimal currentWeight, BigDecimal saleWeight,
            WeightedSaleResult receipt, Set<String> conflicts, boolean tarePending, boolean tareRequired) {
        this.phase = phase;
        this.weighingEnabled = weighingEnabled;
        scaleConnected = connected;
        recognitionLocked = locked;
        this.printing = printing;
        automaticTarePending = tarePending;
        automaticTareRequired = tareRequired;
        stableReadCount = stableReads;
        currentWeightKg = currentWeight;
        saleWeightKg = saleWeight;
        this.receipt = receipt;
        priceConflictPlus = Collections.unmodifiableSet(new LinkedHashSet<>(conflicts));
        hasStableWeight = weighingEnabled && connected && stableReads >= CheckoutSession.STABLE_READS;
        detecting = phase == Phase.DETECTING || phase == Phase.WEIGHT_STABILIZING;
        recognitionActive = !printing && !tarePending && phase != Phase.SOLD_OR_REPORTED && (!weighingEnabled || detecting);
        canEdit = locked && !printing && !tarePending && phase != Phase.SOLD_OR_REPORTED;
        canPrint = !printing && !tarePending && !tareRequired && phase == Phase.LOCKED_VALID && receipt != null
                && SalePricing.isReceiptReady(receipt.getUnitPriceCent(), hasStableWeight, connected, currentWeight);
    }
}
