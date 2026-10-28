package cc.huoyan.retail.diagnostics;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LogPresenterTest {
    @Test public void newestSelectionWinsAndDestroyedPageReceivesNothing() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor(); List<String> text = new ArrayList<>();
        LogPresenter p = new LogPresenter(new LogPresenter.View() {
            public void showDirectory(List<LogFIleDirectory> entries, List<String> path) { }
            public void selectFile(File file) { }
            public void showText(File file, String value) { text.add(value); }
            public void missingFile() { fail("stale failure reached view"); }
        }, new File("logs"), new LogPresenter.Files() {
            public List<LogFIleDirectory> list(File directory) { return Collections.emptyList(); }
            public String read(File file) throws IOException { if (file.getName().equals("old")) throw new IOException(); return file.getName(); }
        }, worker, ui);
        p.open(new LogFIleDirectory(LogFIleDirectoryType.FILE, new File("old")));
        p.open(new LogFIleDirectory(LogFIleDirectoryType.FILE, new File("new")));
        worker.drain(); ui.drain(); assertEquals(Collections.singletonList("new"), text);
        p.open(new LogFIleDirectory(LogFIleDirectoryType.FILE, new File("late")));
        worker.drain(); p.detach(); ui.drain(); assertEquals(1, text.size());
    }
}
