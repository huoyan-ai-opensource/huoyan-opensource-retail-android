package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 能力快照回执：{@code getCapabilities()} 的载荷——接口版本与
 * 服务端各能力面的支持标志，业务 App 据此决定调用路径（如低版本设备走 bytes
 * 降级而非 SharedMemory）。code=0 时能力标志有效；非 0 时 reason 携带说明。
 * SDK 应用版本不依赖激活状态，错误回执中仍可携带版本。
 *
 * <p>{@link #API_VERSION} 为本 AIDL 契约（定稿 15 方法）的版本权威源，
 * {@code getApiVersion()} 与本快照的 apiVersion 字段同源。</p>
 */
public final class CapabilityParcel implements Parcelable {

    /**
     * 接口契约版本。版本 3 新增无参 queryDeviceModels，保留按激活码 queryLicenseModels；
     * 客户端需同步新 AIDL，激活仍显式传入激活码与 modelCode。
     */
    public static final int API_VERSION = 3;

    private final int code;
    private final String reason;
    private final int apiVersion;
    private final boolean sharedMemoryImage;
    private final boolean detect;
    private final boolean recognize;
    private final boolean itemLearn;
    private final boolean itemForget;
    private final boolean itemQuery;
    /** SDK 应用版本名，缺失时为 unknown。 */
    private final String sdkVersionName;
    /** SDK 应用版本码，缺失时为 -1。 */
    private final int sdkVersionCode;

    /** 固定能力与 SDK 应用版本；缺失版本归一为 unknown/-1。 */
    public CapabilityParcel(int code, String reason, int apiVersion, boolean sharedMemoryImage,
            boolean detect, boolean recognize, boolean itemLearn, boolean itemForget, boolean itemQuery,
            String sdkVersionName, int sdkVersionCode) {
        this.code = code;
        this.reason = reason;
        this.apiVersion = apiVersion;
        this.sharedMemoryImage = sharedMemoryImage;
        this.detect = detect;
        this.recognize = recognize;
        this.itemLearn = itemLearn;
        this.itemForget = itemForget;
        this.itemQuery = itemQuery;
        this.sdkVersionName = sdkVersionName;
        this.sdkVersionCode = sdkVersionCode;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 接口契约版本（与 {@code getApiVersion()} 同源）。 */
    public int getApiVersion() {
        return apiVersion;
    }

    /** SharedMemory 传图（API 27+）主路径是否可用。 */
    public boolean isSharedMemoryImage() {
        return sharedMemoryImage;
    }

    /** 纯检测画框（detect）是否可用。 */
    public boolean isDetect() {
        return detect;
    }

    /** 识别闭环（recognize）是否可用。 */
    public boolean isRecognize() {
        return recognize;
    }

    /** 学习（itemLearn）是否可用。 */
    public boolean isItemLearn() {
        return itemLearn;
    }

    /** 遗忘（itemForget/itemForgetByUid）是否可用。 */
    public boolean isItemForget() {
        return itemForget;
    }

    /** 已学查询（getItemClazzList/getItemClazzDetail）是否可用。 */
    public boolean isItemQuery() {
        return itemQuery;
    }

    /** 返回 SDK 应用版本名；unknown 表示未取得版本。 */
    public String getSdkVersionName() {
        return sdkVersionName;
    }

    /** 返回 SDK 应用版本码；-1 表示未取得版本。 */
    public int getSdkVersionCode() {
        return sdkVersionCode;
    }

    /** 按新版 getCapabilities 线序读取能力及 SDK 应用版本。 */
    private CapabilityParcel(Parcel parcel) {
        this(parcel.readInt(), parcel.readString(), parcel.readInt(),
                parcel.readInt() != 0, parcel.readInt() != 0, parcel.readInt() != 0,
                parcel.readInt() != 0, parcel.readInt() != 0, parcel.readInt() != 0,
                parcel.readString(), parcel.readInt());
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeInt(apiVersion);
        parcel.writeInt(sharedMemoryImage ? 1 : 0);
        parcel.writeInt(detect ? 1 : 0);
        parcel.writeInt(recognize ? 1 : 0);
        parcel.writeInt(itemLearn ? 1 : 0);
        parcel.writeInt(itemForget ? 1 : 0);
        parcel.writeInt(itemQuery ? 1 : 0);
        parcel.writeString(sdkVersionName);
        parcel.writeInt(sdkVersionCode);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityParcel)) {
            return false;
        }
        CapabilityParcel that = (CapabilityParcel) other;
        return code == that.code
                && apiVersion == that.apiVersion
                && sharedMemoryImage == that.sharedMemoryImage
                && detect == that.detect
                && recognize == that.recognize
                && itemLearn == that.itemLearn
                && itemForget == that.itemForget
                && itemQuery == that.itemQuery
                && sdkVersionCode == that.sdkVersionCode
                && Objects.equals(reason, that.reason)
                && Objects.equals(sdkVersionName, that.sdkVersionName);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + apiVersion;
        result = 31 * result + (sharedMemoryImage ? 1231 : 1237);
        result = 31 * result + (detect ? 1231 : 1237);
        result = 31 * result + (recognize ? 1231 : 1237);
        result = 31 * result + (itemLearn ? 1231 : 1237);
        result = 31 * result + (itemForget ? 1231 : 1237);
        result = 31 * result + (itemQuery ? 1231 : 1237);
        result = 31 * result + Objects.hashCode(sdkVersionName);
        return 31 * result + sdkVersionCode;
    }

    @Override
    public String toString() {
        return "CapabilityParcel(code=" + code + ", reason=" + reason + ", apiVersion=" + apiVersion
                + ", sharedMemoryImage=" + sharedMemoryImage + ", detect=" + detect
                + ", recognize=" + recognize + ", itemLearn=" + itemLearn
                + ", itemForget=" + itemForget + ", itemQuery=" + itemQuery
                + ", sdkVersionName=" + sdkVersionName + ", sdkVersionCode=" + sdkVersionCode + ")";
    }

    public static final Creator<CapabilityParcel> CREATOR = new Creator<CapabilityParcel>() {
        @Override
        public CapabilityParcel createFromParcel(Parcel parcel) {
            return new CapabilityParcel(parcel);
        }

        @Override
        public CapabilityParcel[] newArray(int size) {
            return new CapabilityParcel[size];
        }
    };
}
