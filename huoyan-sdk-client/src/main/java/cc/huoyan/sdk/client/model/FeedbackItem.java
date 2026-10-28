package cc.huoyan.sdk.client.model;

import java.util.List;
import java.util.Objects;

/** 单个识别目标；detect 按商品编码去重，删除保留识别和检测框并清空最终选择。 */
public final class FeedbackItem {
    /** 用户确认原商品。 */
    public static final int NORMAL = 0;
    /** 用户修改商品。 */
    public static final int MODIFIED = 1;
    /** 用户删除目标。 */
    public static final int DELETED = 2;
    /** 本目标的确认、修改或删除状态。 */
    private final int itemType;
    /** 该框原始命中在前，每个商品编码只保留一条，其余商品按 SDK 首次出现顺序排列。 */
    private final List<FeedbackDetect> detect;
    /** 用户最后选择的商品；删除时为 null，JSON 上报为 result: {}。 */
    private final FeedbackResult result;
    /** 上传原图坐标中的目标框。 */
    private final Double x, y, width, height;

    /** 校验并固定反馈数据，提交线程可安全读取快照。 */
    public FeedbackItem(int itemType, List<FeedbackDetect> detect, FeedbackResult result,
            Double x, Double y, Double width, Double height) {
        if (itemType < NORMAL || itemType > DELETED)
            throw new IllegalArgumentException("item_type 只接受 0、1、2");
        this.itemType = itemType;
        this.detect = FeedbackFields.list(detect, "detect");
        this.result = itemType == DELETED ? null : result;
        this.x = FeedbackFields.geometry(x, "x");
        this.y = FeedbackFields.geometry(y, "y");
        this.width = FeedbackFields.geometry(width, "width");
        this.height = FeedbackFields.geometry(height, "height");
    }
    /** 返回目标操作类型。 */
    public int getItemType() { return itemType; }
    /** 返回该框按商品编码去重的原始命中及同框候选，只读且不受人工改品影响。 */
    public List<FeedbackDetect> getDetect() { return detect; }
    /** 返回最终选择；删除项为空，即使此前已经改品。 */
    public FeedbackResult getResult() { return result; }
    /** 返回目标框横坐标。 */
    public Double getX() { return x; }
    /** 返回目标框纵坐标。 */
    public Double getY() { return y; }
    /** 返回目标框宽度。 */
    public Double getWidth() { return width; }
    /** 返回目标框高度。 */
    public Double getHeight() { return height; }
    /** 按反馈字段比较快照。 */
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackItem)) return false;
        FeedbackItem that = (FeedbackItem) other;
        return itemType == that.itemType && detect.equals(that.detect) && Objects.equals(result, that.result)
                && Objects.equals(x, that.x) && Objects.equals(y, that.y)
                && Objects.equals(width, that.width) && Objects.equals(height, that.height);
    }
    /** 返回与字段相等规则一致的哈希。 */
    @Override public int hashCode() { return Objects.hash(itemType, detect, result, x, y, width, height); }
}
