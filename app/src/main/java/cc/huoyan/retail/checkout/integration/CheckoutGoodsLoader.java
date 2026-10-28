package cc.huoyan.retail.checkout.integration;

import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.CheckoutPresenter;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.sdk.client.model.RecognitionCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** 按识别结果批量加载 SDK 商品；合并排队查询并阻止过期结果覆盖人工修改。 */
public final class CheckoutGoodsLoader implements CheckoutPresenter.GoodsLoader {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LatestRefresh<List<GoodsVo>> refresh;

    /** 指定结果提交线程，页面传入主线程执行器。 */
    public CheckoutGoodsLoader(Executor main) { refresh = new LatestRefresh<>(worker, main); }

    /** 复制本轮分组后异步查询，返回带识别项的商品行，不创建图片。 */
    public void load(CheckoutRecognition response, Consumer<List<GoodsVo>> commit, Consumer<Exception> failure) {
        Map<String, List<RecognitionSelection>> groups = new LinkedHashMap<>();
        if (response != null && response.getSelections() != null) {
            for (RecognitionSelection item : response.getSelections()) {
                if (item != null && item.getSelectedPlu() != null && !item.getSelectedPlu().isEmpty())
                    groups.computeIfAbsent(item.getSelectedPlu(), key -> new ArrayList<>()).add(item);
            }
        }
        Set<String> plus = productPlus(groups);
        refresh.submit(() -> assemble(groups, plus.isEmpty() ? Collections.emptyList()
                : SdkGoodsSource.getInstance().getGoodsByPlu(plus)), commit, failure);
    }

    /** 汇总本轮已选商品、原始命中与候选编码，后台一次批量查询即可补齐反馈名称。 */
    static Set<String> productPlus(Map<String, List<RecognitionSelection>> groups) {
        Set<String> plus = new java.util.LinkedHashSet<>(groups.keySet());
        for (List<RecognitionSelection> items : groups.values()) {
            for (RecognitionSelection item : items) {
                plus.add(item.getOriginalClazz());
                for (RecognitionCandidate candidate : item.getCandidates()) plus.add(candidate.getClazz());
            }
        }
        plus.remove(null);
        plus.remove("");
        return plus;
    }

    /** 后台固定每框的原始商品及候选名称；候选商品仅补充快照，不生成收银行。 */
    static List<GoodsVo> assemble(Map<String, List<RecognitionSelection>> groups, List<GoodsEntity> goods) {
        Map<String, GoodsEntity> products = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (GoodsEntity product : goods) {
            if (product == null) continue;
            products.put(product.getPlu(), product);
            names.put(product.getPlu(), product.getName());
        }
        for (List<RecognitionSelection> items : groups.values()) {
            for (RecognitionSelection item : items) {
                item.retainCandidateNames(names);
                GoodsEntity original = products.get(item.getOriginalClazz());
                if (original != null) item.retainOriginalProduct(
                        new cc.huoyan.sdk.client.model.FeedbackProduct(
                                original.getGoodsId(), original.getName(), original.getPlu()));
            }
        }
        List<GoodsVo> rows = new ArrayList<>();
        Set<String> matched = new HashSet<>();
        for (GoodsEntity product : goods) {
            if (product == null) continue;
            List<RecognitionSelection> items = groups.get(product.getPlu());
            if (items == null || items.isEmpty()) continue;
            matched.add(product.getPlu());
            rows.add(new GoodsVo(items.size(), product.getPrice(), product, items));
        }
        for (Map.Entry<String, List<RecognitionSelection>> entry : groups.entrySet()) {
            if (matched.contains(entry.getKey())) continue;
            rows.add(unmatched(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    /** 将SDK 目录缺失的识别项保留为展示行，不参与计价。 */
    public static GoodsVo unmatched(String plu, List<RecognitionSelection> items) {
        GoodsEntity product = new GoodsEntity();
        product.setPlu(plu);
        product.setName(plu);
        product.setPrice(0);
        product.setUnit(0);
        GoodsVo row = new GoodsVo(items == null ? 0 : items.size(), 0, product, items);
        row.setUnmatched(true);
        return row;
    }

    /** 人工修改、离开页面或重开会话后丢弃旧查询。 */
    public void invalidate() { refresh.invalidate(); }

    /** 页面销毁时停止接受结果并结束查询线程。 */
    public void close() { refresh.close(); worker.shutdownNow(); }
}
