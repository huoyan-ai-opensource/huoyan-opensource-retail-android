# USB ESC/POS 小票打印

当前打印入口不集成厂商打印 SDK 库。`PrinterManager` 串行执行连接测试与打印，`ReceiptRenderer` 保留小票内容和 320 点宽排版，`EscPosReceiptWriter` 编码 ESC/POS 指令，通过原生 USB 发送。

## 本次支持范围

- 支持提供 USB Printer Class（class 7、protocol 1/2、alternate setting 0、Bulk OUT）的 ESC/POS 小票机。C50D 上的 BTP-N90LF 已发现为此接口，VID:PID 为 `154F:1300`。
- 开源版本仅提供通用 USB ESC/POS 接入。设备自带的内置打印机及厂商系统服务由使用方按设备接口自行适配，本项目不提供机型专用实现。
- USB 转串口、主板串口直连、私有 USB 接口及标签协议尚未实现。
- USB 接口可用不等于设备支持 ESC/POS。设备必须已处于兼容指令模式；不能仅凭 USB 枚举承诺机型兼容。
- 小票包含店名、时间、金额、商品行、汇总、EAN-13 条码与结束语。正文保持 320 点宽和原字号；纸张宽度可选 58mm（384 点）或 80mm（576 点），以位图白边居中，分别保留左右 32 点或 128 点。条码按 300 点重新生成并保留静区，不缩放或裁切。规格以 203dpi 常见打印宽度为基准，80mm/512 点机型仍需单独适配。

## 使用与配置

打开打印设置，选择实际 USB 设备及纸张宽度（58mm / 80mm），点击连接测试或打印测试。纸宽默认 58mm，在任务提交时固定，后续切换不改变在途小票。首次访问需要系统 USB 授权。进入页面只枚举设备，不自动选择、授权或试打。

设备选择仅保存在 `printer_device`，值包含型号、VID/PID 和 USB 地址。不读取或迁移旧 SDK 配置，也不预设型号，升级后需重新选择一次。同型号唯一时可恢复拔插后变化的地址，多设备匹配不明确时必须重新选择。设备失败不会自动改用其他打印机。

设备列表及选中项仅显示产品名称，产品名称为空时显示 `USB Printer`。VID/PID 和 USB 地址仅用于内部标识及连接，不在界面展示；同名设备仍以独立选项值保存，不按名称合并。

打印测试使用与真实收银相同的模板，示例商品价格 20.00、重量 0.500kg、总额 10.00；不会修改购物车或提交销售反馈。

## 指令和回执

- 初始化：`ESC @`、`ESC S` 进入标准模式，`GS P 203 203` 设置单位，`ESC a 2` 设置已验证的右对齐。保留复位后的固件打印区域，不再用 `GS L 0` / `GS W` 把窄纸画布锚定到打印头最左端；正文在所选纸宽的画布内居中。此方式兼容当前窄纸靠右的定位假设，居中装纸且固件未配置纸宽的其他机型仍需纸面校准。
- 位图：`GS v 0` 单倍栅格，每带最多 128 行；透明像素合成白底，再按亮度二值化。
- 小票头尾之间保留一次换行；尾部保留全部可打印像素，去除末尾白行。已取消额外 5mm 的 `ESC J 40`，完整输出后使用 `GS V 66 0` 半切，只保留固件为切刀定位所需的送纸。模板顶部为 10 点（约 1.25mm），物理顶部空白还可能受打印头至切刀距离影响。
- 尾部全部发送完后固定等待 300ms，再发送切纸；切纸后保留 100ms 收尾等待才释放设备锁。按用户要求已移除票长、打印速度与剩余输出时间估算，长短票均采用相同等待。等待被中断则停止后续指令。固定等待不是机械完成回执，实际切口需试打确认。
- USB 每次最多写 4096 字节，一条指令限时 10 秒，正确推进短写偏移；失败立即停止，不自动重连重发。
- 打印前可选读取 USB 类状态 `GET_PORT_STATUS`；能识别的缺纸、离线及错误会阻止打印。不支持状态读取时允许发送。未实现厂商专有取票、切刀、开盖状态及出纸完成确认。
- 成功回执只表示全部字节已发送。发送中断可能已经出纸，不自动重试；补打前检查纸张，必要时重启打印机清空残留缓冲。
- 连接由一次后台任务独占并在结束时释放；页面销毁不关闭正在打印的连接。设置页用 ViewModel 恢复在途操作，等待使用本页 LoadingDialog，结果使用 ToastEx。

## 验证

使用 JBR-17 执行 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`。

`EscPosEncoderTest`、`EscPosReceiptWriterTest`、`UsbBulkWriterTest`、`UsbPrinterSelectionTest` 覆盖纸宽白边、位序、尺寸、透明像素、长票分带、短写、失败停止、设备选择、拔插地址恢复及设备歧义；`PrintSettingsPresenterTest` 覆盖重复点击、任务设备快照和视图重建。

`EscPosUsbDeviceTest` 默认只验证无厂商 SDK 的 USB 连接、真实 Android 位图编码及设置页交互，不出纸。仅显式传入 instrumentation 参数 `-e printReceipt true` 才输出一张示例票。真实纸面、条码扫描、进纸与切刀效果需要实机观察确认。

用户已确认 C50D / BTP-N90LF 使用 58mm、取消额外 5mm 走纸、切纸前固定等待 300ms 时，小票正常完整打印。单元测试覆盖完整尾部、固定等待、中断不切纸和设备选择。Android 位图检查已解码 EAN-13 并保存尾部预览；80mm 及其他机型尚待各自纸面验证。

上一轮纸宽 UI 验证中，`PrinterPaperDeviceTest` 检查 58/80mm 选择、持久化与控件完整可见；使用 `-e paperOrientation landscape` 或 `portrait` 指定预期窗口方向，并断言实际 Configuration。C50D 不响应 Activity 的竖屏请求，竖屏布局使用临时 1080×1920、240dpi 窗口模拟，检查后恢复原生 1920×1080。上一轮 Lint 无错误，现有 730 项警告仍保留。

已移除 POSSDK 两个 JAR、JNA 及打印 SDK 专用 native 库；秤的 AclasDriverSdk 和 libAclasArmPos 保留。

## 纸宽设置 UI 对照

| 页面 | 设计节点 | XML | 控制器与验证 |
| --- | --- | --- | --- |
| 横屏打印设置 | huoyan-retail.pen / VVEHo | layout-land、layout-w1100dp-land、默认 page_print.xml | PrintSettingsFragment / PrinterPaperDeviceTest |
| 竖屏打印设置 | huoyan-retail.pen / KpnOZ | layout-port/page_print.xml | 同一控制器和纸宽持久化 |
| 嵌入设置分区 | 复用纸宽行 | fragment_print_settings.xml、printer_paper_setting.xml | 同一配置读写与提交快照 |

设计中的设备名是示例，不作为真实连接回执。纸宽选项使用项目 SelectOptionDialog；横竖屏设计源文件通过 Pencil 同步并保存。
