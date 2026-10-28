package cc.huoyan.sdk.client.model;

/** 单个识别框的反馈输入；原始商品与最终选择分别固定，坐标必须属于上传整图。 */
public final class FeedbackDetection {
    /** 原始识别商品。 */
    private final FeedbackProduct original;
    /** 最终确认商品；未选择时为空。 */
    private final FeedbackProduct selected;
    /** 用户明确删除此框，优先于最终选择。 */
    private final boolean deleted;
    /** 原始识别距离，不是置信度。 */
    private final double distance;
    /** 此框的 SDK 候选，可包含原始命中，原始顺序不可变。 */
    private final java.util.List<FeedbackDetect> candidates;
    /** 整图 AABB/OBB 顶点副本。 */
    private final float[] points;

    /** 固定识别、人工选择与整图坐标，后续页面编辑不会改变本次反馈。 */
    public FeedbackDetection(FeedbackProduct original, FeedbackProduct selected, boolean deleted,
            double distance, float[] points, java.util.List<FeedbackDetect> candidates) {
        this.candidates = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(candidates));
        this.original = original;
        this.selected = selected;
        this.deleted = deleted;
        this.distance = distance;
        this.points = points == null ? null : points.clone();
    }

    /** 返回此框的候选（可包含命中项），保持 SDK 原始顺序。 */
    public java.util.List<FeedbackDetect> getCandidates() { return candidates; }
    /** 返回原始识别商品。 */
    public FeedbackProduct getOriginal() { return original; }
    /** 返回最终确认商品。 */
    public FeedbackProduct getSelected() { return selected; }
    /** 返回显式删除标记。 */
    public boolean isDeleted() { return deleted; }
    /** 返回原始距离。 */
    public double getDistance() { return distance; }
    /** 返回整图坐标副本。 */
    public float[] getPoints() { return points == null ? null : points.clone(); }
}
