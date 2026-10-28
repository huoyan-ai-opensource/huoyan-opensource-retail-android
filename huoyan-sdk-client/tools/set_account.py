#!/usr/bin/env python3
"""改写发行 JAR 内 raw/config 的 Account_id，仅依赖 Python 3 标准库。"""

import argparse
import io
from pathlib import Path
import re
import zipfile


# 发行 JAR 内的账号配置资源路径。
CONFIG_PATH = "raw/config"


def parse_account(value):
    """验证账号为 Java long 范围内的十进制正整数，禁止浮点转换。"""
    if not re.fullmatch(r"[0-9]{1,19}", value):
        raise argparse.ArgumentTypeError("account-id 必须是十进制正整数")
    account = int(value)
    if not 1 <= account <= 9223372036854775807:
        raise argparse.ArgumentTypeError("account-id 必须在 1～9223372036854775807 之间")
    return account


def rewrite_jar(source, destination, account):
    """校验完整输入后生成新 JAR；保留原包和所有非账号条目。"""
    if not 1 <= account <= 9223372036854775807:
        raise ValueError("账号超出 Java long 正整数范围")
    if source.resolve() == destination.resolve() or destination.exists():
        raise ValueError("输出文件已存在或与输入相同，请指定新的输出路径")
    with zipfile.ZipFile(source) as jar:
        entries = jar.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError("JAR 包含重复条目，拒绝修改")
        for name in names:
            upper = name.upper()
            if upper.startswith("META-INF/") and (
                    upper.endswith((".SF", ".RSA", ".DSA", ".EC"))
                    or upper.startswith("META-INF/SIG-")):
                raise ValueError("不支持已签名 JAR，请使用未签名发行包")
        if CONFIG_PATH not in names:
            raise ValueError("JAR 缺少 raw/config，请先用支持配置资源的新版源码打包")
        config = jar.read(CONFIG_PATH).decode("utf-8")
        pattern = r"(?m)^[ \t]*Account_id[ \t]*=[ \t]*([0-9]{1,19})[ \t]*\r?$"
        matches = list(re.finditer(pattern, config))
        # 此文件采用单行 key=value 格式，拒绝重复或转义键，避免读写解释不一致。
        active_lines = [line.strip() for line in config.splitlines()
                        if line.strip() and not line.lstrip().startswith(("#", "!"))]
        if len(matches) != 1 or len(active_lines) != 1:
            raise ValueError("raw/config 必须只包含一行 Account_id=正整数，可附加注释")
        old = parse_account(matches[0].group(1))
        replacement = (config[:matches[0].start(1)] + str(account)
                       + config[matches[0].end(1):]).encode("utf-8")
        # 先在内存中完成校验和重打包，输入损坏时不留下半成品文件。
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as result:
            result.comment = jar.comment
            for entry in entries:
                data = replacement if entry.filename == CONFIG_PATH else jar.read(entry)
                result.writestr(entry, data)
    with destination.open("xb") as output:
        output.write(buffer.getvalue())
    return old


def main():
    """执行发行账号修改命令，失败时给出可操作的错误信息。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path, help="已打包的原始 JAR")
    parser.add_argument("--account-id", required=True, type=parse_account, help="客户账号 ID")
    parser.add_argument("--output", required=True, type=Path, help="新 JAR 路径（不能已存在）")
    args = parser.parse_args()
    try:
        old = rewrite_jar(args.jar, args.output, args.account_id)
    except (OSError, ValueError, argparse.ArgumentTypeError, zipfile.BadZipFile) as error:
        parser.exit(1, "修改失败：%s\n" % error)
    print("已生成 %s\naccountId: %s → %s" % (args.output, old, args.account_id))


if __name__ == "__main__":
    main()
