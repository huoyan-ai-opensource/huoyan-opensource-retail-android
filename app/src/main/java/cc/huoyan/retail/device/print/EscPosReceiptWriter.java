package cc.huoyan.retail.device.print;

import java.io.IOException;

/** 顺序发送一张小票；失败即停止，不重连重发已提交的数据。 */
final class EscPosReceiptWriter {
    /** 限制每条栅格指令的高度，避免一次占满打印机缓冲区。 */
    static final int STRIPE_HEIGHT = 128;
    /** 按用户指定在尾部发送完后固定等待 300ms，不按票长估算。 */
    private static final int OUTPUT_GUARD_MS = 300;
    /** 切纸机构动作缓冲时间；期间继续持有设备锁和 USB 连接。 */
    private static final int CUT_SETTLE_MS = 100;

    /** 位图读取边界；由调用方保证读取期间图像尚未回收。 */
    interface Raster {
        /** 返回原始点宽，输出不缩放。 */
        int width();
        /** 返回总点高。 */
        int height();
        /** 读取连续行的 ARGB 像素。 */
        int[] rows(int top, int height);
    }

    /** 传输边界；一次调用必须写完全部数据，或抛出异常。 */
    interface Sink {
        /** 按原序发送完整指令及其数据。 */
        void write(byte[] data) throws IOException;
    }

    /** 后台等待边界；测试可控制推进时机，不把 USB 写入成功视为机械完成。 */
    interface Waiter {
        /** 等待指定毫秒数；中断或失败必须终止任务，不能继续切纸。 */
        void await(long milliseconds) throws IOException;
    }

    /** 工具类不创建实例。 */
    private EscPosReceiptWriter() { }

    /** 输出原有头部、行间进纸、尾部及切纸；调用方独占设备连接。 */
    static void print(Raster header, Raster footer, ReceiptPaper paper, Sink sink) throws IOException {
        print(header, footer, paper, sink, EscPosReceiptWriter::waitOnWorker);
    }

    /** 使用可控等待器顺序输出；调用方全程持有设备锁，后续小票不得提前复位设备。 */
    static void print(Raster header, Raster footer, ReceiptPaper paper, Sink sink, Waiter waiter) throws IOException {
        sink.write(EscPosEncoder.initialize());
        writeRaster(header, paper, sink);
        sink.write(new byte[]{0x0a});
        writeRaster(footer, paper, sink);
        waiter.await(OUTPUT_GUARD_MS);
        sink.write(EscPosEncoder.cut());
        waiter.await(CUT_SETTLE_MS);
    }

    /** 仅在打印后台线程等待；保留中断标记并停止后续指令，不能吞掉取消后继续切纸。 */
    private static void waitOnWorker(long milliseconds) throws IOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Printer settling interrupted", interrupted);
        }
    }

    /** 分带读取像素并连续输出；带与带之间不追加空行。 */
    private static void writeRaster(Raster raster, ReceiptPaper paper, Sink sink) throws IOException {
        for (int top = 0; top < raster.height(); top += STRIPE_HEIGHT) {
            int height = Math.min(STRIPE_HEIGHT, raster.height() - top);
            sink.write(EscPosEncoder.centeredRaster(paper, raster.width(), height, raster.rows(top, height)));
        }
    }
}
