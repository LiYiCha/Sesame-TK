# NPatch 本地模式方案 · 审查报告（第二版）

本版修正第一版的表述错误：第一版写成「不能升到 102」，容易被读成「方案要求升 102 而我反对」。
实际问题是——**方案把已经完成的事列成了待办，并且写错了清单文件路径**。以下逐条核对。

---

## 一、先把三个「API 版本」分开

这是第一版没讲清楚、也最容易误解的地方。

| 名称 | 当前值 | 位置 | 作用 | 能否随意改 |
|---|---|---|---|---|
| Gradle 依赖版本 | `102.0.0` | `app/build.gradle.kts:179`（`compileOnly`） | 只决定**编译期**能调用哪些接口方法；不打进 APK，不参与运行时 | 可以，纯编译期 |
| `targetApiVersion` | `101` | `app/src/main/resources/META-INF/xposed/module.prop` | **运行时契约**：框架据此决定按哪个 API 档位加载。`>=102` 时框架在 ClassLoader 层封锁 `de.robv.android.xposed` | 不能随意改，见下 |
| 框架实际 API | 设备决定 | `getApiVersion()` | NPatch 内置 Vector 核心的实现版本 | 不可控 |

所以「这个 API 已经使用了」是对的——**依赖库是 102**；但模块向框架声明的契约仍是 **101**。两者不冲突，当前组合是刻意为之：
编译期能调 102 的接口，运行时保留 `de.robv` 兼容。

`targetApiVersion >= 102` 会封锁 legacy 的依据（不是推断）：

- Vector PR #757 原文：「API 102 forbids a module targeting 102 or higher from reaching `de.robv.android.xposed`, and the module class loader is the only place that can be enforced」。
- 已适配模块文档：「两者不能混用：`targetApiVersion >= 102` 的模块在 classloader 层面就被禁止访问 legacy 包，因此 `XposedHelpers`、`XSharedPreferences`、`XposedBridge.log` 全部不可用」。
- 本仓库 `LibXposedRuntime.kt` 注释记录了同一结论。

结论：**问题不是「要升 102」，而是「不能升 102」**。本仓库有 56 个 `XC_MethodHook`、23 处 `findAndHookMethod`，全靠 legacy 桥。

---

## 二、原方案逐条核对

方案文件：`doc/NPatch本地模式与设置页优化方案.md`，行号为核对时的实际行号。

| # | 方案原文 | 仓库/官方事实 | 判定 |
|---|---|---|---|
| 1 | 2.3 表 A 行（第 92 行）：「迁移 libxposed API 102：入口改为 `XposedModule` + `assets/META-INF/xposed/module.prop`」 | 入口**已经是** `XposedModule`：`java_init.list` 指向 `LibXposedRuntime`，后者 `: XposedModule()`；`module.prop` 与 `scope.list` 都在 `app/src/main/resources/META-INF/xposed/` | ✗ 把已完成项列为待办；路径也写错（是 `resources/`，不是 `assets/`） |
| 2 | 2.3 表 A 行：「重写 28 个文件、158 处 hook 调用」 | 只有把 `targetApiVersion` 提到 102 才需要重写；保持 101 则一处都不用改 | ✗ 工作量估算建立在错误前提上 |
| 3 | 2.1 表（第 49 行）：「模块设置页所在进程：集成模式下目标应用进程」 | 官方 `guide/modes` 只说集成模式把模块 APK 打进 `assets/npatch/modules/`、Remote 数据存目标应用数据目录，**没有**说模块 Activity 合并进目标清单 | ⚠ 无依据的断言，需标注为待验证 |
| 4 | 2.3 阶段 C：「商城缓存、日志：设置页通过 Remote Files 只读拉取」 | 官方：模块 App 侧可读写，**注入侧只读**。注入进程写不了 Remote | ✗ 方向反了 |
| 5 | 2.6 风险 1 已提「运行期写配置会抛 `UnsupportedOperationException`」，但没给处理 | 实际 5 处运行期写开关：`AntFarmLoveChickenGathering.kt:98`、`AntFarmRankAward.kt:28`、`AntOcean.java:210`、`HuaBeiJin.java:104`、`OtherTask2.java:84` | ⚠ 已识别未解决 |
| 6 | 2.4 阶段 A：「失败路径捕获三类异常，回退本地」 | 官方错误表就是这三类（`IllegalStateException`/`SecurityException`/`RemoteException`）；但本地模式下「回退本地」等于静默失效 | ⚠ 需明确提示，不能静默 |
| 7 | 2.5 改动清单：「`util/DataStore.kt`、`data/Config.java` 读写后端抽象为 `ConfigSource`」 | `DataStore` 里有大量高频运行期写（`AntFarm.kt:1344/1359` 计数、`RpcTrafficCapture.kt:98/107/114` 模式位、`AntFarm.kt:2065` 答案缓存） | ✗ 整层接 Remote 会把高频写打到 Manager Binder |

其余陈述核对成立：`<queries>` 声明、包名 `top.nkbe.npatch`、authority `top.nkbe.npatch.remote`、NPatch v1.0.7+、Remote Preferences 注入侧只读且 `edit()` 抛异常、集成模式无 Manager Provider 连不上。

---

## 三、必须修正的三处

### 3.1 前提错了：现代入口已存在，不需要「迁移」

`LibXposedRuntime` 已继承 `XposedModule`（→ `XposedInterfaceWrapper`），自带
`getFrameworkProperties()`、`getApiVersion()`、`getRemotePreferences(String)`（`XposedInterface.java:543`）。
Remote 读取不需要迁移，只需在 `onPackageReady` 加几行：

```kotlin
val caps = getFrameworkProperties()
val hasRemote = (caps and XposedInterface.PROP_CAP_REMOTE) != 0L
val prefs = getRemotePreferences("sesame_config")   // 注入侧只读
```

改动面因此不是 28 个文件，而是：探针 1 个文件、`Config` 读取分支 1 处、设置页连接桥 1 个类。

### 3.2 Remote 方向反了，状态类功能必须降级

| 数据 | 产生方 | 能否经 Remote 到设置页 |
|---|---|---|
| 任务开关、UA、兑换商品、整点 | 设置页写 | ✔ |
| `status.json` 的 flagList | 注入进程写 | ✘ 无通道 |
| 运行日志 | 注入进程写 | ✘ |
| 商城缓存 | 注入进程写 | ✘ |

现有 `showAccountStatusDialog` / `readTodayFlags` 读磁盘 `status.json`，本地模式不同 UID 读不到。
必须明确降级：本地模式下不显示当日状态，或提示「仅集成模式可读」，不能假装同步。

### 3.3 配置与状态必须分家

`ConfigSource` 不能把整个 `DataStore` 抽出去，否则高频写（农场计数、抓包模式位）会经 Binder 打到管理器。
正确切分：

- **用户配置**（模型字段 JSON、UA、兑换商品）→ Remote：设置页写、注入读。
- **运行期状态与缓存**（`DataStore` 计数/缓存/模式位、`status.json`）→ 一律留本地。
- **运行期改配置**（上述 5 处 `setValue`）→ 写本地覆盖层；读取顺序：本地覆盖 > Remote > 默认。

---

## 四、修正后的路径

| 阶段 | 内容 | 改动 | 验证 |
|---|---|---|---|
| P0 | 探针：`onPackageReady` 打印 `PROP_CAP_REMOTE`，试读一个 group 并输出结果 | `LibXposedRuntime.kt`，约 15 行 | 本地/集成各跑一次，确认 101 档位是否实现 Remote |
| P1 | `Config` 读取加 Remote 分支（只读，失败回退本地） | 1 处分支 | 改开关重启后仍生效 |
| P2 | 设置页引入 `npatch-remote-api` AAR，`connectAsync` 连接，保存时 Remote + 本地双写 | 1 个桥类 + 保存路径 | 本地模式改动可见；集成模式行为不变 |
| P3 | 状态降级提示（账号状态、日志入口） | 2 处 UI | 不再出现空状态或误导文案 |

P0 不改任何行为，是唯一能证伪「101 不支持 Remote」的手段。

---

## 五、仍需实测的三件事

1. **101 档位下 `PROP_CAP_REMOTE` 是否置位**：官方写「API 101/102」，但本模块声明 101，需实测。
2. **集成模式下 `getRemotePreferences` 的行为**：返回可读的 host-local 实例，还是抛异常，决定是否按模式分流。
3. **`NPatchRemoteClient.connect` 的耗时**：同步最多 3 秒，设置页必须走 `connectAsync`。

---

## 六、结论

- 现代入口、`module.prop`、`scope.list` 都已就绪，**不需要迁移 API**；方案第 92 行那条应删除。
- 真正要做的是三件小事：读 Remote（探针 → Config 分支）、写 Remote（设置页桥）、状态降级。
- 前提是 101 档位支持 Remote；若不支持，只剩「设置 UI 搬进目标进程」一条路，成本远高于收益，维持集成模式即可。
