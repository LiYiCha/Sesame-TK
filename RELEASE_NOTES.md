# Sesame-TK v0.5.8 更新日志

### 🐣 蚂蚁庄园
* **每日捐蛋模式重构**：
  * 新增 `donationMode` 配置（支持三种模式）：
    1. **当日列表中的一个项目**：单项目快速完成每日打卡；
    2. **当日列表中全部可捐项目**：遍历所有未满额公益项目进行捐献；
    3. **当日列表中所有未捐项目**：自动解析 `activityRecords`，智能过滤当前账号历史已捐过的项目，优先捐献全新项目。
  * **支持单次捐蛋数量配置**：新增 `donationAmount` 参数，支持自定义单次捐出爱心蛋数量（默认 1，范围 1~20000 颗）。
  * **适配现代自营公益项目标的**：重载 `donation` RPC 协议接口，自动支持 `SOLDBY` 自营标的与多批次字段（`projectId` / `batchId` / `targetId`），协议版本与来源自动对齐最新标准。
  * **爱心蛋余额与状态同步**：捐蛋完成后动态核算本地爱心蛋余额 `harvestBenevolenceScore`，精准标记今日完成状态，避免无活动时频繁空跑。

### 💰 余额宝体验金
* **修复 RPC 参数格式错误**：修复 `promosdk.index.forward` 在序列化时由于多重花括号拼装导致的非法 JSON (`[{"params":{...},"path":"..."}]`) 问题。
* **修复编译引用缺失**：补充缺失的 `RequestManager` 导入，确保网络调度层正常工作。

### ⚡ 核心架构与稳定性
* **统一 RPC 请求管理**：全面弃用并替换各业务任务（`BaseCommTask`、`AntMemberRpcCall`、`KuaiDiFuLiJia` 等）中的旧版 `ApplicationHook.requestString`，全量统一至 `RequestManager.requestString`，保障拦截、风控与生命周期管理一致性。
