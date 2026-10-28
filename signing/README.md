# xinhai-debug-demo 实验签名

**该签名为非商业用途的开源版本实验签名，仅用于测试 Release 打包，不得用于商业发布。** 私钥和密码有意随仓库公开，不代表可信的生产发布身份。

本地与 CNB 的 Release 构建默认使用同一份 `xinhai-debug-demo.jks`，确保实验版本的签名一致。请保留此文件，不要在每次构建时重新生成。

| 配置 | 值 |
| --- | --- |
| 密钥库文件 | `signing/xinhai-debug-demo.jks` |
| 密钥库格式 | `JKS` |
| 密钥别名 | `xinhai-debug-demo` |
| 密钥库密码 | `xinhai-debug-demo` |
| 密钥密码 | `xinhai-debug-demo` |
| 密钥 / 证书算法 | RSA 2048 / SHA256withRSA |

此前使用本机调试签名安装的 APK，与此实验签名不同，不能直接覆盖安装。后续使用此实验签名的版本，递增 `versionCode` 后可覆盖安装。

商业发布必须使用独立的正式签名，通过不入库的 `local.properties` 完整配置 `signing.storeFile`、`signing.storePassword`、`signing.keyAlias`、`signing.keyPassword`。正式签名不受上述入库例外影响。
