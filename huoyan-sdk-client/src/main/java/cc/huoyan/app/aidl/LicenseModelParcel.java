package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/** 授权可选模型，字段由 Core 原样返回。 */
public final class LicenseModelParcel implements Parcelable {
    /** 模型编码，用于激活。 */
    private final String modelCode;
    /** 后端配置的展示名称。 */
    private final String modelName;
    /** 设备芯片。 */
    private final String chip;
    /** 是否为当前授权模型。 */
    private final boolean current;
    /** 构造模型快照。 */
    public LicenseModelParcel(String modelCode, String modelName, String chip, boolean current) {
        this.modelCode = modelCode; this.modelName = modelName; this.chip = chip; this.current = current;
    }
    /** 读取模型编码。 */
    public String getModelCode() { return modelCode; }
    /** 读取模型名称。 */
    public String getModelName() { return modelName; }
    /** 读取设备芯片。 */
    public String getChip() { return chip; }
    /** 判断当前模型。 */
    public boolean isCurrent() { return current; }
    /** 按固定线序读取。 */
    private LicenseModelParcel(Parcel in) { this(in.readString(), in.readString(), in.readString(), in.readInt() != 0); }
    /** 按固定线序写入。 */
    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(modelCode); out.writeString(modelName); out.writeString(chip); out.writeInt(current ? 1 : 0);
    }
    /** 不携带文件描述符。 */
    @Override public int describeContents() { return 0; }
    /** 跨进程反序列化工厂。 */
    public static final Creator<LicenseModelParcel> CREATOR = new Creator<LicenseModelParcel>() {
        /** 重建单个模型。 */
        public LicenseModelParcel createFromParcel(Parcel in) { return new LicenseModelParcel(in); }
        /** 分配模型数组。 */
        public LicenseModelParcel[] newArray(int size) { return new LicenseModelParcel[size]; }
    };
}
