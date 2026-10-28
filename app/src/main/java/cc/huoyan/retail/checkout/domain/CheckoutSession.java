package cc.huoyan.retail.checkout.domain;

import static cc.huoyan.retail.checkout.domain.CheckoutState.Phase.*;
import cc.huoyan.retail.pricing.WeightChangePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 单笔收银状态机。事件由主线程串行输入，其他线程只读 state() 快照。 */
public final class CheckoutSession {
    static final int STABLE_READS = 2;
    static final BigDecimal MIN_WEIGHT = new BigDecimal("0.020");
    public enum WeightEvent { UPDATED, STARTED, RESTARTED, CLEARED, NEGATIVE, BECAME_STABLE, UNSTABLE, IGNORED }
    private CheckoutState.Phase phase = IDLE_EMPTY;
    private boolean weighing = true;
    private boolean connected;
    private boolean locked;
    private int stableReads;
    private int emptyReads;
    private BigDecimal currentWeight = BigDecimal.ZERO;
    /** 本次真实秤读数对应的总重；未知皮重时不更新识别基线。 */
    private BigDecimal physicalWeight = BigDecimal.ZERO;
    /** 设备回读皮重，未知时为 null，不能据此确认自动去皮。 */
    private BigDecimal scaleTare;
    /** 真实稳定读数计数，允许在取走商品后的负净重状态清除自动皮重。 */
    private int physicalStableReads;
    /** 当前清单要求的整秤皮重，kg。 */
    private BigDecimal automaticTareTarget = BigDecimal.ZERO;
    /** 本页曾设置自动皮重，清单失效后负责归零，不清除用户原有手动皮重。 */
    private boolean ownsAutomaticTare;
    /** 正在等待设备确认的去皮命令，跨清单重置仍保持互斥。 */
    private TareCommand pendingTare;
    /** 当前目标已失败时不随秤读数反复提交，重新识别或目标变化后可重试。 */
    private BigDecimal failedTareTarget;
    private BigDecimal saleWeight = BigDecimal.ZERO;
    private BigDecimal recognitionBaseline;
    private BigDecimal manualBaseline;
    private long generation;
    private String sessionId;
    private List<SaleLine> lines = Collections.emptyList();
    private WeightedSaleResult receipt;
    private Set<String> conflicts = Collections.emptySet();
    private SalePrintJob activePrint;
    private volatile CheckoutState state;

    /** 创建空收银会话。 */
    public CheckoutSession() { publish(); }

    /** 读取最近一次完整状态，不暴露可变会话字段。 */
    public CheckoutState state() { return state; }

    /** 刷新称重开关并按当前清单重新计价。 */
    public void setWeighingEnabled(boolean enabled) { weighing = enabled; reprice(); publish(); }

    /** 标记秤连接成功；连接本身不代表重量稳定。 */
    public void scaleConnected() { connected = true; publish(); }

    /** 断秤立即撤销称重结算资格。 */
    public void scaleDisconnected() {
        connected = false; stableReads = physicalStableReads = 0; scaleTare = null;
        failedTareTarget = null;
        reprice(); publish();
    }

    /** 输入一笔秤读数，并返回页面需要响应的会话变化。 */
    public WeightEvent onWeight(BigDecimal kg, BigDecimal tareKg, boolean stable) {
        if (!weighing) return WeightEvent.IGNORED;
        connected = true;
        currentWeight = kg.setScale(3, RoundingMode.HALF_UP);
        scaleTare = tareKg;
        if (tareKg == null) {
            stableReads = physicalStableReads = 0;
            reprice(); publish();
            return WeightEvent.UNSTABLE;
        }
        physicalWeight = currentWeight.add(tareKg);
        if (locked && recognitionBaseline == null) recognitionBaseline = physicalWeight;
        physicalStableReads = stable ? physicalStableReads + 1 : 0;
        WeightEvent event = acceptWeight(stable);
        reprice();
        publish();
        return event;
    }

    private WeightEvent acceptWeight(boolean stable) {
        // 自动皮重过大只能阻止结算，不能将尚在秤上的商品当作已取走而反复识别。
        if (locked && ownsAutomaticTare && recognitionBaseline != null
                && !WeightChangePolicy.requiresRecognition(recognitionBaseline, physicalWeight)
                && currentWeight.compareTo(MIN_WEIGHT) < 0) {
            stableReads = 0;
            return WeightEvent.UNSTABLE;
        }
        if (currentWeight.signum() < 0) { resetSale(); return WeightEvent.NEGATIVE; }
        if (currentWeight.compareTo(MIN_WEIGHT) < 0) {
            stableReads = 0;
            if (++emptyReads >= 3) { resetSale(); return WeightEvent.CLEARED; }
            return WeightEvent.UNSTABLE;
        }
        emptyReads = 0;
        boolean started = phase == IDLE_EMPTY;
        if (started) beginSale();
        BigDecimal baseline = manualBaseline == null ? recognitionBaseline : manualBaseline;
        if (stable && locked && activePrint == null && phase != SOLD_OR_REPORTED && baseline != null
                && WeightChangePolicy.requiresRecognition(baseline, physicalWeight)) {
            beginSale();
            return WeightEvent.RESTARTED;
        }
        if (!stable) { stableReads = 0; return started ? WeightEvent.STARTED : WeightEvent.UNSTABLE; }
        stableReads++;
        if (stableReads >= STABLE_READS) {
            saleWeight = currentWeight;
            if (!locked) phase = WEIGHT_STABILIZING;
        }
        return started ? WeightEvent.STARTED : stableReads == STABLE_READS ? WeightEvent.BECAME_STABLE : WeightEvent.UPDATED;
    }

    /** 锁定本轮识别；商品加载完成后再输入清单。 */
    public void lockRecognition() {
        generation++;
        if (sessionId == null) sessionId = UUID.randomUUID().toString();
        recognitionBaseline = scaleTare == null ? null : physicalWeight;
        manualBaseline = null;
        locked = true;
        lines = Collections.emptyList();
        receipt = null;
        conflicts = Collections.emptySet();
        phase = WEIGHT_STABILIZING;
        publish();
    }

    /** 接收独立清单快照；正在打印或已售出的单据禁止修改。 */
    public void replaceLines(List<SaleLine> next) {
        if ((activePrint != null && activePrint.belongsTo(generation)) || phase == SOLD_OR_REPORTED) return;
        lines = Collections.unmodifiableList(new ArrayList<>(next));
        reprice();
        publish();
    }

    /** 人工重量以输入值计价，后续变化仍与确认前的物理秤读数比较。 */
    public void setManualWeight(BigDecimal kg) {
        if (!state.canEdit || kg.signum() < 0) return;
        manualBaseline = physicalWeight;
        currentWeight = kg.setScale(3, RoundingMode.HALF_UP);
        saleWeight = currentWeight;
        stableReads = STABLE_READS;
        reprice();
        publish();
    }

    /** 人工重新识别；打印期间拒绝丢弃当前单据。 */
    public boolean restart() {
        if (activePrint != null || pendingTare != null) return false;
        failedTareTarget = null;
        if (weighing && connected && currentWeight.compareTo(MIN_WEIGHT) >= 0) beginSale();
        else resetSale();
        publish();
        return true;
    }

    /** 无称重模式分发结束后，允许继续接收识别。 */
    public void resumeContinuousRecognition() {
        if (!weighing) { locked = false; publish(); }
    }

    /** 固定本次小票并锁住编辑，只有符合结算条件时才允许调用。 */
    public SalePrintJob beginPrint() {
        if (!state.canPrint) throw new IllegalStateException("当前收银单不可打印");
        activePrint = new SalePrintJob(generation, receipt);
        publish();
        return activePrint;
    }

    /** 完成当前打印；过期或重复回调不会污染新单或释放另一笔打印。 */
    public void finishPrint(SalePrintJob job, boolean success) {
        if (job == null || job != activePrint) return;
        activePrint = null;
        if (success && job.belongsTo(generation)) phase = SOLD_OR_REPORTED;
        else reprice();
        publish();
    }

    private void beginSale() {
        resetSale();
        sessionId = UUID.randomUUID().toString();
        saleWeight = currentWeight;
        phase = DETECTING;
    }

    private void resetSale() {
        generation++;
        sessionId = null;
        locked = false;
        stableReads = emptyReads = 0;
        recognitionBaseline = manualBaseline = null;
        saleWeight = BigDecimal.ZERO;
        lines = Collections.emptyList();
        receipt = null;
        conflicts = Collections.emptySet();
        setAutomaticTareTarget(BigDecimal.ZERO);
        phase = IDLE_EMPTY;
    }

    private void reprice() {
        if (!locked || phase == SOLD_OR_REPORTED || (activePrint != null && activePrint.belongsTo(generation))) return;
        BigDecimal weight = stableReads >= STABLE_READS ? saleWeight : currentWeight;
        SalePricing.Quote quote = SalePricing.quote(sessionId, lines, weight.max(BigDecimal.ZERO), weighing);
        setAutomaticTareTarget(quote.tareKg);
        conflicts = quote.conflicts;
        if (quote.receipt == null || !quote.receipt.isSameReceipt(receipt)) receipt = quote.receipt;
        if (quote.missingPrice) phase = LOCKED_PRICE_MISSING;
        else if (quote.invalidGoods || !conflicts.isEmpty()) phase = LOCKED_PRICE_CONFLICT;
        else if (receipt == null) phase = WEIGHT_STABILIZING;
        else phase = !automaticTareRequired() && pendingTare == null
                && SalePricing.isReceiptReady(receipt.getUnitPriceCent(), weighing && stableReads >= STABLE_READS,
                connected, currentWeight) ? LOCKED_VALID : WEIGHT_STABILIZING;
    }

    /** 固定一次皮重目标，由 Presenter 交给设备；身份用于拒绝旧命令的重复回调。 */
    public static final class TareCommand {
        /** 设备目标皮重，kg，发送边界再验证整数克范围。 */
        public final BigDecimal tareKg;
        /** 仅状态机创建命令。 */
        private TareCommand(BigDecimal tareKg) { this.tareKg = tareKg; }
    }

    /** 稳定读数与锁定清单共同满足时发起去皮；清理本页旧自动皮重也走此入口。 */
    public TareCommand beginAutomaticTare() {
        if (!weighing || !connected || physicalStableReads < STABLE_READS || pendingTare != null
                || activePrint != null || phase == SOLD_OR_REPORTED || !automaticTareRequired()
                || (failedTareTarget != null && failedTareTarget.compareTo(automaticTareTarget) == 0)) return null;
        pendingTare = new TareCommand(automaticTareTarget);
        if (automaticTareTarget.signum() > 0) ownsAutomaticTare = true;
        stableReads = physicalStableReads = 0;
        reprice(); publish();
        return pendingTare;
    }

    /** 命令成功仍以实际净重读数计价，失败保留结算拦截且不自动循环重试。 */
    public void finishAutomaticTare(TareCommand command, boolean success) {
        if (command == null || command != pendingTare) return;
        pendingTare = null;
        if (!success) failedTareTarget = command.tareKg;
        else if (command.tareKg.signum() == 0) ownsAutomaticTare = false;
        reprice(); publish();
    }

    /** 目标变化时解除旧失败抑制，删除或改品可立即触发新的皮重。 */
    private void setAutomaticTareTarget(BigDecimal target) {
        if (automaticTareTarget.compareTo(target) != 0) failedTareTarget = null;
        automaticTareTarget = target;
    }

    /** 无皮重商品不主动操作设备；只清理由本页自动设置过的残留皮重。 */
    private boolean automaticTareRequired() {
        return weighing && (automaticTareTarget.signum() > 0 || ownsAutomaticTare)
                && (scaleTare == null || scaleTare.compareTo(automaticTareTarget) != 0);
    }

    private void publish() {
        state = new CheckoutState(phase, weighing, connected, locked, activePrint != null, stableReads,
                currentWeight, saleWeight, receipt, conflicts, pendingTare != null, automaticTareRequired());
    }
}
