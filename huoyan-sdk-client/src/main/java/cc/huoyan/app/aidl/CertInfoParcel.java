package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 证书视图回执：{@code getCertInfo()} 的载荷——授权元数据与
 * 续期诊断全集。code=0 时字段有效；非 0 时 reason 携带说明、字段为默认值。
 *
 * <p>时间戳为秒级整型，0=视图缺失（对齐桥接层 {@code cc.huoyan.bridge.CertInfo}
 * 透传口径）；{@code renewedAt}/{@code nextRenewAt} 额外携带 has 标志，
 * 区分「0 值有效」与「视图无此字段」。</p>
 *
 * <p>{@code licenseId}/{@code needRenew} 已随 C 侧契约 v7 删除
 * （对外泄露内部授权信息），本回执不再携带这两个字段——字段从 15 个减为 13 个。</p>
 *
 * <p>尾部追加 SDK 自身信息三字段（{@code sdkVersionName}/
 * {@code sdkVersionCode}/{@code activationSerial}，SDK APK 版本 + 激活序列号，
 * 来源 {@code cc.huoyan.comm.SdkShellInfo}）：读序 = 写序追加在线尾——旧
 * 客户端不读新字段，前缀线序不动。未激活/包不可查时为 null/0，不伪造。</p>
 */
public final class CertInfoParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final String modelCode;
    private final String modelChannel;
    private final String licenseType;
    private final long activatedAt;
    private final long issuedAt;
    private final long renewedAt;
    private final boolean hasRenewedAt;
    private final long nextRenewAt;
    private final boolean hasNextRenewAt;
    private final long expiresAt;
    private final String sdkVersionName;
    private final int sdkVersionCode;
    private final String activationSerial;

    /** 构造证书回执。 */
    public CertInfoParcel(int code, String reason, String modelCode,
            String modelChannel, String licenseType, long activatedAt, long issuedAt,
            long renewedAt, boolean hasRenewedAt, long nextRenewAt, boolean hasNextRenewAt,
            long expiresAt) {
        this(code, reason, modelCode, modelChannel, licenseType, activatedAt,
                issuedAt, renewedAt, hasRenewedAt, nextRenewAt, hasNextRenewAt, expiresAt,
                null, 0, null);
    }

    /** 全量构造：尾部追加 SDK 版本与激活序列号（空/0/null = 无值）。 */
    public CertInfoParcel(int code, String reason, String modelCode,
            String modelChannel, String licenseType, long activatedAt, long issuedAt,
            long renewedAt, boolean hasRenewedAt, long nextRenewAt, boolean hasNextRenewAt,
            long expiresAt,
            String sdkVersionName, int sdkVersionCode, String activationSerial) {
        this.code = code;
        this.reason = reason;
        this.modelCode = modelCode;
        this.modelChannel = modelChannel;
        this.licenseType = licenseType;
        this.activatedAt = activatedAt;
        this.issuedAt = issuedAt;
        this.renewedAt = renewedAt;
        this.hasRenewedAt = hasRenewedAt;
        this.nextRenewAt = nextRenewAt;
        this.hasNextRenewAt = hasNextRenewAt;
        this.expiresAt = expiresAt;
        this.sdkVersionName = sdkVersionName;
        this.sdkVersionCode = sdkVersionCode;
        this.activationSerial = activationSerial;
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 取得模型编码。 */
    public String getModelCode() {
        return modelCode;
    }

    /** 取得模型渠道。 */
    public String getModelChannel() {
        return modelChannel;
    }

    /** null=旧证书无 license_type 字段。 */
    public String getLicenseType() {
        return licenseType;
    }

    /** 激活时间戳（秒级；0=视图缺失）。 */
    public long getActivatedAt() {
        return activatedAt;
    }

    /** 签发时间戳（秒级；0=视图缺失）。 */
    public long getIssuedAt() {
        return issuedAt;
    }

    /** 最近续期时间戳（秒级）；有效性以 {@link #hasRenewedAt()} 为准。 */
    public long getRenewedAt() {
        return renewedAt;
    }

    /** 视图是否携带 renewedAt 字段。 */
    public boolean hasRenewedAt() {
        return hasRenewedAt;
    }

    /** 下次续期时间戳（秒级）；有效性以 {@link #hasNextRenewAt()} 为准。 */
    public long getNextRenewAt() {
        return nextRenewAt;
    }

    /** 视图是否携带 nextRenewAt 字段。 */
    public boolean hasNextRenewAt() {
        return hasNextRenewAt;
    }

    /** 过期时间戳（秒级；0=视图缺失/永久证书）。 */
    public long getExpiresAt() {
        return expiresAt;
    }

    /** SDK APK versionName；null = 包不可查。 */
    public String getSdkVersionName() {
        return sdkVersionName;
    }

    /** SDK APK versionCode；0 = 包不可查。 */
    public int getSdkVersionCode() {
        return sdkVersionCode;
    }

    /** 激活序列号 = 用户输入的激活码（SDK 侧自存）；null = 未激活。 */
    public String getActivationSerial() {
        return activationSerial;
    }

    private CertInfoParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        modelCode = parcel.readString();
        modelChannel = parcel.readString();
        licenseType = parcel.readString();
        activatedAt = parcel.readLong();
        issuedAt = parcel.readLong();
        renewedAt = parcel.readLong();
        hasRenewedAt = parcel.readInt() != 0;
        nextRenewAt = parcel.readLong();
        hasNextRenewAt = parcel.readInt() != 0;
        expiresAt = parcel.readLong();
        sdkVersionName = parcel.readString();
        sdkVersionCode = parcel.readInt();
        activationSerial = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeString(modelCode);
        parcel.writeString(modelChannel);
        parcel.writeString(licenseType);
        parcel.writeLong(activatedAt);
        parcel.writeLong(issuedAt);
        parcel.writeLong(renewedAt);
        parcel.writeInt(hasRenewedAt ? 1 : 0);
        parcel.writeLong(nextRenewAt);
        parcel.writeInt(hasNextRenewAt ? 1 : 0);
        parcel.writeLong(expiresAt);
        parcel.writeString(sdkVersionName);
        parcel.writeInt(sdkVersionCode);
        parcel.writeString(activationSerial);
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
        if (!(other instanceof CertInfoParcel)) {
            return false;
        }
        CertInfoParcel that = (CertInfoParcel) other;
        return code == that.code
                && activatedAt == that.activatedAt
                && issuedAt == that.issuedAt
                && renewedAt == that.renewedAt
                && hasRenewedAt == that.hasRenewedAt
                && nextRenewAt == that.nextRenewAt
                && hasNextRenewAt == that.hasNextRenewAt
                && expiresAt == that.expiresAt
                && sdkVersionCode == that.sdkVersionCode
                && Objects.equals(reason, that.reason)
                && Objects.equals(modelCode, that.modelCode)
                && Objects.equals(modelChannel, that.modelChannel)
                && Objects.equals(licenseType, that.licenseType)
                && Objects.equals(sdkVersionName, that.sdkVersionName)
                && Objects.equals(activationSerial, that.activationSerial);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Objects.hashCode(modelCode);
        result = 31 * result + Objects.hashCode(modelChannel);
        result = 31 * result + Objects.hashCode(licenseType);
        result = 31 * result + (int) (activatedAt ^ (activatedAt >>> 32));
        result = 31 * result + (int) (issuedAt ^ (issuedAt >>> 32));
        result = 31 * result + (int) (renewedAt ^ (renewedAt >>> 32));
        result = 31 * result + (hasRenewedAt ? 1231 : 1237);
        result = 31 * result + (int) (nextRenewAt ^ (nextRenewAt >>> 32));
        result = 31 * result + (hasNextRenewAt ? 1231 : 1237);
        result = 31 * result + (int) (expiresAt ^ (expiresAt >>> 32));
        result = 31 * result + Objects.hashCode(sdkVersionName);
        result = 31 * result + sdkVersionCode;
        return 31 * result + Objects.hashCode(activationSerial);
    }

    @Override
    public String toString() {
        return "CertInfoParcel(code=" + code + ", reason=" + reason
                + ", modelCode=" + modelCode + ", modelChannel=" + modelChannel
                + ", licenseType=" + licenseType + ", activatedAt=" + activatedAt
                + ", issuedAt=" + issuedAt + ", renewedAt=" + renewedAt
                + ", hasRenewedAt=" + hasRenewedAt + ", nextRenewAt=" + nextRenewAt
                + ", hasNextRenewAt=" + hasNextRenewAt + ", expiresAt=" + expiresAt
                + ", sdkVersionName=" + sdkVersionName
                + ", sdkVersionCode=" + sdkVersionCode + ", activationSerial=" + activationSerial
                + ")";
    }

    public static final Creator<CertInfoParcel> CREATOR = new Creator<CertInfoParcel>() {
        @Override
        public CertInfoParcel createFromParcel(Parcel parcel) {
            return new CertInfoParcel(parcel);
        }

        @Override
        public CertInfoParcel[] newArray(int size) {
            return new CertInfoParcel[size];
        }
    };
}
