package cc.huoyan.sdk.client.geometry;

/** 显式指定的坐标布局；不推断模型类型，也不表示 SDK 支持 OBB。 */
public enum DetectGeometry {
    /**
     * 旋转矩形（Oriented Bounding Box）4 顶点，扁平 8 值数组：
     * points=[x1,y1,x2,y2,x3,y3,x4,y4]。
     */
    OBB,
    /**
     * 轴对齐矩形（Axis-Aligned Bounding Box）2 对角点，4 值数组：
     * [left,top,right,bottom]。
     */
    BBOX
}
