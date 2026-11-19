# NPatch 本地模式方案 · 审查报告

审查对象：`doc/NPatch本地模式与设置页优化方案.md` 第二章（本地模式实现方案）。
依据：NPatch 官方 `guide/modes`、`guide/remote-api`、`guide/faq`；仓库当前代码与构建配置；libxposed API 102.0.0 源码。

结论先行：**方案方向成立，但有 3 处必须修正，其中 2 处会导致方案直接失败**。修正后工作量约为原估算的五分之一，可以先做一次探针验证再决定是否开发。

---

## 一、审查依据的仓库现状

| 项 | 现状 | 依据 |
|---|---|---|
| 模块清单 | `minApiVersion=100`、`targetApiVersion=101`、`staticScope=true` | `app/src/main/resources/META-INF/xposed/module.prop` |
| 现代入口 | `LibXposedRuntime : XposedModule()`，`onPackageReady` 调 `ApplicationHook.startHook` | `hook/libxposed/LibXposedRuntime.kt` |
| hook 实现 | 全部 `de.robv`：`XC_MethodHook` 56、`findAndHookMethod` 23、`hookMethod` 14、`hookAllMethods` 3、`XC_MethodReplacement` 4，分布在 28 个文件 | `grep` 统计 |
| 依赖 | `io.github.libxposed:api:102.0.0` 为 `compileOnly` | `app/build.gradle.kts:179` |
| Remote 接口 | `XposedInterface.getRemotePreferences(String)`、`openRemoteFile(String)`、`PROP_CAP_REMOTE` 均存在 | `api-102.0.0-sources.jar` 内 `XposedInterface.java:67/543/563` |
| 运行期写配置 | 5 处任务会在运行时把开关置 false | `AntFarmLoveChickenGathering.kt:98`、`AntFarmRankAward.kt:28`、`AntOcean.java:210`、`HuaBeiJin.java:104`、`OtherTask2.java:84` |

关键事实：模块**已经**跑在 libxposed 现代管线上（`module.prop` 存在），只是 hook 逻辑借 101 的 legacy 桥运行。这一点直接推翻了原方案对工作量的判断。

---

## 二、必须修正的三处

### 2.1 目标 API 不能升到 102（原方案的最大错误）

原方案 A 写「迁移 libxposed API 102」，并把 `module.prop` 指向 102。但：

- `LibXposedRuntime.kt` 的注释已写明「`targetApiVersion=101` 保证现代管线下 legacy 桥可用（102 起被框架禁用）」。
- 一旦升到 102，`ApplicationHook.startHook` 里的 56 个 `XC_MethodHook`、23 处 `findAndHookMethod` 会**整体失效**，任务功能直接归零。
- 而 Remote 能力并非 102 专属：官方 `guide/remote-api` 对注入侧的描述是「API 101/102 模块在目标进程中通过 libxposed `XposedInterface` 取得只读的 Remote Preferences」，`XposedInterface` 的 Remote 方法在 API 100 就已存在。

**修正**：保持 `targetApiVersion=101`，不迁移 hook。`LibXposedRuntime` 本身是 `XposedModule`（继承 `XposedInterfaceWrapper`），可以直接调用 `getRemotePreferences(group)`，读到后写入进程内缓存，供 legacy hook 代码沿用现有 `Config`/`DataStore` 读取。

工作量从「重写 28 文件、158 处调用」降为「改 1 个入口 + 配置读取层 1 层分支」。

### 2.2 Remote 的数据流方向写反了

原方案 2.3 阶段 C 写「商城缓存、运行日志：设置页通过 Remote Files 只读拉取」。方向错误：

- 官方：模块 App 取得的文件描述符**可读写**；**目标进程内 API 101/102 的 `openRemoteFile()` 只读**。
- 注入进程（支付宝内跑任务的那一侧）没有写 Remote 的能力，写只能由模块 App 完成。

后果：本地模式下，注入进程产生的三类数据无法回到设置页——

| 数据 | 产生方 | 能否经 Remote 传给设置页 |
|---|---|---|
| 当日任务标记 `status.json`（flagList） | 注入进程 | 不能 |
| 运行日志 | 注入进程 | 不能 |
| 商城缓存（`ElemeMallEntity`） | 注入进程为主 | 不能 |

而现有实现依赖这条路径：`showAccountStatusDialog` / `readTodayFlags` 直接读磁盘 `status.json`（代码注释写明「设置进程与钩子进程内存隔离，直接读文件」）。集成模式同 UID 可读，本地模式读不到。

**修正**：把数据分成两类，方案里分别处理，不要混为一谈。

| 类别 | 方向 | 载体 | 本地模式 |
|---|---|---|---|
| 用户配置（任务开关、UA、兑换商品、整点） | 设置页 → 注入 | Remote Preferences | 可行 |
| 运行状态（flagList、日志、商城缓存） | 注入 → 设置页 | 无官方通道 | 降级 |

降级方式只有两种，必须二选一，不能假装能同步：
1. 设置页在本地模式下不展示当日状态，改为提示「本地模式不支持读取运行状态」；
2. 注入进程把状态写到外部可见目录（需存储权限/SAF），设置页再读——引入权限成本，不建议。

### 2.3 运行期写配置会撞上只读实现

原方案在风险里提了一句，但没给处理方式。实际有 5 处任务在运行时把开关置 false（见上表）。这些 `setValue` 会落到 `Config` 持久化：

- 若 `Config` 后端在注入侧指向 Remote（只读），调用 `edit()` 会抛 `UnsupportedOperationException`；
- 若吞掉异常，开关看起来关掉了，重启后又被 Remote 覆盖，表现为「关不掉的开关」。

**修正**：Remote 只承载「设置页写入的配置」，运行期变更一律写本地覆盖层，读取顺序为：

```
本地覆盖值（本次运行期写入） > Remote（用户在设置页保存的） > 字段默认值
```

本地覆盖层用现有 `DataStore` 即可，键加前缀区分，避免与配置本身混淆。

---

## 三、修正后的实施路径（每步可独立验证）

| 阶段 | 内容 | 改动面 | 验证点 |
|---|---|---|---|
| P0 探针 | `LibXposedRuntime.onPackageReady` 里读 `frameworkProperties & PROP_CAP_REMOTE`，尝试 `getRemotePreferences("npatch_probe")` 并日志输出结果 | 1 个文件、约 20 行 | 本地模式与集成模式各跑一次，确认能力位与可读性 |
| P1 注入侧只读 | `Config` 读取增加 Remote 分支：Remote 有值则用，无值回退 `DataStore` | 1 层分支 | 改一个开关，重启后仍生效 |
| P2 设置页写入 | 引入 `npatch-remote-api` AAR，`NPatchRemoteClient.connectAsync`，写入 Remote + 本地双写 | 1 个桥类 + 保存路径 | 本地模式改开关，注入侧读到；集成模式行为不变 |
| P3 状态降级 | 本地模式下账号状态弹窗/日志入口标注不可用原因 | 2 处 UI | 不再出现「空白状态」 |

P0 不改变任何行为，失败也只是多一行日志，应当先做。

---

## 四、必须先真机验证的四件事

1. **API 101 下 Remote 是否真的可用**：`PROP_CAP_REMOTE` 是否置位、`getRemotePreferences` 是否抛 `UnsupportedOperationException`。官方文档写「API 101/102」，但本项目 target 是 101，需实测。
2. **集成模式下注入侧能否读到 host-local Store**：官方说集成模式的数据在目标应用目录，模块初始化时会收到进程内 `IXposedService`。若注入侧可读，则同一套代码两种模式通吃；若不可读，需按模式分流。
3. **NPatchRemoteClient 连接行为**：`connect` 最多等 3 秒，连接期间 Manager 进程可能被拉起；设置页首次打开会明显卡顿，必须走 `connectAsync`。
4. **group 命名**：不可为空、不含 `/` 与 `\`；现有配置键名（如 `ElemeTask`）可用，但要统一前缀，避免与探针 group 冲突。

---

## 五、安全与兼容边界

- **CK 不进 Remote**：Manager 的 Remote Store 不提供内容加密（官方明确「SDK 不为 Remote Files 提供内容加密」）。CK 等价于账号凭据，只保留在注入进程本地。这意味着本地模式下设置页无法展示/复制 CK——现有「复制CK」按钮需按模式禁用。
- **集成模式回归**：P1 的分支必须保证 Remote 不可用时完全回到现有行为，否则会在用户当前可用环境里引入退化。
- **作用域不归模块管**：模块无法自行决定被加载，方案里不应包含任何 scope 写入逻辑。

---

## 六、审查结论

1. 原方案的核心判断「必须迁移 API 102 才能读 Remote」错误，且按原方案执行会破坏现有全部 hook。改为「保持 101 + 在 `LibXposedRuntime` 内读 Remote」后，改动面从 158 处降到 3 个文件。
2. 原方案把「状态回传」当成可行，实际方向相反。本地模式下状态类功能必须明确降级，不能承诺同步。
3. 运行期写配置必须走本地覆盖层，否则会出现关不掉的开关。

建议：先落 P0 探针，用一次真机运行确认 101 的 Remote 能力，再决定 P1～P3 是否值得做。若探针显示 101 不支持 Remote，则本地模式只剩「设置 UI 移入目标进程」一条路，成本远高于收益，应维持集成模式。
