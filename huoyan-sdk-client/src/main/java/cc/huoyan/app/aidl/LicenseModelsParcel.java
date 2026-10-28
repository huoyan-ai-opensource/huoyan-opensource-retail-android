package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 授权模型查询回执；code 为 0 时 models 有效。 */
public final class LicenseModelsParcel implements Parcelable {
    /** 查询结果码。 */
    private final int code;
    /** 失败原因。 */
    private final String reason;
    /** 不可变模型快照。 */
    private final List<LicenseModelParcel> models;
    /** 构造查询回执。 */
    public LicenseModelsParcel(int code, String reason, List<LicenseModelParcel> models) {
        this.code = code; this.reason = reason;
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
    }
    /** 读取结果码。 */
    public int getCode() { return code; }
    /** 读取失败原因。 */
    public String getReason() { return reason; }
    /** 读取模型快照。 */
    public List<LicenseModelParcel> getModels() { return models; }
    /** 按固定线序读取。 */
    private LicenseModelsParcel(Parcel in) {
        this(in.readInt(), in.readString(), in.createTypedArrayList(LicenseModelParcel.CREATOR));
    }
    /** 按固定线序写入。 */
    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(code); out.writeString(reason); out.writeTypedList(models);
    }
    /** 不携带文件描述符。 */
    @Override public int describeContents() { return 0; }
    /** 跨进程反序列化工厂。 */
    public static final Creator<LicenseModelsParcel> CREATOR = new Creator<LicenseModelsParcel>() {
        /** 重建查询回执。 */
        public LicenseModelsParcel createFromParcel(Parcel in) { return new LicenseModelsParcel(in); }
        /** 分配回执数组。 */
        public LicenseModelsParcel[] newArray(int size) { return new LicenseModelsParcel[size]; }
    };
}
