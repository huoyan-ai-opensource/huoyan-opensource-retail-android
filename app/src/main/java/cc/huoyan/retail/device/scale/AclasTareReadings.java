package cc.huoyan.retail.device.scale;

import java.math.BigDecimal;

/** 将皮重回读配对到之后的真实重量帧，禁止将新皮重拼接到旧净重；串口线程安全。 */
final class AclasTareReadings {
    /** 最近确认的设备皮重，未知时为 null。 */
    private BigDecimal tare;
    /** 上次重量帧是否处于去皮状态，用于识别外部去皮操作。 */
    private boolean wasNet;
    /** 上次重量帧之后是否收到过皮重，防止状态位变化丢弃新回执。 */
    private boolean freshTare;

    /** 连接或新去皮命令使旧皮重失效，等待新回读。 */
    synchronized void invalidate() { tare = null; freshTare = false; wasNet = false; }

    /** 只保存回读，不复用上一帧净重生成虚假的重量通知。 */
    synchronized void readTare(BigDecimal value) { tare = value; freshTare = true; }

    /** 收到真实重量帧后才生成三项一致的设备快照。 */
    synchronized AclasWeightSnapshot readWeight(double kg, String unit, boolean stable, boolean net) {
        if (!net) tare = BigDecimal.ZERO;
        else if (!wasNet && !freshTare) tare = null;
        wasNet = net;
        freshTare = false;
        return new AclasWeightSnapshot(kg, unit, stable, net, tare);
    }
}
