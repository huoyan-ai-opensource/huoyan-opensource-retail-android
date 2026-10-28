package cc.huoyan.retail.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * {@link SceneDefaults} 默认值表测试（Issue 决策 8）。
 */
public class SceneDefaultsTest {

    @Test
    public void defaultWeighingEnabled_allScenes_returnsFalse() {
        for (Scene scene : Scene.values()) {
            assertFalse("weighing 2026-08-27 起默认关（避免无秤设备报错），场景：" + scene,
                    SceneDefaults.defaultWeighingEnabled(scene));
        }
    }

    @Test
    public void defaultPrintEnabled_allScenes_returnsFalse() {
        for (Scene scene : Scene.values()) {
            assertFalse("print 2026-08-27 起默认关（避免无打印机设备报错），场景：" + scene,
                    SceneDefaults.defaultPrintEnabled(scene));
        }
    }

    @Test
    public void defaultScene_returnsSnacks() {
        assertEquals(Scene.SNACKS, SceneDefaults.defaultScene());
    }
}
