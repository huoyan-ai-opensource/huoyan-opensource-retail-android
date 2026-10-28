package cc.huoyan.retail.settings;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** 扫码会话的帧节流、后台解码及单次结果交付，不持有 Android 控件。 */
final class QrScanPresenter {
    /** 只在主线程调用的预览与结果出口。 */
    interface View {
        /** 根据帧尺寸更新预览并截取独立图片；未就绪可返回 null。 */
        Frame capture(int width, int height);
        /** 交付本会话唯一有效结果，页面负责通知宿主和关闭。 */
        void scanned(String value);
    }
    /** 一张归扫码任务所有的图片，解码完成或失败后必须关闭。 */
    interface Frame extends AutoCloseable {
        /** 后台解码；未识别到二维码返回 null。 */
        String decode();
        /** 释放图片，不影响相机持有的原始帧。 */
        @Override void close();
    }
    /** 两次截图至少间隔 250 毫秒，沿用原扫码频率。 */
    private static final long FRAME_INTERVAL_MS = 250;
    /** 当前视图，主线程独占；销毁后清空。 */
    private View view;
    /** 后台解码队列；宿主必须有序关闭，保证已接管图片完成回收。 */
    private final Executor worker;
    /** 独立主线程投递器，不捕获 Fragment。 */
    private final Executor ui;
    /** 单调时钟，测试可手动推进。 */
    private final LongSupplier clock;
    /** 相机线程事件合并标志，避免每帧向主线程堆积任务。 */
    private final AtomicBoolean framePending = new AtomicBoolean();
    /** 关闭标志，对相机线程立即可见。 */
    private volatile boolean detached;
    /** 解码互斥标志，仅由主线程读写。 */
    private boolean decoding;
    /** 成功交付后停止接受后续帧，即使弹窗销毁尚未完成。 */
    private boolean completed;
    /** 是否已有截图尝试，用于允许时间为零的首次帧。 */
    private boolean attempted;
    /** 主线程相机会话代次，切换后拒绝旧解码回执。 */
    private int generation;
    /** 最近截图尝试的单调时间。 */
    private long lastAttemptAt;
    /** 注入视图、调度器和时钟，创建一次独立扫码会话。 */
    QrScanPresenter(View view, Executor worker, Executor ui, LongSupplier clock) {
        this.view = view; this.worker = worker; this.ui = ui; this.clock = clock;
    }
    /** 相机线程只投递尺寸；控件访问统一切到主线程。 */
    void frameAvailable(int width, int height) {
        if (detached || !framePending.compareAndSet(false, true)) return;
        ui.execute(() -> { framePending.set(false); capture(width, height); });
    }
    /** 主线程判定频率和在途状态，再接管截图并提交后台。 */
    private void capture(int width, int height) {
        if (view == null || completed || decoding) return;
        long now = clock.getAsLong();
        if (attempted && now - lastAttemptAt < FRAME_INTERVAL_MS) return;
        attempted = true; lastAttemptAt = now;
        Frame frame = view.capture(width, height);
        if (frame == null) return;
        decoding = true;
        try {
            int token = generation;
            worker.execute(() -> decode(frame, token));
        } catch (RuntimeException error) {
            decoding = false; frame.close();
        }
    }
    /** 已接管图片即使页面销毁也回收，异常按本帧未识别处理。 */
    private void decode(Frame frame, int token) {
        String result = null;
        try (Frame owned = frame) { result = owned.decode(); }
        catch (RuntimeException ignored) { /* 相机瞬态或无效图片不终止下一帧扫码。 */ }
        String value = result;
        ui.execute(() -> {
            decoding = false;
            if (view == null || completed || token != generation || value == null || value.isEmpty()) return;
            completed = true; view.scanned(value);
        });
    }
    /** 用户切换摄像头时保留解码互斥，但作废旧结果及节流时间。 */
    void cameraChanged() { generation++; attempted = false; }

    /** 主线程解除视图引用；在途任务只负责资源收尾，不再交付结果。 */
    void detach() { detached = true; view = null; }
}
