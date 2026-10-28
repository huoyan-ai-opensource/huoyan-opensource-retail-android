package cc.huoyan.retail.checkout;
import cc.huoyan.sdk.client.model.RecognitionItem;
import cc.huoyan.sdk.client.model.RecognitionCandidate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 单框商品选择状态；SDK 原始回执始终保持不变，仅在页面线程修改选择。 */
public final class RecognitionSelection {
    /** SDK 原始识别条目，不复制协议字段。 */
    private final RecognitionItem original;
    /** 首次查询得到的原始商品身份，人工改品后仍用于反馈。 */
    private cc.huoyan.sdk.client.model.FeedbackProduct originalProduct;
    /** 本框候选的商品名称快照；后台组装时固定，交付页面后只读，不随改品覆盖。 */
    private final Map<String, String> candidateNames = new HashMap<>();
    /** 人工选择的本地商品 PLU，初值为 SDK 分类。 */
    private String selectedPlu;
    /** 组合 SDK 条目与页面选择状态。 */
    public RecognitionSelection(RecognitionItem original) {
        this.original = Objects.requireNonNull(original);
        selectedPlu = original.getClazz();
    }
    /** 返回不可变的原始识别条目。 */
    public RecognitionItem getOriginal() { return original; }
    /** 当前选中商品标识。 */
    public String getSelectedPlu() { return selectedPlu; }
    /** 人工改品只改变选择，原始类别保留供反馈。 */
    public void select(String plu) { selectedPlu = plu; }
    /** 记录原始商品身份；后台查询组装后交给页面，不随人工改品覆盖。 */
    public void retainOriginalProduct(cc.huoyan.sdk.client.model.FeedbackProduct product) {
        if (originalProduct == null && Objects.equals(getOriginalClazz(), product.getPlu())) originalProduct = product;
    }
    /** 返回查询时固定的原始商品身份，无匹配时为空。 */
    public cc.huoyan.sdk.client.model.FeedbackProduct getOriginalProduct() { return originalProduct; }
    /** 后台商品查询完成后仅固定本框候选的名称，不持有商品实体或整份目录。 */
    public void retainCandidateNames(Map<String, String> names) {
        for (RecognitionCandidate candidate : original.getCandidates()) {
            String name = names.get(candidate.getClazz());
            if (name != null) candidateNames.putIfAbsent(candidate.getClazz(), name);
        }
    }
    /** 返回识别时固定的候选名称；SDK 商品目录没有对应记录时为空。 */
    public String getCandidateName(String clazz) { return candidateNames.get(clazz); }
    /** 原始分类，不受人工改品影响。 */
    public String getOriginalClazz() { return original.getClazz(); }
    /** 原始距离，保留 double 精度。 */
    public double getDistance() { return original.getDistance(); }
    /** SDK 候选原样保留 square/rank。 */
    public List<RecognitionCandidate> getCandidates() { return original.getCandidates(); }
    /** 原始框坐标副本，坐标系变换仍由页面负责。 */
    public float[] getPoints() { return original.getPoints(); }
}
