package cc.huoyan.retail.settings;
import cc.huoyan.retail.testing.QueuedExecutor;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraSettingsPresenterTest {
    static class Screen implements CameraSettingsPresenter.View {
        int frames, restarts, skipped; String resolution;
        public void showFrame(String id, int width, int height) { frames++; }
        public void prepareSelection(String resolution) { this.resolution = resolution; }
        public void restartPreview() { restarts++; }
        public void restartDecision(boolean skip) { if (skip) skipped++; }
    }
    static class Device implements CameraSettingsPresenter.Device {
        boolean open, restarting; String id, resolution;
        public boolean open() { return open; }
        public boolean restarting() { return restarting; }
        public void selectCamera(String id, String resolution) { this.id = id; this.resolution = resolution; }
        public void selectResolution(String resolution) { this.resolution = resolution; }
    }
    @Test public void resumeRespectsOpenInflightAndThrottle() {
        Screen v = new Screen(); Device d = new Device(); CameraSettingsPresenter p = new CameraSettingsPresenter(v, d, Runnable::run, null);
        d.restarting = true; p.resume(10000); assertEquals(1, v.skipped); assertEquals(0, v.restarts);
        d.restarting = false; p.resume(10001); p.resume(11000); assertEquals(1, v.restarts);
        d.open = true; p.resume(20000); assertEquals(1, v.restarts);
        d.open = false; p.resume(20001); assertEquals(2, v.restarts); p.detach(); p.resume(30000); assertEquals(2, v.restarts);
    }
    @Test public void firstFrameCorrectsSavedResolutionButCannotReachDestroyedView() {
        Screen v = new Screen(); Device d = new Device(); QueuedExecutor ui = new QueuedExecutor();
        CameraSettingsPresenter p = new CameraSettingsPresenter(v, d, ui, "640*480");
        p.selectCamera("usb"); assertEquals("1280*720", d.resolution); assertEquals(d.resolution, v.resolution);
        p.frame("usb", 1920, 1080); ui.drain(); assertEquals("1920*1080", p.resolution()); assertEquals(1, v.frames);
        p.frame("usb", 1920, 1080); assertEquals(0, ui.pending());
        p.frame("new", 640, 480); p.detach(); ui.drain(); assertEquals(1, v.frames);
    }
}
