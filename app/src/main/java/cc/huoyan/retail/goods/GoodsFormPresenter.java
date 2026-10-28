package cc.huoyan.retail.goods;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.common.GsonUtil;
import cc.huoyan.retail.common.NumberUtil;
import cc.huoyan.retail.learning.StudyFiles;
import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** 新增和编辑表单的校验、重复 PLU 查询及商品快照构建。 */
final class GoodsFormPresenter {
    /** 表单只负责提示错误及向宿主交付商品。 */
    interface View {
        /** 展示既有校验文案，顺序由表单规则决定。 */
        void invalid(int resource);
        /** SDK 商品查询异常时保留输入供用户重试。 */
        void failed(String reason);
        /** 校验通过后交付独立商品，宿主负责提交 SDK。 */
        void accepted(GoodsEntity goods);
    }
    /** 当前表单视图，销毁后不再交付。 */
    private View view;
    /** 编辑前的商品快照，避免表单取消时修改原对象。 */
    private final GoodsEntity editing;
    /** 按 PLU 查询商品的只读端口。 */
    private final Function<String, GoodsEntity> lookup;
    /** SDK 商品查询队列。 */
    private final Executor worker;
    /** 主线程回执队列，不捕获 Dialog。 */
    private final Executor ui;
    /** 最新确认代次，忽略旧输入的查询结果。 */
    private int generation;
    /** 注入表单快照、仓库及线程边界。 */
    GoodsFormPresenter(View view, GoodsEntity editing, Function<String, GoodsEntity> lookup,
                       Executor worker, Executor ui) {
        this.view = view; this.editing = copy(editing); this.lookup = lookup;
        this.worker = worker; this.ui = ui;
    }
    /** 按原顺序检查价格、PLU、重复商品、名称和精确分金额。 */
    void confirm(String price, String plu, String name, boolean count, String tare) {
        int request = ++generation;
        if (view == null) return;
        if (name.length() > 64 || price.length() > 11) { view.invalid(R.string.goods_input_too_long); return; }
        if (price.isEmpty()) { view.invalid(R.string.toast_price_empty); return; }
        if (!NumberUtil.isFloat(price)) { view.invalid(R.string.toast_price_format); return; }
        if (decimalPlaces(price) > 2) { view.invalid(R.string.toast_price_two_decimals); return; }
        final long grams;
        try {
            grams = count ? 0 : Long.parseLong(tare);
            if (grams < 0 || grams > Integer.MAX_VALUE) throw new NumberFormatException();
        } catch (NumberFormatException invalid) { view.invalid(R.string.goods_tare_invalid); return; }
        if (plu.isEmpty()) { view.invalid(R.string.toast_plu_empty); return; }
        if (!StudyFiles.isValidPlu(plu) || (editing == null && !plu.matches("[A-Za-z0-9]{1,32}"))) { view.invalid(R.string.toast_plu_invalid); return; }
        worker.execute(() -> {
            try {
                GoodsEntity existing = editing == null ? lookup.apply(plu) : null;
                ui.execute(() -> {
                    if (view == null || request != generation) return;
                    if (existing != null && existing.getPlu() != null && !existing.getPlu().isEmpty()) {
                        view.invalid(R.string.toast_plu_exists); return;
                    }
                    accept(price, plu, name, count, grams);
                });
            } catch (RuntimeException error) {
                ui.execute(() -> { if (view != null && request == generation) view.failed(error.getMessage()); });
            }
        });
    }
    /** 名称及金额校验完成后保留未修改的计价单位和其他实体字段。 */
    private void accept(String price, String plu, String name, boolean count, long grams) {
        if (name.isEmpty()) { view.invalid(R.string.toast_name_empty); return; }
        GoodsEntity goods = editing == null ? new GoodsEntity() : copy(editing);
        goods.setPlu(plu); goods.setName(name);
        try {
            long cents = new BigDecimal(price).movePointRight(2).longValueExact();
            if (cents < 0) throw new ArithmeticException();
            goods.setPrice(cents);
        } catch (ArithmeticException | NumberFormatException error) {
            view.invalid(R.string.toast_price_format); return;
        }
        if (editing == null || count != (editing.getUnit() == 5)) goods.setUnit(resolveUnitByType(count));
        goods.setTareWeight(grams);
        view.accepted(goods);
    }
    /** 计件为 5，新建称重为 0；编辑未切换计价类型时保留原单位。 */
    static int resolveUnitByType(boolean count) { return count ? 5 : 0; }
    /** 沿用原小数位校验，科学计数法仍由精确金额转换判定。 */
    private static int decimalPlaces(String input) {
        if (!input.matches("^[+-]?\\d+(\\.\\d+)?$")) return 0;
        int dot = input.indexOf('.');
        return dot < 0 ? 0 : input.length() - dot - 1;
    }
    /** 复制完整实体，保留既有扩展字段。 */
    private static GoodsEntity copy(GoodsEntity goods) {
        return goods == null ? null : GsonUtil.fromJson(GsonUtil.toJson(goods), GoodsEntity.class);
    }
    /** 关闭视图时作废尚未交付的确认结果。 */
    void detach() { ++generation; view = null; }
}
