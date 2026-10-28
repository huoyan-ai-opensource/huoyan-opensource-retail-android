# 字符串 PLU、识别反馈与学习小图

学习、识别、遗忘、类别列表、图片查询、AIDL 与 WS 全链路使用 PLU 原值字符串，允许 1–50 位 ASCII 英文字母与数字；大小写和前导零保留。`A001`、`a001`、`001`、`1` 是不同分类。样本 UID 和商品 ID 仍是数值标识，不能作为 PLU 使用。

前台提交 feedback：每个识别目标对应一个 item，包含该框的 detect、最终 result 及原图坐标。detect 首项为该框原始命中结果，随后为按 SDK 首次出现顺序排列的同框商品候选；每框按 clazz 原值去重，同商品不同距离也只保留一条，命中保留原始距离，其余商品保留首次候选的距离，不包含其他框的识别记录；无候选时仍保留原始命中。修改/删除分别使用 item_type 1/2，删除时 result 固定为 {}，即使删除前已经改品，人工操作不改写 detect。feedback_type 根据整帧 items 派生为 0/1。detect 中的 distance 保留三位小数，不上传检测 score。

识别后的后台商品查询同时读取命中与候选编码，将候选名称固定在本框快照中。候选即使没有入车，上报也携带查询到的名称；SDK 商品目录确实缺失时名称为空，不用编码伪造名称。确认上报时不发起同步商品查询。

SDK 壳收到学习小图后自动上传并调用 `/api/v2/item/` 登记 clazz、image_url、type=0（识物）、is_selected=0。前台不再提交学习 feedback。云端重学读取 item summary 和按 clazz 的 image 清单，不再使用 goods/image 分页协议。

这是 AIDL、JNI 和原生数据库的同步升级，不提供旧整数接口或旧 schema 迁移。VDB schema 3 使用大小写敏感文本；旧库返回版本错误，不自动删除。部署时需同时更新 SDK 壳和客户端。

## 模型选择接入

已对齐 SDK 壳 `662cde4`：AIDL 与 WS 均提供 `queryLicenseModels`，激活改为 `activate(activationCode, modelCode)`。模型为动态列表，不再映射四个固定 UI 选项。当前 feedback Parcel 字段、逐项 item_type、候选顺序和三位距离精度与该版本一致；本次补齐设备测试中遗漏的字符串 PLU 调用。学习小图仍由 SDK 壳上报，客户端不重复提交学习 feedback。
