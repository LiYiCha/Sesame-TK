# Sesame-TK v0.6.2 更新日志

### 🎨 主题系统重构（支付宝专用架构）
* **主题模块收敛为支付宝专用实现**：
  * 下线旧通用主题界面与仓库层，统一收敛至 `ui/theme/alipay/`，管理列表、详情页、预览、槽位模型与导入导出仓库各自独立成文件；
  * 槽位按导航栏/标签页/首页快捷/收款码分组，标签直接取 `meta.json` 的 description。
* **换肤 Hook 适配当前支付宝混淆名**：
  * 读缓存 `G`（旧 `K`）、内存缓存字段 `e`（旧 `g`）、门检 `v(String,Map)`（旧 `y`）均改为按版本探测，不再硬编码；
  * 内存缓存 key 改用官方无分隔符格式（`cached_skin_info_v2` + userId），仅替换 theme 场景，保留 `aptrip`/`ltp` 等其他场景数据。
* **新增主题回滚门拦截**：`isThemeSkinRollBack` 恒 false，避免服务端回滚配置否决本地主题导致应用链路中断；各 Hook 独立安装互不影响，开关未开时进程内仅提示一次防刷屏。
* **主题文件保护机制**：新增 `hookProtectThemeFiles` 拦截官方删除主题目录的操作，防止自定义主题被误删；新增 `isSelectedThemeIntact` 检测选中主题完整性以避免拦截误判；`requestThemeRestore` 异步恢复缺失主题，不阻塞主线程。
* **冷启动主题重绘补发**：在 `applyHooks` 关键项装配成功及自定义缓存注入成功后，进程内一次性延时补发 `reloadAndNotifySkinCenter`，修复重启后主题数据仍在却不生效的问题。
* **其他主题优化**：
  * 主题更新前清空 theme 目录下其他主题目录（含官方残留），避免多主题共存触发官方缓存校验清理导致主题回退；
  * 新增门检探针实测 `hasEnableSkin` 结果，单例定位失败、`notifyThemeSkin` 入口缺失等关键失败点补充明确日志；
  * 移除定时主题与时段深色模式逻辑，删除 schedule 模式时段切换、`TIME_THEMES` 配置与 `getTimeTheme`，深色 auto 模式改为跟随系统；
  * 修复 `userId` 取值不一致（统一使用运行时账号）、`versionLimit` 固化（改为适配运行时支付宝版本）、缓存 Map 识别误匹配（改用字段名与值类型定位）。

### ✨ 功能新增与优化
* **青春特权与奖学金**：
  * 新增青春特权权益开关查询、月转入模型查询、首页数据查询等请求方法，并新增页面初始化序列模拟；
  * 奖学金模块按真实抓包顺序模拟进入小程序后的完整浏览序列，月权益领取逻辑适配新的接口数据结构。
* **更新模块**：修复更新下载中心代理误用问题，完善本地正式签名配置。

### 🛡️ 稳定性与健壮性修复
* **主题 Hook 安装失败根因修复**：
  * `hookFilePath` 目标从旧版混淆名 `q` 修正为本版真实签名 `o(File,String,String)`，原写法必然安装失败；
  * `hookMd5Check`/`hookTimeCheck`/`hookFilePath`/`hookResourceLoad` 等 5 处 `catch (Exception)` 改为 `catch (Throwable)`：`findAndHookMethod` 找不到方法抛 `NoSuchMethodError`（属 Error）此前逃逸导致 `applyHooks` 中断，是"主题Hook模块配置同步失败"且主题不生效的根因；
  * `applyHooks` 汇总关键项成败并输出汇总日志，任一关键项失败时 dump `SCInnerManager`/`SCConfigUtil`/`SCMetaModel` 全部方法签名（含参数、返回类型、static 标记）便于比对混淆名漂移；关键项全失败时保持未挂钩状态，允许下次配置同步自动重试。
* **异常与并发防护**：
  * `Notify` 异常通知增加重入防护，阻断 `Log.error` 递归导致的栈溢出；
  * `PortUtil` 导入改为先开输入流再建输出流，双流 try-with-resources 托管；
  * `LuckyCode.sendTask` 重写为 for 循环，异常不再导致死循环。
* **生命周期与初始化**：
  * `LifecycleManager.initHandler` 新增触发来源参数，启动类触发场景增加竞态防护，避免重复初始化导致任务中断；`LifecycleModule`、`SesameReceiver` 同步传入触发来源；
  * 修复服务重建时误触发全量重载，修正余额宝体验金与养鱼任务的执行顺序；
  * `TaskScheduler` 间隔防抖逻辑改为延迟重试而非续排。
* **资源与持久化**：
  * `SesameReceiver` 广播接收器配对注册注销，修复服务重建重复注册泄漏；`ForegroundHelper` 前台保活全程 CAS 占位并在异常路径回滚；
  * 配置持久化 10 处统一切换 `write2FileAtomic`（Config/Files/UserMap/IdMapManager），`UserMap.saveSelf` 补判空保护。
* **任务细节修复**：`AntForest` 浇水 3000 错误独立重试上限 3 次；`TravelDeals` 滑块冷却 key 统一为常量；`GoldTicket` 修复 weeklyWelfare 成功/失败分支颠倒并为 `newIndexTask` 补三级判空；`Finedu` 私有黑名单迁移至通用 `TaskBlacklist`。
* **UI 生命周期与资源泄漏**：`MainActivity` 广播在 `onDestroy` 注销；`OptionsAdapter` 改用 applicationContext；`ListDialog` 关闭时清静态引用；`ListAdapter` 清理 viewHolderList；`CaptureDetailScreen` 初始化副作用移入 `LaunchedEffect`。

### ♻️ 架构与代码清理
* **唤醒锁改为 lease 模式**：`WakeLockManager` 由 Java 静态 `acquire/release` 重写为 Kotlin lease 模式，调用方各自持有并释放，服务重复初始化时先关闭旧 lease 以防锁泄漏；`LifecycleManager`、`BaseFlashSaleTask`、`SeckillScheduler` 适配 timeout 兜底。
* **下线废弃模块**：移除 20 个废弃任务模块（AntFishpond、ContentDayTask、ContentInteract、DayDaySave、FundApplication、GoldBean、HundredTimesDiscountCard、Salaryday、CollectRedPacket、GameCenter、HarvestLimitedTime、KuaiDiFuLiJia、SesamePigeon、StudentAnswer、UgShooping、ZhimaTree、WenLiBao 等）及 `hook/RpcResponseHandler`、`exception/SesameException`，并清理各注册与调用点。
* **冗余代码与文档清理**：移除废弃类与文件（`RpcRequest`、`HttpHandler`、`RequestStorage` 等），简化条件判断、字符串拼接与协程延迟调用，修复导入与引用错误；清理已下载的皮肤资源与过期分析文档，累计 129 个文件、净减少约 3800 行。
* **废弃开关字段下线**：移除已停用的 `autoTokenEnabled`、`enableCaptchaRPCHook` 字段及注册，并移除 `AnswerAI` 硬编码的内置 API 令牌。

### 🗑️ 功能下线
* 下线消费金（ConsumeGold）与 AntBookRead 读书功能，`YebSceneBffish` 停用空方法调用。
* `FolderLauncher` 打开目录不再创建占位文件：改为只对目录内已存在的文件生成 `content:// URI`（目录无文件则取子目录内最新文件，全空时提示路径返回），对齐 `UpdatePathManager.openApkWithOtherApp` 的通配 MIME + ClipData 方案，不再 `mkdirs`/`createNewFile` 污染目标目录。