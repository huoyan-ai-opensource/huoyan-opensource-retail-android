package cc.huoyan.retail.learning;

import java.io.File;
import java.io.IOException;

/** 学习图片的文件边界；商品编号只能是目录名，递归删除不跟随符号链接。 */
public final class StudyFiles {
    private StudyFiles() {}

    /** 判断 PLU 是否可安全用作单层目录名，保留字母、中文等既有编号。 */
    public static boolean isValidPlu(String plu) {
        if (plu == null || plu.trim().isEmpty() || ".".equals(plu) || "..".equals(plu)) return false;
        for (int i = 0; i < plu.length(); i++) {
            char c = plu.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) return false;
        }
        return true;
    }

    /** 删除指定商品的本地学习目录；非法编号或根目录下的符号链接返回失败。 */
    public static boolean deleteProduct(File studyRoot, String plu) {
        if (!isValidPlu(plu)) return false;
        try {
            File root = studyRoot.getCanonicalFile();
            File product = new File(root, plu);
            if (!product.equals(product.getCanonicalFile())) return false;
            return deleteWithoutFollowingLinks(product);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static boolean deleteWithoutFollowingLinks(File file) throws IOException {
        // 子级链接只删除链接本身，绝不遍历目标目录（包括指向根目录的链接）。
        if (!file.equals(file.getCanonicalFile())) return file.delete();
        if (!file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (!deleteWithoutFollowingLinks(child)) return false;
            }
        }
        return file.delete();
    }
}
