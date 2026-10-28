package cc.huoyan.retail.settings;

import java.util.ArrayList;
import java.util.List;

/** 激活码提交格式及输入框分段，沿用现有十六位手输规则。 */
final class ActivationCode {
    /**
     * 激活码规范化（纯逻辑，供单测）：去输入中已有连字符后重新分组为带连字符
     * 格式为 16 位 4+4+4+4（Z2FB-W9L7-M78R-586H）。
     * 后端存储带连字符（用户 2026-08-27），提交/SP 存储必须用该格式才能匹配；
     * 其他长度（异常输入）原样返回，不猜测分组。
     *
     * @param input 手输纯码/带连字符码、扫码 serial
     * @return 带连字符的规范化激活码；长度异常时原样返回
     */
    static String normalize(String input) { return cc.huoyan.sdk.client.activation.ActivationCode.normalize(input); }

    /**
     * 激活码分段（纯逻辑，供单测）：先去连字符（SP 回填带连字符，用户
     * 2026-08-27），统一按 4-4-4-4 分段；输入过程中的前缀也按每 4 位一段。
     *
     * @param input 激活码文本（纯码或带连字符）
     * @return 分段列表（最多 4 段）
     */
    static List<String> segmentCode(String input) {
        List<String> segments = new ArrayList<>();
        if (input == null) {
            return segments;
        }
        String code = input.replace("-", "");
        if (code.isEmpty()) {
            return segments;
        }
        int length = code.length();
        for (int start = 0; start < length; ) {
            int end = Math.min(start + 4, length);
            segments.add(code.substring(start, end));
            start = end;
        }
        return segments;
    }

    /** 手输入口要求去掉连字符后恰好十六位；扫码不套用此限制。 */
    static boolean validManual(String input) { return cc.huoyan.sdk.client.activation.ActivationCode.validManual(input); }
}
