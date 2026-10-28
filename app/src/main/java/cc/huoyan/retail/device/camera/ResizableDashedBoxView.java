package cc.huoyan.retail.device.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import cc.huoyan.retail.data.SPManager;

import java.util.ArrayList;
import java.util.List;

public class ResizableDashedBoxView extends View {
    private static final int MAX_POINTS = 4;
    private static final float TOUCH_TOLERANCE = 20;
    private List<PointF> points = new ArrayList<>();
    private Paint paint;
    private Path path;
    private int selectedPointIndex = -1;
    private RecognizeAreaEntity recognizeArea;

    private int originalImageWidth;
    private int originalImageHeight;
    private boolean isSwitch = false;

    public ResizableDashedBoxView(Context context) {
        super(context);
        init();
    }

    public ResizableDashedBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ResizableDashedBoxView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /** 主线程收到真实帧后同步尺寸，避免宽高尚未就绪时保存错误坐标。 */
    public void setOriginalImageSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        originalImageWidth = width;
        originalImageHeight = height;
        if (isSwitch) {
            SPManager.getInstance().putRecognizeArea(SPManager.createDefaultRecognizeArea(width, height));
            isSwitch = false;
        }
        initializePoints();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.parseColor("#3C46F2"));
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
        path = new Path();
//        initializePoints();
    }

    private void initializePoints() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        recognizeArea = SPManager.getInstance().getRecognizeArea(originalImageWidth, originalImageHeight);
        points = convertPointsFromOriginalToImageView(recognizeArea.getPoints());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initializePoints();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int width = getWidth();
        int height = getHeight();
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointIndex = findTouchedPoint(x, y);
                if (selectedPointIndex == -1) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (selectedPointIndex != -1) {
                    if (x >= 0 && x <= width && y >= 0 && y <= height) {
                        switch (selectedPointIndex) {
                            case 0:
                                points.get(0).set(x, y);
                                points.get(1).set(points.get(1).x, y);
                                points.get(3).set(x, points.get(3).y);
                                break;
                            case 1:
                                points.get(1).set(x, y);
                                points.get(0).set(points.get(0).x, y);
                                points.get(2).set(x, points.get(2).y);
                                break;
                            case 2:
                                points.get(2).set(x, y);
                                points.get(1).set(x, points.get(1).y);
                                points.get(3).set(points.get(3).x, y);
                                break;
                            case 3:
                                points.get(3).set(x, y);
                                points.get(0).set(x, points.get(0).y);
                                points.get(2).set(points.get(2).x, y);
                                break;
                        }
                        invalidate();
                    }
                }
                // 点击框内非角点位置不整体移动识别区域（用户 2026-08-25 指示：防自动化/误触改动识别区域），
                // 仅保留四个角点拖动缩放
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedPointIndex = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return true;
    }

    private int findTouchedPoint(float x, float y) {
        for (int i = 0; i < points.size(); i++) {
            PointF point = points.get(i);
            if (Math.abs(x - point.x) <= TOUCH_TOLERANCE && Math.abs(y - point.y) <= TOUCH_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try {
            if (points.size() == MAX_POINTS) {
                if (originalImageWidth != 0 && originalImageHeight != 0) {
                    recognizeArea.setHeight(originalImageHeight);
                    recognizeArea.setWidth(originalImageWidth);
                    recognizeArea.setPoints(convertPointsFromImageViewToOriginal(points));
                    SPManager.getInstance().putRecognizeArea(recognizeArea);
                }
                path.reset();
                path.moveTo(points.get(0).x, points.get(0).y);
                for (int i = 1; i < points.size(); i++) {
                    path.lineTo(points.get(i).x, points.get(i).y);
                }
                path.close();
                canvas.drawPath(path, paint);
                // 绘制四个点
                paint.setStyle(Paint.Style.FILL);
                for (PointF point : points) {
                    canvas.drawCircle(point.x, point.y, 20, paint);
                }
                paint.setStyle(Paint.Style.STROKE);
            }
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "onDraw: " + e.getMessage());
        }
    }


    public List<PointF> convertPointsFromImageViewToOriginal(List<PointF> imageViewPoints) {
        int imageViewWidth = getWidth();
        int imageViewHeight = getHeight();
        float scaleX = (float) imageViewWidth / originalImageWidth;
        float scaleY = (float) imageViewHeight / originalImageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (imageViewWidth - originalImageWidth * scale) / 2;
        float offsetY = (imageViewHeight - originalImageHeight * scale) / 2;

        List<PointF> originalPoints = new ArrayList<>();
        for (int i = 0; i < imageViewPoints.size(); i++) {
            PointF imageViewPoint = imageViewPoints.get(i);
            float originalX = (imageViewPoint.x - offsetX) / scale;
            float originalY = (imageViewPoint.y - offsetY) / scale;
            originalPoints.add(new PointF(originalX, originalY));
        }
        return originalPoints;
    }

    public List<PointF> convertPointsFromOriginalToImageView(List<PointF> originalPoints) {
        int imageViewWidth = getWidth();
        int imageViewHeight = getHeight();
        float scaleX = (float) imageViewWidth / originalImageWidth;
        float scaleY = (float) imageViewHeight / originalImageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (imageViewWidth - originalImageWidth * scale) / 2;
        float offsetY = (imageViewHeight - originalImageHeight * scale) / 2;

        List<PointF> imageViewPoints = new ArrayList<>();
        for (int i = 0; i < originalPoints.size(); i++) {
            PointF originalPoint = originalPoints.get(i);
            float imageViewX = originalPoint.x * scale + offsetX;
            float imageViewY = originalPoint.y * scale + offsetY;
            imageViewPoints.add(new PointF(imageViewX, imageViewY));
        }
        return imageViewPoints;
    }

    public void setSwitch(boolean b) {
        this.isSwitch = b;
    }
}
