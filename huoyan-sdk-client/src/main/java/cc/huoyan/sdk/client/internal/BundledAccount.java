package cc.huoyan.sdk.client.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** 读取发行 JAR 的归属账号；不依赖 Android 资源或宿主配置。 */
final class BundledAccount {
    /** JAR 内固定资源路径，打包 APK 时必须保留。 */
    private static final String CONFIG_PATH = "/raw/config";

    /** 工具类不创建实例。 */
    private BundledAccount() { }

    /** 每次绑定读取发行配置；不缓存宿主状态，缺失或无效时禁止发起绑定。 */
    static long getAccountId() {
        try (InputStream input = BundledAccount.class.getResourceAsStream(CONFIG_PATH)) {
            return readAccountId(input);
        } catch (IOException error) {
            throw new IllegalStateException("无法读取 JAR 内 raw/config", error);
        }
    }

    /** 解析正整数账号并保留完整 long 精度；流由调用者关闭。 */
    static long readAccountId(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalStateException("JAR 内缺少 raw/config");
        }
        Properties config = new Properties();
        config.load(input);
        String value = config.getProperty("Account_id", "").trim();
        if (!value.matches("[0-9]{1,19}")) {
            throw new IllegalStateException("raw/config 的 Account_id 必须为 long 范围内的正整数");
        }
        try {
            long accountId = Long.parseLong(value);
            if (accountId > 0) {
                return accountId;
            }
        } catch (NumberFormatException error) {
            throw new IllegalStateException("raw/config 的 Account_id 超出 long 范围", error);
        }
        throw new IllegalStateException("raw/config 的 Account_id 必须大于 0");
    }
}
