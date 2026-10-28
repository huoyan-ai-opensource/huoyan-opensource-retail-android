package cc.huoyan.sdk.client.model;


/**
 * 单个检测框（）：{@link DetectResult} 的元素——画布绝对坐标、
 * 左上为原点，x/y 为框左上角，w/h 为框宽高（与 native 归一化 x,y,w,h 乘帧宽高
 * 取整的口径一致），score 为框置信度（后端无置信度时为 0）。不可变值对象。
 */
public final class DetectBox {

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final double score;

    /** 构造数据对象。 */
    public DetectBox(int x, int y, int w, int h, double score) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.score = score;
    }

    /** 框左上角 x（画布绝对坐标）。 */
    public int getX() {
        return x;
    }

    /** 框左上角 y（画布绝对坐标）。 */
    public int getY() {
        return y;
    }

    /** 框宽（像素）。 */
    public int getW() {
        return w;
    }

    /** 框高（像素）。 */
    public int getH() {
        return h;
    }

    /** 框置信度；后端无置信度输出时为 0。 */
    public double getScore() {
        return score;
    }

    /** 返回有效轴对齐框坐标副本；无面积时返回 null。 */
    public float[] getPoints() {
        if (w <= 0 || h <= 0) return null;
        float[] points = new float[]{x, y, (long) x + w, (long) y + h};
        float left = Math.min(points[0], points[2]), right = Math.max(points[0], points[2]);
        float top = Math.min(points[1], points[3]), bottom = Math.max(points[1], points[3]);
        return right <= left || bottom <= top ? null : new float[]{left, top, right, bottom};
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DetectBox)) {
            return false;
        }
        DetectBox that = (DetectBox) other;
        return x == that.x && y == that.y && w == that.w && h == that.h
                && Double.compare(score, that.score) == 0;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + w;
        result = 31 * result + h;
        return 31 * result + Double.hashCode(score);
    }

    @Override
    public String toString() {
        return "DetectBox(x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + ", score=" + score + ")";
    }

}
