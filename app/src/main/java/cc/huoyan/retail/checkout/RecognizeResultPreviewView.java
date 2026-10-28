package cc.huoyan.retail.checkout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.common.FileUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecognizeResultPreviewView extends View {
    private static final float ROW_TOP_TOLERANCE_FACTOR = 0.45f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint centerNumberBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerNumberTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final List<NumberHit> numberHits = new ArrayList<>();

    private Bitmap previewBitmap;
    private int previewWidth;
    private int previewHeight;
    private List<GoodsVo> goodsVos = new ArrayList<>();
    private Map<String, Integer> goodsColorMap = new LinkedHashMap<>();
    private Set<RecognitionSelection> selectedDetectItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private OnGoodsBoxClickListener listener;
    private OnDetectBoxClickListener detectBoxClickListener;
    private int centerNumberPadding;

    public interface OnGoodsBoxClickListener {
        void onGoodsBoxClick(GoodsVo goodsVo);
    }

    public interface OnDetectBoxClickListener {
        void onDetectBoxClick(GoodsVo goodsVo, RecognitionSelection detectItem);
    }

    public RecognizeResultPreviewView(Context context) {
        this(context, null);
    }

    public RecognizeResultPreviewView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecognizeResultPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        centerNumberPadding = getResources().getDimensionPixelSize(R.dimen.dp_3);
        centerNumberBgPaint.setColor(RecognizeColorUtil.UNSELECTED_LEADING_COLOR);
        centerNumberBgPaint.setStyle(Paint.Style.FILL);
        centerNumberTextPaint.setColor(Color.WHITE);
        centerNumberTextPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.sp_8));
        centerNumberTextPaint.setTextAlign(Paint.Align.CENTER);
        centerNumberTextPaint.setFakeBoldText(true);
        setClickable(true);
    }

    public void setOnGoodsBoxClickListener(OnGoodsBoxClickListener listener) {
        this.listener = listener;
    }

    public void setOnDetectBoxClickListener(OnDetectBoxClickListener detectBoxClickListener) {
        this.detectBoxClickListener = detectBoxClickListener;
    }

    public void setPreviewData(Bitmap previewBitmap, List<GoodsVo> goodsVos) {
        this.previewBitmap = previewBitmap;
        this.previewWidth = previewBitmap == null || previewBitmap.isRecycled() ? 0 : previewBitmap.getWidth();
        this.previewHeight = previewBitmap == null || previewBitmap.isRecycled() ? 0 : previewBitmap.getHeight();
        this.goodsVos = goodsVos == null ? new ArrayList<>() : goodsVos;
        invalidate();
    }

    public void setPreviewData(int previewWidth, int previewHeight, List<GoodsVo> goodsVos) {
        this.previewBitmap = null;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.goodsVos = goodsVos == null ? new ArrayList<>() : goodsVos;
        invalidate();
    }

    public void setPreviewState(Map<String, Integer> goodsColorMap,
                                Set<String> ignoredHiddenGoodsPlus,
                                Set<RecognitionSelection> selectedDetectItems) {
        this.goodsColorMap = goodsColorMap == null ? new LinkedHashMap<>() : goodsColorMap;
        this.selectedDetectItems = selectedDetectItems == null
                ? Collections.newSetFromMap(new IdentityHashMap<>())
                : selectedDetectItems;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        numberHits.clear();
        Bitmap bitmap = previewBitmap;
        if ((bitmap == null || bitmap.isRecycled()) && (previewWidth <= 0 || previewHeight <= 0)) {
            return;
        }
        updateImageRect(bitmap);
        if (imageRect.isEmpty()) {
            return;
        }
        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
        }
        drawGoodsBoxes(canvas);
    }

    private void updateImageRect(Bitmap bitmap) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int imageWidth = bitmap == null || bitmap.isRecycled() ? previewWidth : bitmap.getWidth();
        int imageHeight = bitmap == null || bitmap.isRecycled() ? previewHeight : bitmap.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            imageRect.setEmpty();
            return;
        }
        float scale = cc.huoyan.retail.device.camera.PreviewScale.compute(
                imageWidth, imageHeight, viewWidth, viewHeight,
                getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
        float displayWidth = imageWidth * scale;
        float displayHeight = imageHeight * scale;
        float left = (viewWidth - displayWidth) / 2f;
        float top = (viewHeight - displayHeight) / 2f;
        imageRect.set(left, top, left + displayWidth, top + displayHeight);
    }

    private void drawGoodsBoxes(Canvas canvas) {
        List<PreviewItem> previewItems = buildPreviewItems();
        assignGroupedDisplayIndexes(previewItems);
        for (PreviewItem previewItem : previewItems) {
            int numberColor = RecognizeColorUtil.UNSELECTED_LEADING_COLOR;
            if (selectedDetectItems.contains(previewItem.detectItem)) {
                numberColor = getBoxColor(previewItem.goodsVo);
            }
            NumberCircle numberCircle = drawCenterNumber(canvas, previewItem.rect, previewItem.displayIndex, numberColor);
            numberHits.add(new NumberHit(numberCircle,
                    buildSingleItemGoodsVo(previewItem.goodsVo, previewItem.detectItem),
                    previewItem.detectItem));
        }
    }

    /**
     * 绘制无匹配检测项已由虚拟 GoodsVo 显示链路取代（用户 2026-08-24 确认），
     * 无匹配商品走 goodsVos 正常绘制（彩色框 + 中心编号）。
     */

    private List<PreviewItem> buildPreviewItems() {
        List<PreviewItem> previewItems = new ArrayList<>();
        for (GoodsVo goodsVo : goodsVos) {
            if (goodsVo == null || goodsVo.getRecognitionSelection() == null || goodsVo.getGoods() == null) {
                continue;
            }
            for (RecognitionSelection detectItem : goodsVo.getRecognitionSelection()) {
                RectF originalRect = buildOriginalRect(detectItem);
                if (originalRect == null || originalRect.isEmpty()) {
                    continue;
                }
                RectF displayRect = mapOriginalRectToDisplayRect(originalRect);
                previewItems.add(new PreviewItem(originalRect, displayRect, goodsVo, detectItem));
            }
        }
        return previewItems;
    }

    private void assignGroupedDisplayIndexes(List<PreviewItem> previewItems) {
        sortPreviewItemsByOriginalImagePosition(previewItems);
        Map<String, List<PreviewItem>> itemsByPlu = new LinkedHashMap<>();
        for (PreviewItem previewItem : previewItems) {
            String plu = getGoodsPlu(previewItem.goodsVo);
            if (plu == null) {
                continue;
            }
            List<PreviewItem> items = itemsByPlu.get(plu);
            if (items == null) {
                items = new ArrayList<>();
                itemsByPlu.put(plu, items);
            }
            items.add(previewItem);
        }

        List<GoodsPreviewGroup> goodsGroups = new ArrayList<>();
        int goodsOrder = 0;
        for (GoodsVo goodsVo : goodsVos) {
            String plu = getGoodsPlu(goodsVo);
            if (plu == null) {
                continue;
            }
            List<PreviewItem> goodsItems = itemsByPlu.remove(plu);
            if (goodsItems == null || goodsItems.isEmpty()) {
                continue;
            }
            sortPreviewItemsByOriginalImagePosition(goodsItems);
            goodsGroups.add(new GoodsPreviewGroup(goodsVo, goodsItems, goodsOrder++));
        }
        goodsGroups.sort((left, right) -> {
            int countResult = Integer.compare(getValidGoodsCount(right.goodsVo), getValidGoodsCount(left.goodsVo));
            if (countResult != 0) {
                return countResult;
            }
            int positionResult = comparePreviewItemPosition(left.getFirstItem(), right.getFirstItem());
            if (positionResult != 0) {
                return positionResult;
            }
            return Integer.compare(left.originalOrder, right.originalOrder);
        });

        int displayIndex = 1;
        List<PreviewItem> groupedItems = new ArrayList<>();
        for (GoodsPreviewGroup goodsGroup : goodsGroups) {
            displayIndex = appendIndexedPreviewItems(groupedItems, goodsGroup.items, displayIndex);
        }
        for (List<PreviewItem> remainingItems : itemsByPlu.values()) {
            sortPreviewItemsByOriginalImagePosition(remainingItems);
            displayIndex = appendIndexedPreviewItems(groupedItems, remainingItems, displayIndex);
        }
        previewItems.clear();
        previewItems.addAll(groupedItems);
    }

    private int appendIndexedPreviewItems(List<PreviewItem> targetItems, List<PreviewItem> sourceItems, int displayIndex) {
        if (targetItems == null || sourceItems == null) {
            return displayIndex;
        }
        for (PreviewItem previewItem : sourceItems) {
            previewItem.displayIndex = displayIndex++;
            targetItems.add(previewItem);
        }
        return displayIndex;
    }

    private int comparePreviewItemPosition(PreviewItem left, PreviewItem right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int topResult = Float.compare(left.originalRect.top, right.originalRect.top);
        if (topResult != 0) {
            return topResult;
        }
        return Float.compare(left.originalRect.left, right.originalRect.left);
    }

    private String getGoodsPlu(GoodsVo goodsVo) {
        if (goodsVo == null || goodsVo.getGoods() == null || goodsVo.getGoods().getPlu() == null) {
            return null;
        }
        return goodsVo.getGoods().getPlu();
    }

    private int getValidGoodsCount(GoodsVo goodsVo) {
        if (goodsVo == null || goodsVo.getCount() == null || goodsVo.getCount() < 1) {
            return 1;
        }
        return goodsVo.getCount();
    }

    private void sortPreviewItemsByOriginalImagePosition(List<PreviewItem> previewItems) {
        float rowTopTolerance = Math.max(1f, getMedianOriginalHeight(previewItems) * ROW_TOP_TOLERANCE_FACTOR);
        previewItems.sort((left, right) -> {
            int leftResult = Float.compare(left.originalRect.left, right.originalRect.left);
            if (leftResult != 0) {
                return leftResult;
            }
            return Float.compare(left.originalRect.top, right.originalRect.top);
        });
        List<List<PreviewItem>> rows = new ArrayList<>();
        for (PreviewItem previewItem : previewItems) {
            List<PreviewItem> targetRow = null;
            for (List<PreviewItem> row : rows) {
                if (isSameOriginalImageRow(row, previewItem, rowTopTolerance)) {
                    targetRow = row;
                    break;
                }
            }
            if (targetRow == null) {
                targetRow = new ArrayList<>();
                rows.add(targetRow);
            }
            targetRow.add(previewItem);
        }
        previewItems.clear();
        rows.sort((left, right) -> Float.compare(getRowTop(left), getRowTop(right)));
        for (List<PreviewItem> row : rows) {
            row.sort((left, right) -> Float.compare(left.originalRect.left, right.originalRect.left));
            previewItems.addAll(row);
        }
    }

    private boolean isSameOriginalImageRow(List<PreviewItem> row, PreviewItem previewItem, float rowTopTolerance) {
        if (row == null || row.isEmpty() || previewItem == null) {
            return false;
        }
        return Math.abs(previewItem.originalRect.top - row.get(0).originalRect.top) <= rowTopTolerance;
    }

    private float getRowTop(List<PreviewItem> row) {
        float rowTop = Float.MAX_VALUE;
        for (PreviewItem previewItem : row) {
            rowTop = Math.min(rowTop, previewItem.originalRect.top);
        }
        return rowTop == Float.MAX_VALUE ? 0f : rowTop;
    }

    private float getMedianOriginalHeight(List<PreviewItem> previewItems) {
        if (previewItems == null || previewItems.isEmpty()) {
            return 0f;
        }
        List<Float> heights = new ArrayList<>();
        for (PreviewItem previewItem : previewItems) {
            if (previewItem != null && previewItem.originalRect.height() > 0) {
                heights.add(previewItem.originalRect.height());
            }
        }
        if (heights.isEmpty()) {
            return 0f;
        }
        Collections.sort(heights);
        int middleIndex = heights.size() / 2;
        if (heights.size() % 2 == 1) {
            return heights.get(middleIndex);
        }
        return (heights.get(middleIndex - 1) + heights.get(middleIndex)) / 2f;
    }

    private RectF buildOriginalRect(RecognitionSelection detectItem) {
        if (detectItem == null || imageRect.isEmpty()) {
            return null;
        }
        int imageWidth = getPreviewImageWidth();
        int imageHeight = getPreviewImageHeight();
        if (imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }
        if (detectItem == null || detectItem.getPoints() == null || detectItem.getPoints().length < 4) {
            // 无匹配条目/异常条目可能携带 null 或过短的 points，绘制前须检查长度以避免空指针异常
            return null;
        }
        float[] originalPoints = FileUtil.invertPoints(detectItem, imageWidth, imageHeight);
        if (originalPoints == null || originalPoints.length < 4) {
            return null;
        }
        // OBB 为 4 顶点 8 值，AABB 为 4 值，统一对全部顶点取 min/max
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;
        for (int i = 0; i < originalPoints.length; i += 2) {
            left = Math.min(left, originalPoints[i]);
            right = Math.max(right, originalPoints[i]);
            top = Math.min(top, originalPoints[i + 1]);
            bottom = Math.max(bottom, originalPoints[i + 1]);
        }
        return new RectF(left, top, right, bottom);
    }

    private RectF mapOriginalRectToDisplayRect(RectF originalRect) {
        int imageWidth = getPreviewImageWidth();
        int imageHeight = getPreviewImageHeight();
        float mappedLeft = imageRect.left + (originalRect.left / imageWidth) * imageRect.width();
        float mappedTop = imageRect.top + (originalRect.top / imageHeight) * imageRect.height();
        float mappedRight = imageRect.left + (originalRect.right / imageWidth) * imageRect.width();
        float mappedBottom = imageRect.top + (originalRect.bottom / imageHeight) * imageRect.height();
        return new RectF(mappedLeft, mappedTop, mappedRight, mappedBottom);
    }

    private int getPreviewImageWidth() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            return previewBitmap.getWidth();
        }
        return previewWidth;
    }

    private int getPreviewImageHeight() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            return previewBitmap.getHeight();
        }
        return previewHeight;
    }

    private GoodsVo buildSingleItemGoodsVo(GoodsVo source, RecognitionSelection detectItem) {
        List<RecognitionSelection> detectItems = new ArrayList<>();
        detectItems.add(detectItem);
        GoodsVo goodsVo = new GoodsVo(1, source.getAmount(), source.getGoods(), detectItems);
        goodsVo.setRecognizeBitmap(source.getRecognizeBitmap());
        return goodsVo;
    }

    private NumberCircle drawCenterNumber(Canvas canvas, RectF rect, int displayIndex, int backgroundColor) {
        String numberText = String.valueOf(displayIndex);
        Paint.FontMetrics fontMetrics = centerNumberTextPaint.getFontMetrics();
        float textWidth = centerNumberTextPaint.measureText(numberText);
        float textHeight = fontMetrics.descent - fontMetrics.ascent;
        float radius = Math.max(getResources().getDimensionPixelSize(R.dimen.dp_8),
                Math.max(textWidth, textHeight) / 2f + centerNumberPadding);
        centerNumberBgPaint.setColor(backgroundColor);
        float centerX = rect.centerX();
        float centerY = rect.centerY();
        canvas.drawCircle(centerX, centerY, radius, centerNumberBgPaint);
        float textY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        canvas.drawText(numberText, centerX, textY, centerNumberTextPaint);
        return new NumberCircle(centerX, centerY, radius);
    }

    private int getBoxColor(GoodsVo goodsVo) {
        if (goodsVo != null && goodsVo.getGoods() != null) {
            Integer color = goodsColorMap.get(goodsVo.getGoods().getPlu());
            if (color != null) {
                return color;
            }
        }
        return RecognizeColorUtil.DEFAULT_COLOR;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        for (int i = numberHits.size() - 1; i >= 0; i--) {
            NumberHit hit = numberHits.get(i);
            if (hit.numberCircle.contains(event.getX(), event.getY())) {
                if (detectBoxClickListener != null) {
                    detectBoxClickListener.onDetectBoxClick(hit.goodsVo, hit.detectItem);
                    return true;
                }
                if (listener != null) {
                    listener.onGoodsBoxClick(hit.goodsVo);
                }
                return true;
            }
        }
        return true;
    }

    private static class NumberCircle {
        final float centerX;
        final float centerY;
        final float radius;

        NumberCircle(float centerX, float centerY, float radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }

        boolean contains(float x, float y) {
            float dx = x - centerX;
            float dy = y - centerY;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private static class NumberHit {
        final NumberCircle numberCircle;
        final GoodsVo goodsVo;
        final RecognitionSelection detectItem;

        NumberHit(NumberCircle numberCircle, GoodsVo goodsVo, RecognitionSelection detectItem) {
            this.numberCircle = numberCircle;
            this.goodsVo = goodsVo;
            this.detectItem = detectItem;
        }
    }

    private static class PreviewItem {
        final RectF originalRect;
        final RectF rect;
        final GoodsVo goodsVo;
        final RecognitionSelection detectItem;
        int displayIndex;

        PreviewItem(RectF originalRect, RectF rect, GoodsVo goodsVo, RecognitionSelection detectItem) {
            this.originalRect = originalRect;
            this.rect = rect;
            this.goodsVo = goodsVo;
            this.detectItem = detectItem;
        }
    }

    private static class GoodsPreviewGroup {
        final GoodsVo goodsVo;
        final List<PreviewItem> items;
        final int originalOrder;

        GoodsPreviewGroup(GoodsVo goodsVo, List<PreviewItem> items, int originalOrder) {
            this.goodsVo = goodsVo;
            this.items = items;
            this.originalOrder = originalOrder;
        }

        PreviewItem getFirstItem() {
            if (items == null || items.isEmpty()) {
                return null;
            }
            return items.get(0);
        }
    }
}
