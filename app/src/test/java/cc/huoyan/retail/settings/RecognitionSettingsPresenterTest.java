package cc.huoyan.retail.settings;

import cc.huoyan.sdk.client.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 动态模型展示和过期查询隔离。 */
public class RecognitionSettingsPresenterTest {
    /** 新模型无需新增 Scene 枚举也可显示。 */
    @Test public void dynamicCurrentModelUsesServerName() {
        LicenseModels models = new LicenseModels(0, null, Arrays.asList(
                new LicenseModel("future-1", "动态模型一", "x", false),
                new LicenseModel("future-9", "动态模型九", "y", true)));
        RecognitionSettingsPresenter.Model model = RecognitionSettingsSource.currentModel(models, null);
        assertEquals("动态模型九", model.name); assertNull(model.version);
        assertNull(RecognitionSettingsSource.currentModel(new LicenseModels(-1, "failure", models.getModels()), null));
        assertNull(RecognitionSettingsSource.currentModel(new LicenseModels(0, null, Collections.emptyList()), null));
    }
    /** 查询代次隔离旧结果，销毁后不访问旧视图。 */
    @Test public void staleAndDetachedQueriesCannotUpdateView() {
        Queue<Runnable> worker = new ArrayDeque<>();
        List<RecognitionSettingsPresenter.Model> shown = new ArrayList<>();
        RecognitionSettingsPresenter.Model model = new RecognitionSettingsPresenter.Model("新模型", "v7");
        RecognitionSettingsPresenter presenter = new RecognitionSettingsPresenter(shown::add,
                worker::add, Runnable::run, () -> model);
        presenter.refresh(); presenter.refresh(); worker.remove().run();
        assertEquals(2, shown.size()); worker.remove().run(); assertSame(model, shown.get(2));
        presenter.refresh(); presenter.detach(); int count = shown.size(); worker.remove().run();
        assertEquals(count, shown.size());
    }
    /** 查询异常保留占位，不展示虚构模型。 */
    @Test public void failedQueryKeepsPlaceholder() {
        List<RecognitionSettingsPresenter.Model> shown = new ArrayList<>();
        new RecognitionSettingsPresenter(shown::add, Runnable::run, Runnable::run,
                () -> { throw new IllegalStateException(); }).refresh();
        assertNull(shown.get(shown.size() - 1));
    }
}
