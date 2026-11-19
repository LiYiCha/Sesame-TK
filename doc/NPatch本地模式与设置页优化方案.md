# NPatch 本地模式、设置页字段列表与后续优化方案

> **适用范围**: Sesame-TK 模块（`fansirsqi.xposed.sesame`）
> **依据来源**: NPatch 官方文档（npatch.nkbe.top）、NPatch 源码（`7723mod/NPatch` dev 分支）、本仓库现有实现
> **状态**: 第一节已实现；第二至四节为方案设计

---

## 一、UA 格式判断与日志精简（已实现）

### 1.1 判定依据

抓包（`scratch/tb.log`）中支付宝小程序 WebView 的 UA 具备三段高区分度标记：

| 标记 | 含义 | 缺失时的典型形态 |
|---|---|---|
| `MYWeb/` | 支付宝小程序容器版本 | 普通浏览器 UA 无此段 |
| `UWS/` | UC 内核服务版本 | 系统 WebView UA 无此段 |
| `UCBS/` | 内核构建版本 | 旧版指纹常以 `UCBrowser/` 代替 |

`NebulaSDK/`、`AlipayDefined(...)` 在支付宝系多种容器中均会出现，区分度不足，不参与判定。

### 1.2 实现位置

- `ElemeMtopUtil.UA_TAIL_MARKS`：三段标记的单一事实来源。
- `ElemeMtopUtil.isMiniProgramUa(ua)`：账号状态弹窗的判定入口。
- `ElemeMtopUtil.briefUa()`：返回一行精简结论，例如
  - `内置模板 · 小程序WebView ✅`
  - `自定义UA · ⚠ 非小程序WebView（缺 MYWeb/UWS）`

### 1.3 日志策略

| 输出位置 | 内容 |
|---|---|
| 运行日志（界面可见） | `设备UA: 自定义UA · ⚠ 非小程序WebView（缺 MYWeb/UWS）` |
| runtime 日志（仅文件） | 完整 UA 原文，用于排查 |

整条 UA 长度在 400 字符以上，逐轮打印会淹没其它日志，且包含设备指纹细节，因此只在 runtime 日志保留。

---

## 二、NPatch 本地模式支持

### 2.1 本地模式下模块怎么被加载

官方 `guide/modes` 与 `guide/introduction`：

- 修补包自带 `assets/npatch/loader.bin` 与 `libnpatch.so`，与模块是否嵌入无关。
- 运行时用目标包名向管理器（`top.nkbe.npatch`）查询模块与作用域：先连 Manager Binder，失败再查 ConfigProvider，两者都不可用才用上次同步的缓存。
- 模块以**独立 APK** 安装，作用域由管理器数据库决定，模块不能自己决定被加载。
- 模块分三类，同一套 Vector 运行时：Legacy（`assets/xposed_init`）、libxposed API 101、libxposed API 102。

所以「本地模式下模块显示未激活」不是加载失败，而是**设置页与任务不在同一个沙盒**：
设置页跑在模块 APK 的进程，任务跑在被修补的支付宝进程，两边私有目录互不可见。

### 2.2 配置共享的两条官方路径

官方对本地模式的模块明确给了两条路，区别只在模块用哪套接口：

| 路径 | 适用模块 | 模块 App 侧 | 注入侧 | 官方依据 |
|---|---|---|---|---|
| **A. XSharedPreferences** | Legacy（`assets/xposed_init`） | 普通 `SharedPreferences` 写入 | `XSharedPreferences(包名, 文件名)` 只读 | `guide/introduction`：「Legacy 模块继续支持传统 `assets/xposed_init` 与 `XSharedPreferences`」 |
| **B. Remote API** | 现代（API 101/102） | `NPatchRemoteClient` 读写 Manager 的 Store | `getRemotePreferences(group)` 只读 | `guide/remote-api` 全篇 |

两条路的方向一致：**设置页写、注入侧只读**。这是框架的分层设计，不是可以绕开的限制。

#### 路径 A 为什么可行（源码依据）

Vector 的 `legacy/src/main/java/de/robv/android/xposed/XSharedPreferences.java`：

```java
// :180 由框架服务解析模块的 prefs 目录，而不是按调用进程的 UID 拼路径
mFile = new File(VectorServiceClient.INSTANCE.getPrefsPath(packageName), prefFileName + ".xml");

// :251 / :346 通过注入的文件服务读取，能跨 UID 打开模块 App 的私有文件
try (InputStream is = SELinuxHelper.getAppDataFileService().getFileInputStream(filename)) { ... }
```

也就是说，`XSharedPreferences("模块包名", "文件名")` 在目标进程里读的是**模块 App 自己写的 prefs**，
跨 UID 由框架服务完成。传统模块几十年都是这么做的（LSPosed wiki「New XSharedPreferences」给的就是这套写法）：

```kotlin
// 注入侧（目标进程）
val pref = XSharedPreferences("fansirsqi.xposed.sesame", "sesame_config")
val json = pref.getString("ElemeTask", null)   // 只读
```

#### 路径 B 的适用面

`NPatchRemoteClient` 是给「现代模块的设置 App」用的：需要 AAR、`<queries>` 声明、连接（同步最多 3 秒），
数据存在 Manager 的 SQLite（`configs` 表）里，注入侧通过 libxposed 只读。它更适合需要 Remote Files、
或设置 App 与模块本体分离的现代模块。本模块的设置页与任务同属一个 APK，用不上 Files，走 A 更直接。

### 2.3 本模块的差距

现状配置读写是自研的 `util/DataStore`（直接读写模块私有目录文件）与 `data/Config.java`（模型字段 JSON）：

- 设置页写：模块进程的私有目录；
- 任务读：支付宝进程的私有目录（`DataStore` 按 `AppContext` 的路径解析）；
- 两者都不是 `SharedPreferences`，框架的 `XSharedPreferences` 帮不上忙，Remote API 也没接。

这就是本地模式下「配置读不到、功能像没激活」的全部原因。

### 2.4 推荐做法：把配置存储换成 SharedPreferences

只动存储后端，不动 hook（158 处 `de.robv` 调用全部保留）。

| 项 | 改动 |
|---|---|
| 设置页写入 | `DataStore` 的配置类键改为写 `context.getSharedPreferences("sesame_config", MODE_PRIVATE)`；LSPosed 环境需要 `MODE_WORLD_READABLE`（框架 hook 了 `checkMode`），NPatch/Vector 由服务解析路径，两种模式都试一次，取可用的 |
| 注入侧读取 | 新增 `PrefsBridge.read(key)`：先 `XSharedPreferences(BuildConfig.APPLICATION_ID, "sesame_config")`，文件可读则用；否则回退现有 `DataStore` |
| 运行期状态 | 计数、缓存、`status.json`、日志继续留本地文件，**不要**塞进 prefs（XML 全量重写，高频写会掉帧） |
| 运行期改配置 | `XSharedPreferences` 只读，5 处 `setValue` 只改内存 + 本地覆盖表；读取顺序：本地覆盖 > prefs > 默认 |
| meta-data | 本项目 `module.prop` 的 `minApiVersion=100`，满足 LSPosed 对 New XSharedPreferences 的 `xposedminversion >= 93` 要求 |

迁移不需要改数据结构：`Config` 仍是「一个模型一个 JSON 字符串」，只是从文件挪到 prefs 的一个 String 键。

### 2.5 需要真机确认的三点

1. `getSharedPreferences(name, MODE_WORLD_READABLE)` 在 NPatch 下是否被允许。LSPosed 明确 hook 了 `checkMode`；NPatch 是否同样处理未在文档中说明，若不支持则改用 `MODE_PRIVATE`，靠 Vector 服务读取。
2. NPatch 本地模式下 `XSharedPreferences(模块包名, 文件名)` 的 `getFile().canRead()` 是否为 true。这是路径 A 能否成立的唯一判据。
3. 集成模式下是否同进程。若设置页与任务同进程，`XSharedPreferences` 也能读，行为不受影响；不需要为集成模式单独分支。

### 2.6 签名绕过等级（源码依据）

NPatch 的等级实现集中在 `patch-loader/src/main/java/top/nkbe/npatch/loader/SigBypass.java` 与
`patch-loader/src/main/jni/src/jni/bypass_sig.cpp`、`svc_bypass.cpp`：

| 等级 | 实现要点 | 覆盖的校验路径 | 可被检测的特征 |
|---|---|---|---|
| 1 Basic | Java IO 重定向 + native `open/openat/openat64` 重定向 | 应用读 APK 取签名 | libc 导入符号被替换 |
| 2 High | + 恢复原 `AppComponentFactory`、hook `getPackageArchiveInfo` / `hasSigningCertificate` | PackageManager 查询路径 | 同上（hook 面更宽） |
| 3 Extreme | + hook `PackageInfo(Parcel)` 构造 | Binder 反序列化取得的签名 | 反射/构造器 hook，可被检测栈帧 |
| 4 Seccomp | ARM64 seccomp v2 过滤 + trusted thread 文件重定向 | 直接 syscall 读文件的 native 校验 | `prctl(PR_GET_SECCOMP)` 可探测；与宿主自带 seccomp 冲突 |
| 5 Stealth | xhook 重定向目标 so 的 GOT/PLT，保留 `PackageInfo` 伪装 | 静态链接/内联 open 的 native 库 | 额外 so 出现在 `/proc/self/maps`，GOT 表项异常 |

针对支付宝的选择建议：

1. **优先 3 Extreme**。支付宝的签名校验主要走 Java 层 `PackageManager` 与 Binder 返回的 `PackageInfo`，3 覆盖完整，且不触碰 libc 代码段，与宿主 native 反调试组件冲突最小。
2. **仍被判定时再试 5 Stealth**。支付宝 native 安全组件（如安全键盘、设备指纹库）会自行 `open` APK 校验签名，此时只有 GOT/PLT 重定向能覆盖；代价是 maps 中出现额外 so，需要配合管理器侧的库名隐藏。
3. **4 Seccomp 不建议首选**。该级别在进程上安装 seccomp 过滤器，而支付宝自身也可能安装过滤器；两者叠加会返回 `EPERM`，表现为宿主 native 组件异常退出或功能静默失效，排查成本高。
4. 需明确的预期：**等级越高不等于越不易被识别**。改包本身是强特征（APK 路径、`/proc/self/maps`、加载器 so 均与官方包不同），3 与 5 的差异只在覆盖的校验路径，不在「隐形」程度。若仅为规避识别而切换本地模式，收益低于风险；本地模式的真实价值是「免重新修补即可换模块」。

### 2.7 官方文档引用与现状边界

| 结论 | 出处 |
|---|---|
| 两种修补方式、Extreme/Seccomp/Stealth 仅本地模式可用 | 站点首页「两种修补方式」 |
| 签名绕过 0–5 级定义、3/4/5 必须搭配 `--manager`、集成模式上限 High | `guide/usage.html` →「互斥规则」「签名绕过等级」 |
| 本地模式后续步骤（装管理器 → 装修补包 → 选模块与作用域） | `guide/usage.html` →「本地模式的后续步骤」 |
| Remote API 依赖 AAR、`<queries>` 声明、模块 App 用 `NPatchRemoteClient` 读写 | `guide/remote-api.html` →「添加 SDK」「建立连接」「API 参考」 |
| 目标进程内 Remote Preferences 只读、`edit()` 抛 `UnsupportedOperationException` | `guide/remote-api.html` →「API 101/102 模块运行时」 |
| 管理器包名 `top.nkbe.npatch`、authority `top.nkbe.npatch.remote` | `guide/remote-api.html` →「适用范围」「添加 SDK」 |
| 本地模式运行模型、loader 来源、作用域以管理器为准 | `guide/modes` →「Local Mode」「Comparison」 |
| Legacy 模块支持 `XSharedPreferences` | `guide/introduction`「模块 API 与运行能力」 |
| Legacy 的 `XSharedPreferences` 仍走原文件路径，不会被自动重定向到 Remote API | `guide/faq`「Remote Preferences 与 Remote Files 存在哪里」 |
| 注入侧只读、`edit()` 抛异常 | `guide/remote-api`「API 101/102 模块运行时」 |
| `XSharedPreferences` 跨 UID 读取模块 prefs 的实现 | Vector `legacy/.../XSharedPreferences.java:180/251/346` |
| New XSharedPreferences 模块侧写法（`MODE_WORLD_READABLE` + `XSharedPreferences(包名, 文件名)`） | LSPosed wiki「New XSharedPreferences」 |
| 三类模块加载方式（Legacy / API 101 / API 102） | 站点首页「一套运行时，三种模块」 |
| 各级别的 Java/native hook 面 | 源码 `patch-loader/.../SigBypass.java`、`patch-loader/src/main/jni/src/jni/bypass_sig.cpp`、`svc_bypass.cpp` |

现状边界（避免误判为已支持）：

- 当前仓库两条路径都没接：配置由自研 `DataStore` 直接读写模块私有目录，既不是 `SharedPreferences`，
  也没调用 `getRemotePreferences`。因此本地模式下设置页与任务互不可见，本文是实施方案而非既有能力。
- 设置页的「NPatch 环境」一行只陈述可验证事实（管理器是否安装、Provider 是否可解析），不代表本地模式已可用。
- 已完成的前置只有两项：`AndroidManifest.xml` 的 `<queries>` 声明、`NpatchEnv` 的只读探测（管理器是否安装、Provider 是否可解析），二者不影响集成模式。
- 集成模式暂不需要改造：现有配置在目标应用侧可读写，行为与现状一致；改用 SharedPreferences 后仍兼容。

---

## 三、设置页字段列表：评估后不采纳分组方案

### 3.1 已实现并保留：字段显示依赖

- `ModelField` 新增 `dependOnCode` / `dependOnValue`（`dependOn(code, expectValue)` 链式声明），不参与配置序列化。
- `ModelFieldShowDto` 透传两个字段，`getModelByGroup` 与 `getField` 自动携带。
- Web 端 `visibleFields(list)` 按同模型内父字段当前值过滤。
- 已声明依赖：秒杀模块 8 个商品列表与开抢整点字段，父字段为各自开关。

效果：开关关闭时，其附属字段不再出现。这解决的是「同一模块的开关与其列表被拆成两条互不相关内容」，不新增任何层级。

### 3.2 不采纳：卡片分组 + 高级折叠 + 搜索

原方案给出「一个模块一张卡 → 主开关 → 常用字段 → 高级字段折叠」的三层结构，评估后不采纳，理由：

1. **多一层嵌套**：分组行本身是可点击的容器，展开后才看到字段，等于在「分组（左侧导航）→ 模型 → 字段」之间又插了一层，与现有导航重复。
2. **收益不成立**：设置页实际使用方式是「改某几个开关后保存」，不是逐项浏览；字段数量多的模块（森林 60、庄园 57）本来就按分组进入，不需要再做一次折叠。
3. **成本集中在最不该动的地方**：`advanced`/`order` 要改 DTO 与全部字段声明，只为减少滚动距离，回归面大于收益。

原原型中的字段对比屏已移除，`doc/ui-prototype/index.html` 只保留「任务时间线」设计稿。

### 3.3 保留项

- 依赖隐藏（3.1）：保留，且是唯一的字段级显示规则。
- 域名一致的方案里，「搜索」「仅看已开启」「字段级重置」「危险操作二次确认」一并搁置，不进入开发计划。

---

## 四、后续优化项方案

经评估，只保留指纹一致性自检；时钟校准、失败分级重试、诊断包导出、配置快照回滚不再纳入：前两项分别被「秒杀提前量可调」与「遇风控立即停止」的现有策略覆盖，后两项与现有能力重复（配置已有「导出配置/导入配置」，日志已可查看）。

### 4.1 指纹一致性自检

现状只判断 UA 结构（`MYWeb/UWS/UCBS` 三段）。扩展为字段级一致性检查：

| 检查项 | 冲突示例 |
|---|---|
| UA 机型代码 与 `DeviceProfile.model` | UA 为 `2311FRAFDC`，指纹为 `V2238A` |
| UA 分辨率段 `ws:` 与 `DeviceProfile.screenWs` | `ws:393\|0\|873` 与 `384\|0\|2.8125` |
| `AliApp(AP/x)` 与 `AlipayClient/x` 版本 | 12.12.26.8100 与 12.12.30.8000 |
| `Chrome` 版本与 `MYWeb` 版本组合 | 已知组合集合之外的搭配 |

输出改为逐项结论（命中几项、冲突在哪），而不是笼统的「格式异常」。实现位置：`ElemeMtopUtil.DeviceProfile` 增加 `consistencyIssues(): List<String>`，任务日志与账号状态弹窗复用。
