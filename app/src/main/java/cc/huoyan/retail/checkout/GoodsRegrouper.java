package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 只重组被改品的行；整行改品携带手工数量，拆出部分框时每框转移一件。 */
final class GoodsRegrouper {
    static List<GoodsVo> replace(List<GoodsVo> rows, Set<RecognitionSelection> selected, GoodsEntity target) {
        List<GoodsVo> result = new ArrayList<>();
        List<RecognitionSelection> moved = new ArrayList<>();
        GoodsVo targetRow = null;
        int movedCount = 0;
        for (GoodsVo row : rows) {
            if (row.getGoods() != null && target.getPlu().equals(row.getGoods().getPlu())) {
                targetRow = row;
                result.add(row);
                continue;
            }
            List<RecognitionSelection> items = row.getRecognitionSelection();
            List<RecognitionSelection> remaining = new ArrayList<>();
            int transferred = 0;
            if (items != null) for (RecognitionSelection item : items) {
                if (selected.contains(item)) {
                    moved.add(item);
                    transferred++;
                } else remaining.add(item);
            }
            if (transferred == 0) {
                result.add(row);
            } else if (remaining.isEmpty()) {
                movedCount += count(row);
            } else {
                // 无法为拆分后的两行分配正数数量时，要求先调整数量，不能凭空增加件数。
                if (count(row) <= transferred) throw new IllegalArgumentException("insufficient count to split");
                int transferredCount = transferred;
                movedCount += transferredCount;
                result.add(new GoodsVo(count(row) - transferredCount, row.getAmount(), row.getGoods(), remaining));
            }
        }
        if (moved.isEmpty()) return result;
        for (RecognitionSelection item : moved) item.select(target.getPlu());
        if (targetRow != null) {
            List<RecognitionSelection> items = new ArrayList<>(targetRow.getRecognitionSelection());
            items.addAll(moved);
            result.set(result.indexOf(targetRow), new GoodsVo(count(targetRow) + movedCount,
                    target.getPrice(), target, items));
        } else {
            result.add(new GoodsVo(movedCount, target.getPrice(), target, moved));
        }
        return result;
    }

    private static int count(GoodsVo row) {
        return row.getCount() == null ? 1 : Math.max(1, row.getCount());
    }
}
