# 按客户修改发行 JAR 的账号

在仓库根目录执行（需要 Python 3，无需 Android SDK、Gradle 或重新编译）：

```bash
python3 huoyan-sdk-client/tools/set_account.py \
  --jar huoyan-sdk-client/build/outputs/jar/huoyan-sdk-client-2.0.0.jar \
  --account-id 1234567890123456789 \
  --output huoyan-sdk-client/build/outputs/jar/huoyan-sdk-client-2.0.0-customer.jar
```

将示例 `1234567890123456789` 换成客户的实际 accountId。命令输出修改前后的账号，生成客户专用 JAR，原包保持不变。输出路径必须尚不存在，其父目录必须存在。脚本可以单独复制使用，不依赖仓库其他文件。

JAR 内的 `raw/config` 内容如下，绑定逻辑只从这里读取账号：

```properties
Account_id=2115027529298350203
```

源码配置位于 `src/main/resources/raw/config`，打包后位于 JAR 根目录的 `raw/config`，不是 Android 的 `res/raw`。工具仅改写此配置，所有 class 内容保持不变，支持再次修改已生成的客户 JAR。账号范围是 `1`～`9223372036854775807`，全程按整数处理。

仅支持包含 `raw/config` 的未签名 JAR。旧版把账号编译为常量的 JAR 需要先用本次新版源码重新打包一次；此后即可只改配置。配置文件只保留一行 `Account_id=正整数`，可以添加 `#` 或 `!` 注释。缺失、重复或非法配置时命令报错，不生成输出。运行时配置缺失或账号无效也会报错，不回退到其他账号。此操作应在客户将 JAR 编译进 APK 之前完成；已安装 APK 需要换用新 JAR 后重新构建和安装。

交付完整 ZIP 时，先解压 `clientDistribution` 产物，再用新 JAR **替换** `libs/huoyan-sdk-client-2.0.0.jar`，重新压缩交付。不要同时放入两个主 JAR。该工具只修改指定 JAR，不会自动更新现有 ZIP；之后运行 Gradle 打包仍会生成 `src/main/resources/raw/config` 中账号的原包。

客户工程应保留 Java 资源 `raw/config`，不要在 Android packagingOptions 中排除此路径，也不要在宿主中再添加同名资源。

公共 API 的 `bindDevice` 调用方式不变，客户仍只传外部标识和可选门店信息。若客户使用新的 Android 包名，仍需登记 SDK AIDL 白名单。

首次生成原包：

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :huoyan-sdk-client:clientDistribution
```

发行工具回归测试（需要 Python 3 和 JDK 17）：

```bash
python3 -m unittest discover -s huoyan-sdk-client/tools -p 'test_*.py'
```
