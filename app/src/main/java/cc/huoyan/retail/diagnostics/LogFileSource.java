package cc.huoyan.retail.diagnostics;

import java.io.*;
import java.util.*;

/** 日志文件读取，保留隐藏 SDK_RESULTS 以及文本截断规则。 */
final class LogFileSource implements LogPresenter.Files {
    /** 页面资源提供的本地化截断提示。 */
    private final String truncated;
    /** 注入截断提示，无需持有 Context。 */
    LogFileSource(String truncated) { this.truncated = truncated; }
    /** 读取最多跨过 256 Ki 字符上限的一行，与原页面行为一致。 */
    @Override public String read(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
                if (text.length() > 262144) { text.append(truncated); break; }
            }
        }
        return text.toString();
    }
    /** 过滤 SDK 结果文件，按最后修改时间从新到旧排序。 */
    @Override public List<LogFIleDirectory> list(File directory) {
        List<LogFIleDirectory> result = new ArrayList<>();
        try {
            File[] files = directory.listFiles();
            if (files == null) return result;
            for (File file : files) {
                if ("SDK_RESULTS.txt".equals(file.getName())) continue;
                result.add(new LogFIleDirectory(file.isDirectory()
                        ? LogFIleDirectoryType.DIRECTORY : LogFIleDirectoryType.FILE, file));
            }
            result.sort(Comparator.comparingLong((LogFIleDirectory entry) -> entry.getFile().lastModified()).reversed());
        } catch (Exception error) { Logger.logError("logActivity", error); }
        return result;
    }
}
