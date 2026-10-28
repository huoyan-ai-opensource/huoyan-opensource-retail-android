package cc.huoyan.retail.device.print;

/** 常见 203dpi 小票纸规格；使用有效打印点宽，而非纸张物理宽度。 */
public enum ReceiptPaper {
    /** 58mm 纸常见有效打印宽度 48mm。 */
    MM_58(58, 384),
    /** 80mm 纸常见有效打印宽度 72mm。 */
    MM_80(80, 576);

    /** 现有小票正文点宽，切换纸张时保持字号及条码比例。 */
    static final int CONTENT_WIDTH = 320;
    /** 持久化的纸宽毫米值。 */
    private final int millimeters;
    /** ESC/POS 有效打印区域点宽。 */
    private final int dots;

    /** 固定一种纸张的物理名称与有效打印区域。 */
    ReceiptPaper(int millimeters, int dots) {
        this.millimeters = millimeters;
        this.dots = dots;
    }

    /** 返回用于配置保存和选项展示的毫米数。 */
    public int millimeters() { return millimeters; }

    /** 返回 203dpi 下的有效打印点宽。 */
    public int dots() { return dots; }

    /** 默认使用 58mm；未知值不得生成超出设备范围的指令。 */
    public static ReceiptPaper fromMillimeters(int millimeters) {
        return millimeters == 80 ? MM_80 : MM_58;
    }
}
