package cc.huoyan.retail.recognition;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** 验证事件调度无轮询、通知合并和停止重启时的串行请求边界。 */
public class RecognitionLoopTest {
    /** 模拟生产环境的唯一识别工作线程。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** 当前测试的识别循环，结束时统一释放。 */
    private RecognitionLoop loop;

    /** 释放工作线程，避免测试之间遗留调度。 */
    @After public void close() {
        if (loop != null) loop.close();
        executor.shutdownNow();
    }

    /** 请求在途到达多帧只续发最新一帧，绝不并发或排队旧帧。 */
    @Test public void notificationsDuringRequestReadLatestFrameWithoutQueueing() throws Exception {
        AtomicInteger cameraFrame = new AtomicInteger(1);
        List<Integer> sent = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch response = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        loop = new RecognitionLoop(current -> {
            sent.add(cameraFrame.get());
            if (sent.size() == 1) {
                entered.countDown();
                await(response);
                return RecognitionLoop.Outcome.SUCCESS;
            }
            done.countDown();
            return RecognitionLoop.Outcome.SUCCESS;
        }, executor);
        loop.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        cameraFrame.set(2);
        loop.wakeUp();
        cameraFrame.set(3);
        loop.wakeUp();
        loop.start();
        assertEquals(1, sent.size());
        response.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        drain();
        assertEquals(java.util.Arrays.asList(1, 3), sent);
    }

    /** 空闲和成功返回后都没有自动轮询，外部通知才能再次执行。 */
    @Test public void idleAndSuccessSleepUntilNotified() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch unexpected = new CountDownLatch(1);
        loop = new RecognitionLoop(current -> {
            int count = calls.incrementAndGet();
            if (count > 2) unexpected.countDown();
            return count == 1 ? RecognitionLoop.Outcome.IDLE : RecognitionLoop.Outcome.SUCCESS;
        }, executor);
        loop.start();
        drain();
        assertEquals(1, calls.get());
        loop.wakeUp();
        drain();
        assertEquals(2, calls.get());
        assertFalse("超过旧轮询周期也不得自行再次取帧", unexpected.await(350, TimeUnit.MILLISECONDS));
        assertEquals(2, calls.get());
    }

    /** IDLE 返回前收到新帧或就绪事件也必须续发，避免丢失唤醒。 */
    @Test public void notificationWhileReturningIdleIsNotLost() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch resumed = new CountDownLatch(1);
        loop = new RecognitionLoop(current -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
                return RecognitionLoop.Outcome.IDLE;
            }
            resumed.countDown();
            return RecognitionLoop.Outcome.SUCCESS;
        }, executor);
        loop.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        loop.wakeUp();
        release.countDown();
        assertTrue(resumed.await(2, TimeUnit.SECONDS));
        drain();
        assertEquals(2, calls.get());
    }

    /** 错误后相机通知不会自行重启，须显式启动下一轮。 */
    @Test public void errorStopsUntilExplicitRestart() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        loop = new RecognitionLoop(current -> {
            calls.incrementAndGet();
            loop.wakeUp();
            return RecognitionLoop.Outcome.ERROR;
        }, executor);
        loop.start();
        drain();
        loop.wakeUp();
        drain();
        assertEquals(1, calls.get());
        loop.start();
        drain();
        assertEquals(2, calls.get());
    }

    /** 请求失败停止续发，但排队到主线程的失败提示仍可提交；重启后才失效。 */
    @Test public void errorReceiptRemainsCurrentUntilExplicitRestart() throws Exception {
        AtomicReference<java.util.function.BooleanSupplier> receipt = new AtomicReference<>();
        loop = new RecognitionLoop(current -> {
            receipt.set(current);
            return RecognitionLoop.Outcome.ERROR;
        }, executor);
        loop.start();
        drain();
        java.util.function.BooleanSupplier failedReceipt = receipt.get();
        assertTrue("结束请求后仍须显示真实失败", failedReceipt.getAsBoolean());
        loop.start();
        drain();
        assertFalse("重新识别后不能回显上一轮失败", failedReceipt.getAsBoolean());
        loop.stop();
        assertFalse(receipt.get().getAsBoolean());
    }

    /** 停止重启不能重叠 SDK 请求，旧任务不得污染新一代调度。 */
    @Test public void restartRejectsOldReceiptAndNeverOverlapsRequests() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch response = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Boolean> oldReceiptAccepted = new AtomicReference<>();
        loop = new RecognitionLoop(current -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(response);
                oldReceiptAccepted.set(current.getAsBoolean());
                return RecognitionLoop.Outcome.SUCCESS;
            }
            second.countDown();
            return RecognitionLoop.Outcome.SUCCESS;
        }, executor);
        loop.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        loop.wakeUp();
        loop.stop();
        loop.start();
        assertEquals(1, calls.get());
        response.countDown();
        assertTrue(second.await(2, TimeUnit.SECONDS));
        drain();
        assertEquals(Boolean.FALSE, oldReceiptAccepted.get());
        assertEquals(2, calls.get());
        loop.wakeUp();
        drain();
        assertEquals(3, calls.get());
    }

    /** 锁定或退出期间取消在途通知，后续相机回调不再执行任务。 */
    @Test public void stopDuringRequestPreventsContinuation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch response = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        loop = new RecognitionLoop(current -> {
            calls.incrementAndGet();
            entered.countDown();
            await(response);
            return RecognitionLoop.Outcome.SUCCESS;
        }, executor);
        loop.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        loop.wakeUp();
        loop.stop();
        loop.wakeUp();
        response.countDown();
        drain();
        assertEquals(1, calls.get());
    }

    /** 关闭后生命周期或相机迟到事件不得重新提交任务。 */
    @Test public void closedLoopIgnoresLateEvents() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        loop = new RecognitionLoop(current -> {
            calls.incrementAndGet();
            return RecognitionLoop.Outcome.IDLE;
        }, executor);
        loop.start();
        drain();
        loop.close();
        loop.start();
        loop.wakeUp();
        assertEquals(1, calls.get());
    }

    /** 等待此前提交的串行任务完成，不通过固定延迟推断成功。 */
    private void drain() throws Exception {
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
    }

    /** 工作线程等待测试放行，超时或中断均判定测试失败。 */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
