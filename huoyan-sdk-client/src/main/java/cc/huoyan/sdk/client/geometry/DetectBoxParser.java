package cc.huoyan.sdk.client.geometry;
import cc.huoyan.sdk.client.geometry.DetectGeometry;

/** 按显式坐标布局计算轴对齐外接矩形，不猜测 SDK 模型格式。 */
public final class DetectBoxParser {

    /** 四顶点的扁平坐标长度。 */
    private static final int OBB_VALUE_COUNT = 8;
    /** 两对角点的坐标长度。 */
    private static final int BBOX_VALUE_COUNT = 4;

    /** 静态几何工具。 */
    private DetectBoxParser() {
    }

    /**
     * 解析坐标数组为统一外接矩形。
     * <ul>
     * <li>OBB：4 顶点 8 值扁平数组，取前 8 值各顶点 x/y 的 min/max 得外接矩形</li>
     * <li>BBOX：[x1,y1,x2,y2] 即 [left,top,right,bottom]，非法顺序（right&lt;left 或 bottom&lt;top）clamp 交换</li>
     * </ul>
     * 长度不足（OBB &lt; 8 值 / BBOX &lt; 4 值）或 points 为 null 时返回 null，
     * 不做静默错算；超出所需长度的多余值忽略（OBB 取前 8 值、BBOX 取前 4 值）。
     *
     * @param points   原始坐标数组，可为 null
     * @param geometry 几何格式，可为 null（null 视为无法解析，返回 null）
     * @return 统一外接矩形；输入非法时为 null
     */
    public static DetectRect parse(float[] points, DetectGeometry geometry) {
        if (points == null || geometry == null) {
            return null;
        }
        if (geometry == DetectGeometry.BBOX) {
            if (points.length < BBOX_VALUE_COUNT) {
                return null;
            }
            return DetectRect.of(points[0], points[1], points[2], points[3]);
        }
        if (points.length < OBB_VALUE_COUNT) {
            return null;
        }
        float minX = points[0];
        float maxX = points[0];
        float minY = points[1];
        float maxY = points[1];
        for (int i = 2; i < OBB_VALUE_COUNT; i += 2) {
            float x = points[i];
            float y = points[i + 1];
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }
        return DetectRect.of(minX, minY, maxX, maxY);
    }

    /**
     * 统一外接矩形（轴对齐），float 坐标，满足 left&lt;=right、top&lt;=bottom。
     */
    public static final class DetectRect {
        /** 左边界。 */
        public final float left;
        /** 上边界。 */
        public final float top;
        /** 右边界。 */
        public final float right;
        /** 下边界。 */
        public final float bottom;

        /** 保存已归一化的外接矩形。 */
        private DetectRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /**
         * 以两对角点构造矩形，非法顺序自动 clamp（min/max 交换），保证归一化。
         */
        public static DetectRect of(float x1, float y1, float x2, float y2) {
            return new DetectRect(
                    Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        }
    }
}
