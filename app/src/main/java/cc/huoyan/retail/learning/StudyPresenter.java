package cc.huoyan.retail.learning;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.GsonUtil;
import cc.huoyan.retail.data.GoodsEntity;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** 学习主流程：商品选择、拍照互斥、即时保存收尾、遗忘及页面生命周期。 */
final class StudyPresenter {
    /** 学习页仅处理控件、加载弹窗和图片列表展示。 */
    interface View {
        /** 选择变化后清理旧列表并请求当前商品历史图片。 */
        void showLearningTarget(GoodsEntity goods);
        /** 拍照在途时提示不能切换或退出。 */
        void busy();
        /** 已接受拍照请求，显示处理中。 */
        void captureStarted();
        /** 展示成功学习的样本，并刷新商品学习状态。 */
        void captured(GoodsEntity goods, List<StudyPicEntity> pictures);
        /** SDK 学习已即时生效，只更新保存展示和提示。 */
        void saved(GoodsEntity goods);
        /** 当前商品整组遗忘成功，执行本地保存完成的展示。 */
        void forgotten(GoodsEntity goods);
        /** 删除成功后按 UID 更新当前商品列表。 */
        void removed(StudyPicEntity picture);
        /** 结束加载并按资源文案展示错误原因。 */
        void failed(int message, String reason);
    }
    /** 持久化与图像处理端口；实现不得持有 Activity。 */
    interface Source {
        /** 执行学习并返回已生效样本；active 仅控制可选图片查询。 */
        Capture capture(byte[] bytes, int width, int height, GoodsEntity goods, BooleanSupplier active) throws Exception;
        /** 遗忘提交时商品并清理对应本地记录。 */
        void forgetClass(GoodsEntity goods) throws Exception;
        /** 按 UID 删除 SDK 样本和本地小图。 */
        void forgetSample(StudyPicEntity picture) throws Exception;
        /** 清除受操作影响的图片和学习标记缓存。 */
        void invalidate(GoodsEntity goods);
        /** 已排队学习完成后释放帧转换资源。 */
        void close();
    }
    /** 一次 SDK 学习的显示记录。 */
    static final class Capture {
        /** 可显示及遗忘的成功样本，即使缺图也保留 UID。 */
        final List<StudyPicEntity> pictures;
        /** 固定本次显示列表，避免后台继续修改。 */
        Capture(List<StudyPicEntity> pictures) {
            this.pictures = new ArrayList<>(pictures);
        }
    }
    /** 当前视图，销毁时释放；仅在主线程访问。 */
    private View view;
    /** 学习和文件适配器，页面销毁后仍能完成已提交写操作。 */
    private final Source source;
    /** 后台写操作队列，宿主只允许有序关闭，不中断写入。 */
    private final Executor worker;
    /** 不依赖 Activity SafeHandler 的主线程交付器，保证销毁后仍收尾。 */
    private final Executor ui;
    /** 当前学习商品快照，页面展示使用同一快照。 */
    private GoodsEntity selected;
    /** 当前选择代次，防止遗忘回执改写其他商品界面。 */
    private int selectionGeneration;
    /** 拍照从接受请求到处理完回执的互斥标志。 */
    private boolean capturing;
    /** 提供给后台图片查询的取消标志，不取消已生效学习。 */
    private volatile boolean detached;
    /** 注入视图和资源端口，Presenter 不访问 Android 控件。 */
    StudyPresenter(View view, Source source, Executor worker, Executor ui) {
        this.view = view; this.source = source; this.worker = worker; this.ui = ui;
    }
    /** 非法 v2 分类仍允许选中，实际学习或遗忘时报告具体原因。 */
    void select(GoodsEntity target) {
        if (view == null || target == null || sameProduct(selected, target)) return;
        if (capturing) { view.busy(); return; }
        selected = GsonUtil.fromJson(GsonUtil.toJson(target), GoodsEntity.class);
        ++selectionGeneration; view.showLearningTarget(selected);
    }
    /** 接受点击时已克隆的相机帧；后台只持有商品快照和像素数据。 */
    void capture(byte[] bytes, int width, int height) {
        if (view == null) return;
        if (selected == null) { view.failed(R.string.tip_no_goods_selected, null); return; }
        if (capturing) return;
        capturing = true;
        GoodsEntity target = selected;
        view.captureStarted();
        worker.execute(() -> {
            try {
                Capture receipt = source.capture(bytes, width, height, target, () -> !detached);
                ui.execute(() -> {
                    capturing = false;
                    if (view != null) view.captured(target, receipt.pictures);
                });
            } catch (Exception error) {
                ui.execute(() -> { capturing = false; if (view != null) view.failed(R.string.toast_study_failed, error.getMessage()); });
            }
        });
    }
    /** 学习立即持久化；保存只失效缓存和展示成功。 */
    void save() {
        if (view == null) return;
        if (selected == null) { view.failed(R.string.tip_no_goods_selected, null); return; }
        source.invalidate(selected); view.saved(selected);
    }
    /** 整组遗忘使用提交快照；切换商品只丢弃 UI 回执，不撤销已提交操作。 */
    void clear() {
        if (view == null) return;
        if (selected == null) { view.failed(R.string.tip_no_goods_selected, null); return; }
        GoodsEntity target = selected;
        int request = selectionGeneration;
        worker.execute(() -> {
            try {
                source.forgetClass(target);
                ui.execute(() -> {
                    if (view != null && request == selectionGeneration) view.forgotten(target);
                });
            } catch (Exception error) {
                ui.execute(() -> { if (view != null && request == selectionGeneration) view.failed(R.string.toast_clear_failed, error.getMessage()); });
            }
        });
    }
    /** 单样本删除完成后更新当前列表；旧商品结果不改写新列表。 */
    void remove(StudyPicEntity picture) {
        if (view == null || picture == null) return;
        int request = selectionGeneration;
        worker.execute(() -> {
            try {
                source.forgetSample(picture);
                ui.execute(() -> {
                    if (view != null && request == selectionGeneration
                            && selected != null && Objects.equals(selected.getPlu(), picture.getClazzName())) view.removed(picture);
                });
            } catch (Exception error) {
                ui.execute(() -> { if (view != null && request == selectionGeneration) view.failed(R.string.toast_clear_records_failed, error.getMessage()); });
            }
        });
    }
    /** 拍照处理期间拒绝退出，其余情况允许页面结束。 */
    boolean finish() {
        if (capturing) { if (view != null) view.busy(); return false; }
        return true;
    }
    /** 页面销毁时释放资源，在途拍照的回执到达后单独收尾。 */
    void detach() {
        if (detached) return;
        detached = true; view = null; ++selectionGeneration; worker.execute(source::close);
    }
    /** 商品选择按 PLU 判断，不依赖数据库对象实例。 */
    private static boolean sameProduct(GoodsEntity left, GoodsEntity right) {
        return left != null && right != null && Objects.equals(left.getPlu(), right.getPlu());
    }
}
