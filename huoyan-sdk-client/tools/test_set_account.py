"""以真实 Java 资源读取验证出包工具，所有临时产物由测试回收。"""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

from set_account import CONFIG_PATH, parse_account, rewrite_jar


class SetAccountTest(unittest.TestCase):
    """覆盖账号精度、真实 JAR 读取和错误输入不落盘。"""

    def setUp(self):
        """为每项测试建立独立目录和最小发行包。"""
        # 临时工作区在本测试结束时自动清理。
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        # 路径仅属于当前测试，避免修改仓库产物。
        self.root = Path(self.temporary.name)
        self.source = self.root / "original.jar"
        self.output = self.root / "customer.jar"
        self.write_jar(b"Account_id=2097500510197125120\n")

    def write_jar(self, config, extra=None):
        """生成含独立保留条目的测试输入。"""
        with zipfile.ZipFile(self.source, "w") as jar:
            jar.writestr(CONFIG_PATH, config)
            jar.writestr("unchanged.bin", b"\x00\x01\xff")
            if extra:
                jar.writestr(*extra)

    def test_only_config_changes_and_can_rewrite(self):
        """两次修改可到达 long 上界，原始包和其他条目不变。"""
        original = self.source.read_bytes()
        self.assertEqual(2097500510197125120, rewrite_jar(self.source, self.output, 1))
        final = self.root / "final.jar"
        self.assertEqual(1, rewrite_jar(self.output, final, 9223372036854775807))
        with zipfile.ZipFile(final) as jar:
            self.assertEqual(b"Account_id=9223372036854775807\n", jar.read(CONFIG_PATH))
            self.assertEqual(b"\x00\x01\xff", jar.read("unchanged.bin"))
        self.assertEqual(original, self.source.read_bytes())

    def test_rejects_invalid_input(self):
        """拒绝浮点、溢出、零和负数等不可绑定账号。"""
        for value in ("0", "-1", "1.0", "1e5", "+1", "abc", "9223372036854775808"):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                parse_account(value)

    def test_never_overwrites(self):
        """现有输出和原始文件均不可被覆盖。"""
        self.output.write_bytes(b"keep")
        with self.assertRaises(ValueError):
            rewrite_jar(self.source, self.output, 123)
        with self.assertRaises(ValueError):
            rewrite_jar(self.source, self.source, 123)
        self.assertEqual(b"keep", self.output.read_bytes())

    def test_rejects_bad_config_and_signed_jar(self):
        """配置歧义及签名包失败时不能产生输出。"""
        for config in (b"", b"Account_id=1\nAccount_id=2\n", b"Account_id=bad\n"):
            self.write_jar(config)
            with self.assertRaises(ValueError):
                rewrite_jar(self.source, self.output, 123)
            self.assertFalse(self.output.exists())
        self.write_jar(b"Account_id=1\n", ("META-INF/SIGN.SF", b"signature"))
        with self.assertRaises(ValueError):
            rewrite_jar(self.source, self.output, 123)
        self.assertFalse(self.output.exists())

    def test_rejects_legacy_jar(self):
        """旧版没有资源配置的包必须先升级，不能生成无效客户包。"""
        with zipfile.ZipFile(self.source, "w") as jar:
            jar.writestr("legacy.class", b"old")
        with self.assertRaises(ValueError):
            rewrite_jar(self.source, self.output, 123)
        self.assertFalse(self.output.exists())

    def test_java_reads_modified_jar(self):
        """编译实际读取类，证明修改后无需重编译 class 即可读取新账号。"""
        sdk = Path(__file__).resolve().parents[1]
        java_home = os.environ.get("JAVA_HOME")
        javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
        java = str(Path(java_home) / "bin/java") if java_home else "java"
        runner = self.root / "ReadAccount.java"
        runner.write_text('''package cc.huoyan.sdk.client.internal;
/** 仅供工具集成测试使用的读取入口。 */
public final class ReadAccount {
    /** 输出 JAR 内配置，供测试断言。 */
    public static void main(String[] args) {
        System.out.println(BundledAccount.getAccountId());
    }
}
''', encoding="utf-8")
        subprocess.run([javac, "--release", "11", "-encoding", "UTF-8", "-d", str(self.root),
                        str(sdk / "src/main/java/cc/huoyan/sdk/client/internal/BundledAccount.java"),
                        str(runner)], check=True, capture_output=True)
        with zipfile.ZipFile(self.source, "a") as jar:
            for file in self.root.rglob("*.class"):
                jar.write(file, file.relative_to(self.root).as_posix())
        rewrite_jar(self.source, self.output, 2097500510197125121)
        result = subprocess.run([java, "-cp", str(self.output),
                                 "cc.huoyan.sdk.client.internal.ReadAccount"],
                                check=True, capture_output=True, text=True)
        self.assertEqual("2097500510197125121", result.stdout.strip())
        with zipfile.ZipFile(self.source) as before, zipfile.ZipFile(self.output) as after:
            for name in before.namelist():
                if name != CONFIG_PATH:
                    self.assertEqual(before.read(name), after.read(name))


if __name__ == "__main__":
    unittest.main()
