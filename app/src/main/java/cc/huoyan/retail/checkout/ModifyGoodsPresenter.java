package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.GoodsSearchIndex;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.sdk.client.model.RecognitionCandidate;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** 改品候选合并与选择状态；SDK 商品查询在后台，结果按请求代次交付。 */
final class ModifyGoodsPresenter {
    /** 改品弹窗提供的显示与提交出口。 */
    interface View {
        /** 显示候选和当前选中 PLU。 */
        void candidates(List<RecognizeModifyCandidate> candidates, String selectedPlu);
        /** 刷新选中状态，不重新查询数据。 */
        void selection(String plu);
        /** 提示尚未选中商品。 */
        void selectionRequired();
        /** 查询失败保留旧结果供重试。 */
        void failed(String reason);
        /** 宿主判断是否允许改品，false 时保留弹窗。 */
        boolean confirm(GoodsEntity goods);
        /** 宿主接受选择后关闭弹窗。 */
        void close();
    }
    /** 候选读取端口，避免 Presenter 直接访问 SDK 单例。 */
    interface Source {
        /** 一次读取识别相似商品。 */
        List<GoodsEntity> similar(Set<String> plus);
        /** 获取搜索匹配的补充商品，保留仓库顺序。 */
        List<GoodsEntity> search(String query);
    }
    /** 当前视图，销毁时释放。 */
    private View view;
    /** 打开弹窗时的原商品标识，仅用于候选标记。 */
    private final String currentPlu;
    /** 识别目标总数，包含原响应中的空项以保持支持率语义。 */
    private final int totalCount;
    /** 相似项首次出现顺序，构造后只读。 */
    private final LinkedHashSet<String> similarPlus = new LinkedHashSet<>();
    /** 相似项出现次数，不擅自按目标去重。 */
    private final Map<String, Integer> supportCountMap = new LinkedHashMap<>();
    /** 当前选择，搜索隐藏该行时仍保留。 */
    private GoodsEntity selectedGoods;
    /** 商品候选数据源。 */
    private final Source source;
    /** 后台查询队列。 */
    private final Executor worker;
    /** 主线程交付器。 */
    private final Executor ui;
    /** 最新搜索代次。 */
    private volatile int generation;
    /** 注入识别快照、数据源及线程调度。 */
    ModifyGoodsPresenter(View view, GoodsVo goodsVo, Source source, Executor worker, Executor ui) {
        this.view = view; this.source = source;
        this.worker = worker; this.ui = ui;
        selectedGoods = goodsVo == null ? null : goodsVo.getGoods();
        List<RecognitionSelection> detectItems = goodsVo == null || goodsVo.getRecognitionSelection() == null
                ? new ArrayList<>()
                : goodsVo.getRecognitionSelection();
        totalCount = detectItems.size();
        currentPlu = goodsVo == null || goodsVo.getGoods() == null ? null : goodsVo.getGoods().getPlu();
        for (RecognitionSelection detectItem : detectItems) {
            if (detectItem == null) {
                continue;
            }
            if (detectItem.getCandidates() == null) {
                continue;
            }
            for (RecognitionCandidate similarEntity : detectItem.getCandidates()) {
                if (similarEntity == null || empty(similarEntity.getClazz())) {
                    continue;
                }
                similarPlus.add(similarEntity.getClazz());
                supportCountMap.put(similarEntity.getClazz(),
                        supportCountMap.getOrDefault(similarEntity.getClazz(), 0) + 1);
            }
        }
    }
    /** 异步合并候选；仅第一次缺少选中项时自动选择首项。 */
    void search(String query) {
        int request = ++generation;
        worker.execute(() -> {
            if (request != generation) return;
            try {
                List<RecognizeModifyCandidate> candidates = buildCandidates(query);
                ui.execute(() -> {
                    if (view == null || request != generation) return;
                    if (selectedGoods == null && !candidates.isEmpty()) selectedGoods = candidates.get(0).getGoods();
                    view.candidates(candidates, selectedGoods == null ? null : selectedGoods.getPlu());
                });
            } catch (RuntimeException error) {
                ui.execute(() -> { if (view != null && request == generation) view.failed(error.getMessage()); });
            }
        });
    }
    /** 点击商品即选择并提交；宿主拒绝时保留弹窗供重新选择。 */
    void select(RecognizeModifyCandidate candidate) {
        if (candidate == null || candidate.getGoods() == null || view == null) return;
        selectedGoods = candidate.getGoods(); view.selection(selectedGoods.getPlu());
        confirm();
    }
    /** 保留宿主拒绝确认时继续调整的交互语义。 */
    private void confirm() {
        if (view == null) return;
        if (selectedGoods == null) view.selectionRequired();
        else if (view.confirm(selectedGoods) && view != null) view.close();
    }
    /** 销毁后忽略晚到的查询结果。 */
    void detach() { ++generation; view = null; }
    /** 按识别相似顺序优先合并，其余商品按仓库顺序追加。 */
    private List<RecognizeModifyCandidate> buildCandidates(String searchText) {
        List<RecognizeModifyCandidate> candidates = new ArrayList<>();
        Map<String, GoodsEntity> similarGoodsMap = similarPlus.isEmpty()
                ? new LinkedHashMap<>()
                : source.similar(similarPlus).stream()
                .collect(Collectors.toMap(GoodsEntity::getPlu, goods -> goods, (left, right) -> left, LinkedHashMap::new));
        Set<String> matchedSimilar = new GoodsSearchIndex(new ArrayList<>(similarGoodsMap.values()))
                .search(searchText).stream().map(GoodsEntity::getPlu).collect(Collectors.toSet());
        for (String plu : similarPlus) {
            GoodsEntity goods = similarGoodsMap.get(plu);
            if (goods == null || !matchedSimilar.contains(plu)) {
                continue;
            }
            RecognizeModifyCandidate candidate = new RecognizeModifyCandidate(goods, true, java.util.Objects.equals(plu, currentPlu), Double.NaN);
            candidate.setSupportCount(supportCountMap.getOrDefault(plu, 0));
            candidate.setTotalCount(totalCount);
            candidates.add(candidate);
        }

        List<GoodsEntity> allGoods = new GoodsSearchIndex(source.search(searchText)).search(searchText);
        for (GoodsEntity goods : allGoods) {
            if (goods == null || similarGoodsMap.containsKey(goods.getPlu())) {
                continue;
            }
            RecognizeModifyCandidate candidate = new RecognizeModifyCandidate(goods, false, java.util.Objects.equals(goods.getPlu(), currentPlu), Double.NaN);
            candidate.setSupportCount(0);
            candidate.setTotalCount(totalCount);
            candidates.add(candidate);
        }

        return candidates;
    }

    /** 空字符串判断不依赖 Android，以便纯 JVM 验证候选规则。 */
    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
