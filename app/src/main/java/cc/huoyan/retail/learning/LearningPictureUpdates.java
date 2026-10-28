package cc.huoyan.retail.learning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按 SDK UID 合并图片；保持本次拍照及已显示图片的相对顺序。 */
final class LearningPictureUpdates {
    private LearningPictureUpdates() { }

    /** 接纳新学习回执，并清除同 UID 上一代样本的保存、删除状态。 */
    static List<StudyPicEntity> prepend(List<StudyPicEntity> current,
            List<StudyPicEntity> captured, Set<Long> saved, Set<Long> removed) {
        Map<Long, StudyPicEntity> unique = new LinkedHashMap<>();
        for (StudyPicEntity picture : captured) {
            // 学习成功回执代表新样本；SDK 会复用已删除的最大 UID。
            saved.remove(picture.getUid());
            removed.remove(picture.getUid());
            unique.putIfAbsent(picture.getUid(), picture);
        }
        for (StudyPicEntity picture : current) {
            if (!removed.contains(picture.getUid())) unique.putIfAbsent(picture.getUid(), picture);
        }
        return new ArrayList<>(unique.values());
    }

    static List<StudyPicEntity> mergeHistory(List<StudyPicEntity> current,
            List<StudyPicEntity> history, Set<Long> saved, Set<Long> removed) {
        List<StudyPicEntity> latest = new ArrayList<>(history);
        latest.sort((a, b) -> Long.compare(b.getUid(), a.getUid()));
        Map<Long, StudyPicEntity> remaining = new LinkedHashMap<>();
        for (StudyPicEntity picture : latest) {
            if (!removed.contains(picture.getUid())) remaining.putIfAbsent(picture.getUid(), picture);
        }
        Map<Long, StudyPicEntity> merged = new LinkedHashMap<>();
        for (StudyPicEntity picture : current) {
            long uid = picture.getUid();
            StudyPicEntity stored = remaining.remove(uid);
            if (removed.contains(uid)) continue;
            // 保存之前保留拍照回执的反馈标识，且不能丢掉查询期间刚拍的照片。
            if (picture.getCaptureTimestamp() > 0 && !saved.contains(uid)) {
                merged.putIfAbsent(uid, picture);
            } else if (stored != null) {
                merged.putIfAbsent(uid, stored);
            }
        }
        merged.putAll(remaining);
        return new ArrayList<>(merged.values());
    }

    /** 合并局部分页，保留未出现在本页中的已展示图片及其顺序。 */
    static List<StudyPicEntity> mergePage(List<StudyPicEntity> current,
            List<StudyPicEntity> page, Set<Long> saved, Set<Long> removed) {
        Map<Long, StudyPicEntity> history = new LinkedHashMap<>();
        for (StudyPicEntity picture : current) {
            if (saved.contains(picture.getUid())) {
                StudyPicEntity stored = new StudyPicEntity();
                stored.setUid(picture.getUid());
                stored.setClazzName(picture.getClazzName());
                stored.setFilePath(picture.getFilePath());
                history.put(stored.getUid(), stored);
            } else {
                history.put(picture.getUid(), picture);
            }
        }
        for (StudyPicEntity picture : page) history.put(picture.getUid(), picture);
        return mergeHistory(current, new ArrayList<>(history.values()), saved, removed);
    }
}
