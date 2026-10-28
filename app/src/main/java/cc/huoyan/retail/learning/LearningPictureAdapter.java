package cc.huoyan.retail.learning;

import cc.huoyan.retail.common.ui.RowViewHolder;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import cc.huoyan.retail.R;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** 合并展示已有样本与本次拍照，沿用原 UID 删除入口。 */
public final class LearningPictureAdapter extends StudyPicAdapter {
    /** 创建紧凑的学习图片列表。 */
    public LearningPictureAdapter() { super(R.layout.item_learning_picture); }

    /** 按 UID 局部更新图片，保留未变化条目的视图与位置。 */
    public void submitPictures(List<StudyPicEntity> pictures) {
        List<StudyPicEntity> previous = new ArrayList<>(getData());
        List<StudyPicEntity> next = new ArrayList<>(pictures);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).getUid() == next.get(newPosition).getUid();
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                StudyPicEntity old = previous.get(oldPosition), updated = next.get(newPosition);
                return Objects.equals(old.getFilePath(), updated.getFilePath())
                        && old.getCaptureTimestamp() == updated.getCaptureTimestamp()
                        && old.getPicId() == updated.getPicId();
            }
        });
        getData().clear();
        getData().addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override protected void convert(@NonNull RowViewHolder holder, StudyPicEntity picture) {
        super.convert(holder, picture);
        holder.setGone(R.id.newPicture, picture.getCaptureTimestamp() == 0);
    }
}
