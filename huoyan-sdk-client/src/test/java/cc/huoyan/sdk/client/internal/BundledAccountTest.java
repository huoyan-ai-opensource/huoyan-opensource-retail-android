package cc.huoyan.sdk.client.internal;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 验证资源账号的精度和失败边界，防止配置错误时绑定到默认账号。 */
public class BundledAccountTest {
    /** 默认 Java 资源随测试运行时打包，读取账号与当前发行配置一致。 */
    @Test public void readsBundledResource() {
        assertEquals(2115027529298350203L, BundledAccount.getAccountId());
    }

    /** 验证大于浮点安全整数的账号和 long 上界均不丢精度。 */
    @Test public void preservesLongPrecision() throws IOException {
        assertEquals(2097500510197125121L, read("Account_id=2097500510197125121\n"));
        assertEquals(Long.MAX_VALUE, read("Account_id=9223372036854775807\n"));
        assertEquals(1L, read("# customer\nAccount_id = 1 \n"));
    }

    /** 缺失配置或非法账号不允许静默回退。 */
    @Test public void rejectsMissingAndInvalidAccounts() {
        assertThrows(IllegalStateException.class, () -> BundledAccount.readAccountId(null));
        for (String value : new String[]{"", "0", "-1", "+1", "1.5", "1e3", "abc",
                "9223372036854775808"}) {
            assertThrows(IllegalStateException.class, () -> read("Account_id=" + value));
        }
        assertThrows(IllegalStateException.class, () -> read("account_id=123"));
    }

    /** 用内存资源驱动解析测试，输入流限定在本次调用内。 */
    private static long read(String config) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(
                config.getBytes(StandardCharsets.UTF_8))) {
            return BundledAccount.readAccountId(input);
        }
    }
}
