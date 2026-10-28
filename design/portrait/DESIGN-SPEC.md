# 火眼零售 · Design Tokens

浅灰底配白色面板，文字用深蓝，主操作用品牌蓝，金额提示用橙色。横屏与竖屏共用。

## 配色

| Token | 色值 | 用途 |
| --- | --- | --- |
| `color.bg.page` | `#F2F4F8` | 页面背景 |
| `color.bg.surface` | `#FFFFFF` | 面板、商品行、底栏 |
| `color.bg.selected` | `#EEF0FF` | 选中商品、选中区域 |
| `color.text.primary` | `#293A51` | 标题、商品名、正文 |
| `color.text.secondary` | `#6E7C90` | 单位、说明、辅助信息 |
| `color.text.inverse` | `#FFFFFF` | 主按钮文字 |
| `color.primary` | `#3F3EED` | 主按钮、选中分类、链接 |
| `color.primary.pressed` | `#3231C9` | 主按钮按下 |
| `color.primary.soft` | `#EDF0FF` | 浅蓝底、计件标签 |
| `color.accent` | `#FA6400` | 大字号金额、强调图形 |
| `color.accent.text` | `#B54708` | 小字号橙色文字 |
| `color.accent.soft` | `#FFF0E5` | 重试按钮、橙色提示底 |
| `color.success` | `#27835B` | 稳定、成功、称重标签 |
| `color.success.soft` | `#EAF3ED` | 成功、称重标签底 |
| `color.error` | `#B42318` | 错误、异常 |
| `color.error.soft` | `#FEF3F2` | 错误提示底 |
| `color.border` | `#E7EBF1` | 边框、分隔线 |
| `color.disabled.bg` | `#E7EBF1` | 禁用背景 |
| `color.disabled.text` | `#6E7C90` | 禁用文字 |
| `color.preview.bg` | `#283342` | 摄像头占位背景 |
| `color.overlay` | `#142033` / 44% | 弹层遮罩 |

## 字体

字体沿用 Android 中文无衬线字体；设计稿使用 Noto Sans SC。

| Token | 值 | 用途 |
| --- | --- | --- |
| `font.size.caption` | `14sp` | 辅助信息 |
| `font.size.body` | `16sp` | 正文、单价 |
| `font.size.button` | `18sp` | 按钮文字 |
| `font.size.subtitle` | `20sp` | 商品名、区域标题 |
| `font.size.title` | `24sp` | 页面、面板标题 |
| `font.size.amount` | `32sp` | 合计金额 |
| `font.size.reading` | `48sp` | 称重读数 |
| `font.weight.regular` | `400` | 正文、说明 |
| `font.weight.bold` | `700` | 标题、金额、主操作 |
| `font.lineHeight.body` | `1.4` | 正文行高倍率 |

## 间距与形状

| Token | 值 |
| --- | --- |
| `space.1` | `4dp` |
| `space.2` | `8dp` |
| `space.3` | `12dp` |
| `space.4` | `16dp` |
| `space.5` | `20dp` |
| `space.6` | `24dp` |
| `space.8` | `32dp` |
| `layout.pagePadding` | `16dp` |
| `layout.sectionGap` | `16dp` |
| `layout.itemGap` | `8dp` |
| `radius.tag` | `4dp` |
| `radius.control` | `8dp` |
| `radius.panel` | `12dp` |
| `radius.sheet` | `20dp`，仅顶部 |
| `border.width` | `1dp` |
| `elevation.panel` | `0dp` |

## 控件与动效

| Token | 值 |
| --- | --- |
| `size.touchMin` | `48 × 48dp` |
| `size.buttonHeight` | `56dp` |
| `size.inputHeight` | `56dp` |
| `size.keyHeight` | `56dp` |
| `size.icon` | `24dp` |
| `motion.feedback` | `150ms` |
| `motion.sheet` | `220ms` |
| `motion.easing` | `ease-out` |

## 常用组合

- **主按钮**：`primary` 背景 + `text.inverse` 文字 + `radius.control`。
- **次级按钮**：`primary.soft` 背景 + `primary` 文字。
- **重新识别**：`accent.soft` 背景 + `accent.text` 文字。
- **商品面板**：`bg.surface` 背景 + `radius.panel` + `space.4` 内边距。
- **合计金额**：`accent` + `font.size.amount` + `font.weight.bold`。

尺寸使用 dp，文字使用 sp；不按横屏／竖屏整体倍增 Token。橙色小字使用 `accent.text`，状态同时提供文字说明。

页面分区、首页卡片和状态显示见 [横竖屏布局适配方案](ORIENTATION-ADAPTATION.md)；选项弹窗见 [通用单选弹窗](../SELECT-DIALOG.md)。
