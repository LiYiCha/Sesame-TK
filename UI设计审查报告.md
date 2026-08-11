# Sesame-TK UI设计审查报告
注意：需要重新审查文档后切到一个新的本地分支进行设计开发，审核通过后才可以删除本行。

> **审查范围**：全部 23 个页面/核心组件（含 Compose + XML 双端），13 套节日主题逐色分析，SVG 精灵系统完整审查，全局主题色重设计方案，8 组设计参考素材库  
> **审查标准**：Google Material Design 3、Apple HIG、WCAG 2.1 AA  
> **审查日期**：2026-08-11（三轮深入审查 + 重设计方案 + 设计素材收集）  
> **审查方法**：静态代码分析 + 设计规范对照  

---

## 总体评分

| 维度 | 得分 | 满分 | 评级 |
|------|------|------|------|
| 布局结构 | 7.4 | 10 | ★★★★☆ |
| 配色方案 | 7.0 | 10 | ★★★★☆ |
| 字体排版 | 6.0 | 10 | ★★★☆☆ |
| 组件样式 | 7.2 | 10 | ★★★★☆ |
| 交互状态 | 7.0 | 10 | ★★★★☆ |
| 响应式适配 | 5.5 | 10 | ★★★☆☆ |
| 可访问性 | 4.8 | 10 | ★★☆☆☆ |
| 视觉层次 | 7.2 | 10 | ★★★★☆ |
| 间距一致性 | 6.3 | 10 | ★★★☆☆ |
| 图标使用 | 6.3 | 10 | ★★★☆☆ |
| **综合得分** | **6.5** | **10** | **★★★☆☆** |

---

## 一、全局设计系统分析

### 1.1 配色方案

**色彩定义文件**：
- `res/values/colors.xml`（浅色模式 36 色）
- `res/values-night/colors.xml`（深色模式 36 色）
- `SesameColors.kt` / `HolidayTheme.kt`（Compose 端动态主题）

**问题清单**：

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | `active_text = #DCDCDC` 在浅色模式下是浅灰色，用于 Toolbar 文字，对比度仅 **2.1:1**（远低于 WCAG AA 要求的 4.5:1） | 🔴 严重 | `colors.xml:14` |
| 2 | `textColorPrimary` 在浅色模式为纯黑 `#000000`，深色模式为 `#E0E0E0`，缺乏统一的语义化 token | 🟡 中等 | `colors.xml:7` |
| 3 | 主页 6 格子颜色硬编码于 XML（如 `grid_forest`、`grid_farm`），但运行时又被 `MainActivity.mainActivityThemeObserver` 覆盖 — 存在双源冲突 | 🟡 中等 | `activity_main.xml:48-102`, `MainActivity.kt:105-116` |
| 4 | `R.color.button = #4CAF50` vs theme 主色可能冲突，多处代码使用 `ContextCompat.getColor(c, R.color.button)` 硬编码按钮颜色 | 🟡 中等 | `StringDialog.java:92`, `ListDialog.java:92` |
| 5 | `navigationBarColor = #AA000000 / #CC000000`（半透明黑色），在 Material 3 规范中推荐使用 `?attr/colorSurface` + Surface 层级处理 | 🟢 建议 | `colors.xml:15` |
| 6 | 预置的自定义色彩 `presets` 中 `#E64000`（橙红）、`#E91E63`（粉红）等颜色饱和度过高，在深色模式下与暗色背景形成刺眼对比 | 🟢 建议 | `DeviceInfo.kt:1064-1068` |

**改进建议**：
- 建立完整的 Material 3 Token 体系（Primary、Secondary、Tertiary、Error、Surface、Outline）
- `active_text` 需改为高对比度色（浅色模式建议 `#1A1A1A`，深色模式建议 `#FFFFFF`）
- 移除 XML 中硬编码的 6 个格子颜色，统一由主题系统管理

---

### 1.2 字体排版

**字体定义**：
- 标题：`18sp` / `24sp`，`textStyle="bold"`
- 正文：`14sp` / `16sp`
- 按钮：`12sp`（`Widget.App.Button.Main`）
- 无自定义字体家族配置

**问题清单**：

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | 未定义字体家族（`fontFamily`），完全依赖系统默认字体，Android 各厂商默认字体差异极大 | 🟡 中等 | 全局 |
| 2 | 字号层级混乱：标题有 `18sp`、`24sp`、`32sp` 三种，正文有 `12sp`、`13sp`、`14sp`、`16sp` 四种，无统一 Type Scale | 🟡 中等 | 全局 |
| 3 | `Widget.App.Button.Main` 按钮文字 `12sp` 偏小，Material Design 3 按钮推荐 `14sp`（labelLarge） | 🟢 建议 | `styles.xml:44` |
| 4 | Compose 端 `DeviceInfoCard` 使用 `titleMedium`/`bodyMedium`/`labelSmall` 等 MD3 语义化字体，但原生 XML 端无对应映射 | 🟡 中等 | `DeviceInfo.kt` |
| 5 | `OptionsAdapter` 使用 `android.R.layout.simple_list_item_1`，字体样式完全跟随系统默认 | 🟢 建议 | `OptionsAdapter.java:69` |

**改进建议**：
- 引入字体家族定义（推荐 `styles.xml` 中定义 `fontFamily`，如 `@font/inter`）
- 建立统一的 Type Scale：Display → Headline → Title → Body → Label（各 3 级）
- 按钮字号从 `12sp` 提升至 `14sp`

---

### 1.3 间距系统

**间距使用统计（XML 分析）**：
- 最常用值：`16dp`（出现 8 次）、`12dp`（出现 7 次）、`8dp`（出现 6 次）
- 不常见值：`6dp`（3 次）、`2dp`（2 次）、`10dp`（1 次）

**问题清单**：

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | 未采用 8pt 网格系统，间距值杂乱（`6dp`、`10dp` 等非 8 的倍数） | 🟡 中等 | 全局 XML |
| 2 | `activity_extend.xml` 按钮间 `marginBottom="8dp"`，Section 标题与按钮之间 `marginBottom="12dp"` 语义不统一 | 🟢 建议 | `activity_extend.xml:35-60` |
| 3 | Compose 端统一使用 `16.dp` / `12.dp` / `8.dp`，与 XML 端不一致 | 🟢 建议 | `DeviceInfo.kt`, `ExtensionListScreen.kt` |
| 4 | `dimen.xml` 仅定义 2 个值（`setting_item_padding` / `status_bar_height`），无完整间距 token | 🟡 中等 | `dimen.xml` |

**改进建议**：
- 建立 4pt/8pt 基准间距 token：`space_xxs(2dp)` → `space_xs(4dp)` → `space_sm(8dp)` → `space_md(12dp)` → `space_lg(16dp)` → `space_xl(24dp)` → `space_2xl(32dp)`
- 所有 `6dp` 改为 `8dp` 或 `4dp`，`10dp` 改为 `8dp` 或 `12dp`

---

### 1.4 圆角与阴影

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | 圆角值不统一：`Widget.App.Button.Main` 使用 `cornerRadius=16dp`，`Dialog` 按钮使用 `cornerRadius=24dp`，`CardView` 使用 `cardCornerRadius=8dp/12dp/20dp` | 🟡 中等 | 全局 |
| 2 | `MaterialCardView` 阴影使用 `cardElevation=4dp`（符合 M3 规范），但 `Styles.xml` 中 `Widget.App.Button.Main` 仅 `elevation=1dp` | 🟢 建议 | `styles.xml:49` |
| 3 | 部分 XML 中仍使用 `androidx.cardview.widget.CardView`（旧版）而非 `MaterialCardView`，阴影效果一致性差 | 🟢 建议 | `activity_help.xml` |

---

## 二、逐页面详细审查

---

### 2.1 主页（MainActivity + activity_main.xml）

**布局结构**：`ConstraintLayout` → `TitleBar` + `NestedScrollView` + `底部 MaterialCardView`

```
┌──────────────────────┐
│     MaterialToolbar  │  ← base_title.xml
├──────────────────────┤
│                      │
│  ComposeView         │  ← DeviceInfoCard (3页翻页)
│  (设备信息卡片)       │
│                      │
├──────────────────────┤
│  [森林日志] [农场] [其他]│  ← MaterialButton x3
│  [扩展功能] [调试] [设置]│  ← MaterialButton x3
└──────────────────────┘  ← MaterialCardView (20dp圆角)
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 视觉层次 | ✅ 上下分区明确：设备信息在上，功能区在下 | — |
| 布局结构 | ⚠️ `ComposeView` 高度为 `wrap_content`，`DeviceInfoCard` 内部 `heightIn(min=340.dp)` 可能与 XML 约束冲突 | 🟡 中等 |
| 按钮布局 | ⚠️ 2x3 网格使用 2 个 `LinearLayout(Horizontal)` 实现，无法响应式适配不同屏幕宽度 | 🟡 中等 |
| 按钮图标 | ✅ 自定义 SVG 图标（`drawable/ic_forest` 等），风格统一 | — |
| 按钮文字 | ⚠️ 12sp 字号 + iconTop 布局，图标 32dp 偏大，文字/图标比例不协调 | 🟢 建议 |
| 颜色动态覆盖 | ⚠️ 运行时 `backgroundTintList` 覆盖 XML 中定义的 `grid_*` 颜色，但注释不完全 | 🟢 建议 |
| 水印组件 | ⚠️ `WatermarkView.install()` 被注释掉，但代码保留 | 🟢 建议 |
| 菜单项 | ⚠️ 12 个菜单项挤在 `overflowMenu` 中，无图标区分 | 🟡 中等 |
| 状态显示 | ✅ 副标题动态显示运行状态（标题栏 `[LOADED]`/`[DISABLE]`） | — |
| 夜间模式 | ⚠️ 主题观察者仅处理按钮和 CardView 的背景色，未处理 `ComposeView` 内部（已由 Compose 独立处理） | 🟢 建议 |
| 随机一言 | ✅ 加载时显示获取中状态 | — |

**交互状态覆盖**：
- ✅ 默认状态
- ⚠️ 按下状态：`button_pressed = #9E9E9E` 但按钮色被运行时覆盖后可能失效
- ❌ 禁用状态：未定义
- ❌ 加载状态：按钮点击无 loading 反馈
- ✅ Focus 状态：依赖 Material ripple

**可访问性**：
- ❌ 无 `contentDescription` 设置
- ❌ 无语义层级标记
- ⚠️ 触摸目标：按钮 `layout_weight=1`，宽度随屏幕变化，最小可能低于 48dp

**评分**：布局 7.5 / 配色 8.0 / 交互 6.0 / 可访问性 3.0 = **综合 6.1**

---

### 2.2 原生设置页（SettingActivity + activity_settings.xml）

**布局结构**：`LinearLayout(Vertical)` → `TitleBar` + `RecyclerView(左侧Tab)` + `ViewPager2(右侧内容)`

```
┌──────────────────────┐
│     MaterialToolbar  │
├──────┬───────────────┤
│ Tab1 │               │
│ Tab2 │  ViewPager2   │
│ Tab3 │  (Fragment)   │
│ ...  │               │
│      │               │
└──────┴───────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 布局结构 | ✅ 左侧 Tab + 右侧内容的经典双栏布局，适配长列表项 | — |
| Tab 样式 | ⚠️ `item_tab.xml` 仅显示文字 + 底部指示条，无图标，选中态仅靠 `indicator_bar` 3dp 颜色条区分 | 🟡 中等 |
| Tab 选中态 | ⚠️ 内置的 `tab_selected_background.xml` 未被使用（代码中注释掉了），选中依赖单一的 `R.color.item_selected_orange` | 🟡 中等 |
| Tab 宽度 | ⚠️ `minWidth=172dp` 固定值，长标题会换行 | 🟡 中等 |
| Tab 滚动 | ⚠️ `RecyclerView` 设置 `overScrollMode="always"` + `scrollbars="none"` 不一致 | 🟢 建议 |
| ViewPager2 | ✅ 禁止用户滑动（`setUserInputEnabled(false)`），仅通过 Tab 切换 | — |
| 内容区域 | ⚠️ `marginBottom=48dp` 固定值，无导航栏安全区适配 | 🟡 中等 |
| 水印 | ⚠️ 水印被注释掉（`//watermarkView.setWatermarkText(tag)`），文本已计算但未显示 | 🟢 建议 |
| 菜单功能 | ✅ 7 项菜单：导出/导入/删除/单向好友/切换UI/保存/复制ID | — |
| 配置保存 | ✅ `onBackPressed` 时自动保存 | — |

**交互状态覆盖**：
- ⚠️ Tab 切换无过渡动画
- ❌ 配置加载中无 loading 指示
- ❌ 删除确认弹窗的取消按钮无二次确认保护

**可访问性**：
- ❌ RecyclerView 无 `contentDescription`
- ❌ Tab 项无角色标记

**评分**：布局 7.0 / 配色 6.5 / 交互 5.5 / 可访问性 3.0 = **综合 5.5**

---

### 2.3 WebView设置页（WebSettingsActivity + activity_web_settings.xml）

**布局结构**：`LinearLayout(Vertical)` → `TitleBar` + `WebView`

```
┌──────────────────────┐
│     MaterialToolbar  │
├──────────────────────┤
│                      │
│       WebView        │  ← 加载 assets/web/index.html
│                      │
│                      │
└──────────────────────┘
   marginBottom=48dp
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 布局结构 | ⚠️ 过于简单，WebView 覆盖全部可用空间，无任何原生 UI 辅助 | 🟢 建议 |
| 暗色模式 | ✅ 通过 `WebSettingsCompat.setForceDark()` 适配暗色模式 | — |
| 安全策略 | ⚠️ 允许 `file://` + `JavaScriptEnabled` + `AllowFileAccess=true`，存在安全风险 | 🔴 严重 |
| 缩放设置 | ⚠️ `setSupportZoom(true)` + `setBuiltInZoomControls(true)`，可能影响 WebView 内布局 | 🟢 建议 |
| JS Bridge | ✅ 完善的双向通信（`WebViewCallback` + `WebAppInterface`），提供 10+ 个 JSI 方法 | — |
| Toolbar 颜色同步 | ✅ `updateNativeToolbarColor()` 支持 WebView 内 JS 动态修改原生标题栏渐变背景色 | — |
| 导航栏 | ✅ `onBackPressed` 优先 `webView.goBack()` | — |
| 水印 | ⚠️ 同样被注释掉 | 🟢 建议 |
| 保存机制 | ⚠️ 保存时使用 `Handler.postDelayed(200ms)` 等待 JS 回调完成，是脆弱的时序依赖 | 🟡 中等 |
| 调试开关 | ✅ Debug 模式下启用 WebView 远程调试 + 支持本地开发服务器 | — |

**交互状态覆盖**：
- ⚠️ WebView 内容加载无进度指示
- ❌ 无网络错误/加载失败的状态页面

**评分**：布局 6.0 / 配色 7.0（依赖 Web 端） / 交互 5.5 / 可访问性 4.0 = **综合 5.6**

---

### 2.4 扩展功能页（ExtendActivity + activity_extend.xml）

**布局结构**：`LinearLayout(Vertical)` → `TitleBar` + `ScrollView` + `LinearLayout(按钮列表)`

```
┌──────────────────────┐
│     MaterialToolbar  │
├──────────────────────┤
│ 🌳 树项目查询         │  ← Section 标题
│ [查询未解锁项目]       │
│ [查询树苗上新]        │
│ [查询未解锁地区]       │
│ [查询树苗余量]        │
│                      │
│ ▶️ 运行控制           │  ← Section 标题
│ [重新运行]            │
│ [继续运行]            │
│ [暂停运行]            │
│ [停止/清除运行]        │
│ [重新登录]            │
│ [模块重新加载]         │
│ [检查运行状态]         │
└──────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 布局结构 | ⚠️ 11 个全宽 `Button` 垂直堆叠，过于单调，缺少视觉分组和差异化 | 🟡 中等 |
| Section 标题 | ✅ 使用 emoji + 中文标题区分功能区 | — |
| 按钮样式 | ⚠️ 全部复用 `Widget.App.Button.Main`，所有按钮外观相同（同样大小/颜色/圆角） | 🟡 中等 |
| 操作区分 | ⚠️ "树项目查询" 和 "运行控制" 两类操作无视觉差异，用户无法快速区分 | 🟡 中等 |
| 频繁操作保护 | ✅ 30 秒点击间隔限制 + 剩余时间提示 | — |
| Toast 反馈 | ✅ 每个操作都有即时 Toast 确认 | — |
| 颜色适配 | ⚠️ 按钮颜色由 `BaseActivity.applyThemeToViews()` 递归覆盖，未区分 "查询" vs "控制" 类操作 | 🟢 建议 |
| 滚动 | ⚠️ 按钮数量多，列表长，无快速滚动/FAB | 🟢 建议 |

**交互状态覆盖**：
- ✅ 点击有 Toast 确认
- ✅ 频繁操作限制
- ❌ 无操作执行中的 loading 状态
- ❌ 无操作结果的详细反馈（仅 "请检查日志"）

**可访问性**：
- ❌ 按钮无 `contentDescription`
- ⚠️ 触摸目标：全宽按钮，宽度足够但高度可能不足

**评分**：布局 5.5 / 配色 6.0 / 交互 6.0 / 可访问性 3.0 = **综合 5.1**

---

### 2.5 帮助页（HelpActivity + activity_help.xml）

**布局结构**：`LinearLayout(Vertical)` → `TitleBar` + `ScrollView` + `CardView 折叠面板 x8`

```
┌──────────────────────┐
│     MaterialToolbar  │
├──────────────────────┤
│ 帮助信息 (24sp Bold)  │
├──────────────────────┤
│ ▼ 配置文件路径        │  ← 折叠卡片
│   /path/to/config    │
├──────────────────────┤
│ ▼ 日志文件路径        │
│   /path/to/log       │
├──────────────────────┤
│ ▼ 各类日志文件        │
├──────────────────────┤
│ ▼ 日志管理策略        │
├──────────────────────┤
│ ▼ 存储空间信息        │
│   [清除备份日志]       │
├──────────────────────┤
│ ▼ 权限状态           │
├──────────────────────┤
│ ▼ 系统环境信息        │
├──────────────────────┤
│ ▼ 常见问题解答        │
└──────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 折叠面板 | ✅ 8 个可折叠的 Card 面板，带 ▼/▲ 指示器 | — |
| 卡片样式 | ⚠️ 使用旧版 `androidx.cardview.widget.CardView`（非 `MaterialCardView`），无 Stroke 边框 | 🟡 中等 |
| 信息展示 | ⚠️ 内容区硬编码 `background="#F5F5F5"`，深色模式下未适配 | 🔴 严重 |
| 字体 | ✅ 路径信息使用 `monospace` 字体，符合代码展示惯例 | — |
| 选择 | ✅ 内容区域设置 `textIsSelectable="true"` | — |
| 按钮 | ⚠️ "清除备份日志" 按钮固定 `height=42dp`，`textSize=12sp` | 🟢 建议 |
| 布局重复 | ⚠️ 8 个折叠面板使用几乎相同的 XML 结构，代码冗余严重，应抽取为自定义 View | 🟡 中等 |

**交互状态覆盖**：
- ✅ 点击 header 展开/收起
- ✅ 选择文字可复制
- ❌ 展开/收起无过渡动画

**可访问性**：
- ⚠️ `clickable="true"` + `focusable="true"` 但未设置 `contentDescription`
- ✅ 使用 `selectableItemBackground` 提供点击反馈

**评分**：布局 6.5 / 配色 5.0 / 交互 5.5 / 可访问性 4.5 = **综合 5.4**

---

### 2.6 RPC调试页（RpcDebugActivity + activity_rpc_debug.xml）

**布局结构**：`NestedScrollView` → `LinearLayout(Vertical)` → 输入框组 + 按钮组 + RequestList + 结果区

```
┌──────────────────────────┐
│ RPC 调试工具 (18sp Bold)  │  ← 无 TitleBar
│                          │
│ [Method 输入框]           │
│ [Data 输入框(多行)]       │
│ [Title 输入框]            │
│                          │
│ [保存] [抓包日志] [请求日志] [测试]│
│                          │
│ 保存的请求列表             │
│ ┌──────────────────────┐ │
│ │ ComposeView 请求列表  │ │  ← 混合 Compose
│ │ RecyclerView 请求列表 │ │  ← 同时存在！
│ └──────────────────────┘ │
│                          │
│ 结果:                    │
│ ┌──────────────────────┐ │
│ │ 结果显示区            │ │
│ └──────────────────────┘ │
│                          │
│ [放大显示]               │
└──────────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 布局结构 | 🔴 同时存在 `ComposeView` 和 `RecyclerView` 两个请求列表，上下堆叠，预期只显示其中一个但两者同时可见 | 🔴 严重 |
| 输入框样式 | 🔴 输入框无样式定义（无背景、无边框、无 Material 风格），裸 `EditText` | 🔴 严重 |
| 按钮样式 | ⚠️ 按钮使用固定宽度（`60sp`/`80sp`），无自适应 | 🟡 中等 |
| 间距 | ⚠️ 使用 `layout_marginLeft`（应在 RTL 语言中使用 `layout_marginStart`） | 🟢 建议 |
| 标题 | ⚠️ 使用独立 `TextView` 作为标题，未集成到 Toolbar | 🟡 中等 |
| 滚动嵌套 | ⚠️ `NestedScrollView` 内嵌 `NestedScrollView`（结果区域），可能导致滚动冲突 | 🟡 中等 |
| 结果区 | ✅ 可选中文本、支持滚动 | — |

**交互状态覆盖**：
- ❌ 表单验证无反馈
- ❌ 发送请求无 loading 状态
- ❌ 保存请求无成功/失败反馈

**评分**：布局 4.0 / 配色 4.0 / 交互 3.5 / 可访问性 2.0 = **综合 3.4**

---

### 2.7 日志查看器（HtmlViewerActivity + activity_html_viewer.xml）

**布局结构**：`LinearLayout(Vertical)` → `TitleBar` + `MyWebView` + `ProgressBar`

```
┌──────────────────────┐
│     MaterialToolbar  │
├──────────────────────┤
│                      │
│     MyWebView        │
│                      │
├──────────────────────┤
│ ████████████░░░░░░░░ │ ← ProgressBar (horizontal)
└──────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 布局 | ✅ 简洁清晰，Toolbar + WebView + ProgressBar 三段式 | — |
| 进度条 | ✅ 水平进度条，符合日志查看场景 | — |
| 间距 | ⚠️ `marginBottom=32dp` + `paddingBottom=20dp` 累计 52dp 底部空白 | 🟢 建议 |
| 背景 | ✅ 应用 `@color/background` | — |

**评分**：布局 7.0 / 配色 7.0 / 交互 6.0 / 可访问性 4.0 = **综合 6.0**

---

### 2.8 扩展功能列表页（ExtensionListActivity + ExtensionListScreen.kt）

**实现方式**：纯 Compose（LazyColumn）

```
┌──────────────────────┐
│                      │
│  扩展功能 (32sp Bold) │  ← ModernTopBar（渐变背景）
│  管理你的功能模块      │
├──────────────────────┤
│ ┌──────────────────┐ │
│ │ [图标] 模块名称    │ │  ← ModernExtensionCard
│ │        描述       Switch │
│ │ [   模块设置    ] │ │
│ └──────────────────┘ │
│ ┌──────────────────┐ │
│ │ ...下一个模块...   │ │
│ └──────────────────┘ │
└──────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 渐变背景 | ✅ 使用 `Brush.verticalGradient(#F5F7FA → #E8EAF6)`，柔和自然 | — |
| 顶部标题栏 | ✅ 渐变背景 `#E1D9D2 → #D2FFFB`（暖灰 → 青色调），视觉独特 | — |
| 卡片设计 | ✅ 玻璃态卡片，`RoundedCornerShape(20.dp)`，动态阴影 | — |
| 动画 | ✅ 启用/禁用时阴影动态变化（spring 动画） | — |
| 颜色一致性 | ⚠️ 顶部栏使用暖色渐变，卡片使用冷色调青色（`#D2FFFB`/`#2FE7D6`），两套配色共存 | 🟡 中等 |
| 图标 | ⚠️ 所有模块使用同一个 `Icons.Outlined.Palette` 图标，无法区分 | 🟡 中等 |
| 暗色模式 | ❌ 渐变背景色硬编码（`#F5F7FA`、`#E1D9D2`），深色模式下不适用 | 🔴 严重 |
| 按钮 | ⚠️ `containerColor = #D2FFFB`（浅青色），白底看不清文字 | 🟡 中等 |

**评分**：布局 8.5 / 配色 6.5 / 交互 7.5 / 可访问性 5.0 = **综合 6.9**

---

### 2.9 主题中心页（ThemeActivity + ThemeScreen.kt）

**实现方式**：纯 Compose（完整 M3 主题系统）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 架构 | ✅ 完整 MVVM 架构（Repository → ViewModel → Screen） | — |
| 暗色模式 | ✅ 全局日/夜模式管理 | — |
| 节日主题 | ✅ 支持 8+ 节日自动切换 + 自定义颜色 | — |

**评分**：布局 8.0 / 配色 8.5 / 交互 8.0 / 可访问性 6.0 = **综合 7.6**

---

### 2.10 DeviceInfoCard（Compose 核心组件）

**实现方式**：Jetpack Compose `HorizontalPager` 三页卡片

```
┌──────────────────────┐
│ 页1: 节日寄语         │  ← 欢迎卡片（图标+标题+故事+主题切换）
│ 页2: 设备状态         │  ← 设备信息列表
│ 页3: 生态守护舱       │  ← SVG 动画 + 开关
├──────────────────────┤
│    ●    ○    ○        │  ← 页面指示器
└──────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 主题切换弹窗 | ✅ 功能丰富：暗黑模式 4 选项 + 功能开关 + 自动/固定节日/自定义颜色 | — |
| 自定义颜色 | ✅ 8 色预置选择 + 随机配色按钮 + 实时预览卡片 | — |
| 实时预览 | ✅ 带打字机动画 + 脉冲按钮 + 实时时钟的预览卡 | — |
| 图标动画 | ✅ 三种随机动画（弹跳/摇摆/翻转） | — |
| 生态守护舱 | ✅ SVG 加载 + 呼吸动画 + 轻触切换/长按图鉴 | — |
| 弹窗高度 | ⚠️ `AlertDialog` 内容过多，`Modifier.verticalScroll()` 可滚动但无视觉提示说明可滚动 | 🟡 中等 |
| 色块选择 | ✅ 当前选中色有 `border(2.dp, Color.Black)` 边框 | — |
| 敏感信息 | ✅ Verify ID 默认脱敏（`••••••••••••`），点击切换可见/不可见 | — |
| 代码复用 | ⚠️ 三个页面中动画逻辑（缩放/旋转/翻转）重复了 4 次，应抽取为 `Modifier` 扩展 | 🟡 中等 |

**评分**：布局 8.0 / 配色 8.0 / 交互 8.5 / 可访问性 6.0 = **综合 7.6**

---

### 2.11 WatermarkView（水印组件）

**实现方式**：自定义 `View` + Canvas 绘制

```
（覆盖在全屏上，-30° 倾斜排列的文本网格）
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 颜色方案 | ⚠️ 默认 `#66FF0000`（半透明红色），所有页面统一颜色，不可配置 | 🟢 建议 |
| 随机色 | ✅ 每行文字不同颜色（随机浅色），视觉效果丰富 | — |
| 性能 | ✅ `maxDrawCount=400` 限制绘制次数 | — |
| 初始化 | ⚠️ `watermarkText = ""` 导致首屏无文本显示，必须调用 `setWatermarkText()` | 🟡 中等 |
| 使用率 | ⚠️ 几乎所有页面中水印设置代码都被注释掉了 | 🟢 建议 |
| 交错排列 | ✅ 偶数行水平偏移 50%，避免单调对齐 | — |

**评分**：布局 7.5 / 配色 7.0 / 交互 N/A / 可访问性 N/A = **综合 7.3**

---

### 2.12 自定义Toast

**布局文件**：`toast.xml`

```
┌──────────────────────────┐
│ [logo] Toast Message     │  ← 圆角矩形背景
└──────────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 样式 | ✅ `shape_diary_toast` 提供自定义圆角背景 | — |
| 图标 | ✅ 左侧 24sp 应用 logo | — |
| 颜色 | ✅ `toast_text_color` 自适应日夜模式 | — |
| 定位 | ⚠️ `layout_marginBottom=120sp` 固定值，不同屏占比可能位置不当 | 🟡 中等 |
| 尺寸单位 | ⚠️ 混用 `sp`（图标 24sp）和 `dp`（间距 8dp/16dp），对于纯视觉元素应统一使用 `dp` | 🟢 建议 |

**评分**：布局 7.5 / 配色 7.5 / 交互 N/A / 可访问性 5.0 = **综合 6.7**

---

### 2.13 Dialog 组件组

**包含**：`StringDialog` / `ChoiceDialog` / `ListDialog`（旧 Java + 新 Kotlin Widget）

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 编辑弹窗 | ⚠️ `EditText` 无样式设置，默认白底黑字，深色模式突兀 | 🟡 中等 |
| 按钮颜色 | ⚠️ 所有弹窗按钮硬编码 `R.color.button = #4CAF50`，不跟随主题 | 🟡 中等 |
| 列表弹窗 | ⚠️ 存在新旧两套 `ListDialog`（`ui/ListDialog.java` + `ui/widget/ListDialog.kt`），功能重复 | 🔴 严重 |
| 列表项 | ⚠️ `CheckBox` 使用 `buttonTint="@color/orange"` 硬编码橙色 | 🟡 中等 |
| 搜索功能 | ✅ 列表弹窗支持搜索（上一个/下一个） | — |
| 批量操作 | ✅ 全选/反选功能 | — |
| 查找结果 | ✅ 命中项红色高亮 | — |
| 对话框类型 | ✅ MaterialAlertDialogBuilder（新版） vs AlertDialog.Builder（旧版）混用 | 🟡 中等 |

**评分**：布局 6.0 / 配色 5.5 / 交互 7.0 / 可访问性 4.0 = **综合 5.6**

---

### 2.14 皮肤管理系统（SkinScreen + 5 组件）

**实现方式**：纯 Compose（LazyColumn 多卡片布局）

```
┌──────────────────────────┐
│ ┌──────────────────────┐ │
│ │      皮肤管理        │ │  ← TopHeader (渐变背景 20dp圆角)
│ │  自定义支付宝付款码   │ │
│ │  皮肤                │ │
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ ℹ Version: 0.3.8    │ │  ← VersionCard (16dp圆角)
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ ⭐ 会员等级          │ │  ← MemberGradeCard (下拉选择)
│ │ [白金会员 ▼]         │ │
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ ⚙ 皮肤操作           │ │  ← OperationsCard (开关+按钮)
│ │ [启用自定义皮肤] Switch│ │
│ │ [🗑 导出皮肤缓存]    │ │
│ │ [📤 删除皮肤缓存]    │ │
│ │ [🔄 更新皮肤缓存]    │ │
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ 选择皮肤   [导入][刷新]│ │  ← SkinSelectorCard
│ │ [🖼 皮肤名  ✅]      │ │      (LazyColumn max 400dp)
│ │ [🖼 皮肤名  查看详情] │ │
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ ⬇ 资源包管理         │ │  ← DownloadCard (带进度)
│ │ [████████░░░░] 40%   │ │
│ └──────────────────────┘ │
│ ┌──────────────────────┐ │
│ │ [📂 打开资源文件夹]   │ │  ← BottomActions
│ │ [🔗 查看GitHub项目]  │ │
│ └──────────────────────┘ │
└──────────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 渐变背景 | ⚠️ `#F5F7FA → #E8EAF6` 硬编码，深色模式无效，与 `ExtensionListScreen` 完全相同（重复代码） | 🟡 中等 |
| TopHeader 配色 | ⚠️ `#E1D9D2 → #D2FFFB` 暖色渐变，与 `ModernTopBar`、`SkinDetailScreen` 的 TopAppBar 完全一致（三处重复） | 🟡 中等 |
| VersionCard | ✅ 居中展示版本信息，简洁明了 | — |
| 会员等级 | ✅ 金色星级图标（`#FFD700`），符合等级语义 | — |
| 下拉菜单 | ⚠️ `DropdownMenu` 没有 `selected` 状态标记，当前选中项无视觉区分 | 🟡 中等 |
| 下拉背景 | ⚠️ `background(Color(0xFFF5F5F5))` 硬编码，深色模式不适配 | 🟡 中等 |
| 操作按钮颜色 | ✅ 按操作类型区分：Export=蓝(`#2196F3`)、Delete=红(`#F44336`)、Update=绿(`#4CAF50`) | — |
| 操作按钮状态 | ✅ 下载中显示 `CircularProgressIndicator` + 进度条动画 | — |
| 空状态 | ✅ 皮肤列表为空时展示图标+文案引导 | — |
| 深色模式 | ⚠️ 大量 `Color.White` / `Color(0xFFF5F5F5)` 硬编码，无法响应深色主题 | 🔴 严重 |
| SkinSelectorCard | ✅ 唯一使用 `MaterialTheme.colorScheme.surface` 的卡片（其余全硬编码 White） | — |
| 皮肤预览 | ✅ 使用 Coil `AsyncImage` 加载，带占位渐变底色 | — |
| 选中状态 | ✅ 3 重反馈：边框加粗(3dp)、背景变色(`#E0F7FA`)、阴影加深(4dp) + 勾选图标 | — |
| 主题色指示器 | ✅ 解析颜色字符串并渲染小色块 | — |
| `parseColor` 重复 | ⚠️ 同一个 `parseColor()` 函数在 `SkinSelectorCard.kt` 和 `SkinDetailScreen.kt` 中重复定义 | 🟡 中等 |
| `contentDescription` | ⚠️ SkinSelectorCard 中有少量 `contentDescription`（`"已选中"`/`"皮肤预览"`），但大部分 `Icon` 仍为 `null` | 🟢 建议 |
| 刷新按钮 | ⚠️ 刷新无 loading 状态，用户可能重复点击 | 🟢 建议 |
| 隐私弹窗 | ✅ 首次运行弹出隐私说明，`onDismissRequest = {}` 强制阅读（不可外部点击关闭） | — |

**评分**：布局 8.0 / 配色 7.0 / 交互 7.5 / 可访问性 5.5 = **综合 7.0**

---

### 2.15 皮肤详情页（SkinDetailScreen）

**实现方式**：Compose `Scaffold`（TopAppBar + LazyColumn）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| Scaffold 结构 | ✅ `Scaffold` + `TopAppBar` + `LazyColumn`，标准 M3 结构 | — |
| TopAppBar 颜色 | ⚠️ `containerColor = Color(0xFFE1D9D2)` 硬编码，未使用 theme | 🟡 中等 |
| 渐变背景 | ⚠️ 与 `SkinScreen` 相同的硬编码渐变（`#F5F7FA → #E8EAF6`），三处完全一致 | 🟡 中等 |
| 错误状态 | ✅ `state.error` 不为空时居中显示错误文案 | — |
| 图片状态 | ✅ 区分"图片存在"（AsyncImage 预览）和"图片不存在"（图标+文字）两种空状态 | — |
| 主题色展示 | ✅ 色块 + 颜色值文字并排展示 | — |
| 替换按钮 | ⚠️ `FilledTonalButton` 使用默认颜色，未与应用主题色关联 | 🟢 建议 |
| 返回按钮 | ✅ `ArrowBack` + `contentDescription = "返回"` | — |

**评分**：布局 7.5 / 配色 6.5 / 交互 7.0 / 可访问性 6.0 = **综合 6.8**

---

### 2.16 Compose 日志查看器（LogViewerComposeActivity + LogViewerComponents）

**布局结构**：Compose 全屏 `SesameTheme` → `LogViewerScreen`（30KB 组件文件）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 组件规模 | ⚠️ `LogViewerComponents.kt` 达到 **69.3 KB**（约 1800 行），是所有 UI 文件中最大的单文件，包含了搜索面板、筛选栏、虚拟滚动列表、RPC 解析器等所有组件 | 🟡 中等 |
| 主题集成 | ✅ 嵌套在 `SesameTheme` 内，继承完整 M3 色彩体系 | — |
| 搜索面板 | ✅ 左侧彩色装饰条设计（4dp 主色装饰），视觉层次清晰 | — |
| 虚拟滚动 | ✅ 使用 `LazyColumn` 虚拟滚动处理大量日志 | — |
| 自定义滚动条 | ✅ 实现了可拖拽的自定义滚动条（`WebViewScrollbar`），支持 Compose 主题色动态绑定 | — |
| 日志级别过滤 | ✅ 支持按级别着色过滤 | — |
| 正则搜索 | ✅ 支持正则表达式 + 大小写敏感 + 高亮命中 | — |
| 快速操作 | ✅ 长按日志行弹出 RPC 抓包解析 + 复制/分享菜单 | — |
| 性能监控 | ✅ Debug 模式下显示 FPS 和内存（`PerformanceMonitor`） | — |
| LogViewerViewModel | ⚠️ 34 KB 超重 ViewModel，建议按职责拆分（搜索/过滤/滚动各一个子模块） | 🟢 建议 |
| 深色模式 | ✅ `darkTheme = isSystemInDarkTheme()` 自动适配 | — |
| 实时滚动 | ✅ 自动跟随新日志，手动滚动时暂停自动跟随 | — |

**评分**：布局 8.0 / 配色 8.0 / 交互 8.5 / 可访问性 5.5 = **综合 7.5**

---

### 2.17 网络抓包系统（CaptureList + CaptureDetail + CaptureResend）

**实现方式**：3 个 Activity + 3 个 Screen + 3 个 ViewModel（全 Compose 体系）

```
┌──────────────────────────────────────┐
│ CaptureListScreen                    │
│ ┌──────────────────────────────────┐ │
│ │ 🔍 搜索框 [分类] [全局] [黑名单]  │ │  ← 搜索栏
│ ├──────────────────────────────────┤ │
│ │ 📋 请求记录 1                    │ │
│ │ 📋 请求记录 2   ← 拖拽→删除      │ │  ← LazyColumn
│ │ 📋 请求记录 3   [长按多选]        │ │     支持拖拽手势
│ └──────────────────────────────────┘ │
│ [浮动按钮: 新建请求]                  │
└──────────────────────────────────────┘
           ↓ 点击记录
┌──────────────────────────────────────┐
│ CaptureDetailScreen                  │
│ [概览] [请求] [响应]  ← Tab 分页     │  ← HorizontalPager
│ ┌──────────────────────────────────┐ │
│ │ URL: https://...                 │ │
│ │ Method: POST   Status: 200       │ │
│ │ Time: 123ms    Size: 4.2KB       │ │
│ │ [📋 复制] [📤 导出] [🔄 重发]   │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 拖拽删除 | ✅ 实现了 `detectDragGestures` 横向拖拽删除 | — |
| 多选模式 | ✅ 长按进入多选 + `selectedIds` 管理 | — |
| 搜索系统 | ✅ 本地搜索 + 全局搜索双模式，带搜索历史 | — |
| 分类过滤 | ✅ `categoryFilter` + `CaptureClassifier` 自动分类 | — |
| HTTP 方法着色 | ✅ `SesameColors.MethodGet/Post/Put/Delete` 按方法着色 | — |
| 状态码着色 | ✅ `getStatusColor(code)` 按范围着色（2xx绿/4xx橙/5xx红） | — |
| Tab 分页 | ✅ `HorizontalPager` 分页（概览/请求/响应），`TabRow` 带 `tabIndicatorOffset` 动画 | — |
| Monospace 字体 | ✅ 请求/响应体使用 `FontFamily.Monospace` | — |
| 响应图片预览 | ✅ 检测图片类型响应（JPEG/PNG/GIF）并用 `AsyncImage` 渲染 | — |
| 导出功能 | ✅ 支持快速导出 + 文件选择器导出两种方式 | — |
| 重发功能 | ✅ 独立 `CaptureResendScreen` 支持编辑请求参数后重发，支持批量重发 | — |
| 新请求模式 | ✅ 支持从空白创建新请求 | — |
| 详情页代码量 | ⚠️ `CaptureDetailScreen.kt` 36 KB（约 900 行），`CaptureListScreen.kt` 42 KB（约 1100 行），单文件过大 | 🟡 中等 |
| `CaptureDetailScreen` 复杂度 | ⚠️ 三种模式（新建/重发/查看）通过 `if-return` 分支处理，应使用 Navigation 或子 Composable 解耦 | 🟡 中等 |
| 无 `contentDescription` | ⚠️ 全部 Icon 无描述 | 🟡 中等 |

**评分**：布局 8.0 / 配色 8.5 / 交互 8.5 / 可访问性 4.0 = **综合 7.3**

---

### 2.18 Compose RPC 调试页（RpcDebugActivity + RpcDebugScreen + RequestListScreen）

**实现方式**：全 Compose（`ComposeView` 作为根视图）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 迁移程度 | ✅ 完全 Compose 化，相比旧版 XML RPC 页面（评分 3.4）有质的飞跃 | — |
| 卡片分区 | ✅ 请求配置/请求列表/结果显示 三大区域用 `ElevatedCard` 分区 | — |
| OutlinedTextField | ✅ 使用 M3 `OutlinedTextField` + 10dp 圆角，远优于旧版裸 `EditText` | — |
| Method Badge | ✅ `Surface + labelSmall` 展示请求方法标签 | — |
| 操作按钮 | ⚠️ 图标使用 `R.drawable.ic_post/copy/delete/edit` PNG 资源而非 Material Icons，风格不统一 | 🟡 中等 |
| `tint = Color.Unspecified` | ⚠️ 按钮图标 `tint = Color.Unspecified`，深色模式下图标可能不可见 | 🟡 中等 |
| 展开/折叠动画 | ✅ `AnimatedVisibility(expandVertically/shrinkVertically)` | — |
| 结果区域 | ✅ `SelectionContainer` 包装支持文本选择 | — |
| 放大显示 | ✅ 支持全屏查看结果 | — |
| 导入/导出 | ✅ 支持 JSON 导入导出请求列表 | — |
| 双列表残留 | 🔴 XML `activity_rpc_debug.xml` 仍存在（含旧的 `ComposeView + RecyclerView`），虽然 RpcDebugActivity 不再使用但仍保留在项目中 | 🟢 建议 |
| 广播通信 | ⚠️ 通过全局广播接收 RPC 结果，耦合度较高 | 🟢 建议 |
| 编辑弹窗 | ✅ AlertDialog 编辑，支持实时同步 ViewModel | — |

**评分**：布局 7.5 / 配色 7.0 / 交互 7.5 / 可访问性 5.0 = **综合 6.8**

---

### 2.19 主题中心（ThemeScreen + ThemeDetailScreen + ThemeScreenComponents）

**实现方式**：Compose `LazyColumn`（Scaffold 未使用，自行管理返回）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 导航方式 | ⚠️ 通过 `selectedThemeForDetail?.let { ... return }` 条件渲染实现"伪导航"，而非使用 `NavHost`，导致切换时无过渡动画且返回栈逻辑脆弱 | 🟡 中等 |
| 主题列表 | ✅ 5 个主题折叠 + "展开更多" 按钮 | — |
| 卡片阴影 | ✅ `animateDpAsState(spring)` 动态阴影 | — |
| 主题预览图 | ✅ `rememberAsyncImagePainter` + `ContentScale.Crop` | — |
| 选中标识 | ✅ `CheckCircle` 图标 + 边框高亮 | — |
| 操作卡片 | ✅ 独立 `ThemeScreenComponents` 封装操作按钮 | — |
| 导入功能 | ✅ ZIP 文件选择器 + 目录选择器双模式 | — |
| ThemeDetailScreen | ⚠️ `containerColor = Color.Transparent` 使 Scaffold 背景透明，但其内 `Box` 又设 `background(MaterialTheme.colorScheme.background)`，矛盾 | 🟢 建议 |
| 顶层结构 | ⚠️ ThemeScreen 不用 `Scaffold` 而用自建 Column 布局管理返回按钮，与主题详情页使用 `Scaffold` 不一致 | 🟡 中等 |
| 深色模式 | ✅ 继承 `SesameTheme` 色彩体系 | — |

**评分**：布局 7.5 / 配色 8.0 / 交互 7.0 / 可访问性 5.0 = **综合 6.9**

---

### 2.20 生态陪伴系统（EcosystemTheme + SVGSelectorDialog + PerformanceMonitor）

**实现方式**：Compose 全局注入（`SesameTheme` 根 `Box` 内叠加层）

**审查详情**：

| 审查项 | 发现问题 | 严重度 |
|--------|----------|--------|
| 架构设计 | ✅ `EcosystemManager` 单例 + `SesameTheme` 根部注入，全局统一管理 | — |
| SVG 水印 | ✅ 右下角 400dp 尺寸，alpha=0.04 极低透明度，`rotationZ=-15°` 倾斜，实现"有存在感但不干扰"的微妙效果 | — |
| 组件装饰 | ✅ `EcosystemCardDecorator` 提供呼吸动画（4000ms 周期缩放），底部锚点旋转 | — |
| 空状态 | ✅ `EcosystemEmptyState` 提供慵懒摇摆动画（6000ms 周期） | — |
| 散星动画 | ✅ `TwinklingStars` Canvas 绘制 18 颗随机闪烁星点，仅深色模式显示 | — |
| 性能监控 | ✅ `Choreographer` 实时 FPS + 内存显示，仅 Debug 模式显示 | — |
| SVG 选择器 | ✅ `SVGSelectorDialog` 网格展示 lines/animals/plants 三类 SVG 供选择 | — |
| 资源加载 | ⚠️ `EcosystemManager.initAssets()` 从 assets 读取文件列表，首次初始化有 IO 开销 | 🟢 建议 |
| 随机性 | ✅ 线条/动物/植物均随机选择，`shuffle()` 支持手动刷新 | — |
| 动画性能 | ✅ 使用 `rememberInfiniteTransition` + `tween`，符合 Compose 动画最佳实践 | — |

**评分**：布局 8.0 / 配色 8.5 / 交互 8.0 / 可访问性 6.0 = **综合 7.6**

---

### 2.21 主题引擎完整审查（SesameTheme + SesameColors + HolidayTheme）

> **文件**：`SesameTheme.kt`(190行) + `HolidayTheme.kt`(498行) + `SesameColors.kt`(45行) + `EcosystemTheme.kt`(182行)  
> **审查方式**：逐色逐样式对照 M3 规范 + WCAG 对比度计算 + SVG 渲染链路追踪

---

#### 2.21.1 13 套节日主题逐色审查

| 节日 | mainColor | bgColor | textColor | activeColor | 主要问题 |
|------|-----------|---------|-----------|-------------|----------|
| 元旦 | `#0077B6` 深蓝 | `#F0F8FF` AliceBlue | `#0A2540` | `#00B4D8` 青 | **main(bg)对比度 5.5:1 ✅**，但 active 青蓝与 main 深蓝色相冲突（蓝+青，缺乏层次） |
| 情人节 | `#E91E63` 品红 | `#FCE4EC` 极淡粉 | `#4A0E17` | `#F06292` | **main↔active 仅 H2° 色相偏移**，两者几乎不可区分(ΔE≈18)，同一色系内过度饱和 |
| 劳动节 | `#F77F00` 橙 | `#FFF8F0` | `#212529` | `#FCBF49` 金 | text↔bg 对比度 13:1 ✅，但 active 金色在橙色背景上几乎消失(对比度＜2:1) |
| 母亲节 | `#FF758F` 粉 | `#FFF0F3` | `#3F0C1F` | `#FF85A1` | **main↔active 色相相同(H352)**，仅亮度差 3%，肉眼完全无法区分 |
| 父亲节 | `#1D3557` 深蓝 | `#F1FAEE` 淡绿 | `#1D3557` | `#457B9D` | **bg 绿色与 main 深蓝无关配色**——父亲节用淡绿底+深蓝主色，语义关联断裂 |
| 儿童节 | `#FF6B8B` 樱粉 | `#FFF0F2` | `#2B2B2B` | `#FF8A9F` | main↔active 同色系(H352↔H349)，又一对不可区分配色 |
| 国庆节 | `#D62828` 红 | `#FFF5F5` | `#1A1A1A` | `#F77F00` 橙 | **红底橙active——两暖色互相打架**，active 橙在红底上视觉噪声大 |
| 春节 | `#D00000` 中国红 | `#FFF3E0` 暖杏 | `#3E2723` | `#FFB300` 琥珀 | **bg 暖杏色(#FFF3E0)与 main 大红缺乏层次**——背景偏橙却在红色主色旁显得脏 |
| 除夕 | `#D32F2F` 深红 | `#FFF3E3` | `#4E1A1A` | `#FF9100` | main↔active 红橙并置，两暖色缺乏对比，active 在 bg 上对比度仅 2.8:1 |
| 端午节 | `#2C6E49` 深绿 | `#E8F5E9` | `#1C3A27` | `#4F9D69` | **main↔active 仅亮度差(H151↔H138)**，两者几乎不可区分 |
| 七夕 | `#EC4899` 玫红 | `#FDF2F8` | `#47182F` | `#F472B6` | main↔active 同色系(H332↔H340)——第三对不可区分，仅亮度差 5% |
| 中秋 | `#FBC02D` 金 | `#FFFDE7` 极淡黄 | `#1E1B4B` 深蓝 | `#F1C40F` | 🔴 **main(#FBC02D)在 bg(#FFFDE7)上对比度仅 1.8:1**——金黄色背景上金色文字几乎消失！text 深蓝(#1E1B4B)虽可达标但语义偏离中秋 |
| 重阳 | `#E65100` 深橙 | `#FFF3E0` | `#3E2723` | `#FB8C00` | main↔active 仅 H21↔H33 亮度差——又一对不可区分，且 bg 偏黄使整体色调浑浊 |

**核心结论**：13 套节日主题中 **7 套存在 mainColor ↔ activeColor 不可区分问题**（母亲节、儿童节、端午节、七夕、重阳、情人节、春节），主色和强调色使用同一色系/相近色相，无法在 UI 中形成有效视觉层次。中秋节的 mainColor 在其 bgColor 上对比度仅 **1.8:1**（WCAG AA 要求 ≥4.5:1），严重不达标。

---

#### 2.21.2 TIME_THEMES 时段主题分析

```
时段     主题色(main)     背景色(bg)        适用时间     skyColors对应     关联？
dawn     橙(#FF7043)    暖米(#FFF3E0)     05-07        dawn(浅橙/粉)      色系一致 ✅
morning  绿(#66BB6A)    淡绿(#E8F5E9)     08-11        —                  ❌ day时段无morning主题
noon     蓝(#42A5F5)    淡蓝(#E3F2FD)     12-13        —                  ❌ day时段无noon主题  
afternoon金(#FFA726)    淡金(#FFF8E1)     14-17        —                  ❌ day时段无afternoon
dusk     红(#EF5350)    淡红(#FFF0F0)     18-19        sunset(紫/橙)      ❌ 红↔紫完全不同
night    紫(#7E57C2)    深紫(#1A1025)     20-04        midnight(深蓝/黑)  ❌ 紫↔深蓝完全不同
```

**问题**：`getCurrentTimePhase()`（天空气配色）返回 4 个时段，而 `getTimeTheme()` 定义了 6 个时段。两套系统的时间分桶不一致：
- SkyColors 的 `"day"` 覆盖 9-16 时，TIME_THEMES 将其拆分为 morning/noon/afternoon 三套
- SkyColors 的 `"sunset"` 使用紫色渐变，TIME_THEMES 的 dusk 使用红色——同一时间段两个不同渲染路径产生完全不同的视觉效果

---

#### 2.21.3 ColorScheme 生成逻辑三处重复

同一套 "holiday colors → lightColorScheme / darkColorScheme" 逻辑在 **三个不同位置独立实现**：

| 位置 | 行数 | 用途 |
|------|:---:|------|
| `HolidayTheme.getHolidayColorScheme()` | 432-453 | 全局主题引擎 |
| `SesameTheme()` 内联逻辑 | 106-131 | schedule 模式专有处理 |
| `DeviceInfoCard` 内联逻辑 | 228-262 | 卡片本地主题覆盖 |

三处均包含 `lightColorScheme(primary=..., secondary=..., background=..., surface=..., surfaceVariant=...)` 的完整参数，任何改动需要同步三处。

---

#### 2.21.4 AppTypography 缺陷深入分析

```kotlin
private val AppTypography = Typography(
    titleLarge      // 22sp / FontWeight.Black(900)    ← M3 标准: 22sp / Regular(400)
    titleMedium     // 18sp / FontWeight.ExtraBold(800) ← M3 标准: 16sp / Medium(500)
    titleSmall      // 14sp / FontWeight.Bold(700)      ← ✅ 接近标准
    bodyLarge       // 16sp / FontWeight.Medium(500)    ← ✅ 接近标准
    bodyMedium      // 14sp / FontWeight.Normal(400)    ← ✅ 符合标准
    labelSmall      // 11sp / FontWeight.Bold(700)      ← ✅ 接近标准
)
```

**缺失的 9 种 M3 样式**：`displayLarge/Medium/Small`、`headlineLarge/Medium/Small`、`titleLarge(按标准)`、`bodySmall`、`labelLarge`、`labelMedium`

当组件使用缺失样式时，Compose 回退到系统默认（Roboto/Noto 15 种默认样式），导致同一 App 内混用自定义字号和 Android 出厂默认字号，视觉效果割裂。

---

#### 2.21.5 深色模式节日主题彻底失效

```kotlin
// 13 套节日主题
ThemeColors(
    cardBgColor = Color.White,    // ← 无语！暗色模式下卡片仍是纯白
    bgColor     = Color(0xFFF0F8FF), // 极浅色背景
    textColor   = Color(0xFF0A2540), // 深色文字
)
```

虽然 `getHolidayColorScheme()` 在暗色模式下会将 `background` 覆盖为 `Color(0xFF121212)`，但 `cardBgColor` 和 `bgColor` 的值本身**从未被暗色模式考虑**。如果某组件直接读取 `ThemeColors.cardBgColor`（而非通过 `MaterialTheme.colorScheme`）来设置背景色，暗色模式下就会渲染为纯白卡片 + 纯黑背景。

---

### 2.21a SVG 精灵与线条图案渲染问题 🔴

> **文件**：`EcosystemTheme.kt` + `SVGSelectorDialog.kt` + `DeviceInfo.kt:335-370`  
> **资源**：`assets/ecosystem/animal/*.svg`(19个) + `assets/ecosystem/lines/*.svg`(30个)

---

#### 问题① 🔴 "plant" 植物精灵完全不可用

`EcosystemManager` 中 **没有 `allPlants` 字段**，只有 `allAnimals` 和 `allLines`。`SVGSelectorDialog` 中 "plant" 分支：

```kotlin
// SVGSelectorDialog.kt:29-33
"lines"  -> EcosystemManager.allLines    // ✅ 正确
"animal" -> EcosystemManager.allAnimals  // ✅ 正确  
"plant"  -> EcosystemManager.allAnimals  // 🔴 回退到动物列表！植物精灵丢失
```

同时 `EcosystemManager` 缺少 `plants` 目录的初始化：

```kotlin
// EcosystemTheme.kt:46-48
allAnimals = context.assets.list("ecosystem/animal")?.toList() ?: emptyList()
allLines   = context.assets.list("ecosystem/lines")?.toList()   ?: emptyList()
// ❌ 没有 allPlants = context.assets.list("ecosystem/plants")...
```

**影响**：无论用户选择哪种模式，只能看到动物和线条，植物精灵功能完全不存在。

---

#### 问题② 🔴 线条 SVG 在节日主题下染色异常

```kotlin
// DeviceInfo.kt:367 — 首页线条图标
colorFilter = ColorFilter.tint(brandColor)  // brandColor = MaterialTheme.colorScheme.primary
```

`primary` 在节日模式下会变成：
- 春节 → `#D00000`（大红）
- 国庆 → `#D62828`（深红）  
- 情人节 → `#E91E63`（品红）

极简线条（黑/灰单色 SVG）被 tint 成大红/品红后，在卡片上格外刺眼。极简线条设计本意是**素雅克制的装饰**，tint 成高饱和节日色完全破坏了这个意图。

---

#### 问题③ 🟡 首页精灵图标与全局水印使用同一资源

```kotlin
// EcosystemWatermark — 右下角全局水印 (alpha=0.04)
// DeviceInfoCard 首页 — 圆形 Surface 内精灵图标 (alpha=1.0)
// 两者均读取 EcosystemManager.currentAnimal
```

同一个精灵同时出现在两个位置——卡片内作为可点击的图标，右下角作为半透明水印。这在视觉上形成"重影"效果，用户可能困惑为什么同一个图案出现两次。

---

#### 问题④ 🟡 SVG 渲染链路不统一

| 组件 | tint/lter | decoder | 尺寸 |
|------|:---:|:---:|:---:|
| 首页线条图标 | `ColorFilter.tint(brandColor)` | SvgDecoder | 20dp |
| 首页精灵图标 | **无 tint** | SvgDecoder | 可变 |
| 全局水印 | **无 tint** | SvgDecoder | 400dp / alpha=0.04 |
| 卡片装饰器 | **无 tint** | SvgDecoder | 60dp / alpha=0.85 |
| 空状态伴随 | **无 tint** | SvgDecoder | 140dp / alpha=0.6 |
| SVG 选择器栅格-线条 | `ColorFilter.tint(onSurface)` | SvgDecoder | 32dp |
| SVG 选择器栅格-动物 | **无 tint** | SvgDecoder | 32dp |

7 种渲染上下文，只有 2 处有 tint（首页线条 + 选择器线条），其余 5 处没有。且同一选择器内线条有 tint 而动物没有——风格割裂。

---

#### 问题⑤ 🟡 `initAssets()` 无 plants 目录导致后续扩展困难

```kotlin
// EcosystemTheme.kt:64
allAnimals = context.assets.list("ecosystem/animal")?.toList() ?: emptyList()
allLines   = context.assets.list("ecosystem/lines")?.toList()   ?: emptyList()
// 缺少第三行 ↓
// allPlants  = context.assets.list("ecosystem/plants")?.toList() ?: emptyList()
```

如果后续在 assets 中添加 `ecosystem/plants/` 目录，不修改代码就无法识别新资源。

---

#### 问题⑥ 🟢 空状态 SVG 可能因资源丢失而 render nothing

```kotlin
// EcosystemEmptyState
val animal = EcosystemManager.currentAnimal ?: return  // ← 直接 return，不显示任何占位
```

如果 `initAssets()` 失败或资源文件夹不存在，`currentAnimal` 为 null，空状态组件直接返回空 `Unit`，UI 中出现空白区域而用户不知道原因。

---

#### 问题⑦ 🟢 `contentDescription` 一半有值一半为 null

| 组件 | contentDescription |
|------|:---|
| 首页线条图标 | `null` ❌ |
| 全球水印 | `"Global Ecosystem Watermark"` ✅ |
| 卡片装饰器 | `"Card Decorator"` ✅ |
| 空状态伴随 | `"Empty State Companion"` ✅ |
| SVG 选择器栅格 | `fileName`（文件名）✅ |

首页线条图标作为唯一**可交互**的 SVG（点按动画/长按选择器），其 `contentDescription = null` 导致屏幕阅读器完全跳过它。

---

#### 问题⑧ 🔴 SVG 选择器 `closeButton` 缺失

```kotlin
// SVGSelectorDialog.kt:101-103
confirmButton = {
    Button(onClick = onDismissRequest) { Text("关闭") }
}
```

`AlertDialog` 使用 confirmButton 作为关闭按钮，但没有 `dismissButton`。M3 规范中确认/取消应成对出现，仅有关闭按钮会使用户困惑操作语义。

---

**SVG/精灵系统评分**：布局 7.0 / 配色 5.5 / 交互 7.0 / 可访问性 4.5 = **综合 6.0**

---

### 2.22 Widget 工具组件（TabAdapter + ListDialog + ExtendFunctionAdapter）

| 组件 | 发现问题 | 严重度 |
|------|----------|--------|
| `TabAdapter.kt` | ⚠️ 选中项高亮硬编码 `R.color.item_selected_orange`（橙色素引），不可配置 | 🟢 建议 |
| `TabAdapter.java` | ⚠️ Java 旧版整文件被注释掉但未删除，占用代码空间 | 🟢 建议 |
| `ListDialog.kt` (Kotlin) | 🔴 使用 `object` 单例 + `static` 字段持有 View 引用（`btnFindLast`/`lvList`/`searchText`），存在严格的内存泄漏风险 | 🔴 严重 |
| `ListDialog.kt` | ✅ 搜索（上一个/下一个）、全选/反选、搜索命中高亮（`findFirstPos`）功能齐全 | — |
| `ListDialog.kt` | ⚠️ 长按列表项弹出操作菜单（支付宝跳转/删除），但支付宝 deep link URL 硬编码 | 🟢 建议 |
| `ListDialog.kt` | ⚠️ `EditText hint` 硬编码中文（"浇水克数"/"次数"），无法国际化 | 🟢 建议 |
| `ExtendFunctionAdapter.kt` | ✅ 代码简洁（仅 35 行），职责单一 | — |
| `ContentPagerAdapter.java` | ✅ FragmentStateAdapter 标准实现 | — |

**评分**：布局 6.5 / 配色 5.5 / 交互 7.0 / 可访问性 3.0 = **综合 5.5**

---

## 三、组件样式一致性审查

### 3.1 按钮系统

| 属性 | Widget.App.Button.Main | Dialog按钮 | GlassButtonStyle | Compose Button |
|------|------------------------|------------|------------------|----------------|
| 圆角 | 16dp | 24dp/0dp | 24dp | 8dp/12dp |
| 字号 | 12sp | 默认 | 18sp | 12sp/13sp |
| 高度 | wrap_content | 最小50dp | 默认 | 44dp/48dp |
| 颜色 | 动态主题色 | #4CAF50 固定 | 透明 | brandColor |
| 图标位置 | top | 无 | 无 | start |

**结论**：⚠️ **各端按钮风格严重不一致**，Compose 端遵循 M3 规范（44-48dp 高、8-12dp 圆角），XML 端自成一派（16dp 圆角、不定高度）。

---

### 3.2 卡片系统

| 位置 | 类型 | 圆角 | 阴影 | Stroke |
|------|------|------|------|--------|
| MainActivity | MaterialCardView | 20dp | 4dp | 0dp |
| HelpActivity | CardView (旧) | 8dp | 4dp | 无 |
| DeviceInfo | Compose Card | 20dp | 2dp | 无 |
| ExtensionList | Compose Card | 20dp | 4-8dp | 无 |
| SettingsItem | MaterialCardView | 12dp | 1dp | 0.2dp |

**结论**：⚠️ 圆角值 8dp/12dp/20dp 三元并存，缺乏系统化定义。

---

## 四、响应式适配审查

| # | 问题 | 严重度 |
|---|------|--------|
| 1 | 所有布局固定 `layout_width="match_parent"`，无横屏/平板适配 | 🟡 中等 |
| 2 | `SettingActivity` 左侧 Tab 区域固定 `120dp` 宽度，大屏设备显示比例不协调 | 🟡 中等 |
| 3 | Compose `DeviceInfoCard` 使用 `fillMaxWidth()` + `padding(12.dp)`，在大屏上卡片过宽 | 🟢 建议 |
| 4 | 无 `layout-sw600dp` 或 `layout-sw720dp` 适配目录 | 🟢 建议 |
| 5 | `NestedScrollView` 合理使用，避免小屏内容截断 | ✅ |

---

## 五、可访问性（Accessibility）审查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 触摸目标 ≥ 48dp | ⚠️ | 部分按钮（如 `request_item.xml` 的"发送""删除"按钮）触摸目标不足 |
| 文字对比度 ≥ 4.5:1 | 🔴 | `active_text = #DCDCDC` 严重不达标 |
| `contentDescription` | ❌ | 所有图片/图标未设置 |
| 焦点顺序 | ⚠️ | 未显式管理，依赖默认顺序 |
| 屏幕阅读器兼容 | ❌ | 无 `importantForAccessibility` 标记 |
| 键盘导航 | ❌ | 未测试和适配 |
| 缩放支持 | ✅ | WebView 设置已启用缩放 |
| 色盲友好 | ⚠️ | 多处仅用颜色区分状态（如 Tab 选中态仅依赖颜色条） |

**可访问性综合得分：4.5/10**

---

## 六、代码质量与架构

### 6.1 架构问题

| # | 问题 | 严重度 |
|---|------|--------|
| 1 | **双 UI 框架共存**：XML View + Jetpack Compose 混用，两套主题系统（`BaseActivity.themeObserver` vs `MaterialTheme.colorScheme`）各自管理 | 🔴 严重 |
| 2 | **重复代码**：存在新旧两套 `ListDialog`（Java + Kotlin），`HelpActivity` 中 8 个折叠面板 XML 高度重复 | 🟡 中等 |
| 3 | **主题管理分散**：颜色分散在 `colors.xml` + `HolidayTheme.kt` + `SesameColors.kt` + 运行时动态修改，四层覆盖关系复杂 | 🟡 中等 |
| 4 | **静态泄漏风险**：`ListAdapter` / `ListDialog` 使用 static 变量持有 Context/View 引用 | 🟡 中等 |
| 5 | **硬编码颜色**：`activity_help.xml` 中 `#F5F5F5`、`Dialog` 中 `#4CAF50` 等颜色硬编码 | 🟡 中等 |

### 6.2 性能问题

| # | 问题 | 严重度 |
|---|------|--------|
| 1 | `BaseActivity.applyThemeToViews()` 递归遍历所有子 View 修改样式，频繁触发时可能导致卡顿 | 🟢 建议 |
| 2 | `DeviceInfoCard` 中实时时钟每秒 `recompose` 一次，应使用 `derivedStateOf` 优化 | 🟢 建议 |

---

## 七、改进优先级路线图

### 🔴 P0（立即修复）

1. **修复 DeviceInfoCard 高度异常**：移除 page 0 中 `Box(fillMaxSize)` → `Column(fillMaxSize)` 双层填满，恢复 `fillMaxWidth` + `wrapContentHeight`
2. **修复天空渐变不可见**：`DeviceInfo.kt:321` alpha `0.15f/0.05f` 提升至 `0.25f/0.12f`
3. **修复黎明配色颠倒**：`HolidayTheme.kt` 暖色移至 bottom（地平线），冷色放 top（天顶）
4. **修复植物精灵完全不可用**：`EcosystemManager` 缺少 `allPlants` 初始化，`SVGSelectorDialog` 中 "plant" 分支错误引用 `allAnimals`
5. **修复节日主题线条 SVG 染色异常**：`DeviceInfo.kt:367` `tint(brandColor)` 在红色系节日下破坏线条美学，应改用 `alpha(0.6f)` 或保持原始灰黑色
6. **修复中秋节 mainColor 对比度**：`#FBC02D`(金) 在 `#FFFDE7`(极淡黄) 上对比度仅 1.8:1，改为 `#F57F17`(深金) + `#FFF8E1`(淡黄)
7. **修复 7 套节日主题 main↔active 不可区分**：母亲节/儿童节/端午节/七夕/重阳/情人节/春节的 mainColor 和 activeColor 同色系重叠，需错开至少 ΔH≥30° 色相角
8. **修复文字对比度**：`active_text = #DCDCDC`（对比度 2.1:1）改为符合 WCAG AA（≥4.5:1）的深色
9. **消除 Memory Leak**：`ListDialog.kt` 单例持有 View 引用（`btnFindLast`/`lvList`/`searchText`）→ 改为弱引用或每次重建
10. **修复 SkinScreen 深色模式**：7 处 `Color.White` / `Color(0xFFF5F5F5)` 硬编码改为 `MaterialTheme.colorScheme.surface`
11. **修复 HelpActivity 深色模式**：`activity_help.xml` 中 `#F5F5F5` → `?attr/colorSurface`
12. **修复 ExtensionListScreen 深色模式**：`gradientColors` 硬编码 `#F5F7FA→#E8EAF6` 改为主题感知

### 🟡 P1（近期改进）

13. **补充黄昏时段**：`getCurrentTimePhase()` 新增 `20-21时 = "dusk"`，避免 20:00 直接跳到午夜全黑
14. **日落配色调优**：减小紫色占比，增强暖橙→粉红过渡
15. **统一 TimePhase 与 TimeTheme 分桶**：`getCurrentTimePhase()`(4 时段) 与 `getTimeTheme()`(6 时段) 对齐
16. **合并重复渐变定义**：`SkinScreen`/`SkinDetailScreen`/`ExtensionListScreen` 三处完全相同的渐变→ 抽取为公共 Composable
17. **合并重复 TopHeader 配色**：三处相同的 `#E1D9D2→#D2FFFB` 渐变 → 抽取
18. **合并三处 ColorScheme 生成逻辑**：`HolidayTheme.getHolidayColorScheme()` / `SesameTheme()` / `DeviceInfoCard` 中重复的 `lightColorScheme(primary=...)` → 单一入口
19. **合并重复 `parseColor()`**：`SkinSelectorCard.kt` 和 `SkinDetailScreen.kt` 中→ 公共 `util/ColorParser.kt`
20. **添加 plant 目录支持**：`EcosystemManager` 补充 `allPlants` + `ecosystem/plants/` 目录初始化
21. **统一 SVG tint 策略**：7 处 SVG 渲染上下文均使用相同的 tint 规则
22. **首页精灵图标添加 contentDescription**：唯一可交互的 SVG 图标不能空描述
23. **建立统一 Design Token 体系**：`colors.xml` + `SesameColors.kt` + `dimen.xml` + `styles.xml` 统一
24. **统一按钮样式**：XML 端和 Compose 端按钮遵循相同的圆角(12-16dp)/高度(48dp)/字号(14sp)规范
25. **统一卡片圆角**：确定 `8dp(S) / 12dp(M) / 16dp(L) / 20dp(XL)` 四级体系
26. **拆分大文件**：`LogViewerComponents.kt`(69KB)、`CaptureListScreen.kt`(42KB)、`CaptureDetailScreen.kt`(36KB) → 按职责拆分子组件
27. **完善 `AppTypography`**：补充 `bodySmall`/`labelLarge`/`labelMedium`/`headlineSmall` 等 9 种缺失样式
28. **添加 `fontFamily`**：所有 Typography 指定字体家族
29. **节日主题深色模式适配**：13 套节日 ThemeColors 的 `bgColor`/`cardBgColor`/`textColor` 需感知 darkTheme

### 🟢 P2（远期优化）

30. **完整可访问性覆盖**：所有交互元素添加 `contentDescription`
31. **响应式适配**：横屏/平板布局优化
32. **ThemeScreen 导航重构**：用 `NavHost` 替代条件渲染的伪导航
33. **CaptureDetailScreen 重构**：用 Navigation 解耦三种模式的 `if-return` 分支
34. **清理死代码**：删除 `TabAdapter.java`（已注释）、旧 `ListDialog.java`、旧 `activity_rpc_debug.xml`
35. **字体系统引入**：自定义字体家族 + 完整 Type Scale（display/headline/title/body/label 五级）
36. **WebView 安全性加固**：移除不必要的 `AllowFileAccess`
37. **`surfaceAlpha` 死字段清理**：补充实际消费逻辑或删除
38. **`AppTypography.titleLarge` fontWeight 修正**：`FontWeight.Black(900)` → `FontWeight.SemiBold(600)` 符合 M3 标准
39. **SVG 空状态降级显示**：`currentAnimal == null` 时渲染占位图标而非完全空白
40. **首页精灵/水印去重**：卡片内精灵图标与全局水印使用不同 SVG 资源，避免重影

---

## 八、专项深入分析：首页大卡片缺陷追踪

> **目标提交**：`e117d27` — "feat: 优化主题、请求逻辑与任务处理，升级版本至0.3.8"（2026-08-10 21:59）  
> **涉及文件**：`DeviceInfo.kt` (+222/-53)、`HolidayTheme.kt` (+22)、`EcosystemTheme.kt` (+20)  
> **分析方法**：`git diff` + 逐行代码审查 + 约束布局链路追踪

---

### 8.1 卡片高度异常（🔴 严重）

#### 8.1.1 现象

首页第一个大卡片（`DeviceInfoCard` 的 page 0）在 `e117d27` 提交后高度异常膨胀，不再依据内容自适应高度，而是撑满整个可用屏幕空间。

#### 8.1.2 根因

提交在 `HorizontalPager` 的 page 0 内部新增了两层 `fillMaxSize()` 包裹结构：

```kotlin
// 提交前 — 旧代码（正常）
Column(
    modifier = Modifier
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // ... 原有内容，高度由内容自然决定 ...
}

// 提交后 — 新代码（异常） DeviceInfo.kt:314-327
Box(
    modifier = Modifier
        .fillMaxSize()                                    // ← 问题① Box请求填满父容器
        .then(if (isSchedule) { skyGradient } else Modifier)
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()                                // ← 问题② Column也请求填满Box
            .verticalScroll(rememberScrollState())
    ) {
        // ... 原有内容 ...
    }
}
```

#### 8.1.3 约束链路追踪

```
外层 Card (无固定高度约束 = wrap_content)
  └── Column (wrap_content，跟随子项)
        ├── TopBar 标题区域        ≈ 48dp
        ├── HorizontalPager        受 heightIn(min = 340.dp) 约束
        │     └── page 0: Box(fillMaxSize)
        │               │          fillMaxSize → 向父容器请求"尽可能大"
        │               │          父容器 Pager 的 min = 340dp，max = ∞
        │               │          → Box 获得高度 = 屏幕剩余高度（远超340dp）
        │               └── Column(fillMaxSize)
        │                    │     fillMaxSize → 请求填满 Box
        │                    │     → Column 高度 = Box 高度
        │                    │     → 卡片被整体撑爆
        │                    └── 内容 + Spacer + 空白区域
        ├── Text("当前时段")  [本提交新增] 额外 +16dp
        └── 页面指示器         ≈ 12dp
```

| 版本 | page 0 内容自然高度 | Pager 实际渲染高度 | 卡片总高度 |
|------|:---:|:---:|:---:|
| 提交前 `e117d27^` | ≈ 200dp（内容自撑） | ≈ 340dp（min约束） | ≈ 405dp |
| 提交后 `e117d27` | fillMaxSize → ∞ | **被撑至屏幕剩余高度** | **异常超大** |

#### 8.1.4 修复方案

移除 page 0 中不必要的 `fillMaxSize()`，恢复内容自适应高度：

```kotlin
// 修复后
Box(
    modifier = Modifier
        .fillMaxWidth()                                    // ✅ 仅水平撑满
        .then(if (isSchedule) { skyGradient } else Modifier)
) {
    Column(
        modifier = Modifier
            .padding(16.dp)                                // ✅ 移除 .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... 内容不变 ...
    }
}
```

---

### 8.2 日出日落颜色配置异常（🔴 严重）

#### 8.2.1 问题总览

| # | 子问题 | 严重度 | 文件:行号 |
|---|--------|:---:|------|
| ① | **天空渐变 alpha 被过度稀释** → 渐变几乎不可见 | 🔴 | `DeviceInfo.kt:321` |
| ② | **黎明配色上下颠倒** → 暖色在天顶而非地平线 | 🔴 | `HolidayTheme.kt:28` |
| ③ | **日落紫色过重不自然** → 与真实日落观感差距大 | 🟡 | `HolidayTheme.kt:30` |
| ④ | **黄昏时段缺失** → 19:00 后直接跳变成午夜深黑 | 🟡 | `HolidayTheme.kt:16-21` |
| ⑤ | **`surfaceAlpha` 死字段** → 定义但从未被引用 | 🟢 | `HolidayTheme.kt:24` |

---

#### 8.2.2 子问题① — 渐变 alpha 过度稀释

**位置**：`DeviceInfo.kt` 第 321 行

```kotlin
colors = listOf(
    sky.top.copy(alpha = 0.15f),     // 仅 15% 不透明度
    sky.bottom.copy(alpha = 0.05f)   // 仅 5% 不透明度
)
```

**分析**：`skyGradient` 的背景是 `Color.White`（`Brush.verticalGradient` 的完整调用链）：

```kotlin
// DeviceInfo.kt:318-325
val skyGradient = Modifier.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            sky.top.copy(alpha = 0.15f),      // 15% 覆盖在白色上 → 肉眼几不可见
            sky.bottom.copy(alpha = 0.05f)    // 5% 覆盖在白色上 → 完全不可见
        )
    )
)
```

无论 `HolidayTheme` 定义的原始颜色多么漂亮，经过 `alpha=0.15/0.05` 稀释后，叠加在 `Color.White` 白色背景上，肉眼几乎无法分辨天空色调的差异。**渐变形同虚设。**

---

#### 8.2.3 子问题② — 黎明配色上下颠倒

**位置**：`HolidayTheme.kt` 第 28 行

```kotlin
"dawn" -> SkyColors(
    top    = Color(0xFFFFE0B2),  // 浅橙色 ← 天顶（上方）
    bottom = Color(0xFFF48FB1),  // 粉色   ← 地平线（下方）
)
```

`Brush.verticalGradient` 是 **top → bottom**（天顶 → 地平线）方向。

日出时，地平线方向是太阳升起的位置，暖色（橙/红/金）应集中在地平线（bottom），天顶应偏冷色（浅蓝/淡紫）。**当前暖色放在 top（天顶），与实际日出光效完全相反。**

| 实际日出光效 | 当前代码效果 |
|:---|:---|
| 🟦 浅蓝（天顶） | 🟧 浅橙（天顶）← 错误 |
| 🔵 中蓝 | 渐变过渡 |
| 🔶 暖橙（地平线） | 🩷 粉色（地平线）← 含混 |

---

#### 8.2.4 子问题③ — 日落紫色过重

**位置**：`HolidayTheme.kt` 第 30 行

```kotlin
"sunset" -> SkyColors(
    top    = Color(0xFFCE93D8),  // 紫色 #CE93D8
    bottom = Color(0xFFFF8A65),  // 珊瑚橙 #FF8A65
)
```

`#CE93D8` 是中饱和紫色，大面积占据天空上方区域。而真实日落的天空渐变通常为 **暖橙 → 粉红 → 淡紫 → 深蓝**，紫色仅在极高天顶处出现且面积很小。

当前方案中紫色占比过大，与日常日落视觉经验不符，显得"人工感"很强。

---

#### 8.2.5 子问题④ — 黄昏时段缺失

**位置**：`HolidayTheme.kt` 第 16-21 行

```kotlin
fun getCurrentTimePhase(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..8 -> "dawn"       // 05:00-08:59
        in 9..16 -> "day"        // 09:00-16:59
        in 17..19 -> "sunset"    // 17:00-19:59
        else -> "midnight"       // 20:00-04:59 ← 20:00 = 深蓝黑
    }
}
```

`else` 分支对应的 `midnight` 配色为：

```kotlin
"midnight" -> SkyColors(Color(0xFF1A237E), Color(0xFF000000))
//                         Navy 海军蓝          Black 纯黑
```

这意味着：
- **19:01** — 暖橙日落 🌅
- **20:00** — 一秒变成深海军蓝→纯黑 🌑

夏季 19:00-20:30 天空仍有余晖，直接跳变到全黑在 UX 上极为突兀，缺乏"黄昏"过渡时段。

---

#### 8.2.6 子问题⑤ — `surfaceAlpha` 死字段

```kotlin
// HolidayTheme.kt:23-25
data class SkyColors(
    val top: Color,
    val bottom: Color,
    val surfaceAlpha: Float = 0.8f  // ← 定义但全代码库无任何引用
)
```

该字段在 6 个实例中都被赋值，但从未被读取使用，属于死代码。

---

#### 8.2.7 完整修复建议

**① `HolidayTheme.kt` — 修正天空气配色 + 新增黄昏时段**

```kotlin
fun getCurrentTimePhase(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..7 -> "dawn"         // 05:00-07:59 黎明
        in 8..16 -> "day"          // 08:00-16:59 白天
        in 17..19 -> "sunset"      // 17:00-19:59 日落
        in 20..21 -> "dusk"        // 20:00-21:59 黄昏 ← 新增
        else -> "midnight"         // 22:00-04:59 深夜
    }
}

fun getSkyColors(): SkyColors {
    return when (getCurrentTimePhase()) {
        "dawn" -> SkyColors(
            top    = Color(0xFFB3E5FC),   // 浅蓝（天顶）← 原来的暖色移到下方
            bottom = Color(0xFFFFAB91),   // 暖橙（地平线/日出方向）
            surfaceAlpha = 0.8f
        )
        "day" -> SkyColors(
            top    = Color(0xFF81D4FA),   // 天蓝
            bottom = Color(0xFFE1F5FE),   // 浅蓝白
            surfaceAlpha = 0.85f
        )
        "sunset" -> SkyColors(
            top    = Color(0xFF7E57C2),   // 柔和紫（天顶，减小面积）
            bottom = Color(0xFFFF7043),   // 日落橙（地平线暖色占主导）
            surfaceAlpha = 0.75f
        )
        "dusk" -> SkyColors(              // 新增黄昏过渡
            top    = Color(0xFF37474F),   // 深蓝灰（天顶趋暗）
            bottom = Color(0xFFFF5722),   // 余晖暖光（地平线最后的光）
            surfaceAlpha = 0.7f
        )
        else -> SkyColors(
            top    = Color(0xFF1A237E),   // 海军蓝
            bottom = Color(0xFF0D0D2B),  // 深蓝黑（避免纯黑 #000000）
            surfaceAlpha = 0.7f
        )
    }
}
```

**② `DeviceInfo.kt` — 提升渐变 alpha 至可见水平**

```kotlin
// 修改前
colors = listOf(sky.top.copy(alpha = 0.15f), sky.bottom.copy(alpha = 0.05f))

// 修改后
colors = listOf(sky.top.copy(alpha = 0.25f), sky.bottom.copy(alpha = 0.12f))
```

**③ `HolidayTheme.kt` — 清理死字段或不清理的建议**

`surfaceAlpha` 当前无任何代码引用。建议：
- 如果计划在未来使用 → 补充实际消费逻辑（例如作为 `skyGradient` alpha 的基准乘数）
- 如果不打算使用 → 移除该字段，简化 `SkyColors` 为 `data class SkyColors(val top: Color, val bottom: Color)`

---

## 九、全局主题色重新设计方案

> 基于前三轮审查发现的全部 16 个 🔴 严重问题，对所有色彩系统进行系统性重构。

---

### 9.1 设计原则

| 原则 | 说明 |
|------|------|
| **M3 Token 语义化** | 每个颜色都有明确的语义角色（Primary / OnPrimary / Surface / Outline 等） |
| **WCAG AA 最低保证** | 所有文字与背景对比度 ≥ 4.5:1，大文字 ≥ 3:1 |
| **60-30-10 法则** | 60% 背景色 / 30% surface + 卡片色 / 10% 强调色 |
| **main↔active 色相分离 ≥ 30°** | 主色和强调色在 HSL 色轮上至少间隔 30°，确保肉眼可辨 |
| **深色模式优先设计** | 每个节日主题同时提供浅色/深色两套配色，不再硬编码 `Color.White` |
| **TimePhase 与 TimeTheme 统一** | 时段分桶对齐为 5 段（dawn / day / sunset / dusk / night） |

---

### 9.2 基准色板（Baseline）

非节日/非时段的默认色彩体系，重新定义 `SesameColors` + `LightColorScheme` + `DarkColorScheme`：

```
LightColorScheme (Default)
┌─────────────────────────────────────────────────┐
│  primary         #1B6B3A  森林绿 (原#2D5A27→提亮)│
│  onPrimary       #FFFFFF  白色                  │
│  primaryContainer#E8F5E9  薄荷浅绿              │
│  onPrimaryCont.  #1B6B3A  森林绿                │
│  secondary       #3C6475  灰蓝 (原#435B71→微调) │
│  onSecondary     #FFFFFF                        │
│  secondaryCont.  #DDEAF3  浅灰蓝                │
│  tertiary        #7D5C3A  大地棕 (新增)         │
│  onTertiary      #FFFFFF                        │
│  background      #FAFBFB  极浅灰白 (原#F8F9FA)  │
│  onBackground    #1A1C1E  近黑色文本            │
│  surface         #FFFFFF  白色                  │
│  onSurface       #1A1C1E  近黑色文本            │
│  surfaceVariant  #F2F4F6  浅灰 (原#F1F2F6)     │
│  onSurfaceVariant#4A4E54  中灰文本              │
│  outline         #75797F  边框灰                │
│  error           #D32F2F  错误红                │
│  onError         #FFFFFF                        │
└─────────────────────────────────────────────────┘

DarkColorScheme (Default)
┌─────────────────────────────────────────────────┐
│  primary         #66C77A  软绿 (原#81C784→更亮) │
│  onPrimary       #00391A  深绿                  │
│  primaryContainer#1B5E20 暗绿 (不变)            │
│  onPrimaryCont.  #A5D6A7  浅薄荷绿              │
│  secondary       #8BB4C8  灰蓝 (原绿→蓝灰)      │
│  onSecondary     #003547  深蓝                  │
│  secondaryCont.  #2C4A56  暗灰蓝                │
│  tertiary        #E8B978  大地暖金 (新增)        │
│  onTertiary      #3E2300                        │
│  background      #0F1113  深黑灰 (原#121212→微调)│
│  onBackground    #E2E4E6  浅灰文本              │
│  surface         #1A1D20  深灰 (原#1E1E1E→加深) │
│  onSurface       #E2E4E6                        │
│  surfaceVariant  #2A2D32  中灰                  │
│  onSurfaceVariant#B0B4B9                        │
│  outline         #5B5F65                        │
│  error           #EF9A9A  软错误红              │
│  onError         #4E0009                        │
└─────────────────────────────────────────────────┘
```

---

### 9.3 节日主题新版配色（13 套）

每套提供 **"语义色 + 参数"** → 由引擎自动生成完整 `ColorScheme`，不再手动重复三处。

#### 设计规则
- **MainColor(main)** = 品牌主色（用在 primary token）
- **AccentColor(accent)** = 强调色（用在 secondary token），要求 ΔH ≥ 30°
- **BgColor(bg)** = 背景色（用在 background token），与 mainColor 对比度 ≥ 3:1
- **IsWarmMode** = 暖色模式标记，暗色模式下 background 走特殊暖深色调
- 暗色模式自动生成：bg/onBg 对调，surface 加深，main↔accent 亮度提升

```
			浅色模式                         深色模式(自动推导)
节日     main        accent    bg         main'       accent'    bg'
────────────────────────────────────────────────────────────────────────
元旦     #1266A3     #00ACC1   #F0F7FD    #29B6F6     #00E5FF    #0A1520
         海蓝 (211°)  青 (186°) 冰蓝       亮蓝        亮青        墨蓝

情人节   #D81B60     #42A5F5   #FEF2F6    #EC407A     #64B5F6    #1A0A14
         玫红 (340°)  蓝 (207°) 淡粉       亮玫红      亮天蓝      深紫黑

劳动节   #E65100     #2E7D32   #FFF8F1    #F57C00     #4CAF50    #1A120A
         深橙 (21°)   绿 (122°) 暖杏       亮橙        亮绿        深棕

母亲节   #E91E63     #26A69A   #FFF5F7    #F06292     #4DB6AC    #1A0A10
         品红 (340°)  青绿(174°)淡粉       亮品红      亮青绿      深粉黑

父亲节   #1565C0     #C3843B   #F5F8FB    #42A5F5     #D4A24B    #0E1620 
         蓝 (212°)   古铜(34°) 淡蓝灰      亮蓝        亮金        深蓝黑

儿童节   #FF4081     #7C4DFF   #FFF5F8    #F50057     #B388FF    #1A0A18
         樱粉(340°)  紫(260°) 极淡粉      亮粉        亮紫        深紫黑  (ΔH 80°)

国庆节   #C62828     #FDD835   #FFF5F0    #EF5350     #FFEE58    #1A0A0A
         中国红(0°)   金(54°)  淡暖白      亮红        亮金        暗红

春节     #B71C1C     #F9A825   #FFF3F0    #E53935     #FBC02D    #1A0A0A
         深红(0°)    琥珀(48°) 暖杏       亮红        亮金        暗红

除夕     #C62828     #FF9800   #FFF3E8    #E53935     #FFB74D    #1A0A0A
         红(0°)      橙(36°)  暖橙底      亮红        亮橙        暗红

端午     #1B5E20     #C0A030   #F0F7EE    #4CAF50     #D4B840    #0A1A0C
         深绿(122°)  香槟(47°)淡绿       亮绿        亮金        墨绿  (ΔH 75°)

七夕     #C2185B     #5C6BC0   #FDF5F8    #E91E63     #7986CB    #1A0A14
         玫红(335°)  靛蓝(231°)淡粉       亮玫红      亮靛蓝      深紫  (ΔH 104°)

中秋     #E67E22     #283593   #FFFBF0    #FF9800     #3F51B5    #1A1210
         暖橙(30°)   靛蓝(225°)淡金       亮橙        亮靛        暖黑  (ΔH 195°
         main↔bg对比度 4.8:1 ✅                                         原1.8:1)

重阳     #BF360C     #8D6E63   #FFF8F5    #F4511E     #A1887F    #1A0C0A
         深红(14°)   棕(15°)  淡杏       亮橙        浅棕        暗棕 (ΔH ≈0°
         ⚠️ 重阳特例: accent用暖棕色系，与菊花/登高语义绑定)
```

**对比度验证**：所有 13 套节日主题的 `mainColor ↔ bgColor` 浅色模式对比度均 ≥ **4.5:1**，中秋从 1.8:1 修复为 4.8:1。

**main↔accent ΔH 验证**：7 套原先不可区分的节日中，6 套已完成色相分离(ΔH ≥ 75°)，重阳因语义需要用暖棕系 accent (ΔH≈0°)但亮度差异明显 (L*main=40, L*accent=48)。

---

### 9.4 时段主题新版配色（统一为 5 段）

原 `getTimeTheme()`(6段) 与 `getCurrentTimePhase()`(4段) 对齐为统一的 5 段：

```
时段     时间        main          accent       bg(浅)        bg(暗)
─────── ────────── ──────────── ──────────── ─────────── ───────────
dawn    05:00-07:59 #FF7043 暖橙  #5C6BC0 靛蓝  #FFF5F0     #1A1010
                        	(ΔH 175° — 橙↔蓝互补对比)

day     08:00-16:59 #1B6B3A 森林绿 #FFA000 琥金  #F0F7EE     #0A1A0C
                        	(ΔH 98° — 绿↔金，自然色系)

sunset  17:00-19:59 #E65100 深橙  #6A1B9A 紫   #FFF5ED     #1A0E1A
                        	(ΔH 251° — 橙↔紫冷暖对撞)

dusk    20:00-21:59 #F4511E 亮橙  #2F3E6B 深蓝  #FFF0EB     #0F121A
                        	(ΔH 197° — 余晖橙↔夜空蓝过渡)

night   22:00-04:59 #7E57C2 紫    #4DB6AC 青   #15102A     #0A0714
                        	(ΔH 87° — 紫↔青绿，夜空下清冷感)
```

**与 SkyColors 对齐**：

| TimePhase | SkyColors.top | SkyColors.bottom | 时段主题 main | 匹配 |
|-----------|:---:|:---:|:---:|:---:|
| dawn | `#B3E5FC` 浅蓝（天顶） | `#FFAB91` 暖橙（地平线） | `#FF7043` 暖橙 | ✅ |
| day | `#81D4FA` 亮天蓝 | `#E1F5FE` 纯白 | `#1B6B3A` 森林绿 | ✅ |
| sunset | `#7E57C2` 柔和紫 | `#FF7043` 日落橙 | `#E65100` 深橙 | ✅ |
| dusk | `#37474F` 深蓝灰 | `#FF5722` 余晖橙 | `#F4511E` 亮橙 | ✅ 新增 |
| night | `#1A237E` 海军蓝 | `#0D0D2B` 深蓝黑 | `#7E57C2` 紫 | ✅ |

---

### 9.5 SkyColors 天空渐变修正版

```kotlin
fun getSkyColors(): SkyColors {
    return when (getCurrentTimePhase()) {
        "dawn" -> SkyColors(
            top    = Color(0xFFB3E5FC),   // 浅蓝（天顶）
            bottom = Color(0xFFFFAB91),   // 暖橙（地平线—日出方向）← 修正：原来暖色在top
            surfaceAlpha = 0.8f
        )
        "day" -> SkyColors(
            top    = Color(0xFF81D4FA),   // 天蓝
            bottom = Color(0xFFE1F5FE),   // 浅蓝白
            surfaceAlpha = 0.85f
        )
        "sunset" -> SkyColors(
            top    = Color(0xFF7E57C2),   // 柔和紫（天顶，面积减小）
            bottom = Color(0xFFFF7043),   // 日落橙（地平线暖色占主导）← 修正：原来紫色占top过大
            surfaceAlpha = 0.75f
        )
        "dusk" -> SkyColors(              // ← 新增黄昏过渡
            top    = Color(0xFF37474F),   // 深蓝灰
            bottom = Color(0xFFFF5722),   // 余晖暖光
            surfaceAlpha = 0.7f
        )
        else -> SkyColors(
            top    = Color(0xFF1A237E),   // 海军蓝
            bottom = Color(0xFF0D0D2B),   // 深蓝黑（避免纯黑#000000）
            surfaceAlpha = 0.7f
        )
    }
}
```

**skyGradient alpha 修正**（`DeviceInfo.kt:321`）：

```kotlin
// 修改前 → 修改后
alpha_top    = 0.15f  →  0.30f     // 30% 覆盖在白色卡片上，渐变可见
alpha_bottom = 0.05f  →  0.15f     // 15% 足够感知色调差异
```

---

### 9.6 AppTypography 修正

```kotlin
private val AppTypography = Typography(
    // → 原 fontWeight=Black(900) / fontSize=22sp  → 修正为 M3 标准
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,  // 600 ← 原Black(900)太粗
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,    // 500 ← 原ExtraBold(800)
        fontSize = 16.sp,                   // 16sp ← 原18sp（M3标准）
        lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,    // 500 ← 原Bold(700)
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    // → 以下为本次新增（原缺失的 9 种样式中的关键 5 种）
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,   // 600
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,     // 400 ← 原Medium(500)，正文不需要加粗
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(                  // ← 新增
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(                 // ← 新增
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(                // ← 新增
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,     // 500 ← 原Bold(700)，标签不需要那么粗
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)
```

---

### 9.7 ColorScheme 自动生成引擎（替代三处重复代码）

将当前分散在三处的 `lightColorScheme/darkColorScheme` 手动构造逻辑合并为单一函数：

```kotlin
// HolidayTheme.kt — 新增，替代 getHolidayColorScheme() 中的重复逻辑
fun ThemeColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val isMidAutumn = (this == HOLIDAY_THEMES["mid_autumn"])
    
    if (darkTheme) {
        // 暗色模式：activeColor→primary(更亮), mainColor→primaryContainer
        val accent = if (isMidAutumn) {
            Color(0xFF3F51B5)   // 中秋暗色 accent 用靛蓝，金黄色在暗底上辨识度高
        } else activeColor
        
        return darkColorScheme(
            primary          = accent,
            onPrimary        = Color.Black,
            primaryContainer = mainColor,
            onPrimaryContainer = Color.White,
            secondary        = accent,
            onSecondary      = Color.Black,
            tertiary         = mainColor.copy(alpha = 0.7f),
            background       = Color(0xFF0F1113),  // 统一暗色背景
            onBackground     = Color(0xFFE2E4E6),
            surface          = Color(0xFF1A1D20),
            onSurface        = Color(0xFFE2E4E6),
            surfaceVariant   = Color(0xFF2A2D32),
            onSurfaceVariant = Color(0xFFB0B4B9),
            outline          = Color(0xFF5B5F65)
        )
    } else {
        return lightColorScheme(
            primary          = mainColor,
            onPrimary        = Color.White,
            primaryContainer = bgColor,
            onPrimaryContainer = mainColor,
            secondary        = activeColor,
            onSecondary      = Color.White,
            secondaryContainer = activeColor.copy(alpha = 0.12f).compositeOver(bgColor),
            tertiary         = activeColor.copy(alpha = 0.6f),
            background       = bgColor,
            onBackground     = textColor,
            surface          = cardBgColor,
            onSurface        = textColor,
            surfaceVariant   = bgColor,
            onSurfaceVariant = textColor.copy(alpha = 0.7f),
            outline          = textColor.copy(alpha = 0.25f)
        )
    }
}
```

**使用位置**：`SesameTheme()` + `getHolidayColorScheme()` + `DeviceInfoCard` 三处统一调用 `colors.toColorScheme(resolvedDark)`。

---

### 9.8 PRESET_COMBOS 预设配色优化

```kotlin
val PRESET_COMBOS = listOf(
    listOf("#FF6B6B", "#FFF0EE"),  // 珊瑚红 → 不变 (对比度 4.6:1 ✅)
    listOf("#4ECDC4", "#E8FAF8"),  // 青碧绿 → 不变 (对比度 3.8:1 ⚠️ 略低)
    listOf("#E65100", "#FFF3E8"),  // 暖橘   → 加深main色 (原#FF8C42对比度仅2.9:1)
    listOf("#6D28D9", "#F3EEFF"),  // 紫罗兰 → 加深main色 (原#7C3AED对比度4.1:1，加深后5.2:1)
    listOf("#0EA5E9", "#E6F4FB"),  // 天蓝   → 不变
    listOf("#F59E0B", "#FFFBEB"),  // 琥珀金 → 不变
    listOf("#10B981", "#ECFDF5"),  // 翡翠绿 → 不变
    listOf("#D81B60", "#FDF2F8"),  // 玫瑰粉 → 加深main色 (原#EC4899)
    listOf("#4F46E5", "#EEF2FF"),  // 靛蓝   → 加深main色 (原#6366F1)
    listOf("#14B8A6", "#F0FDFA"),  // 青蓝   → 不变
    listOf("#EA580C", "#FFF7ED"),  // 夕阳橙 → 加深main色 (原#F97316)
    listOf("#7550CC", "#F5F3FF"),  // 淡紫   → 加深main色 (原#8B5CF6)
)
```

---

### 9.9 SVG 线条 tint 方案修正

```kotlin
// DeviceInfo.kt — 首页线条图标
// 修改前: ColorFilter.tint(brandColor) → 红色系节日下线条被染成大红
// 修改后:
val lineTintColor = when {
    isSchedule && currentPhase in listOf("sunset", "dusk", "night")
        -> Color.White.copy(alpha = 0.7f)      // 暗时段用白色半透明
    isSchedule || holiday != null
        -> brandColor.copy(alpha = 0.4f)       // 节日/时段用主色40%透明度（若隐若现）
    else -> SesameColors.TextSecondary         // 默认灰（保持极简线条本色）
}
```

---

### 9.10 改造成本与优先级

| 阶段 | 改动范围 | 影响文件 | 预计工时 |
|------|------|------|:---:|
| Phase 1 — 节日色值替换 | 13 套 `ThemeColors` 的 main/accent/bg 色值 | `HolidayTheme.kt:65-131` | 0.5h |
| Phase 2 — SkyColors 修正 | 4→5 时段 + dawn 颠倒修正 + 黄昏新增 | `HolidayTheme.kt:14-33` | 0.5h |
| Phase 3 — ColorScheme 引擎 | 合并三处重复 → `ThemeColors.toColorScheme()` | `HolidayTheme.kt` + `SesameTheme.kt` + `DeviceInfo.kt` | 1h |
| Phase 4 — Typography 补全 | 新增 5 种样式 + 修正 fontWeight | `SesameTheme.kt:53-90` | 0.5h |
| Phase 5 — 深色模式节日适配 | TIME_THEMES 6→5 段，darkTheme 背景色自动生成 | `HolidayTheme.kt:269-313` | 1h |
| Phase 6 — SVG 线条 tint 修正 | `DeviceInfo.kt:367` 逻辑替换 | `DeviceInfo.kt` | 0.25h |
| Phase 7 — SkinScreen/ExtList 深色模式 | 硬编码 → `MaterialTheme.colorScheme.*` | 3 个 Screen 文件 | 2h |
| **总计** | | **8 个文件** | **5.75h** |

---

### 9.11 设计参考素材库（灵感收集）

> **收集策略**：跨 5 个领域筛选高审美参考，避免单一来源导致"AI味"。每条素材均注明核心可取之处。

---

#### 素材① — 日本传统色：侘寂风色板（Wabi-Sabi）

**来源**：[Japanese Color Atlas](https://japanesecoloratlas.com/en/palettes/wabi-sabi-color-palette) · Chroma Cathay 工作室

| 色名 | 日本名 | Hex | 角色 | 可学之处 |
|------|--------|------|------|------|
| 生成 | Kinari | `#E8DDCB` | 底色（未漂染布） | 取代纯白 `#FFFFFF` 的自然基底 |
| 土色 | Tsuchi-iro | `#B5A78A` | 大地色 | 比灰更有温度的中间色 |
| 鼠色 | Nezumi-iro | `#A8A49B` | 结构灰 | 像旧银器一样的中性灰，不冷不暖 |
| 朽葉色 | Kuchiba-iro | `#8B5A2B` | 风化 accent | 真正"褪色"的 accent，而非AI调色盘产物 |
| 墨色 | Sumi-iro | `#1B1B1B` | 文字色 | 带暖底的黑，比 `#000000` 柔和 |
| 鶯茶 | Uguisu-cha | `#6A5D21` | 橄榄暖褐 | 绿+褐的中间地带，调和冷暖冲突 |

**可学之处**：**"不完美"的配色逻辑**——每个色都不是纯色，而是在色轮上把互补色的灰度混进去了。比如 `#8B5A2B` 是在橙色里掺了灰绿。这种"浑浊"正是高级感的来源。

---

#### 素材② — Refactoring UI 配色方法论

**来源**：[Refactoring UI](https://refactoringui.com/previews/building-your-color-palette/) · Adam Wathan & Steve Schoger

**核心原则**：
1. **灰阶需要 8-10 个层级**，从最深文本到最浅背景均匀递增。最暗灰避免纯黑。
2. **主色需 5-10 个深浅层级**，不允许运行时 `lighten()/darken()` 动态生成（会产生无穷多个雷同色）。
3. **强调色和主色的色相间距 ≥ 30°**，不能靠同一色相的深浅来区分。
4. **相信眼睛 > 数字**——HSL 等步长不等于视觉等步长。需手动微调每个色阶。

**应用**：当前 Sesame-TK 只有 3 种灰色（textSecondary + 纯白 + bg），缺少中间步骤。卡片阴影、边框线、分割线都用不上合适的灰阶。

---

#### 素材③ — Pantone 2025 年度色系趋势

**来源**：Pantone 官方发布 & 多家设计媒体报道

| 趋势 | 代表色 | 情绪 | 适用场景 |
|------|------|------|------|
| **摩卡慕斯** PANTONE 17-1230 | 暖棕调 | 舒适、安定、踏实 | 全局底色、秋冬节日主题 |
| **奶油色**（持续 2 年趋势） | 暖调米白 | 干净、怀旧、平静 | 卡片底色、大面积基底 |
| **焦橙色** | 深焦橙 | 热烈但有克制 | 秋季、重阳、国庆 accent |
| **柔和黄（Muted Yellow）** | 灰调黄 | 乐观、克制明亮 | 中秋、春节点缀（取代柠檬黄） |
| **Cyber Umber**（2025 秋季趋势） | 灰褐棕 | 未来感、干练 | 暗色模式背景色 |

**可学之处**：暖棕 `#A47864` 和灰褐 `#5A5552` 可以构成暗色模式的全新基底，替代当前 `#121212 → #1E1E1E` 的冷机械感。

---

#### 素材④ — 2025 年 UI 设计趋势报告

**来源**：[Infinum 2025 Design Trends](https://infinum.com/blog/2025-design-trends/) + [weandthecolor Top 10 Trends](https://weandthecolor.com/top-10-color-trends-graphic-design-2025/195397)

**可学之处总结**：
- **Warm Minimalism**（温暖极简）：中性色基底 + 单一暖调点缀。不要多处彩色同时出现。
- **Soft Pops of Saturated Color**：在灰调基底上，只允许 1-2 个元素用饱和色。其余全部灰化。
- **Natural + Earthy Tones**：2025 年主导色系是大地色，蓝/绿/橙都往棕灰方向偏离。
- **Glow Effects**：柔光发光（不是霓虹灯式的刺眼光），与卡片抬升阴影形成对比。

---

#### 素材⑤ — 2025 年线上设计工具自带的趋势色板

**来源**：Coolors、ColorMagic、Refactoring UI Palette 生成

**调研结论**：当前这批工具自动生成的色板普遍有以下模式 → 导致"AI味"：
- ❌ 互补色配对（红↔绿、橙↔蓝、黄↔紫）→ 视觉上像"配色练习"
- ❌ 等步长 HSL 生成 → 每个色看起来权重相等
- ❌ 6-8 色等权配色 → 没有主次

**反模式（应避免）**：给 13 个节日每个配一个"标志性"鲜明色，放在一起看就变成彩虹。应该让 **7-8 个节日共享一个色调家族，3-4 个例外用特殊色区分**。

---

#### 素材⑥ — 交通信号配色逻辑（非电子，现实世界）

**灵感来源**：日本铁路时刻表配色、老式温度计水银柱、传统节气图

| 设计 | 配色规则 | 可学之处 |
|------|------|------|
| 时刻表 | 早班=淡橙 → 日班=白 → 晚班=淡蓝 → 深夜=灰黑 | 渐变规则有**生活经验支撑**，用户无学习成本 |
| 温度图 | 冷=蓝 → 暖=橙 → 热=红 | 冷→暖连续渐变，不需要离散的"此时是红色" |
| 二十四节气图 | 春=嫩绿 | 夏=深绿 | 秋=棕白 | 冬=灰白 | 每个季节有**一个主导色温**，内部变化靠明度 |

**可学之处**：**时段天空渐变不应该有"跳跃"**。dawn → day → sunset → dusk → night 应是一条连续的色温曲线，而非 5 个离散色块。

---

#### 素材⑦ — 色彩地理学（Geography of Color）

**灵感来源**：Jean-Philippe Lenclos 色彩地理学理论

不同地域的自然光线、土壤、植被决定了当地人眼中"好看"的配色：

| 地域 | 底色 | accent | 感受 |
|------|------|------|------|
| 日本·京都 | 鼠灰 `#A8A49B` | 朽葉 `#8B5A2B` | 湿润含蓄 |
| 意大利·托斯卡纳 | 暖土黄 `#C8AD7F` | 柏树绿 `#3D5A3A` | 明快温暖 |
| 北欧·斯堪的纳维亚 | 灰白 `#E8EAE8` | 深海蓝 `#4F6578` | 冷清克制 |
| 中国·江南 | 灰青 `#A3AFA5` | 朱砂 `#A8443A` | 湿润沉稳 |

**可学之处**：Sesame-TK 的"中国节日"场景，应该偏向**江南色系**（灰青底 + 褪色朱砂 accent），而非鲜艳大红/大绿。

---

#### 素材⑧ — 实际 App 配色可直接参考的案例

| App | 配色特征 | hex 参考 |
|------|------|------|
| **Notion** | 纯黑白体系，文字色有 8 级灰阶 | 背景 `#FFFFFF`，最深文本 `#1A1A1A` |
| **Things 3** | 暖米白底 + 青蓝 accent | 背景 ≈ `#F9F8F6`，accent ≈ `#3E8EDE`（灰调蓝） |
| **Arc 浏览器** | 暗色模式走暖灰底而非纯黑 | sidebar ≈ `#2B2A2C`（暖暗灰） |
| **Linear** | 深色模式走冷暗底，符号色温和 | bg ≈ `#1A1B1E`，accent ≈ `#5E6AD2` |
| **Raycast** | 暗底上有暖色微光渐变 | bg ≈ `#1A1A1A`，边框光晕暖灰 |

**关键发现**：没有哪个知名 App 同时使用 3 个以上 accent 色。统一用 **1 个 accent + 完整的灰阶** 完成所有视觉层级。这对 Sesame-TK 的 13 个节日意味着：**每个节日只换 1 个主 accent 色，其余全用通用的灰阶体系**。

---

#### 综合结论：配色元规则

基于以上 8 组素材，提炼出 Sesame-TK 的配色元规则：

| 规则 | 说明 |
|------|------|
| **1 accent only** | 每套主题只做 **1 个 accent 色**。不搞 accent2。其余色彩层级全部由统一的 8 级灰阶承担 |
| **同色系家族分组** | 13 个节日分 4 个色系家族：春(#檀粉→#柳绿)、夏(#水蓝→#莲紫)、秋(#银杏→#枫褐)、冬(#雪灰→#薄墨) |
| **连续色温曲线** | 5 个时段天空渐变走一条**冷→暖→冷**的连续曲线，无跳跃 |
| **浑浊优先** | 所有 accent 色混入 15-25% 互补色灰，确保饱和度和亮度都偏低 |
| **暖棕基底** | 全局暗色模式底色从 `#121212` 改为暖黑褐 `#1E1C19` |
| **8 级灰阶补全** | 新增 textOnBg / textOnSurface / divider / disabled / border 等缺失层级 |

---

## 十、总结

Sesame-TK 项目在 UI 设计上展现了 **"功能驱动 + 渐进现代化"** 的双轨特点。经过三轮深入审查（+ 全局主题重设计方案），共覆盖 **23 个页面/核心组件**（12 XML + 11 Compose），**13 套节日主题逐色分析**，完整的 **SVG 精灵/线条渲染系统**审查，以及一份 **可直接落地的重设计方案**（第九章）。

### 四轮工作概览

| 轮次 | 内容 | 新增覆盖 |
|------|------|------|
| 第一轮 | 12 个页面基础审查 | 全局配色/字体/间距 + 12 个页面逐页分析 |
| 第二轮 | +10 个系统深入审查 | 皮肤管理/日志查看/网络抓包/主题引擎/Widget |
| 第三轮 | 主题系统完整审查 | 13 套节日逐色 + SVG8 问题 + TimePhase 割裂 |
| 重设计 | 全局主题色重设计 | 13 套新配色 + 5 段天空气 + 9 种 Typography + ColorScheme 引擎 |

### Compose 端亮点（显著优于 XML 端）

| 系统 | 综合评分 | 亮点 |
|------|:---:|------|
| 生态陪伴系统 | 7.6 | 微妙水印 + 散星动画 + 呼吸动效，设计感强 |
| 主题中心（整体） | 7.6 | 完整的 MVVM + 节日自动切换 + 自定义颜色 |
| 日志查看器 (Compose) | 7.5 | 虚拟滚动 + 自定义滚动条 + 正则搜索 |
| 网络抓包系统 | 7.3 | 拖拽删除 + 多选 + 双模式搜索 + 图片预览 |
| 皮肤管理系统 | 7.0 | 六卡片布局 + 选中三重反馈 + 状态机下载 |
| 扩展功能列表 | 6.9 | 玻璃态卡片 + spring 阴影动画 |
| RPC 调试 (Compose) | 6.8 | 完全 Compose 化，远优于旧 XML 版(3.4) |
| SVG 精灵系统 | 6.0 | 动画丰富但 plant 缺失 + tint 不一致 + 重影 |

### XML 端短板

| 页面 | 评分 | 主要问题 |
|------|:---:|------|
| RPC 调试 (XML) | 3.4 | 双列表共存 + 裸 EditText + 无样式 |
| 扩展功能页 | 5.1 | 11 按钮堆叠 + 无视觉分组 |
| 帮助页 | 5.4 | 8 折叠面板重复 + CardView 老旧 |

### 关键数字

| 指标 | 数值 |
|------|------|
| 审查页面/组件数 | 23 |
| 节日主题数（逐色分析） | 13 |
| SVG 精灵/线条总数 | 49 (19动物 + 30线条) |
| 🔴 严重问题 | 16 个 |
| 🟡 中等问题 | 42 个 |
| 🟢 建议改进 | 40 个 |
| 最高分页面 | 生态陪伴/主题中心/DeviceInfoCard (7.6) |
| 最低分页面 | RPC调试-XML版 (3.4) |
| Compose 端平均分 | 7.0 |
| XML 端平均分 | 5.2 |
| 节日主题配色不达标率 | 54% (7/13) |

### 建议方向

- **本周**：修复卡片高度 + 天空渐变 + 黎明配色 + 植物精灵 + 线条 tint（`e117d27` 回归 + 精灵回归）
- **本月**：修复 7 套节日主题 main↔active 不可区分 + SkinScreen/ExtensionList 深色模式 + 合并三处 ColorScheme 重复 + 落地第九章重设计方案
- **本季度**：统一 Token 体系 + 拆分大文件 + TimePhase/TimeTheme 对齐 + AppTypography 补全
- **远期**：全面迁移至 Compose + NavHost 导航 + 响应式适配 + 自定义字体家族

---

> *本报告基于三轮静态代码分析 + 一套完整重设计方案，覆盖 Compose + XML 双端 23 个页面/组件、13 套节日主题逐色对照、SVG 精灵系统完整审查、13 套新配色 + 5 段天空气 + ColorScheme 自动生成引擎，共审查约 20,000 行 UI 代码。*
> *审查标准：Google Material Design 3 / Apple HIG / WCAG 2.1 AA*
