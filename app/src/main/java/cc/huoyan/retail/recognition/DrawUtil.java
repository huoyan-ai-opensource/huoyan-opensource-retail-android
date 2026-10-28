package cc.huoyan.retail.recognition;

import cc.huoyan.retail.checkout.RecognizeColorUtil;
import cc.huoyan.retail.common.FileUtil;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.PointGoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.sdk.client.model.DetectBox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DrawUtil {
    private static final int RECT_HEIGHT = 30;
    private static Paint paint;
    private static Paint studyPaint;
    private static Paint textPaint;
    private static Paint rectPaint;
    private static Path path;
    private static Path studyPath;
    private static Canvas canvas;
    private static int sizeDP1;
    private static List<PointGoodsEntity> pointGoodsEntities;

    public static RecognitionSelection getPointGoods(float[] event) {
        if (pointGoodsEntities != null && !pointGoodsEntities.isEmpty()) {
            for (int i = 0; i < pointGoodsEntities.size(); i++) {
                PointGoodsEntity pointGoodsEntity = pointGoodsEntities.get(i);
                List<float[]> pointFloats = pointGoodsEntity.getPointFloats();
                for (int j = 0; j < pointFloats.size(); j++) {
                    float[] pointFloat = pointFloats.get(j);
                    if (event[0] >= pointFloat[0] && event[0] <= pointFloat[2] && event[1] >= pointFloat[1] && event[1] <= pointFloat[3]) {
                        return pointGoodsEntity.getDetectItemEntities().get(j);
                    }
                }
            }
        }
        return null;
    }

    public static void drawMainBitmap(Context context, Bitmap bitmap, List<GoodsVo> goodsVos) {
        if (sizeDP1 == 0) {
            sizeDP1 = context.getResources().getDimensionPixelSize(R.dimen.dp_1);
        }
        if (paint == null) {
            paint = new Paint();
            paint.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.dp_2));
            paint.setStyle(Paint.Style.STROKE);
            path = new Path();
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setTextSize(20);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        if (rectPaint == null) {
            rectPaint = new Paint();
            rectPaint.setStyle(Paint.Style.FILL);
        }
        canvas = new Canvas(bitmap);
        pointGoodsEntities = new ArrayList<>();
        Map<String, Integer> goodsColorMap = RecognizeColorUtil.buildGoodsColorMap(goodsVos, false);
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

                    float[] originalPoints = FileUtil.invertPoints(detectItemEntity, bitmap.getWidth(), bitmap.getHeight());
                    if (originalPoints == null || originalPoints.length < 4) {
                        continue;
                    }
                    floats.add(originalPoints);
                    // 计算包围盒（OBB 为 4 顶点 8 值，AABB 为 4 值，统一对全部顶点取 min/max）
                    float minX = Float.MAX_VALUE;
                    float maxX = Float.MIN_VALUE;
                    float maxY = Float.MIN_VALUE;
                    for (int i = 0; i < originalPoints.length; i += 2) {
                        minX = Math.min(minX, originalPoints[i]);
                        maxX = Math.max(maxX, originalPoints[i]);
                        maxY = Math.max(maxY, originalPoints[i + 1]);
                    }

                    path.reset();
                    for (int i = 0; i < originalPoints.length; i += 2) {
                        if (i == 0) {
                            path.moveTo(originalPoints[i], originalPoints[i + 1]);
                        } else {
                            path.lineTo(originalPoints[i], originalPoints[i + 1]);
                        }
                    }
                    path.close();
                    canvas.drawPath(path, paint);

                    // 计算矩形的位置和大小，将矩形置于方框上方
                    float rectLeft = minX - sizeDP1; // 矩形左边界为方框最小x坐标
                    float rectTop = maxY + RECT_HEIGHT; // 矩形在方框上方
                    float rectRight = maxX + sizeDP1; // 矩形右边界为方框最大x坐标
                    // 矩形下边界为方框最小y坐标

                    // 绘制矩形
                    canvas.drawRect(rectLeft, rectTop, rectRight, maxY, rectPaint);

                    // 计算文字的位置
                    float textX = (rectLeft + rectRight) / 2;
                    float textY = rectTop - (RECT_HEIGHT + textPaint.getTextSize()) / 2;

                    // 绘制文字
                    String pluName = goods.getName();
                    if (pluName != null) {
                        // 保存当前画布状态
                        int saveCount = canvas.save();
                        // 反向旋转画布
                        canvas.rotate(-180, textX, textY);
                        if (pluName.length() > 6) {
                            canvas.drawText(String.format("%s...", pluName.substring(0, 6)), textX, textY, textPaint);
                        } else {
                            canvas.drawText(pluName, textX, textY, textPaint);
                        }
                        // 恢复画布状态
                        canvas.restoreToCount(saveCount);

                    }
                }
                pointGoodsEntity.setPointFloats(floats);
                pointGoodsEntities.add(pointGoodsEntity);
            }
        }
    }

    public static void drawStudyBitmap(Context context, Bitmap bitmap, List<DetectBox> outDetectionEntities) {
        if (studyPaint == null) {
            studyPaint = new Paint();
            studyPaint.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.dp_2));
            studyPaint.setStyle(Paint.Style.STROKE);
            studyPaint.setColor(Color.WHITE);
            studyPath = new Path();
        }
        canvas = new Canvas(bitmap);
        if (outDetectionEntities != null && !outDetectionEntities.isEmpty()) {
            WeakReference<ArrayList<DetectBox>> detectionEntities = new WeakReference<>(new ArrayList<>(outDetectionEntities));
            for (DetectBox detectItemEntity : detectionEntities.get()) {
                float[] originalPoints = FileUtil.invertPoints(detectItemEntity, bitmap.getWidth(), bitmap.getHeight());
                if (originalPoints == null || originalPoints.length < 4) {
                    continue;
                }
                studyPath.reset();
                for (int i = 0; i < originalPoints.length; i += 2) {
                    if (i == 0) {
                        studyPath.moveTo(originalPoints[i], originalPoints[i + 1]);
                    } else {
                        studyPath.lineTo(originalPoints[i], originalPoints[i + 1]);
                    }
                }
                studyPath.close();
                canvas.drawPath(studyPath, studyPaint);

                // 保存当前画布状态
                int saveCount = canvas.save();
                // 恢复画布状态
                canvas.restoreToCount(saveCount);
            }

        }
    }

    public static void scaleImage(View view, float scale) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", scale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", scale);
        scaleX.setDuration(50);
        scaleY.setDuration(50);
        scaleX.start();
        scaleY.start();
    }
}
