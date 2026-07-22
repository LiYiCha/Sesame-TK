# 支付宝 v10.8.30.8000 反编译源码分析报告

> **文档存放位置**：[alipay_10.8.30_source_analysis.md](file:///d:/Desktop/py/wj_1/Sesame-TK-0.2.5-beta.6/alipay_10.8.30_source_analysis.md)  
> **源码分析目标**：`d:\Desktop\py\wj_1\Sesame-TK-0.2.5-beta.6\scratch\支付宝_10.8.30.8000`  

---

## 一、 反编译工程结构与核心模块透视

反编译目录 `scratch/支付宝_10.8.30.8000` 包含了支付宝 `v10.8.30.8000` 的完整 DEX 解包与 Java 还原源码：

```
scratch/支付宝_10.8.30.8000/
├── sources/               # Java 反编译源码 (53个顶级包)
│   ├── com/alipay/mobile/nebulaappproxy/api/rpc/  # H5 / 小程序 RPC 代理层
│   ├── com/alipay/mobile/h5container/api/          # H5 容器与 JSBridge
│   ├── com/alipay/mobile/quinox/                  # 动态插件加载框架 (Quinox)
│   └── com/alipay/mobile/common/fgbg/              # 前后台监控 (FgBgMonitorImpl)
├── smali/ ~ smali_classes15/  # 15个分包的 Smali 字节码
└── res/                   # 资源与 Layout XML
```

---

## 二、 支付宝底层 RPC 架构与 H5 桥接原理

通过对 `sources/com/alipay/mobile/nebulaappproxy/api/rpc/` 源码的深入剖析，梳理出支付宝 H5/小程序与移动网关（MobileGW）通信的完整链路：

### 1. `H5RpcRequest.java` 异步发包机制
在 `H5RpcRequest.java` (位于 `classes11.dex`) 中：
- `H5RpcRequest` 实现了 `Runnable` 接口，通过线程池并发异步执行；
- 请求携带 `H5Event` 事件 payload，解析包含 `operationType`、`requestData` 以及 `headers`；
- 执行完成后，通过 `H5BridgeContext.sendBridgeResult()` 将数据回调给前端 WebView JSBridge。

### 2. `H5RpcUtil.java` 网关路由与异常捕获
在 `H5RpcUtil.java` 中：
```java
public static String executeRpc(String operationType, String requestData) {
    try {
        return executeRpcNoCatch(operationType, requestData);
    } catch (Exception e) {
        if (!(e instanceof RpcException)) {
            return "";
        }
        RpcException rpcException = (RpcException) e;
        return rpcException.getCode() == 1002 ? "limit" : "";
    }
}
```
- **核心逻辑**：所有 H5 / 小程序页面的 RPC 调用均通过 `executeRpcNoCatch` 转接给底层的 `SimpleRpcService.executeRPC`；
- **限流判定**：当 RPC 返回错误码 `1002` 时，`H5RpcUtil` 会统一返回 `"limit"` 标志位，代表被服务端风控或触发频控限制。

---

## 三、 业务层 RPC 接口规范与参数解析

对应本项目模块用到的支付宝业务 RPC 接口规约：

### 1. 蚂蚁会员 / 闪递业务 (`com.alipay.shandie`)
- **多分类商品查询 (`com.alipay.shandie.delivery.queryCategoryGoods`)**：
  - 参数：`[{"categoryId":"94000SR...", "pageIndex":1, "pageSize":18}]`
  - 约束：`pageSize` 必须为 `18`。
- **专区翻页查询 (`com.alipay.shandie.delivery.queryZoneGoodsPage`)**：
  - 参数：`[{"zoneId":"...", "pageIndex":2, "pageSize":18}]`
  - 翻页规则：`pageIndex` 初始从 2 开始累加。
- **权益/商品规格详情 (`com.alipay.antmember.biz.rpc.benefit.h5.queryBenefitDetail`)**：
  - 解析对象：返回 `skuInfoList` 数组与 `simpleSkus` 字典（包含 `sku_id` 和 `price_cent` 字段）。
- **订单提单结算页 (`com.alipay.shandie.order.prepare`)**：
  - 强制规则：当实物商品规格 `skuId != "-1"` 时，拉起天猫 H5 结算页。如果 `skuId == "-1"`，回退至支付宝原生详情页 (`detailUrl` > `officialDetailUrl` > `jumpUrl`)。

### 2. 福气鱼塘业务 (`com.alipay.antfishpond`)
- **抛竿钓鱼 (`com.alipay.antfishpond.fishpondAngle`)**：
  - 请求带入 `riskToken`。响应中会返回新的 `riskToken` 及广告任务 `angleAdInfo`。
- **广告任务完成 (`com.alipay.antiep.finishTask`)**：
  - 请求：`[{"taskType":"...", "pwPreBizId":"..."}]`
  - 服务端处理：当任务今日已完成或状态非 `TODO` 时，返回 `desc: "无状态转换处理"`。

---

## 四、 本项目与源码对接的关键技术点

1. **`Application.attach` 第一时间挂载**：
   `android.app.Application` 属于 Android 系统 `framework.jar` 核心类，在 App 启动第一毫秒存在。在 `ApplicationHook.java` 中立即同步 Hook `Application.attach`，保证 `AppContext.setContext` 100% 成功。

2. **插件类安全查找 (`findClassIfExists`)**：
   针对位于 `classes11.dex` 中的 `H5AppRpcUpdate` 等延迟加载类，使用 `findClassIfExists` 检查，未加载时安全返回 `null`，杜绝 `ClassNotFoundException` 崩溃。

3. **鱼塘原子并发锁 (`inProgressTaskIds`)**：
   在 `AntFishpond.java` 中采用 `inProgressTaskIds.add(taskId)` 线程安全锁，避免主线程快速抛竿时并发拉起重复子线程发包，解决 `"无状态转换处理"` 报错。
