package cc.huoyan.sdk.client.operation;
import java.util.*;
import cc.huoyan.sdk.client.model.*;

/** 批量学习结果解释；零 UID 是正常空结果，不是新增样本。 */
public final class BatchLearning {
    /** 静态结果工具。 */
    private BatchLearning() {}
    /** 按回执顺序返回成功且 UID 非零的唯一样本。 */
    public static List<ItemLearnBox> learnedBoxesOf(ItemLearnBatch batch) {
        List<ItemLearnBox> learned = new ArrayList<>();
        if (batch == null || batch.getCode() != 0 || batch.getBoxes() == null) {
            return learned;
        }
        java.util.Set<Long> seenUids = new java.util.HashSet<>();
        for (ItemLearnBox box : batch.getBoxes()) {
            if (box != null && box.getCode() == 0 && box.getUid() > 0 && seenUids.add(box.getUid())) {
                learned.add(box);
            }
        }
        return learned;
    }

    /** 格式化每框诊断，不影响成功/失败判定。 */
    public static String boxDetailOf(ItemLearnBatch batch) {
        if (batch == null || batch.getBoxes() == null || batch.getBoxes().isEmpty()) {
            return "";
        }
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < batch.getBoxes().size(); i++) {
            ItemLearnBox box = batch.getBoxes().get(i);
            if (i > 0) {
                detail.append(' ');
            }
            detail.append('[').append(i).append(']');
            if (box == null) {
                detail.append("null");
            } else if (box.getCode() == 0) {
                detail.append("uid=").append(box.getUid());
            } else {
                detail.append("fail(").append(box.getCode()).append(')');
            }
        }
        return detail.toString();
    }

}
