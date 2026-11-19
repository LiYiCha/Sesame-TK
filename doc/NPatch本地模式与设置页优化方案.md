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

## 二、NPatch 本地模式：核查结论

### 2.1 配置本来就是跨进程共享的

代码事实（`util/Files.kt:56-67`）：

```kotlin
// MAIN_DIR
Environment.getExternalStorageDirectory() + "/Android/media/" + General.PACKAGE_NAME + "/sesame-TK"
// CONFIG_DIR
File(MAIN_DIR, "config")
```

实际路径：`/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/sesame-TK/config`。

这是**公共媒体目录**，不是任何应用的私有目录。模块设置页（模块进程）与任务（支付宝进程）读写的是同一个 `config_v2.json`
与同一批按 userId 划分的子目录。因此「本地模式下配置读不到」这个判断不成立——它从来没有因为进程不同而失效。

### 2.2 之前那套方案错在哪

| 判断 | 事实 | 结果 |
|---|---|---|
| 设置页写模块私有目录、任务读支付宝私有目录 | 两者都读写 `/sdcard/Android/media/<支付宝包名>/sesame-TK/config` | 前提错误 |
| 因此需要 XSharedPreferences / Remote API 打通 | 公共目录已打通，不需要额外通道 | 方案多余 |

据此新增的 `PrefsBridge`、`Config` 的 prefs 分支、`xposedsharedprefs` 声明、`<queries>` 声明已全部回退，代码恢复到改动前状态。

### 2.3 本地模式「未激活」该查什么

按官方运行模型，模块能否被加载由管理器决定，与配置存储无关。排查顺序：

1. **作用域**：NPatch 管理器里是否把本模块勾选给 `com.eg.android.AlipayGphone`（`guide/modes`：作用域始终以管理器数据库为准）。
2. **模块识别**：`META-INF/xposed/{module.prop, java_init.list, scope.list}` 是否被打包进 APK（APK 内检查，不是源码目录）。
3. **注入是否发生**：注入进程启动后 `xp_env.txt` 是否更新、runtime 日志是否有 `Loaded process=` 记录。
4. **账号一致**：设置页与注入进程的 `UserMap.currentUid` 是否相同；不同账号会落到不同的配置子目录，表现为「设置不生效」。

### 2.4 签名绕过等级（源码依据）

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
