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

## 二、NPatch 本地模式实现方案

> 本章为初版方案。经代码审查，其中有 3 处必须修正（目标 API 版本、Remote 数据流方向、运行期写配置），
> 修正后的路径与验证清单见 `doc/NPatch本地模式方案审查.md`。开发前以审查报告为准。


### 2.1 两种修补方式的运行模型

| 维度 | 集成模式 `--embed` | 本地模式 `--manager` |
|---|---|---|
| 模块 APK 位置 | 目标应用 `assets/npatch/modules/<pkg>.apk` | 独立安装，管理器按作用域加载 |
| 模块设置页所在进程 | 目标应用进程（待验证：官方未明说 Activity 是否合并） | 模块自身进程 |
| 配置目录 | 目标应用私有目录（设置页与任务共用） | 两个进程各自私有目录（互不可见） |
| 换模块 | 需重新修补 | 管理器内切换，无需重新修补 |
| 签名绕过上限 | High（2） | Extreme（3）/ Seccomp（4）/ Stealth（5） |

本地模式「模块显示未激活、功能不可用」的根因是**配置与状态存储位置不同**，与激活检测逻辑无关：
设置页把配置写入模块 APK 的私有目录，任务运行在支付宝进程，读取的是支付宝的私有目录，二者没有任何共享通道。

官方的运行模型（`guide/modes`）：

- 修补后的 APK 自带 `assets/npatch/loader.bin` 与 `assets/npatch/so/<abi>/libnpatch.so`，加载器与原生核心都来自被修补包，不从管理器提取。
- 运行时用目标包名向管理器（`top.nkbe.npatch`）查询模块与作用域：先连 Manager Service，不可用再查 Manager ConfigProvider，两者都不可用才回退到目标应用上一次成功同步的模块缓存。
- 作用域始终以管理器数据库为准，模块无法自行决定被加载。
- 集成模式把模块 APK 打进 `assets/npatch/modules/`，Remote 数据存在目标应用自己的数据目录，因此模块 UI 与目标应用同目录；本地模式的数据统一存在管理器，按模块包名隔离。

### 2.2 共享通道：NPatch Remote API

官方提供的通道是 Remote Preferences / Remote Files，由管理器保存并按模块包名隔离：

- 模块设置页侧：`NPatchRemoteClient.getRemotePreferences(group)`，可读写。
- 目标进程侧：`XposedInterface.getRemotePreferences(group)`，**只读**。
- 变更通知：管理器把 Diff 推送给已注入的 API 101/102 runtime，目标进程无需重启。

读写方向与本模块的模型完全吻合：设置在设置页写入，任务只读取。

**注入侧入口是否存在，取决于模块走哪条管线**：官方 FAQ 的表述是「作用域内的 API 101/102
目标进程只取得读取与变更通知能力」，`XposedInterface` 就是这条只读通道。

本仓库是**双入口**：

- `app/src/main/resources/META-INF/xposed/{module.prop, scope.list, java_init.list}` → 现代管线，
  入口 `LibXposedRuntime : XposedModule()`（继承 `XposedInterfaceWrapper`，自带
  `getRemotePreferences()` / `getFrameworkProperties()`）；
- `assets/xposed_init` → Legacy 管线，仅老框架使用。

NPatch 按 `module.prop` 选择现代管线，因此在 NPatch 下注入侧**有** Remote 只读入口，不需要迁移。
但 `targetApiVersion=101` 时框架是否真的实现该能力，必须先探针验证（见 2.3 路径 A）。

需要注意的反向限制：`targetApiVersion >= 102` 会在 ClassLoader 层封锁 `de.robv.android.xposed`，
而本仓库 hook 全部依赖 `de.robv`（56 个 `XC_MethodHook`、23 处 `findAndHookMethod`），
所以**不能**为了 Remote 而把 target 提到 102。

### 2.3 前置决策：三条路径

| 路径 | 做法 | 代价 | 结论 |
|---|---|---|---|
| A. 复用现有现代入口（首选） | `module.prop` 保持 101；在 `LibXposedRuntime.onPackageReady` 里用 `getRemotePreferences` 读配置，hook 继续走 legacy 桥 | 探针约 15 行 + `Config` 读取分支 1 处 + 设置页桥 1 个类 | 前提是 101 档位支持 Remote，需实测 |
| B. 设置 UI 移入目标进程 | 在支付宝进程内用原生 View 提供设置界面，配置直接写目标应用私有目录 | 需重写设置界面（现有为 Web UI），但与任务天然同进程 | 仅在 A 走不通时考虑 |
| C. 暂不支持本地模式 | 维持集成模式，配置与任务同进程 | 无 | 当前状态；签名绕过上限 High（2） |

**不要把 target 提到 102 当作路径**：102 会在 ClassLoader 层封锁 `de.robv`，现有 hook 全部失效，
重写规模是 28 个文件、158 处调用，且收益只是「本地模式可用」，不值得。

不建议为「更高级别的签名绕过」而做任何迁移：改包本身（APK 路径、`/proc/self/maps`、加载器 so）已是最强特征，
3/4/5 只扩大覆盖的校验路径，并不降低被识别概率。

以下分阶段方案以路径 A 为目标。

### 2.4 分阶段实现

#### 阶段 A：接入依赖与连接（可独立验证）

1. `app/libs/` 放入 `npatch-remote-api-v<ver>-release.aar`，`implementation(files(...))`；
   `io.github.libxposed:api:102.0.0` 已经是 `compileOnly`（`app/build.gradle.kts:179`），编译期接口够用，
   但 AAR 运行期需要 `io.github.libxposed:interface:102.0.0` 时再显式加（注意不要改 `module.prop`）。
2. `AndroidManifest.xml` 已声明 `<queries>`（`top.nkbe.npatch.remote` 与包名 `top.nkbe.npatch`）。
3. 新增 `NpatchRemoteBridge`：
   - `isAvailable(context)` → `NPatchRemoteClient.isAvailable(context)`
   - `connectAsync(context)` → 成功后在主线程回调，缓存 client
   - 按官方错误表分别处理 `IllegalStateException`（未安装/超时）、`SecurityException`（模块未登记）、
     `RemoteException`（Binder 断开，需丢弃 client 重连）；连接失败时在设置页明确提示
     「NPatch 不可用，配置仅本机生效」，不静默回退。

验证点：设备安装 NPatch 管理器且模块已登记时，设置页「设备信息 → NPatch 环境」应显示 Remote Provider 可用；未安装时该行降级为「未检测到 NPatch 管理器」，且不产生异常。

#### 阶段 B：配置读取抽象

现状配置读写集中在 `fansirsqi.xposed.sesame.util.DataStore`（键值）与 `Config`（模型字段序列化）。

1. 定义接口 `ConfigSource`：
   - `String get(String key, String def)`
   - `void put(String key, String value)`
   - `Map<String, String> getAll()`
2. 两个实现：
   - `LocalConfigSource`：现有 DataStore，行为保持不变。
   - `RemoteConfigSource`：包一层 `SharedPreferences`（Remote Preferences 返回标准 `SharedPreferences` 实例）。
3. 选择逻辑（按进程角色分流，而不是按是否检测到 NPatch）：
   - 目标进程（hook 侧，`AppContext.isHooked()` 为 true）：优先 Remote（只读），读不到时回退本地。
   - 设置页进程：Remote（可写）优先，连接失败回退本地并提示「当前为本地存储，本地模式下配置不会同步到目标进程」。
4. `Config` 的模型字段整体 JSON 仍以单键存储（例如 `ElemeTask` 一个键），迁移不涉及数据结构改造，仅替换读写后端。

#### 阶段 C：文件类数据

日志、商城缓存、CK 凭据属于文件型数据：

| 数据 | 建议 |
|---|---|
| 商城缓存 `eleme_mall_cache.json` | 目标进程本地写；设置页需要展示时通过 Remote Files 只读拉取 |
| 运行日志 | 保留目标进程本地；设置页查看日志改用 Remote Files 拉取副本 |
| CK 凭据 | 不写入 Remote Files（管理器私有库无内容加密，且 CK 等价于账号凭据），仅存目标进程本地 |

Remote Files 只接受单层文件名，禁止路径分隔符，与现有平铺文件名兼容。

#### 阶段 D：降级与提示

- 设置页顶部增加环境条：`集成模式（同进程，配置直连）` / `本地模式（Remote 已连接）` / `本地模式（Remote 不可用，配置仅本机生效）`。
- 当处于第三态时，所有依赖配置的功能入口保持可见，但点击保存时给出一次明确提示，避免用户误以为配置已生效。

### 2.5 改动清单

| 文件 | 改动 |
|---|---|
| `app/build.gradle.kts` | 引入 Remote AAR 与 `libxposed:interface` |
| `AndroidManifest.xml` | 已加 `<queries>` |
| `util/NpatchEnv.kt` | 已实现探测；补充连接状态缓存 |
| `util/NpatchRemoteBridge.kt` | 新增，封装 client 生命周期与异常降级 |
| `util/DataStore.kt`、`data/Config.java` | 读写后端抽象为 `ConfigSource` |
| `ui/WebSettingsActivity.java` | 暴露 `getRuntimeEnv()` 给设置页展示环境条 |
| `assets/web/index.html` | 环境条与保存提示 |

### 2.6 风险与验证

1. Remote Preferences 在目标进程为只读，任何既有「任务运行时写配置」的逻辑（如 `disableElemeLuckyStar` 关闭开关）会抛 `UnsupportedOperationException`，需改为写本地覆盖值并在设置页合并展示。
2. 管理器进程被杀后 Binder 失效，需要重连逻辑与超时（同步 `connect` 最多 3 秒，UI 必须走异步）。
3. 无法在没有真机的情况下验证注入侧行为，全部改动需在集成模式（回归）与本地模式（新功能）各跑一轮。
4. 不实现热重载与作用域控制：这两项属于管理器职责，模块侧无收益。

### 2.7 签名绕过等级选择（源码依据）

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

### 2.8 官方文档引用与现状边界

| 结论 | 出处 |
|---|---|
| 两种修补方式、Extreme/Seccomp/Stealth 仅本地模式可用 | 站点首页「两种修补方式」 |
| 签名绕过 0–5 级定义、3/4/5 必须搭配 `--manager`、集成模式上限 High | `guide/usage.html` →「互斥规则」「签名绕过等级」 |
| 本地模式后续步骤（装管理器 → 装修补包 → 选模块与作用域） | `guide/usage.html` →「本地模式的后续步骤」 |
| Remote API 依赖 AAR、`<queries>` 声明、模块 App 用 `NPatchRemoteClient` 读写 | `guide/remote-api.html` →「添加 SDK」「建立连接」「API 参考」 |
| 目标进程内 Remote Preferences 只读、`edit()` 抛 `UnsupportedOperationException` | `guide/remote-api.html` →「API 101/102 模块运行时」 |
| 管理器包名 `top.nkbe.npatch`、authority `top.nkbe.npatch.remote` | `guide/remote-api.html` →「适用范围」「添加 SDK」 |
| 本地模式运行模型、loader 来源、作用域以管理器为准 | `guide/modes` →「Local Mode」「Comparison」 |
| 注入侧只读、Legacy `XSharedPreferences` 不自动迁移 | `guide/faq`「Remote Preferences 与 Remote Files 存在哪里」、`guide/remote-api` 首段 |
| 三类模块加载方式（Legacy / API 101 / API 102） | 站点首页「一套运行时，三种模块」 |
| 各级别的 Java/native hook 面 | 源码 `patch-loader/.../SigBypass.java`、`patch-loader/src/main/jni/src/jni/bypass_sig.cpp`、`svc_bypass.cpp` |

现状边界（避免误判为已支持）：

- 当前仓库**尚未接入** Remote API：NPatch 下走现代管线（`LibXposedRuntime`）、具备 `XposedInterface`，
  但从未调用过 `getRemotePreferences`；配置仍写在本机私有目录，因此本地模式目前不可用。
  本文是实施方案，不是既有能力。
- 设置页的「NPatch 环境」一行只陈述可验证事实（管理器是否安装、Provider 是否可解析），不代表本地模式已可用。
- 已完成的前置只有两项：`AndroidManifest.xml` 的 `<queries>` 声明、`NpatchEnv` 的只读探测（管理器是否安装、Provider 是否可解析），二者不影响集成模式。
- 集成模式不需要任何改造，配置与任务同进程，行为与现状一致。

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

经评估，以下两项保留；时钟校准、失败分级重试、诊断包导出、配置快照回滚不再纳入：前两项分别被「秒杀提前量可调」与「遇风控立即停止」的现有策略覆盖，后两项与现有能力重复（配置已有「导出配置/导入配置」，日志已可查看）。

### 4.1 指纹一致性自检

现状只判断 UA 结构（`MYWeb/UWS/UCBS` 三段）。扩展为字段级一致性检查：

| 检查项 | 冲突示例 |
|---|---|
| UA 机型代码 与 `DeviceProfile.model` | UA 为 `2311FRAFDC`，指纹为 `V2238A` |
| UA 分辨率段 `ws:` 与 `DeviceProfile.screenWs` | `ws:393\|0\|873` 与 `384\|0\|2.8125` |
| `AliApp(AP/x)` 与 `AlipayClient/x` 版本 | 12.12.26.8100 与 12.12.30.8000 |
| `Chrome` 版本与 `MYWeb` 版本组合 | 已知组合集合之外的搭配 |

输出改为逐项结论（命中几项、冲突在哪），而不是笼统的「格式异常」。实现位置：`ElemeMtopUtil.DeviceProfile` 增加 `consistencyIssues(): List<String>`，任务日志与账号状态弹窗复用。

### 4.2 任务时间线

原型见 `doc/ui-prototype/index.html`（单屏设计稿，含顶部统计与时间轴）。

数据来源与可行性：

- 调度侧：`TaskCommon` / `AlarmScheduler` 持有各任务的下次触发时间，需在 `ModelTask` 暴露只读快照（`nextRunAt` 与 `lastRunResult`），不新增网络请求。
- 展示侧：设置页新增一个只读页面，按时间排序渲染；每条含任务名、分组、状态（已执行/部分失败/待执行）、最近结果摘要。
- 未执行过的任务显示「待首次执行」，不使用推测时间填充。

界面要素：左侧时间列 + 竖轴 + 状态圆点（绿=成功、橙=部分失败、灰=待执行），条目卡片显示任务名与所属分组。