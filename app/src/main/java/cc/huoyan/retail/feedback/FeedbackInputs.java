package cc.huoyan.retail.feedback;

import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.common.FileUtil;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.sdk.client.model.FeedbackDetection;
import cc.huoyan.sdk.client.model.FeedbackProduct;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 将 SDK 商品快照、收银选择及相机坐标转换为 JAR 输入；不组装反馈协议或执行上传。 */
public final class FeedbackInputs {
    /** 仅提供宿主参数转换。 */
    private FeedbackInputs() {}

    /** 在确认时固定页面选择、SDK 商品 ID 和整图坐标，不保留页面可变对象。 */
    public static List<FeedbackDetection> detections(CheckoutRecognition response,
            List<GoodsVo> selectedRows, List<GoodsVo> deletedRows, int width, int height) {
        return detections(response, selectedRows, deletedRows,
                plu -> findProduct(plu, selectedRows, deletedRows), FeedbackInputs::product,
                item -> width > 0 && height > 0 ? FileUtil.convertDetectPointsToOriginal(item, width, height) : null);
    }

    /** 纯映射边界；调用线程提供内存商品快照及坐标转换，返回固定的 SDK 输入。 */
    static List<FeedbackDetection> detections(CheckoutRecognition response,
            List<GoodsVo> selectedRows, List<GoodsVo> deletedRows, Function<String, GoodsEntity> lookup,
            BiFunction<GoodsEntity, String, FeedbackProduct> products,
            Function<RecognitionSelection, float[]> coordinates) {
        if (response == null || response.getSelections() == null) return Collections.emptyList();
        List<FeedbackDetection> inputs = new ArrayList<>();
        for (RecognitionSelection item : response.getSelections()) {
            if (item == null) continue;
            String originalPlu = blank(item.getOriginalClazz()) ? item.getSelectedPlu() : item.getOriginalClazz();
            GoodsEntity original = lookup.apply(originalPlu);
            GoodsEntity selected = findGoods(item, selectedRows);
            if (selected == null) selected = findGoods(item, deletedRows);
            float[] points = coordinates.apply(item);
            inputs.add(new FeedbackDetection(item.getOriginalProduct() == null ? products.apply(original, originalPlu) : item.getOriginalProduct(),
                    selected == null ? null : products.apply(selected, selected.getPlu()),
                    findGoods(item, deletedRows) != null, item.getDistance(), points, candidates(item)));
        }
        return Collections.unmodifiableList(inputs);
    }

    /** 固定 SDK 候选顺序及识别时查询的名称快照，不依赖候选是否入车。 */
    private static List<cc.huoyan.sdk.client.model.FeedbackDetect> candidates(RecognitionSelection item) {
        List<cc.huoyan.sdk.client.model.FeedbackDetect> result = new ArrayList<>();
        for (cc.huoyan.sdk.client.model.RecognitionCandidate candidate : item.getCandidates()) {
            result.add(new cc.huoyan.sdk.client.model.FeedbackDetect(candidate.getClazz(),
                    item.getCandidateName(candidate.getClazz()), candidate.getDistance()));
        }
        return result;
    }

    /** 直接使用 SDK 查询快照携带的商品 ID，不读取持久化映射。 */
    public static FeedbackProduct product(GoodsEntity goods, String fallbackPlu) {
        String plu = goods == null || blank(goods.getPlu()) ? fallbackPlu : goods.getPlu();
        String name = goods == null ? null : goods.getName();
        return new FeedbackProduct(goods == null ? null : goods.getGoodsId(), name, plu);
    }

    /** 从当前确认快照中查商品；禁止确认主线程发起 SDK 查询。 */
    private static GoodsEntity findProduct(String plu, List<GoodsVo> selected, List<GoodsVo> deleted) {
        for (List<GoodsVo> rows : java.util.Arrays.asList(selected, deleted)) {
            if (rows == null) continue;
            for (GoodsVo row : rows) {
                if (row != null && row.getGoods() != null && Objects.equals(plu, row.getGoods().getPlu()))
                    return row.getGoods();
            }
        }
        return null;
    }

    /** 按本轮框身份关联商品；相同坐标可能属于不同框，不能用于匹配删除。 */
    private static GoodsEntity findGoods(RecognitionSelection item, List<GoodsVo> rows) {
        if (rows == null) return null;
        for (GoodsVo row : rows) {
            if (row == null || row.getRecognitionSelection() == null) continue;
            for (RecognitionSelection selected : row.getRecognitionSelection()) {
                if (item == selected)
                    return row.getGoods();
            }
        }
        return null;
    }

    /** 判断宿主商品标识是否缺失。 */
    private static boolean blank(String value) { return value == null || value.isEmpty(); }
}
