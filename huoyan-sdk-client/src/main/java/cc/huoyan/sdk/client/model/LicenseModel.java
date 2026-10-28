package cc.huoyan.sdk.client.model;

/** 授权中的一个可用模型；名称用于展示，代码用于提交，不映射固定业态。 */
public final class LicenseModel {
    /** 服务端模型代码。 */
    private final String modelCode;
    /** 服务端展示名称。 */
    private final String modelName;
    /** 模型芯片标识。 */
    private final String chip;
    /** 当前设备正在使用该模型。 */
    private final boolean current;

    /** 固定一次查询返回的模型信息。 */
    public LicenseModel(String modelCode, String modelName, String chip, boolean current) {
        this.modelCode = modelCode;
        this.modelName = modelName;
        this.chip = chip;
        this.current = current;
    }
    /** 取得提交用模型代码。 */
    public String getModelCode() { return modelCode; }
    /** 取得显示名称。 */
    public String getModelName() { return modelName; }
    /** 取得芯片标识。 */
    public String getChip() { return chip; }
    /** 是否为当前模型。 */
    public boolean isCurrent() { return current; }
}
