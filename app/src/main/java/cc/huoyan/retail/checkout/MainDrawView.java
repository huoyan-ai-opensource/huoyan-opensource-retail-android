package cc.huoyan.retail.checkout;

import cc.huoyan.retail.device.camera.PreviewScale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.common.FileUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainDrawView extends View {
    private Path path;           // 绘制路径
    private List<GoodsVo> goodsVos;
    private Map<String, Integer> goodsColorMap = new LinkedHashMap<>();
    private int mWidth = 0;
    private int mHeight = 0;
    private static int sizeDP1;
    private static Paint paint;
    private static Paint textPaint;
    private static Paint rectPaint;
    private static final int RECT_HEIGHT = 28;


    public MainDrawView(Context context) {
        this(context, null);
    }

    public MainDrawView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainDrawView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public MainDrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setGoodsVo(List<GoodsVo> goodsVos, int width, int height) {
        this.goodsVos = goodsVos;
        this.goodsColorMap = RecognizeColorUtil.buildGoodsColorMap(goodsVos, false);
        mWidth = width;
        mHeight = height;
        invalidate();
    }

    private void init(Context context) {
        sizeDP1 = context.getResources().getDimensionPixelSize(R.dimen.dp_1);
        paint = new Paint();
        paint.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.dp_2));
        paint.setStyle(Paint.Style.STROKE);
        path = new Path();
        textPaint = new Paint();
        textPaint.setTextSize(20);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        rectPaint = new Paint();
        rectPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (goodsVos != null && !goodsVos.isEmpty()) {
            WeakReference<ArrayList<GoodsVo>> goodsVo = new WeakReference<>(new ArrayList<>(goodsVos));
            for (GoodsVo vo : goodsVo.get()) {
                if (vo == null) {
                    continue;
                }
                List<RecognitionSelection> detectItemEntities = vo.getRecognitionSelection();
                GoodsEntity goods = vo.getGoods();
                if (detectItemEntities == null || goods == null) {
                    continue;
                }
                int boxColor = RecognizeColorUtil.getGoodsColor(goodsColorMap, vo);
                PointGoodsEntity pointGoodsEntity = new PointGoodsEntity();
                pointGoodsEntity.setGoods(goods);
                pointGoodsEntity.setDetectItemEntities(detectItemEntities);
                List<float[]> floats = new ArrayList<>();
                for (RecognitionSelection detectItemEntity : detectItemEntities) {
                    paint.setColor(boxColor);
                    rectPaint.setColor(boxColor);

                    float[] originalPoints = FileUtil.invertPoints(detectItemEntity, mWidth, mHeight);
                    if (originalPoints == null || originalPoints.length < 4) {
                        // 防御：points 异常条目跳过绘制（真机 NPE 修复，2026-08-24）
                        continue;
                    }
                    // 获取控件实际尺寸
                    int viewWidth = getWidth();
                    int viewHeight = getHeight();

                    // 与预览保持同一缩放方式：竖屏完整居中，横屏沿用高度适配。
                    float scale = PreviewScale.compute(mWidth, mHeight, viewWidth, viewHeight,
                            getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
                    float displayHeight = mHeight * scale;
                    float displayWidth = displayHeight * ((float) mWidth / mHeight);
                    float offsetX = (viewWidth - displayWidth) / 2f;
                    float offsetY = (viewHeight - displayHeight) / 2f;

                    // 转换坐标并绘制
                    float[] transformedPoints = new float[originalPoints.length];

                    for (int i = 0; i < originalPoints.length; i += 2) {
                        float x = originalPoints[i];
                        float y = originalPoints[i + 1];

                        // 归一化到 [0,1] 范围，再映射到控件坐标系
                        transformedPoints[i] = (x / mWidth) * displayWidth + offsetX;
                        transformedPoints[i + 1] = (y / mHeight) * displayHeight + offsetY;
                    }

                    floats.add(transformedPoints);
                    // 计算包围盒（OBB 为 4 顶点 8 值，AABB 为 4 值，统一取全部点的 min/max）
                    float minX = Float.MAX_VALUE;
                    float maxX = Float.MIN_VALUE;
                    float minY = Float.MAX_VALUE;
                    float maxY = Float.MIN_VALUE;
                    for (int i = 0; i < transformedPoints.length; i += 2) {
                        minX = Math.min(minX, transformedPoints[i]);
                        maxX = Math.max(maxX, transformedPoints[i]);
                        minY = Math.min(minY, transformedPoints[i + 1]);
                        maxY = Math.max(maxY, transformedPoints[i + 1]);
                    }

                    if (transformedPoints.length == 4) {
                        // BBOX（bakery 默认）：2 对角点 4 值，直接画轴对齐矩形
                        canvas.drawRect(minX, minY, maxX, maxY, paint);
                    } else {
                        // OBB（snacks）：4 顶点 8 值，画旋转四边形
                        path.reset();
                        for (int i = 0; i < transformedPoints.length; i += 2) {
                            if (i == 0) {
                                path.moveTo(transformedPoints[i], transformedPoints[i + 1]);
                            } else {
                                path.lineTo(transformedPoints[i], transformedPoints[i + 1]);
                            }
                        }
                        path.close();
                        canvas.drawPath(path, paint);
                    }
                }
                pointGoodsEntity.setPointFloats(floats);
            }

        }
    }
}
