package cc.huoyan.sdk.client.geometry;

/** 将整图坐标中的 AABB/OBB 顶点转换为反馈 x/y/width/height。 */
public final class FeedbackBounds {
    /** 静态几何工具。 */
    private FeedbackBounds() {
    }

    /** 将源图坐标限制在图片边界并输出 x/y/w/h。 */
    public static int[] fromPoints(float[] points, int imageWidth, int imageHeight) {
        if (points == null || points.length < 4 || points.length % 2 != 0) {
            return new int[4];
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < points.length; i += 2) {
            if (Float.isNaN(points[i]) || Float.isInfinite(points[i])
                    || Float.isNaN(points[i + 1]) || Float.isInfinite(points[i + 1])) {
                return new int[4];
            }
            minX = Math.min(minX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxX = Math.max(maxX, points[i]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        int left = clamp(Math.round(minX), imageWidth);
        int top = clamp(Math.round(minY), imageHeight);
        int right = clamp(Math.round(maxX), imageWidth);
        int bottom = clamp(Math.round(maxY), imageHeight);
        return new int[]{left, top, Math.max(0, right - left), Math.max(0, bottom - top)};
    }

    /** 将单轴坐标限制在有效范围。 */
    private static int clamp(int value, int limit) {
        return Math.max(0, limit > 0 ? Math.min(value, limit) : value);
    }
}
