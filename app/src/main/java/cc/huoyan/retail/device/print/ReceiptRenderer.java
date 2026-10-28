package cc.huoyan.retail.device.print;

import static cc.huoyan.retail.data.SPManager.SP_SHOP_NAME;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import cc.huoyan.retail.R;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.checkout.domain.WeightedSaleResult;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.common.LocalePreferenceUtil;
import cc.huoyan.retail.data.SPManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/** 小票内容与位图排版；保留原有 320 点宽、字号、行距和条码，不依赖设备协议。 */
final class ReceiptRenderer {
    /** 创建无共享可变状态的排版器；仅在打印后台任务使用。 */
    ReceiptRenderer() { }

    /** 生成原有小票头部，返回位图由调用方回收。 */
    Bitmap header(WeightedSaleResult result) {
        return PrintUtil.print(buildReceiptHeader(result), ReceiptPaper.CONTENT_WIDTH);
    }

    /** 生成原有小票尾部并释放临时条码位图，返回位图由调用方回收。 */
    Bitmap footer(WeightedSaleResult result) {
        ArrayList<Line> lines = buildReceiptFooter(result, buildWeightedBarcode(result));
        try {
            return trimBottomWhitespace(PrintUtil.print(lines, ReceiptPaper.CONTENT_WIDTH));
        } finally {
            for (Line line : lines) {
                for (Unit unit : line.getUnits()) {
                    if (unit.getBitmap() != null) unit.getBitmap().recycle();
                }
            }
        }
    }

    /** 仅去除末尾无打印像素的白行，保留全部正文；物理送纸距离由切刀定位决定。 */
    private Bitmap trimBottomWhitespace(Bitmap bitmap) {
        int[] row = new int[bitmap.getWidth()];
        for (int y = bitmap.getHeight() - 1; y >= 0; y--) {
            bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int pixel : row) {
                int luminance = (299 * ((pixel >> 16) & 255) + 587 * ((pixel >> 8) & 255)
                        + 114 * (pixel & 255)) / 1000;
                if (luminance < 160) {
                    if (y == bitmap.getHeight() - 1) return bitmap;
                    Bitmap trimmed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), y + 1);
                    bitmap.recycle();
                    return trimmed;
                }
            }
        }
        return bitmap;
    }

    /** 沿用现有店名、时间、重量、金额和商品行内容。 */
    private ArrayList<Line> buildReceiptHeader(WeightedSaleResult result) {
        ArrayList<Line> lines = new ArrayList<>();
        String shopName = SPManager.getInstance().getString(SP_SHOP_NAME, LocalizedText.get(R.string.default_shop_name));
        lines.add(new Line().addUnit(new Unit(shopName, 22, Unit.EALIGN.CENTER, false)));
        lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_title), 18, Unit.EALIGN.CENTER, false)));
        lines.add(new Line().addUnit(new Unit("--------------------------------", 18)));
        lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_time, java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                LocalePreferenceUtil.getCurrentLocale(RetailApplication.getInstance())).format(new java.util.Date())), 18)));
        if (result.getUnitPriceCent() > 0) {
            lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_weight, formatWeight(result.getWeightKg())), 20, false)));
            lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_price, formatMoney(result.getUnitPriceCent())), 20, false)));
        }
        lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_total, formatMoney(result.getTotalAmountCent())), 20, false)));
        lines.add(new Line().addUnit(new Unit("--------------------------------", 18)));
        lines.add(new Line()
                .addUnit(new Unit(LocalizedText.get(R.string.receipt_item_header), 18, Unit.EALIGN.LEFT, false))
                .addUnit(new Unit(LocalizedText.get(R.string.receipt_quantity), 18, Unit.EALIGN.RIGHT, false)));
        long weightedKinds = result.getItems().stream()
                .filter(item -> item.getMode() == cc.huoyan.retail.pricing.PricingMode.WEIGHT).count();
        for (WeightedSaleResult.Item item : result.getItems()) {
            String quantity = item.getMode() == cc.huoyan.retail.pricing.PricingMode.COUNT
                    ? LocalizedText.get(R.string.receipt_pieces, item.getDetectedCount())
                    : weightedKinds == 1 ? formatWeight(result.getWeightKg()) + " kg"
                    : LocalizedText.get(R.string.receipt_shared_weight);
            android.text.TextPaint rowPaint = new android.text.TextPaint();
            rowPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
            rowPaint.setTextSize(18);
            String name = android.text.TextUtils.ellipsize(formatReceiptItemName(item), rowPaint,
                    Math.max(0, ReceiptPaper.CONTENT_WIDTH - rowPaint.measureText(quantity) - 12),
                    android.text.TextUtils.TruncateAt.END).toString();
            lines.add(new Line()
                    .addUnit(new Unit(name, 18, Unit.EALIGN.LEFT))
                    .addUnit(new Unit(quantity, 18, Unit.EALIGN.RIGHT, false)));
        }
        return lines;
    }

    /** 沿用现有汇总、EAN-13 条码及结束语。 */
    private ArrayList<Line> buildReceiptFooter(WeightedSaleResult result, String barcode) {
        ArrayList<Line> lines = new ArrayList<>();
        lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_summary, result.getItemKindCount(), result.getDetectedPieceCount()), 18, Unit.EALIGN.CENTER)));
        lines.add(new Line().addUnit(new Unit("--------------------------------", 18)));
        if (barcode != null) {
            try {
                // 按正文可用宽度重新生成条码并保留静区，禁止将 350 点图像画到 320 点画布后裁切。
                lines.add(new Line().addUnit(new Unit(barcode, BarcodeFormat.EAN_13,
                        ReceiptPaper.CONTENT_WIDTH - 20, 100, Unit.EALIGN.CENTER)));
            } catch (WriterException e) {
                throw new IllegalStateException(LocalizedText.get(R.string.printer_error_barcode_failed), e);
            }
            lines.add(new Line().addUnit(new Unit(barcode, 18, Unit.EALIGN.CENTER)));
        }
        lines.add(new Line().addUnit(new Unit(LocalizedText.get(R.string.receipt_thanks), 18, Unit.EALIGN.CENTER, false)));
        return lines;
    }

    /** 保持原有 PLU 和商品名称截断规则。 */
    private String formatReceiptItemName(WeightedSaleResult.Item item) {
        String plu = item.getPlu() == null ? "" : item.getPlu().trim();
        String name = item.getName() == null ? "" : item.getName().trim();
        if (name.isEmpty()) {
            name = LocalizedText.get(R.string.goods_unnamed);
        }
        if (plu.length() > 8) {
            plu = plu.substring(0, 8);
        }
        if (name.length() > 10) {
            name = name.substring(0, 9) + "…";
        }
        return plu.isEmpty() ? name : plu + " " + name;
    }

    /** 沿用原有金额条码规则。 */
    private String buildWeightedBarcode(WeightedSaleResult result) {
        return ReceiptBarcode.build(result.getItems().get(0).getPlu(), result.getTotalAmountCent());
    }

    /** 将分转换为两位小数金额，禁止浮点金额。 */
    private String formatMoney(long cent) {
        return BigDecimal.valueOf(cent).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 将千克重量格式化为三位小数。 */
    private String formatWeight(BigDecimal weightKg) {
        return weightKg.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }
}
