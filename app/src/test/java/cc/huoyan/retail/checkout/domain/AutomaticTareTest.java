package cc.huoyan.retail.checkout.domain;

import cc.huoyan.retail.pricing.PricingMode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** 自动去皮、总重基线及跨单清理的纯业务回归。 */
public class AutomaticTareTest {
    /** 创建具有单件皮重的称重商品。 */
    private SaleLine line(String plu, long grams, int count) {
        return new SaleLine(plu, plu, 1000, grams, count, PricingMode.WEIGHT, false, false, plu);
    }

    /** 生成已锁定且具备稳定毛重的会话。 */
    private CheckoutSession session(SaleLine... lines) {
        CheckoutSession session = new CheckoutSession();
        session.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        session.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        session.lockRecognition();
        session.replaceLines(Arrays.asList(lines));
        return session;
    }

    /** 设备确认后至少两帧净重稳定，才使用新重量结算。 */
    private void confirm(CheckoutSession session, CheckoutSession.TareCommand command, String net) {
        session.onWeight(new BigDecimal(net), command.tareKg, true);
        session.finishAutomaticTare(command, true);
        session.onWeight(new BigDecimal(net), command.tareKg, true);
    }

    /** 同价称重皮重逐件相加，计件和未匹配行不参与。 */
    @Test public void sumsOnlyMatchedWeightedItems() {
        CheckoutSession session = session(line("A", 25, 2), line("B", 30, 3),
                new SaleLine("C", "C", 100, 900, 2, PricingMode.COUNT, false, false, "C"),
                new SaleLine("?", "?", 0, 900, 2, PricingMode.WEIGHT, true, false, "?"));
        CheckoutSession.TareCommand command = session.beginAutomaticTare();
        assertEquals(new BigDecimal("0.140"), command.tareKg);
        confirm(session, command, "0.360");
        assertTrue(session.state().canPrint);
        assertEquals(560L, session.state().receipt.getTotalAmountCent());
        assertNull(session.beginAutomaticTare());
        assertTrue(session.state().recognitionLocked);
        assertEquals(CheckoutSession.WeightEvent.RESTARTED,
                session.onWeight(new BigDecimal("0.366"), new BigDecimal("0.140"), true));
    }

    /** 取走商品后负净重仍允许清除自动皮重，下笔无皮重商品不会继承扣重。 */
    @Test public void removalClearsAutomaticTareBeforeNextSale() {
        CheckoutSession session = session(line("A", 50, 2));
        confirm(session, session.beginAutomaticTare(), "0.400");
        assertEquals(CheckoutSession.WeightEvent.NEGATIVE,
                session.onWeight(new BigDecimal("-0.100"), new BigDecimal("0.100"), true));
        CheckoutSession.TareCommand clear = session.beginAutomaticTare();
        assertNotNull(clear);
        assertEquals(0, clear.tareKg.signum());
        confirm(session, clear, "0.000");
        assertNull(session.beginAutomaticTare());
        session.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        session.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        session.lockRecognition();
        session.replaceLines(Collections.singletonList(line("NO_TARE", 0, 1)));
        assertTrue(session.state().canPrint);
        assertEquals(500L, session.state().receipt.getTotalAmountCent());
        assertNull(session.beginAutomaticTare());
    }

    /** 自动皮重大于毛重时保留识别结果但不能结算，也不反复设置或重开识别。 */
    @Test public void excessiveTareBlocksPrintWithoutRecognitionLoop() {
        CheckoutSession session = session(line("A", 600, 1));
        confirm(session, session.beginAutomaticTare(), "-0.100");
        for (int i = 0; i < 5; i++)
            assertEquals(CheckoutSession.WeightEvent.UNSTABLE,
                    session.onWeight(new BigDecimal("-0.100"), new BigDecimal("0.600"), true));
        assertTrue(session.state().recognitionLocked);
        assertFalse(session.state().canPrint);
        assertNull(session.beginAutomaticTare());
    }

    /** 无商品皮重时不覆盖用户的手动去皮，未知皮重也不能伪造稳定结算。 */
    @Test public void manualTareIsPreservedAndUnknownReadCannotUnlockPrinting() {
        CheckoutSession session = session(line("A", 0, 1));
        session.onWeight(new BigDecimal("0.400"), new BigDecimal("0.100"), true);
        assertTrue(session.state().recognitionLocked);
        assertNull(session.beginAutomaticTare());
        session.onWeight(new BigDecimal("0.400"), null, true);
        assertFalse(session.state().canPrint);
        assertTrue(session.state().recognitionLocked);
        assertNull(session.beginAutomaticTare());
    }

    /** 重复旧回执不能结束下一条命令，人工改品无皮重后撤销旧扣重。 */
    @Test public void replacementClearsTareAndOldCompletionCannotFinishNewCommand() {
        CheckoutSession session = session(line("A", 50, 2));
        CheckoutSession.TareCommand first = session.beginAutomaticTare();
        confirm(session, first, "0.400");
        session.replaceLines(Collections.singletonList(line("B", 0, 1)));
        CheckoutSession.TareCommand clear = session.beginAutomaticTare();
        session.finishAutomaticTare(first, true);
        assertTrue(session.state().automaticTarePending);
        assertFalse(session.state().canPrint);
        confirm(session, clear, "0.500");
        assertTrue(session.state().canPrint);
        assertEquals(500L, session.state().receipt.getTotalAmountCent());
    }
}
