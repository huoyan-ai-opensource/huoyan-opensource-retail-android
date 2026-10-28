package cc.huoyan.retail.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * {@link ModuleConfig} 与 Context 无关部分的约定测试。
 * <p>
 * SP 读写路径（{@code getScene/setScene/isXxxEnabled/setXxxEnabled}）依赖 Android
 * {@code Context#getSharedPreferences}，纯 JUnit 不可测：
 * <ul>
 *     <li>工程未配置 {@code unitTests.returnDefaultValues = true}，单测中 android.jar
 *     桩方法直接抛 "not mocked" 异常；</li>
 *     <li>仓库单测无 Robolectric/mock 框架，且既有 {@code SPManager} 为绑定
 *     {@code RetailApplication.getInstance()} 的单例，无静态注入点，无法借道测试。</li>
 * </ul>
 * 故 SP 读写留待真机验证；本测试固定 key 常量与存储编码约定，防止后续接线任务误改。
 */
public class ModuleConfigTest {

    @Test
    public void spKeys_nonEmptyAndPairwiseDistinct() {
        String[] keys = {
                ModuleConfig.SP_SCENE,
                ModuleConfig.SP_WEIGHING_ENABLED,
                ModuleConfig.SP_PRINT_ENABLED,
                ModuleConfig.SP_TRANSFER_ENABLED,
        };
        for (String key : keys) {
            assertFalse(key == null || key.isEmpty());
        }
        for (int i = 0; i < keys.length; i++) {
            for (int j = i + 1; j < keys.length; j++) {
                assertNotEquals(keys[i], keys[j]);
            }
        }
    }

    @Test
    public void spFileName_sameAsSpManagerStore() {
        // 与 SPManager 共用 "HuoYanApplication" 同一 SP 文件，保证后续接线任务与既有配置同库
        assertEquals("HuoYanApplication", ModuleConfig.SP_FILE_NAME);
    }

    @Test
    public void sceneStorageName_roundTrips() {
        // ModuleConfig 以 Scene 枚举名为 SP 存储编码，需保证可无损反解析
        for (Scene scene : Scene.values()) {
            assertEquals(scene, Scene.fromName(scene.name()));
        }
    }

    @Test
    public void sceneFromName_nullEmptyOrInvalid_returnsNull() {
        assertNull(Scene.fromName(null));
        assertNull(Scene.fromName(""));
        assertNull(Scene.fromName("fresh"));
        assertNull(Scene.fromName("NOT_A_SCENE"));
    }
}
