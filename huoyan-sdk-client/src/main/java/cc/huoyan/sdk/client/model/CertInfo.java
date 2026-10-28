package cc.huoyan.sdk.client.model;

import java.util.Objects;

/** 证书与 SDK 版本回执。时间戳单位为秒，可选续期时间以 has 标志判断。 */
public final class CertInfo {

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
    public CertInfo(int code, String reason, String modelCode,
            String modelChannel, String licenseType, long activatedAt, long issuedAt,
            long renewedAt, boolean hasRenewedAt, long nextRenewAt, boolean hasNextRenewAt,
            long expiresAt) {
        this(code, reason, modelCode, modelChannel, licenseType, activatedAt,
                issuedAt, renewedAt, hasRenewedAt, nextRenewAt, hasNextRenewAt, expiresAt,
                null, 0, null);
    }

    /** 全量构造：尾部追加 SDK 版本与激活序列号（空/0/null = 无值）。 */
    public CertInfo(int code, String reason, String modelCode,
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CertInfo)) {
            return false;
        }
        CertInfo that = (CertInfo) other;
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
        return "CertInfo(code=" + code + ", reason=" + reason
                + ", modelCode=" + modelCode + ", modelChannel=" + modelChannel
                + ", licenseType=" + licenseType + ", activatedAt=" + activatedAt
                + ", issuedAt=" + issuedAt + ", renewedAt=" + renewedAt
                + ", hasRenewedAt=" + hasRenewedAt + ", nextRenewAt=" + nextRenewAt
                + ", hasNextRenewAt=" + hasNextRenewAt + ", expiresAt=" + expiresAt
                + ", sdkVersionName=" + sdkVersionName
                + ", sdkVersionCode=" + sdkVersionCode + ", activationSerial=" + activationSerial
                + ")";
    }

}
