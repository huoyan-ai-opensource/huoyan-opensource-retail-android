package cc.huoyan.retail.goods;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.testing.QueuedExecutor;
import org.junit.Test;
import cc.huoyan.retail.pricing.PricingMode;
import static org.junit.Assert.*;

public class GoodsFormPresenterTest {
    private final QueuedExecutor worker = new QueuedExecutor();
    private final QueuedExecutor ui = new QueuedExecutor();
    private int error;
    private GoodsEntity accepted;
    private final GoodsFormPresenter.View view = new GoodsFormPresenter.View() {
        public void invalid(int resource) { error = resource; }
        public void failed(String reason) { fail(reason); }
        public void accepted(GoodsEntity goods) { accepted = goods; }
    };
    @Test public void duplicatePrecedesNameErrorAndDoesNotCommit() {
        GoodsEntity existing = new GoodsEntity(); existing.setPlu("123");
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> existing, worker, ui);
        presenter.confirm("1.25", "123", "", true, "0"); worker.drain(); ui.drain();
        assertEquals(R.string.toast_plu_exists, error); assertNull(accepted);
    }
    @Test public void editPreservesOriginalWeightUnitAndDoesNotMutateOriginal() {
        GoodsEntity original = new GoodsEntity(); original.setPlu("123"); original.setUnit(3); original.setPrice(100);
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, original, plu -> { fail("edit must not query duplicates"); return null; }, worker, ui);
        presenter.confirm("2.25", "123", "new", false, "0"); worker.drain(); ui.drain();
        assertEquals(3, accepted.getUnit()); assertEquals(225, accepted.getPrice()); assertEquals(100, original.getPrice());
    }
    @Test public void exactCentsRejectOverflowAndNegative() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        for (String price : new String[]{"-1", "1e100"}) {
            presenter.confirm(price, "123", "apple", false, "0"); worker.drain(); ui.drain();
            assertEquals(R.string.toast_price_format, error); assertNull(accepted);
        }
    }
    /** SDK long 分金额不再受旧数据库 int 列上限限制。 */
    @Test public void centsBeyondIntRangeRemainExact() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        presenter.confirm("21474836.48", "123", "apple", false, "0"); worker.drain(); ui.drain();
        assertEquals(2147483648L, accepted.getPrice()); assertEquals(0, error);
    }
    @Test public void destroyedViewDoesNotReceiveQueuedSuccess() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        presenter.confirm("1.25", "123", "apple", true, "0"); worker.drain(); presenter.detach(); ui.drain();
        assertNull(accepted);
    }
    @Test
    public void resolveUnitByType_count_returns5() {
        assertEquals(5, GoodsFormPresenter.resolveUnitByType(true));
    }

    @Test
    public void resolveUnitByType_weight_returns0() {
        assertEquals(0, GoodsFormPresenter.resolveUnitByType(false));
    }

    @Test
    public void resolveUnitByType_alignsWithPricingModeDispatch() {
        assertEquals(PricingMode.COUNT, PricingMode.fromUnit(GoodsFormPresenter.resolveUnitByType(true)));
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(GoodsFormPresenter.resolveUnitByType(false)));
    }
    /** 禁止路径、标点、中文和超长编码，在查询 SDK 前拒绝。 */
    @Test public void newPluOnlyAcceptsAsciiLettersAndDigits() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        for (String plu : new String[]{"中123", "AB-1", "A_1", "a b", "a/1", "a".repeat(33)}) {
            error = 0; presenter.confirm("1.25", plu, "apple", true, "0");
            assertEquals(R.string.toast_plu_invalid, error); assertEquals(0, worker.pending());
        }
        presenter.confirm("1.25", "Ab009", "apple", true, "0"); worker.drain(); ui.drain();
        assertEquals("Ab009", accepted.getPlu()); assertEquals(125, accepted.getPrice());
    }
    /** Presenter 也校验长度，不能靠绕过输入框把超长值提交到 SDK。 */
    @Test public void overlongFieldsAreRejectedBeforeLookup() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        presenter.confirm("1.25", "1", "x".repeat(65), true, "0");
        assertEquals(R.string.goods_input_too_long, error); assertEquals(0, worker.pending());
        presenter.confirm("123456789012", "1", "apple", true, "0");
        assertEquals(R.string.goods_input_too_long, error); assertEquals(0, worker.pending());
    }
    /** 皮重按整数克保存，非法输入在 SDK 查询前拒绝，计件不保留皮重。 */
    @Test public void tareIsValidatedAndSavedInGrams() {
        GoodsFormPresenter presenter = new GoodsFormPresenter(view, null, plu -> null, worker, ui);
        for (String invalid : new String[]{"", "-1", "0.5", "2147483648"}) {
            presenter.confirm("2.50", "A01", "apple", false, invalid);
            assertEquals(R.string.goods_tare_invalid, error);
            assertEquals(0, worker.pending());
        }
        presenter.confirm("2.50", "A01", "apple", false, "25"); worker.drain(); ui.drain();
        assertEquals(25, accepted.getTareWeight());
        presenter.confirm("2.50", "A01", "apple", true, "25"); worker.drain(); ui.drain();
        assertEquals(0, accepted.getTareWeight());
    }
}
