package cc.huoyan.retail.checkout;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.goods.SaleModeMapper;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.common.DisplayFormatUtil;
import cc.huoyan.retail.common.NumberUtil;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RecognizeResultAdapter extends RetailAdapter<GoodsVo> {
    public interface OnGoodsVoActionListener {
        void onChangeCount(GoodsVo goodsVo);

        void onPreviewVisibleChanged(GoodsVo goodsVo);
    }

    /** 称重卡片底色（浅蓝，与 bg_recognize_weight_card 一致，价格冲突红描边分支复用） */

    private OnGoodsVoActionListener actionListener;
    private Map<String, Integer> goodsColorMap = new LinkedHashMap<>();
    private Set<RecognitionSelection> selectedDetectItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<String> priceConflictPlus = new LinkedHashSet<>();
    private Set<String> blockedPlus = new LinkedHashSet<>();
    private double currentWeightKg;
    private boolean editingEnabled = true;
    /** 配置开关与识别锁定共同控制入口显隐。 */
    private boolean deletionVisible;
    /** 主线程整行删除回调，生命周期与收银页一致。 */
    private Consumer<GoodsVo> deleteGoodsListener;

    public RecognizeResultAdapter() {
        super(R.layout.adapter_recognize_result_goods);
    }

    /** 锁定打印中及已结算清单的编辑入口。 */
    public void setEditingEnabled(boolean enabled) {
        if (editingEnabled == enabled) return;
        editingEnabled = enabled;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** 主线程绑定删除结果的交互入口。 */
    public void setOnDeleteGoodsClickListener(Consumer<GoodsVo> listener) { deleteGoodsListener = listener; }

    /** 关闭时回收删除按钮占位，仍保留原修改按钮。 */
    public void setDeletionVisible(boolean visible) {
        if (deletionVisible == visible) return;
        deletionVisible = visible;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setOnGoodsVoActionListener(OnGoodsVoActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setPreviewState(Map<String, Integer> goodsColorMap,
                                Set<String> ignoredHiddenGoodsPlus,
                                Set<RecognitionSelection> selectedDetectItems) {
        this.goodsColorMap = goodsColorMap == null ? new LinkedHashMap<>() : goodsColorMap;
        this.selectedDetectItems = selectedDetectItems == null
                ? Collections.newSetFromMap(new IdentityHashMap<>())
                : selectedDetectItems;
        notifyDataSetChanged();
    }

    public void setPriceConflictPlus(Set<String> priceConflictPlus) {
        this.priceConflictPlus = priceConflictPlus == null ? new LinkedHashSet<>() : new LinkedHashSet<>(priceConflictPlus);
        notifyDataSetChanged();
    }

    /**
     * 设置计价拦截商品 PLU 集合（称重计价但称重模块关闭）：
     * 命中商品在列表项内显示「当前没有称重模块，无法计价」内嵌提示（替代 toast，用户 2026-08-25 指示）。
     *
     * @param blockedPlus 被拦截商品 PLU 集合（null 安全，按空集处理）
     */
    public void setBlockedPlus(Set<String> blockedPlus) {
        this.blockedPlus = blockedPlus == null ? new LinkedHashSet<>() : new LinkedHashSet<>(blockedPlus);
        notifyDataSetChanged();
    }

    /**
     * 设置当前秤读数（kg），供称重商品卡片数量位显示重量使用（用户 2026-08-25 指示：识别结果列表拆分计件/称重卡片）。
     * <p>未开启称重模块或无读数时传 0，未拦截称重卡片显示 0.000 兜底；
     * 被拦截称重商品不受影响；读数变化时刷新列表，去皮回执不能沿用旧重量。
     *
     * @param currentWeightKg 当前秤读数（kg）
     */
    public void setCurrentWeightKg(double currentWeightKg) {
        if (Double.compare(this.currentWeightKg, currentWeightKg) == 0) return;
        this.currentWeightKg = currentWeightKg;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    protected void convert(@NonNull RowViewHolder holder, GoodsVo item) {
        GoodsEntity goods = item.getGoods();
        if (goods == null) {
            return;
        }
        int count = item.getCount() == null || item.getCount() < 1 ? 1 : item.getCount();
        if (item.getCount() == null || item.getCount() < 1) {
            item.setCount(count);
        }
        boolean previewVisible = hasSelectedBox(item);
        boolean countMode = SaleModeMapper.toSaleMode(goods.getUnit()) == SaleModeMapper.SALE_MODE_COUNT;
        boolean weightBlocked = !countMode && blockedPlus.contains(goods.getPlu());
        holder.setText(R.id.goodsName, goods.getName())
                .setText(R.id.goodsPlu, getContext().getString(R.string.goods_plu_prefix) + goods.getPlu())
                .setText(R.id.goodsPrice, getContext().getString(R.string.price_yuan_format, java.math.BigDecimal.valueOf(goods.getPrice(), 2).toPlainString())
                        + "/" + LocalizedText.unit(getContext(), goods.getUnit()))
                .setText(R.id.goodsCount, buildCountText(count, countMode, weightBlocked))
                // 称重卡片单位恒显示（含拦截 0.000 显示 kg），不再随拦截隐藏（用户 2026-08-25 指示）
                .setText(R.id.goodsCountUnit, LocalizedText.unit(getContext(), goods.getUnit()));
        holder.getView(R.id.goodsCountUnit).setVisibility(View.VISIBLE);
        TextView goodsCountView = holder.getView(R.id.goodsCount);
        // 称重重量（含拦截 0.000）字体比计件数量小 2 号（sp_16 vs sp_18），三位小数一行放得下（用户 2026-08-25 指示）
        goodsCountView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getContext().getResources().getDimension(countMode ? R.dimen.sp_18 : R.dimen.sp_16));
        bindColorBlock(holder.getView(R.id.colorBlock), goods.getPlu(), previewVisible);
        holder.getView(R.id.cardContent).setAlpha(1f);
        bindCardBackground(holder.getView(R.id.cardContent), goods.getPlu());

        holder.getView(R.id.cardContent).setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onPreviewVisibleChanged(item);
            }
        });
        View deleteButton = holder.getView(R.id.deleteGoods);
        deleteButton.setVisibility(deletionVisible ? View.VISIBLE : View.GONE);
        deleteButton.setEnabled(editingEnabled);
        deleteButton.setAlpha(editingEnabled ? 1f : 0.45f);
        deleteButton.setContentDescription(getContext().getString(R.string.delete_recognition_result_description, goods.getName()));
        deleteButton.setOnClickListener(v -> {
            if (deletionVisible && editingEnabled && deleteGoodsListener != null) deleteGoodsListener.accept(item);
        });
        // 拦截称重商品（称重模块未启用）无法计价，改重量无意义 → 禁用「改」按钮（用户 2026-08-25 指示）
        TextView modifyButton = holder.getView(R.id.modifyCount);
        modifyButton.setEnabled(editingEnabled && !weightBlocked);
        modifyButton.setAlpha(editingEnabled && !weightBlocked ? 1f : 0.45f);
        modifyButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onChangeCount(item);
            }
        });
    }

    private boolean hasSelectedBox(GoodsVo goodsVo) {
        if (goodsVo == null || goodsVo.getRecognitionSelection() == null || selectedDetectItems.isEmpty()) {
            return false;
        }
        for (RecognitionSelection detectItem : goodsVo.getRecognitionSelection()) {
            if (detectItem != null && selectedDetectItems.contains(detectItem)) {
                return true;
            }
        }
        return false;
    }

    private void bindColorBlock(View colorBlock, String plu, boolean previewVisible) {
        int color = previewVisible
                ? (goodsColorMap.containsKey(plu) ? goodsColorMap.get(plu) : RecognizeColorUtil.DEFAULT_COLOR)
                : RecognizeColorUtil.UNSELECTED_LEADING_COLOR;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        colorBlock.setBackground(drawable);
    }

    /**
     * 按计价模式生成数量区文案：计件显示个数，称重显示当前秤读数（kg，3 位小数，
     * 与秤读数区 DisplayFormatUtil.formatWeightKg 口径一致），无读数兜底 0.000，
     * 被拦截称重商品（称重模块未启用）显示「无重量」。
     */
    private String buildCountText(int count, boolean countMode, boolean weightBlocked) {
        if (countMode) {
            return String.valueOf(count);
        }
        // 称重卡片重量统一 3 位小数（用户 2026-08-25 指示）；
        // 未开称重（拦截）或无秤读数统一显示 0.000 标明无重量
        double displayWeightKg = weightBlocked || currentWeightKg <= 0d ? 0d : currentWeightKg;
        return DisplayFormatUtil.formatWeightKg(displayWeightKg, null);
    }

    /**
     * 按计价模式与价格冲突态绑定卡片背景：计件白底灰描边（bg_similar_choice_item），
     * 称重浅蓝底蓝描边（bg_recognize_weight_card）；价格冲突时红描边优先，称重底色保留。
     */
    private void bindCardBackground(View cardContent, String plu) {
        boolean priceConflict = priceConflictPlus.contains(plu);
        if (priceConflict) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xFFFFFFFF);
            drawable.setCornerRadius(cardContent.getResources().getDimension(R.dimen.dp_3));
            drawable.setStroke((int) cardContent.getResources().getDimension(R.dimen.dp_1), 0xFFD65A56);
            cardContent.setBackground(drawable);
        } else {
            cardContent.setBackgroundResource(R.drawable.bg_similar_choice_item);
        }
    }
}
