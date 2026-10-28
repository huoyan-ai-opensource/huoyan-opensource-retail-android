package cc.huoyan.retail.checkout;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.RecognitionItem;
import java.util.*;

/** 收银页面的单帧识别与人工选择；不是 SDK 响应 DTO，也不承载稳定判定。 */
public final class CheckoutRecognition {
    /** 原始 SDK 响应，供日志和诊断使用。 */
    private final RecognizeResult result;
    /** 每个原始框对应独立选择状态，列表不可修改。 */
    private final List<RecognitionSelection> selections;
    /** 从 SDK 回执创建页面状态，保留原对象。 */
    public CheckoutRecognition(RecognizeResult result) {
        this.result = Objects.requireNonNull(result);
        List<RecognitionSelection> items = new ArrayList<>();
        if (result.getCode() == 0) for (RecognitionItem item : result.getItems())
            if (item != null) items.add(new RecognitionSelection(item));
        selections = Collections.unmodifiableList(items);
    }
    /** 使用页面已有选择建立同帧快照，原始 SDK 信息由各选择保存。 */
    public CheckoutRecognition(List<RecognitionSelection> items) {
        selections = Collections.unmodifiableList(new ArrayList<>(items));
        List<RecognitionItem> originals = new ArrayList<>();
        for (RecognitionSelection item : items) originals.add(item.getOriginal());
        result = new RecognizeResult(0, null, originals);
    }
    /** 返回 SDK 原始响应。 */
    public RecognizeResult getResult() { return result; }
    /** 返回页面选择集合。 */
    public List<RecognitionSelection> getSelections() { return selections; }
    /** 帧稳定比较键属于收银规则；排序但保留同类多件的数量。 */
    public String setKey() {
        if (selections.isEmpty()) return null;
        List<String> classes = new ArrayList<>();
        for (RecognitionSelection item : selections) classes.add(item.getOriginalClazz());
        Collections.sort(classes);
        return String.join(",", classes);
    }
}
