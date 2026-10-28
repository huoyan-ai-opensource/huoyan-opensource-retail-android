package cc.huoyan.retail.checkout;

import cc.huoyan.retail.checkout.domain.CheckoutState;
import cc.huoyan.retail.checkout.RecognitionSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 一次页面展示所需的数据；集合结构只读，商品行沿用现有适配器模型。 */
public final class CheckoutViewState {
    /** 当前不可变业务会话快照。 */
    public final CheckoutState sale;
    /** 结构只读的展示行，行模型沿用原有图片所有权。 */
    public final List<GoodsVo> rows;
    /** 按对象身份复制的当前选框集合。 */
    public final Set<RecognitionSelection> selected;
    /** 当前被计价前置校验拦截的商品 PLU。 */
    public final Set<String> blockedPlus;
    /** 逐行至少按一件计算的展示总件数。 */
    public final int itemCount;
    /** 当前允许编辑且至少选中一个框时启用改品。 */
    public final boolean modifyEnabled;
    /** 锁定识别且本轮商品查询成功后，清单确实为空才展示空态。 */
    public final boolean emptyVisible;
    /** 仅空闲且尚未锁定时显示放盘引导；商品查询中的空清单不属于等待态。 */
    public final boolean waitingVisible;
    /** 锁定后存在称重单价冲突的提示标记。 */
    public final boolean priceConflict;

    /** 固定一次渲染所需状态，并复制所有集合结构。 */
    CheckoutViewState(CheckoutState sale, List<GoodsVo> rows, Set<RecognitionSelection> selected,
            Set<String> blocked, boolean goodsResolved) {
        this.sale = sale;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        Set<RecognitionSelection> selection = Collections.newSetFromMap(new IdentityHashMap<>());
        selection.addAll(selected);
        this.selected = Collections.unmodifiableSet(selection);
        blockedPlus = Collections.unmodifiableSet(new LinkedHashSet<>(blocked));
        int count = 0;
        for (GoodsVo row : rows) if (row != null) count += row.getCount() == null ? 1 : Math.max(1, row.getCount());
        itemCount = count;
        modifyEnabled = sale.canEdit && !selection.isEmpty();
        emptyVisible = sale.recognitionLocked && goodsResolved && rows.isEmpty();
        waitingVisible = rows.isEmpty() && !sale.recognitionLocked
                && sale.phase == CheckoutState.Phase.IDLE_EMPTY;
        priceConflict = sale.phase == CheckoutState.Phase.LOCKED_PRICE_CONFLICT;
    }
}
