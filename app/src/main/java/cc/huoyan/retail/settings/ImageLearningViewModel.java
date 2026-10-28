package cc.huoyan.retail.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkGoodsImages;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cc.huoyan.sdk.client.model.ImageFlowProgress;

/** 小图包学习的页面状态；长调用与进度查询分线程，旋转屏幕后不重复提交。 */
public class ImageLearningViewModel extends ViewModel {
    static final class State {
        final boolean running;
        final ImageFlowProgress download;
        final ImageFlowProgress learn;
        final ImageFlowProgress result;

        State(boolean running, ImageFlowProgress download,
                ImageFlowProgress learn, ImageFlowProgress result) {
            this.running = running;
            this.download = download;
            this.learn = learn;
            this.result = result;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, null, null));
    private final ScheduledExecutorService workers = Executors.newScheduledThreadPool(2);
    /** 可取消的只读观察，不拥有写操作生命周期。 */
    private ScheduledFuture<?> observer;
    private boolean running;
    private long generation;
    private State reportedResult;

    LiveData<State> state() { return state; }

    // 仅在页面展示完成提示时消费，返回页面或重建视图时不重复弹出。
    boolean consumeResult(State value) {
        if (value.result == null || value == reportedResult) return false;
        reportedResult = value;
        return true;
    }

    synchronized boolean isRunning() { return running; }

    synchronized void start(HuoyanSdkClient bridge) {
        if (running) return;
        running = true;
        long operation = ++generation;
        state.setValue(new State(true, null, null, null));
        ScheduledFuture<?> polling = workers.scheduleWithFixedDelay(() -> {
            ImageFlowProgress download = cc.huoyan.sdk.client.operation.SdkOperations.imageProgress(bridge, true);
            ImageFlowProgress learn = cc.huoyan.sdk.client.operation.SdkOperations.imageProgress(bridge, false);
            synchronized (this) {
                if (running && generation == operation) {
                    state.postValue(new State(true, download, learn, null));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        observer = polling;
        workers.execute(() -> {
            ImageFlowProgress result = cc.huoyan.sdk.client.operation.SdkOperations.learnImages(bridge);
            polling.cancel(false);
            SdkGoodsImages.invalidate();
            synchronized (this) {
                if (generation != operation) return;
                running = false;
                state.postValue(new State(false, null, null, result));
            }
        });
    }

    @Override
    protected void onCleared() {
        synchronized (this) {
            running = false;
            generation++;
            if (observer != null) observer.cancel(false);
        }
        // 停止观察但让已提交的 SDK 写操作完成。
        workers.shutdown();
    }
}
