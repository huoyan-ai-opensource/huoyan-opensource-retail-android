package cc.huoyan.retail.learning;

import org.junit.Rule;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class StudyFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void deletesOnlyNamedProduct() throws Exception {
        File root = temp.newFolder("study");
        File product = new File(root, "ABC-123");
        assertTrue(product.mkdir());
        assertTrue(new File(product, "sample.jpg").createNewFile());
        File sibling = new File(root, "other");
        assertTrue(sibling.mkdir());
        assertTrue(StudyFiles.deleteProduct(root, "ABC-123"));
        assertFalse(product.exists());
        assertTrue(sibling.exists());
    }

    @Test public void rejectsTraversalAndRootBeforeDeletingAnything() throws Exception {
        File root = temp.newFolder("study");
        File sentinel = temp.newFile("keep");
        for (String plu : new String[]{"", ".", "..", "../keep", "../study-other", "/tmp", "a/b", "a\\b"}) {
            assertFalse(plu, StudyFiles.deleteProduct(root, plu));
        }
        assertTrue(root.exists());
        assertTrue(sentinel.exists());
    }

    @Test public void neverTraversesSymbolicLinks() throws Exception {
        File root = temp.newFolder("study");
        File outside = temp.newFolder("outside");
        File sentinel = new File(outside, "keep");
        assertTrue(sentinel.createNewFile());
        File linkedProduct = new File(root, "linkedProduct");
        Files.createSymbolicLink(linkedProduct.toPath(), outside.toPath());
        // 本用例依赖 Android/Linux 的 File 规范路径语义；Windows JDK 不解析目录符号链接。
        Assume.assumeTrue("当前文件系统的 File API 不解析符号链接", outside.getCanonicalFile().equals(linkedProduct.getCanonicalFile()));
        assertFalse(StudyFiles.deleteProduct(root, "linkedProduct"));
        File product = new File(root, "123");
        assertTrue(product.mkdir());
        Files.createSymbolicLink(new File(product, "link").toPath(), outside.toPath());
        assertTrue(StudyFiles.deleteProduct(root, "123"));
        assertTrue(sentinel.exists());
    }
}
