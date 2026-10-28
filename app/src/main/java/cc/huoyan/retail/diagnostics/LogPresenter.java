package cc.huoyan.retail.diagnostics;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;

/** 日志目录导航与读取协调器，晚到的文件内容不能覆盖新选择。 */
final class LogPresenter {
    /** 日志页面展示能力。 */
    interface View {
        /** 同步目录条目与面包屑，参数是本次展示快照。 */
        void showDirectory(List<LogFIleDirectory> entries, List<String> path);
        /** 高亮正在读取的文件。 */
        void selectFile(File file);
        /** 展示已读取内容，横屏内嵌、竖屏弹窗由页面决定。 */
        void showText(File file, String text);
        /** 显示文件不存在或无法读取的提示。 */
        void missingFile();
    }
    /** 文件访问边界，文本读取在后台执行。 */
    interface Files {
        /** 列出目录，包含原有过滤及排序规则。 */
        List<LogFIleDirectory> list(File directory);
        /** 读取有长度上限的文件文本。 */
        String read(File file) throws IOException;
    }
    /** 当前页面，销毁时释放。 */
    private View view;
    /** 日志根目录，导航始终从此处开始。 */
    private final File root;
    /** 目录及文本读取实现。 */
    private final Files files;
    /** 后台文本读取队列。 */
    private final Executor worker;
    /** 不捕获 Activity 的主线程投递器。 */
    private final Executor ui;
    /** 面包屑，第一个元素为根目录显示名。 */
    private final List<String> path = new ArrayList<>();
    /** 当前目录条目快照。 */
    private List<LogFIleDirectory> entries = Collections.emptyList();
    /** 文件选择代次，读取结果只对最后一次选择有效。 */
    private int generation;
    /** 注入日志根目录和文件访问，构造时不读取。 */
    LogPresenter(View view, File root, Files files, Executor worker, Executor ui) {
        this.view = view; this.root = root; this.files = files; this.worker = worker; this.ui = ui;
    }
    /** 每次进入先展示根目录；横屏自动进入最新目录并选首个文件。 */
    void resume(boolean inline) {
        path.clear(); path.add("/"); loadDirectory();
        if (!inline || entries.isEmpty()) return;
        LogFIleDirectory latest = entries.get(0);
        if (latest.getType() == LogFIleDirectoryType.DIRECTORY) {
            path.add(latest.getFile().getName()); loadDirectory();
        }
        for (LogFIleDirectory entry : entries) {
            if (entry.getType() == LogFIleDirectoryType.FILE) { read(entry.getFile()); break; }
        }
    }
    /** 目录点击进入下一级，文件点击读取内容。 */
    void open(LogFIleDirectory entry) {
        if (entry.getType() == LogFIleDirectoryType.DIRECTORY) {
            path.add(entry.getFile().getName()); loadDirectory();
        } else if (entry.getType() == LogFIleDirectoryType.FILE) read(entry.getFile());
    }
    /** 保留既有面包屑行为：只有根节点可点击返回。 */
    void breadcrumb(int position) {
        if (position == 0 && path.size() > 1) { path.clear(); path.add("/"); loadDirectory(); }
    }
    /** 释放 View 并使所有在途文件读取失效。 */
    void detach() { ++generation; view = null; }
    /** 按面包屑解析路径并展示文件源给出的有序列表。 */
    private void loadDirectory() {
        File directory = root;
        for (int i = 1; i < path.size(); i++) directory = new File(directory, path.get(i));
        entries = files.list(directory);
        if (view != null) view.showDirectory(new ArrayList<>(entries), new ArrayList<>(path));
    }
    /** 高亮新选择并异步读取；失败同样受选择代次保护。 */
    private void read(File file) {
        if (view == null) return;
        int request = ++generation;
        view.selectFile(file);
        worker.execute(() -> {
            try {
                String text = files.read(file);
                ui.execute(() -> { if (view != null && request == generation) view.showText(file, text); });
            } catch (IOException error) {
                ui.execute(() -> { if (view != null && request == generation) view.missingFile(); });
            }
        });
    }
}
