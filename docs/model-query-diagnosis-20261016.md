# 已激活设备查询模型提示未激活：链路排查

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

后续已按用户要求切换 8001 并完成清空重装后的真实激活与切换，详见 [实机验收](model-activation-real-device-20261016.md)。本文保留 8000 环境的历史故障证据。

设备 `DEVICE_SERIAL` 的授权有效。故障源头是当前运行后端未部署新增模型列表路由；通用错误映射把路由 404 转成了“操作要求先激活”。本次只读排查，未重启服务、部署后端或更改授权。

## 实机与部署证据

- SDK 状态：`code=0`、`sdkState=READY`、`certificateValid=true`、`modelReady=true`；证书读取成功，`modelCode=bakery`。
- 客户端按契约调用 `queryLicenseModels()`，JNI 保留空参数，C 库发送 `POST /api/v2/licenses/models/`，请求体为 `{}`。
- 设备 SDK 配置的后端地址是 `http://backend.example.test:8000`；该端口由 `backend-container` 容器提供。
- 容器日志在 2026-10-16 22:00:34 记录该请求返回 `404 12533`，响应为 Django `Page not found at /api/v2/licenses/models/`，不是“找不到有效 License”的业务响应。
- 容器 `/app/apps/licenses/urls.py` 只有 current、renew、admin/sn-prebinds 路由，没有 models。容器未挂载后端源码目录。
- 远端工作区 `<backend-workspace>/apps/licenses/urls.py:17` 已包含 models 路由，其相关修改尚在工作区；运行容器未包含这项修改。

## 错误传播

| 层 | 实际行为 | 源码位置 |
| --- | --- | --- |
| 零售客户端 / AIDL | 传递 null，原样接收模型查询结果 | `ModelActivationSource.query`、`AidlSdkBridge.queryLicenseModels` |
| SDK 壳 | 经生命周期与 LicenseManager 调用 native 查询 | `ShellSdkCommFacade.queryLicenseModels`、`SdkLifecycleCoordinator.queryLicenseModels` |
| 后端容器 | 路由不存在，返回 HTML 404 | `/app/apps/licenses/urls.py` 与容器访问日志 |
| C 库 | 当前设备查询遇到任何 HTTP 404，均转换为 `HUOYAN_ERR_LICENSE_UNAVAILABLE=-50`，未区分路由 404 和业务 404 | 远端 `libwujing/src/sdk/huoyan_sdk.c:1629` |
| Java 会话 | 将 `ERR_LICENSE_UNAVAILABLE` 与 `ERR_NOT_ACTIVATED` 合并为 `SessionError.NotActivated` | `SessionErrorMapper.java:52` |
| Java 通信层 | 将 `NotActivated` 转为 `-42 / 操作要求先激活` | `CommErrorMapping.java:63` |

因此，客户端收到的 `-42` 并非本次查询对本地有效证书的否定。

## 构建核对

从设备已安装的 SDK APK 提取核对：arm64 `libwujing.so` SHA-256 为 `5ae65241786d71ea901dab419a0d9cc147ec9695a640be4bcbf6d7eb8aae1afa`，与 API 14 构建记录一致。APK 中源文件清单记录的 `src/sdk/huoyan_sdk.c` 和 `ports/android/wujing_jni.c` 哈希均与远端当前源码一致。安装包 DEX 的查询实现也已包含新增调用链，排除仅凭工作区源码误判已安装版本。

## 处理位置

1. 将已完成的新模型接口及对应激活契约部署到当前 8000 端口的后端服务，再用真实授权执行只读模型查询验收。
2. C 库应区分业务错误响应与路由不存在；Java 查询错误应保留原始类别，避免将接口不可用解释成需要重新激活。
3. 证书接口同时未返回激活序列号，这是切换提交所需的另一项待核实数据；它不是本次模型列表 404 的原因。模型列表恢复后仍需单独验收切换流程。

实机诊断日志保存在本地 `design/qa-model-selection-20261016/model-auth-diagnosis.log`。
