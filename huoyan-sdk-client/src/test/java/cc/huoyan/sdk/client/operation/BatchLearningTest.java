package cc.huoyan.sdk.client.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cc.huoyan.sdk.client.model.ItemLearnBatch;
import cc.huoyan.sdk.client.model.ItemLearnBox;

/**
 * 批量学习回执过滤单测：{@code BatchLearning.learnedBoxesOf} 取
 * 学习成功框（code=0 且 uid&gt;0），失败框（code≠0 该框失败 / uid=0 裁片无检测
 * 条目）跳过不阻塞其他框——SDK 部分成功语义在业务侧的裁决；{@code boxDetailOf}
 * 为 [Biz] 打点明细。
 */
public class BatchLearningTest {

    private static ItemLearnBox box(long uid, int code) {
        return new ItemLearnBox(1, 2, 30, 40, 0.9, uid, code,
                code == 0 ? null : "失败原因");
    }

    private static ItemLearnBatch batch(int code, ItemLearnBox... boxes) {
        return new ItemLearnBatch(code, code == 0 ? null : "整体失败",
                new ArrayList<>(Arrays.asList(boxes)));
    }

    /** 重复成功 UID 只能生成一次小图与反馈条目。 */
    @Test public void repeatedUidKeepsFirstSuccessfulBox() {
        ItemLearnBox first = box(101L, 0);
        List<ItemLearnBox> learned = BatchLearning.learnedBoxesOf(
                batch(0, first, box(102L, 0), box(101L, 0)));
        assertEquals(2, learned.size());
        assertEquals(first, learned.get(0));
        assertEquals(102L, learned.get(1).getUid());
    }

    @Test
    public void 成功框过滤_失败框与uid0框跳过_保持框序() {
        ItemLearnBatch result = batch(0,
                box(101L, 0), box(0L, -5), box(0L, 0), box(102L, 0), null);

        List<ItemLearnBox> learned = BatchLearning.learnedBoxesOf(result);

        assertEquals(2, learned.size());
        assertEquals(101L, learned.get(0).getUid());
        assertEquals(102L, learned.get(1).getUid());
    }

    @Test
    public void 全框失败_返回空_走既有失败提示路径() {
        ItemLearnBatch result = batch(0, box(0L, -5), box(0L, -7));
        assertTrue(BatchLearning.learnedBoxesOf(result).isEmpty());
    }

    @Test
    public void boxes空_无检测框正常空结果() {
        assertTrue(BatchLearning.learnedBoxesOf(batch(0)).isEmpty());
    }

    @Test
    public void 整体失败与null回执_返回空() {
        assertTrue(BatchLearning.learnedBoxesOf(batch(-110, box(101L, 0))).isEmpty());
        assertTrue(BatchLearning.learnedBoxesOf(null).isEmpty());
    }

    @Test
    public void 单框场景_与原itemLearn链路等价() {
        List<ItemLearnBox> learned =
                BatchLearning.learnedBoxesOf(batch(0, box(101L, 0)));
        assertEquals(1, learned.size());
        assertEquals(101L, learned.get(0).getUid());
    }

    @Test
    public void 打点明细_成功框uid失败框错误码平铺() {
        String detail = BatchLearning.boxDetailOf(batch(0,
                box(101L, 0), box(0L, -5), box(102L, 0)));
        assertEquals("[0]uid=101 [1]fail(-5) [2]uid=102", detail);
        assertEquals("", BatchLearning.boxDetailOf(batch(0)));
        assertEquals("", BatchLearning.boxDetailOf(null));
    }
}
