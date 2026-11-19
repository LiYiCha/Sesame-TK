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

### 2.1 两种修补方式的运行模型

| 维度 | 集成模式 `--embed` | 本地模式 `--manager` |
|---|---|---|
| 模块 APK 位置 | 目标应用 `assets/npatch/modules/<pkg>.apk` | 独立安装，管理器按作用域加载 |
| 模块设置页所在进程 | 目标应用进程（支付宝） | 模块自身进程 |
| 配置目录 | 目标应用私有目录（设置页与任务共用） | 两个进程各自私有目录（互不可见） |
| 换模块 | 需重新修补 | 管理器内切换，无需重新修补 |
| 签名绕过上限 | High（2） | Extreme（3）/ Seccomp（4）/ Stealth（5） |

本地模式「模块显示未激活、功能不可用」的根因是**配置与状态存储位置不同**，与激活检测逻辑无关：
设置页把配置写入模块 APK 的私有目录，任务运行在支付宝进程，读取的是支付宝的私有目录，二者没有任何共享通道。

### 2.2 共享通道：NPatch Remote API

官方提供的通道是 Remote Preferences / Remote Files，由管理器保存并按模块包名隔离：

- 模块设置页侧：`NPatchRemoteClient.getRemotePreferences(group)`，可读写。
- 目标进程侧：`XposedInterface.getRemotePreferences(group)`，**只读**。
- 变更通知：管理器把 Diff 推送给已注入的 API 101/102 runtime，目标进程无需重启。

读写方向与本模块的模型完全吻合：设置在设置页写入，任务只读取。

### 2.3 分阶段实现

#### 阶段 A：接入依赖与连接（可独立验证）

1. `app/libs/` 放入 `npatch-remote-api-v<ver>-release.aar`，`implementation(files(...))`；
   若 `io.github.libxposed:interface` 未传递到，显式添加 102.0.0。
2. `AndroidManifest.xml` 已声明 `<queries>`（`top.nkbe.npatch.remote` 与包名 `top.nkbe.npatch`）。
3. 新增 `NpatchRemoteBridge`：
   - `isAvailable(context)` → `NPatchRemoteClient.isAvailable(context)`
   - `connectAsync(context)` → 成功后在主线程回调，缓存 client
   - 所有失败路径捕获 `IllegalStateException` / `SecurityException` / `RemoteException`，回退本地存储。

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

### 2.4 改动清单

| 文件 | 改动 |
|---|---|
| `app/build.gradle.kts` | 引入 Remote AAR 与 `libxposed:interface` |
| `AndroidManifest.xml` | 已加 `<queries>` |
| `util/NpatchEnv.kt` | 已实现探测；补充连接状态缓存 |
| `util/NpatchRemoteBridge.kt` | 新增，封装 client 生命周期与异常降级 |
| `util/DataStore.kt`、`data/Config.java` | 读写后端抽象为 `ConfigSource` |
| `ui/WebSettingsActivity.java` | 暴露 `getRuntimeEnv()` 给设置页展示环境条 |
| `assets/web/index.html` | 环境条与保存提示 |

### 2.5 风险与验证

1. Remote Preferences 在目标进程为只读，任何既有「任务运行时写配置」的逻辑（如 `disableElemeLuckyStar` 关闭开关）会抛 `UnsupportedOperationException`，需改为写本地覆盖值并在设置页合并展示。
2. 管理器进程被杀后 Binder 失效，需要重连逻辑与超时（同步 `connect` 最多 3 秒，UI 必须走异步）。
3. 无法在没有真机的情况下验证注入侧行为，全部改动需在集成模式（回归）与本地模式（新功能）各跑一轮。
4. 不实现热重载与作用域控制：这两项属于管理器职责，模块侧无收益。

### 2.6 签名绕过等级选择（源码依据）

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

---

## 三、设置页字段列表方案

### 3.1 已实现：字段显示依赖

- `ModelField` 新增 `dependOnCode` / `dependOnValue`（`dependOn(code, expectValue)` 链式声明），不参与配置序列化。
- `ModelFieldShowDto` 透传两个字段，`getModelByGroup` 与 `getField` 自动携带。
- Web 端 `visibleFields(list)` 按同模型内父字段当前值过滤。
- 已声明依赖：秒杀模块 8 个商品列表与开抢整点字段，父字段为各自开关。

效果：开关关闭时，其附属字段不再出现，同一模块的配置不再被拆成多条互不相关的内容。

### 3.2 目标信息架构

```
分组（左侧导航，现有）
└── 模型卡片（一个 ModelTask 一张卡）
    ├── 主开关（含一句话说明）
    ├── 常用字段（≤5 个，直接展示）
    ├── 高级字段（折叠，默认收起）
    └── 动作字段（EmptyModelField：查看/刷新等）
```

实现方式：`ModelField` 增加 `advanced` 标记与 `order` 序号，Web 端按 `order` 排序，`advanced` 归入折叠区。
不新增后端接口，仅扩展 DTO 字段。

### 3.3 字段能力扩展

| 能力 | 实现要点 | 收益 |
|---|---|---|
| 搜索 | 顶部输入框，对 `name/desc/code` 做包含匹配，跨模型检索 | 字段总数超过 100 后，翻页成本显著下降 |
| 仅看已开启 | 过滤 `BooleanModelField` 为 true 的模型 | 快速核对当日实际生效范围 |
| 字段说明折叠 | `desc` 超过 30 字时折叠为「说明」链接 | 避免长文案挤压列表 |
| 字段级重置 | 每个字段右侧提供「重置为默认」 | 替代整页恢复默认 |
| 危险操作确认 | 对「清空 UA 历史」「关闭账号级开关」等二次确认 | 减少误触 |

### 3.4 兼容与迁移

- `advanced`/`order` 均有默认值（`false`/`Integer.MAX_VALUE`），旧配置无需迁移。
- `dependOnCode` 指向不存在的字段时按「无依赖」处理，避免字段被静默隐藏。

### 3.5 工作量拆分

1. 依赖收起（已完成）。
2. `advanced` + `order` + 折叠区（后端 1 处、前端 1 处）。
3. 搜索与「仅看已开启」（纯前端）。
4. 字段级重置与危险操作确认（后端补 `resetField` 桥方法）。

---

## 四、后续优化项方案

### 4.1 服务端时钟校准（优先级最高）

秒杀以「整点减 200ms」发车，时间基准是本机 `System.currentTimeMillis()`。本机时钟偏差 1 秒，提前量即失效。

- 采集：任一次 mtop 响应头或响应体中的服务端时间戳，与本地时间求差，取多次中位数。
- 存储：`ClockOffsetStore`，按天衰减（偏差会随 NTP 校正变化）。
- 使用：`BaseFlashSaleTask.calculateTargetTime` 改为 `now() + offset` 计算整点。
- 展示：设置页秒杀分组显示「时钟偏差 +12ms」。

### 4.2 指纹一致性自检

现有 UA 判定只查结构。可扩展为一致性检查：

- UA 中的 Android 版本、机型代码、分辨率（`AlipayDefined(ws:...)`）必须与 `DeviceProfile` 自洽；
- `AlipayClient` 版本与 `AliApp(AP/...)` 版本必须相同；
- `Chrome` 版本与 `MYWeb` 版本组合需落在已知区间。

不一致时在日志给出具体冲突项（如「UA 分辨率 393|0|873 与设备指纹 384|0|2.8125 不一致」），而不是笼统的「格式异常」。

### 4.3 失败分级重试

现状：秒杀固定重试 3 次，任务层遇到 405/417 直接拉黑。

分级：

| 类别 | 判定 | 处理 |
|---|---|---|
| 可重试 | 网络异常、超时、`SYSTEM_BUSY` | 指数退避重试 |
| 不可重试 | 405 行为受限、417 防作弊、账号风险 | 立即停止并记录 |
| 需人工 | 会话失效、参数缺失、活动下线 | 停止并提示具体动作 |

实现：在 `ElemeMtopUtil.execute` 返回 `MtopResult` 时附带 `retryable` 枚举，调用方不再各自判断错误码。

### 4.4 诊断包导出

一键生成 zip，内含：最近 500 行运行日志、当前 UA 与结构判定、CK 摘要（长度 + 前 6 位 + 过期时间，不含明文）、最近 10 条失败响应原文、配置快照（脱敏）。

价值：用户反馈问题时无需逐条粘贴日志。

### 4.5 配置快照与回滚

每次任务运行前把当前 `Config` 序列化写入 `config_history/<timestamp>.json`，保留最近 5 份；设置页提供「查看/回滚」。

价值：调试 UA、风控名单等改动后出现异常时，可一键回到上一状态。

### 4.6 任务时间线视图

汇总各任务下次执行时间与最近执行结果，按时间排序展示。数据来源是任务调度器的下次触发时间，需在 `ModelTask` 层暴露只读快照。

价值：替代逐个进入模块确认「今天跑没跑、下次什么时候跑」。
