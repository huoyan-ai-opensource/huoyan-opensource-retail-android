package cc.huoyan.retail.learning;

import cc.huoyan.retail.checkout.MainDrawView;
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
import cc.huoyan.sdk.client.model.DetectBox;
import cc.huoyan.retail.common.FileUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

// 坐标变换与绘制逻辑与识别页 MainDrawView 逐行对齐（2026-08-25 指示：以识别页预览画框为准）
public class StudyDrawView extends View {
    private Path mPath;           // 绘制路径
    private Paint mPaint;         // 画笔
    private List<DetectBox> detectionEntities;
    private int mWidth = 0;
    private int mHeight = 0;

    public StudyDrawView(Context context) {
        this(context, null);
    }

    public StudyDrawView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StudyDrawView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public StudyDrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setDetectionEntities(List<DetectBox> detectionEntities, int width, int height) {
        this.detectionEntities = detectionEntities;
        mWidth = width;
        mHeight = height;
        invalidate();
    }

    private void init(Context context) {
        mPaint = new Paint();
        mPaint.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.dp_2));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.WHITE);
        mPath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detectionEntities != null && !detectionEntities.isEmpty()) {
            WeakReference<ArrayList<DetectBox>> detectionEntities = new WeakReference<>(new ArrayList<>(this.detectionEntities));
            for (DetectBox detectItemEntity : detectionEntities.get()) {
                float[] originalPoints = FileUtil.invertPoints(detectItemEntity, mWidth, mHeight);
                if (originalPoints == null || originalPoints.length < 4) {
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

                // 计算包围盒（OBB 为 4 顶点 8 值，BBOX 为 2 对角点 4 值，统一取全部点的 min/max）
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
                    // BBOX：2 对角点 4 值，画轴对齐矩形（原 Path 闭合只连对角点，画不出矩形）
                    canvas.drawRect(minX, minY, maxX, maxY, mPaint);
                } else {
                    // OBB：4 顶点 8 值，画旋转四边形
                    mPath.reset();
                    for (int i = 0; i < transformedPoints.length; i += 2) {
                        if (i == 0) {
                            mPath.moveTo(transformedPoints[i], transformedPoints[i + 1]);
                        } else {
                            mPath.lineTo(transformedPoints[i], transformedPoints[i + 1]);
                        }
                    }
                    mPath.close();
                    canvas.drawPath(mPath, mPaint);
                }
            }

        }
    }
}
