package cc.huoyan.retail.device.scale;

import android.util.Log;

import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.R;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import aclasdriver.AclasScale;

/**
 * Minimal Aclas-only scale bridge. It never imports the demo balance configuration stack.
 */
public class AclasScaleManager {
    private static final String LOG_TAG = "AclasScaleManager";

    private static String s(int resId, Object... args) {
        return RetailApplication.getInstance().getString(resId, args);
    }

    public interface Listener {
        void onConnected(String port);
        void onWeightRead(AclasWeightSnapshot snapshot);
        void onError(String message);
    }

    public interface CommandListener {
        void onResult(boolean success, String message);
    }

    private final ExecutorService openExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean opening = new AtomicBoolean(false);
    private final Object scaleLock = new Object();
    private volatile boolean requested;
    private volatile AclasScale scale;
    private volatile long lastReadAt;
    /** 皮重回执只与之后的真实净重配对，连接结束即失效。 */
    private final AclasTareReadings tareReadings = new AclasTareReadings();
    /** 本连接最近的稳定性及重量快照。 */
    private volatile AclasWeightSnapshot latest;
    /** 皮重查询限频，避免与重量流争用串口。 */
    private volatile long tareReadAt;
    /** 主线程超时器，命令没有设备回执时必须结束等待。 */
    private final android.os.Handler commandTimer = new android.os.Handler(android.os.Looper.getMainLooper());
    /** 命令互斥及结果只投递一次的锁。 */
    private final Object commandLock = new Object();
    /** 正在等待设备读数确认的命令，锁内替换。 */
    private volatile PendingCommand pendingCommand;
    /** 连接代次，旧串口回调不能污染重连后的读数。 */
    private volatile int connectionGeneration;

    /** 一条命令的不可变目标和独立超时，禁止旧回调结束新命令。 */
    private final class PendingCommand {
        /** 本条命令的完成回调。 */
        final CommandListener listener;
        /** null 表示置零；非空表示目标皮重 kg。 */
        final java.math.BigDecimal tare;
        /** 发出命令时的连接代次。 */
        final int generation;
        /** 已完成串口发送，之后才能接受回执。 */
        volatile boolean sent;
        /** 已回读到目标皮重，仍须等待之后的重量帧才能确认命令完成。 */
        volatile boolean tareConfirmed;
        /** 本命令专属的超时动作。 */
        final Runnable timeout = () -> finishCommand(this, false, s(R.string.checkout_scale_timeout));
        /** 固定命令目标、回调与所属连接。 */
        PendingCommand(java.math.BigDecimal tare, CommandListener listener) {
            this.tare = tare; this.listener = listener; this.generation = connectionGeneration;
        }
    }

    public void open(Listener listener) {
        open("", listener);
    }

    public void open(String configuredPort, Listener listener) {
        if (!opening.compareAndSet(false, true)) {
            return;
        }
        requested = true;
        tareReadings.invalidate(); latest = null; tareReadAt = 0; lastReadAt = 0;
        int generation = ++connectionGeneration;
        openExecutor.execute(() -> openInternal(configuredPort, listener, generation));
    }

    private void openInternal(String configuredPort, Listener listener, int generation) {
        try {
            List<String> ports = AclasScale.getAvailableUartList();
            if (!requested || generation != connectionGeneration) {
                return;
            }
            if (ports == null || ports.isEmpty()) {
                listener.onError(s(R.string.scale_error_no_ports));
                return;
            }
            String port;
            if (configuredPort != null && !configuredPort.trim().isEmpty()) {
                if (!ports.contains(configuredPort)) {
                    listener.onError(s(R.string.scale_error_port_unavailable));
                    return;
                }
                port = configuredPort;
            } else if (ports.size() == 1) {
                // Only the unambiguous single-port case may use automatic discovery.
                port = ports.get(0);
            } else {
                listener.onError(s(R.string.scale_error_no_port_configured));
                return;
            }
            AclasScale localScale = new AclasScale(new File(port), 0, new AclasScale.AclasScaleListener() {
                @Override
                public void OnError(int code) {
                    if (!requested || generation != connectionGeneration) return;
                    latest = null; tareReadings.invalidate();
                    PendingCommand command = pendingCommand;
                    if (command != null) finishCommand(command, false, s(R.string.scale_error_connect_failed, code));
                    Log.e(LOG_TAG, "Aclas error=" + code);
                    listener.onError(s(R.string.scale_error_connect_failed, code));
                }

                @Override
                public void OnDataReceive(AclasScale.St_Data data) {
                    if (!requested || generation != connectionGeneration) return;
                    if (data == null || data.m_iStatus == -1 || !Float.isFinite(data.m_fWeight)) {
                        latest = null; tareReadings.invalidate();
                        PendingCommand failed = pendingCommand;
                        if (failed != null) finishCommand(failed, false, s(R.string.scale_error_read_failed));
                        listener.onError(s(R.string.scale_error_read_failed));
                        return;
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastReadAt < 300L) {
                        return;
                    }
                    lastReadAt = now;
                    double weightKg = Math.round(data.m_fWeight * 1000.0d) / 1000.0d;
                    if (!requested || generation != connectionGeneration) return;
                    AclasWeightSnapshot reading = tareReadings.readWeight(weightKg, data.m_strUnit, data.m_iStatus != 0, data.m_bTare);
                    latest = reading;
                    PendingCommand command = pendingCommand;
                    boolean waitingTare = command != null && command.tare != null;
                    if ((data.m_bTare || waitingTare) && now - tareReadAt > (waitingTare ? 500 : 2000)) {
                        tareReadAt = now;
                        openExecutor.execute(() -> { AclasScale current = scale; if (current != null && requested && generation == connectionGeneration) current.getTareValue(); });
                    }
                    // 去皮切换期间的读数可能仍带旧皮重，不能交给收银重新识别或计价。
                    // 未去皮状态位本身确认零皮重；清零后设备可能不再返回皮重查询结果。
                    boolean tareConfirmed = waitingTare && (command.tareConfirmed
                            || (command.tare.signum() == 0 && !reading.isNetWeight()));
                    if (waitingTare && (!command.sent || !tareConfirmed
                            || reading.tareKg() == null || command.tare.compareTo(reading.tareKg()) != 0)) return;
                    listener.onWeightRead(reading);
                    if (command != null && command.sent && reading.isStable()) {
                        if (command.tare == null && reading.netKg().signum() == 0)
                            finishCommand(command, true, s(R.string.checkout_zero_done));
                        else if (command.tare != null)
                            finishCommand(command, true, s(R.string.checkout_tare_done));
                    }
                }

                @Override
                public void OnReadTare(float value, boolean success) {
                    if (!requested || generation != connectionGeneration || !success || !Float.isFinite(value) || value < 0) return;
                    java.math.BigDecimal tareKg = new java.math.BigDecimal(Float.toString(value)).setScale(3, java.math.RoundingMode.HALF_UP);
                    tareReadings.readTare(tareKg);
                    PendingCommand command = pendingCommand;
                    if (command != null && command.sent && command.tare != null && command.tare.compareTo(tareKg) == 0)
                        command.tareConfirmed = true;
                }
            });
            synchronized (scaleLock) {
                if (!requested || generation != connectionGeneration) {
                    return;
                }
                scale = localScale;
            }
            localScale.bLogFlag = false;
            localScale.open();
            if (!requested || generation != connectionGeneration) {
                closeLocalScale(localScale);
                return;
            }
            localScale.StartRead();
            listener.onConnected(port);
        } catch (Exception e) {
            Log.e(LOG_TAG, "open scale failed", e);
            if (requested && generation == connectionGeneration) listener.onError(s(R.string.scale_error_open_failed, e.getMessage()));
        } finally {
            opening.set(false);
        }
    }

    public void close() {
        requested = false; ++connectionGeneration;
        tareReadings.invalidate(); latest = null;
        PendingCommand command = pendingCommand;
        if (command != null) finishCommand(command, false, s(R.string.checkout_scale_disconnected));
        AclasScale localScale;
        synchronized (scaleLock) {
            localScale = scale;
            scale = null;
        }
        closeLocalScale(localScale);
    }

    /** 发送置零并等待稳定零读数，不把成功写串口视为设备成功。 */
    public void setZero(CommandListener listener) { executeConfirmedCommand(null, listener); }

    /** 预置皮重使用设备要求的克整数，回读一致且收到后续稳定净重才报告成功。 */
    public void setPreTare(int tareValue, CommandListener listener) {
        if (tareValue < 0) { listener.onResult(false, s(R.string.checkout_tare_invalid)); return; }
        executeConfirmedCommand(java.math.BigDecimal.valueOf(tareValue, 3), listener);
    }

    /** 一次只允许一条稳定、已连接状态下的命令，3秒未确认则失败。 */
    private void executeConfirmedCommand(java.math.BigDecimal tare, CommandListener listener) {
        final PendingCommand command;
        synchronized (commandLock) {
            if (pendingCommand != null) { listener.onResult(false, s(R.string.checkout_scale_busy)); return; }
            if (scale == null || !requested || latest == null || android.os.SystemClock.elapsedRealtime() - lastReadAt > 2000
                    || !latest.isStable() || (latest.netKg().signum() < 0 && tare == null)) {
                listener.onResult(false, s(R.string.checkout_scale_not_ready)); return;
            }
            command = new PendingCommand(tare, listener); pendingCommand = command;
            if (tare != null) tareReadings.invalidate();
            commandTimer.postDelayed(command.timeout, 3000);
        }
        openExecutor.execute(() -> {
            try {
                AclasScale current = scale;
                if (pendingCommand != command || command.generation != connectionGeneration) return;
                if (current == null || !requested) throw new IllegalStateException(s(R.string.checkout_scale_disconnected));
                if (tare == null) current.SetZero();
                else current.setPreTare(tare.movePointRight(3).intValueExact());
                command.sent = true;
                if (tare != null) current.getTareValue();
            } catch (Exception error) { finishCommand(command, false, error.getMessage()); }
        });
    }

    /** 清理同一命令的等待并只投递一次设备回执，允许设备线程调用。 */
    private void finishCommand(PendingCommand command, boolean success, String message) {
        synchronized (commandLock) {
            if (pendingCommand != command) return;
            pendingCommand = null;
            commandTimer.removeCallbacks(command.timeout);
        }
        command.listener.onResult(success, message);
    }

    public void release() {
        close();
        openExecutor.shutdownNow();
    }

    private void closeLocalScale(AclasScale localScale) {
        if (localScale == null) {
            return;
        }
        try {
            localScale.StopRead();
            localScale.close();
        } catch (Exception e) {
            Log.w(LOG_TAG, "close scale failed", e);
        }
    }
}
