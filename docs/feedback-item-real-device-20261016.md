# feedback / item 客户端真机验收

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

2026-10-16，设备 `DEVICE_SERIAL`，后端 `http://backend.example.test:8001`，最终使用 rk3576 烘焙模型。客户端学习、正常打印、改品打印、改品后删除流程通过；图片上传因测试服务缺少 COS 凭据被阻塞，**item / feedback 最终上报未通过验收**。用户确认本轮忽略 COS 配置，没有填假凭据或伪造上传成功。

## 实际入口与测试数据

仅准备步骤通过 SDK `bindDevice` 绑定独立测试门店。新增商品通过客户端商品管理表单；学习通过商品选择弹窗、真实相机的“拍照学习”和“保存学习”；收银使用真实相机识别结果，点击改品、打印或删行按钮。未注入识别框，未直接调用 learn、submitFeedback 或后端写接口代替客户端操作。

- 后端设备：`DEVICE_ID`。
- 测试门店：`SHOP_ID`，外部标识 `EXTERNAL_SHOP_ID`。
- 账号：`ACCOUNT_ID`，由 SDK 内置绑定配置确定。
- 商品 A：`00FbA1016`，计件，¥1.23；商品 B：`00FbB1016`，计件，¥2.34。
- A 学习真实画面中的两个烘焙物体，供同一测试类别的多框反馈验证；B 仅用于人工改品。测试数据不代表真实商品分类。

## 结果

| 场景 | 客户端实测 | 上报结果 |
| --- | --- | --- |
| 拍照学习 | 首次成功学习 2 个框，UID 1、2；小图在学习列表显示，重启后仍可识别 | 两张小图均由 SDK 后台尝试上传；`PhotoUploadException`，没有创建 item |
| 正常识别打印 | 2 个真实识别目标，A × 2，¥2.46；`printer_result=sent` | `feedback=fail:-21`，失败发生在图片上传阶段 |
| 改品后打印 | 选择识别行，从客户端改为 B × 2，¥4.68；`printer_result=sent` | `feedback=fail:-21`，同一上传阻塞 |
| 改品后删空 | 先改为 B，再点击行内“删”；清单清空并触发独立删除反馈 | `deleted_feedback=fail:-21`，同一上传阻塞 |

打印成功在这里指真实 USB 字节发送成功，不推断机械出纸结果。客户端 UI 测试通过也不代表异步反馈上报成功。

后端最终核对：该测试门店两种 PLU 的 `Item` 数量为 0，该设备的 `Feedback` 数量为 0。请求到达 `/api/v2/system/upload_token/`，返回 HTTP 500；服务抛出“文件桶 COS 凭据未配置：请设置 FILE_COS_SECRET_ID / FILE_COS_SECRET_KEY 环境变量”。学习和收银均在此阶段停止，未进入 `/api/v2/item/` 或 `/api/v2/feedback/` 的本轮写入验证。候选顺序、distance 三位小数、删除 result 空对象 等**后端落库断言尚未验证**。

## 测试中发现的模型切换缺陷

最初生鲜模型拍照返回无检测框。按用户要求，通过识别设置的真实“切换模型”入口切到 bakery，授权和模型状态检查通过，但实际 detect / batchLearn 返回：

```text
code=-6
object/code=-5 engine_create: 建链失败（识别链侧）
```

重启 SDK 后仍失败。只读检查发现 SDK 共用 `files/huoyan/vdb.vecdb`：切换前的生鲜库 `vec_dim=320`，样本数为 0。保留该空库备份为 `files/huoyan/vdb-fresh-empty-feedback-backup.vecdb`，让烘焙模型重新创建库后，`vec_dim=960`，检测和学习立即恢复，首次写入 2 个 A 样本。

这证明已有激活验收的 `READY/modelReady` 不能代表模型实际建链成功。SDK 需要解决不同模型识别库隔离或明确的切换生命周期；本次没有修改壳或 native 实现，没有把人工备份空库当成产品修复，也未删除任何已有学习样本。

## 代码、验证与复测

新增 `RealFeedbackClientDeviceTest`，必须传 `realFeedbackTest=true` 才执行；首次绑定另须传 `externalShopId`，指向已核对的测试门店外部标识。测试保留商品、样本和截图供对账；新增商品前拒绝覆盖已存在的测试 PLU。只覆盖客户端操作与本地状态，最终上报须独立查询后端。

修正 `CheckoutOutput` 写死 `feedback_type=1` 的误导日志，改为稳定请求 ID、实际 `FeedbackSubmission.Stage` 和真实结果码；普通与删空反馈均可定位 UPLOAD / SUBMIT / COMPLETE 阶段。未改变业务提交逻辑。

JBR 17 构建 Debug / AndroidTest 成功，客户端 452 个单元测试全部通过。最终安装新包后，删除链路复测通过（19.443 秒），日志明确为 `feedback_stage=UPLOAD deleted_feedback=fail:-21`；学习复测通过（11.857 秒），新增 UID 3、4，当前共保留 4 个 A 样本。实机各步骤单独执行，避免测试框架方法顺序影响准备数据。入口示例：

```bash
adb -s DEVICE_SERIAL shell am instrument -w -r \
  -e realFeedbackTest true -e feedbackMode modified \
  -e class cc.huoyan.retail.feedback.RealFeedbackClientDeviceTest#checkoutThroughClient \
  cc.huoyan.retail.test/androidx.test.runner.AndroidJUnitRunner
```

方法顺序：`bindAndCreateGoodsThroughClient`（仅首次准备）、`learnThroughClientCamera`、`configureCheckoutThroughClient`、`checkoutThroughClient`（mode 分别为 normal / modified / deleted）。设备保留烘焙模型、测试商品与学习库，打印模块开启、称重关闭、删行开启、打印机选择 BTP-N90LF；原分辨率未改动。

COS 配好后，应重新从客户端拍照和提交收银操作。当前学习小图上传是尽力执行、不自动重试，不能仅补配置后等待旧图片补传。先确认图片 PUT 与 item 创建，再对账 feedback 三张表、原图坐标、候选顺序和最终选择。历史截图和日志不随源码提供，复测时需重新采集。
