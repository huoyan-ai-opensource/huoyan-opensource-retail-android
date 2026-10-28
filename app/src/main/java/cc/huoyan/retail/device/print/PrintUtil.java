package cc.huoyan.retail.device.print;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PrintUtil {

    public static Bitmap print(List<Line> listContent, int canvasWidth) {
        if (listContent.isEmpty()) {
            return null;
        }
        int totalHeight = 20;
        for (Line line : listContent) {
            totalHeight += lineHeight(line) + 4;
        }
        Bitmap bmp = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.MONOSPACE);

        int top = 10;
        for (Line line : listContent) {
            int rowHeight = lineHeight(line);
            for (Unit unit : line.getUnits()) {
                Bitmap bitmap = unit.getBitmap();
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, bitmapLeft(bitmap, unit.getAlign(), canvasWidth),
                            top + (rowHeight - bitmap.getHeight()) / 2f, paint);
                    continue;
                }
                if (TextUtils.isEmpty(unit.getContent())) {
                    continue;
                }
                paint.setTextSize(Math.max(1, unit.getFontSize()));
                paint.setFakeBoldText(unit.isBold());
                paint.setTextAlign(toPaintAlign(unit.getAlign()));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float baseline = top + (rowHeight - metrics.bottom - metrics.top) / 2f;
                float x = Unit.EALIGN.RIGHT.equals(unit.getAlign()) ? canvasWidth
                        : Unit.EALIGN.CENTER.equals(unit.getAlign()) ? canvasWidth / 2f : 0f;
                canvas.drawText(unit.getContent(), x, baseline, paint);
            }
            top += rowHeight + 4;
        }
        paint.setFakeBoldText(false);
        return bmp;
    }

    private static int lineHeight(Line line) {
        int height = 18;
        for (Unit unit : line.getUnits()) {
            height = Math.max(height, unit.getFontSize() + 8);
            if (unit.getBitmap() != null) {
                height = Math.max(height, unit.getBitmap().getHeight());
            }
        }
        return height;
    }

    private static Paint.Align toPaintAlign(Unit.EALIGN align) {
        if (Unit.EALIGN.CENTER.equals(align)) {
            return Paint.Align.CENTER;
        }
        return Unit.EALIGN.RIGHT.equals(align) ? Paint.Align.RIGHT : Paint.Align.LEFT;
    }

    private static float bitmapLeft(Bitmap bitmap, Unit.EALIGN align, int canvasWidth) {
        if (Unit.EALIGN.CENTER.equals(align)) {
            return (canvasWidth - bitmap.getWidth()) / 2f;
        }
        return Unit.EALIGN.RIGHT.equals(align) ? canvasWidth - bitmap.getWidth() : 0f;
    }

    public static Bitmap print58(@NotNull ArrayList<Line> list) {
        return print(list, 320);
    }

    @Nullable
    public static Bitmap print80(@NotNull ArrayList<Line> list) {
        return print(list, 576);
    }

    @Nullable
    public static Bitmap print(@NotNull ArrayList<Line> list, boolean is58) {
        return print(list, is58 ? 384 : 576);
    }

    public static String ToDBC(String input) {
        char[] c = input.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == 12288) {
                c[i] = (char) 32;
                continue;
            }
            if (c[i] > 65280 && c[i] < 65375)
                c[i] = (char) (c[i] - 65248);
        }
        return new String(c);
    }

}
