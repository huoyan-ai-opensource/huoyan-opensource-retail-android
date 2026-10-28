package cc.huoyan.retail.diagnostics;

import java.io.File;

public class LogFIleDirectory {
    private LogFIleDirectoryType type;
    private File file;

    public LogFIleDirectory(LogFIleDirectoryType type, File file) {
        this.type = type;
        this.file = file;
    }

    public LogFIleDirectoryType getType() {
        return type;
    }

    public void setType(LogFIleDirectoryType type) {
        this.type = type;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
