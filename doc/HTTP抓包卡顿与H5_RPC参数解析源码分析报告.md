# 支付宝 10.8.30.8000 与 Sesame 模块抓包性能及 H5 RPC 参数解析源码分析报告

## 一、 概述

针对用户反馈的两个核心问题：
1. **开启 HTTP 抓包时支付宝极度卡顿**；
2. **`submitEvent` 等 H5 RPC 的 `Params` 在日志中捕获为 `[]` 的原因**；

结合支付宝 10.8.30.8000 反编译源码与 Sesame 模块源码 (`LifecycleManager.java` & `HttpCaptureHook.kt`)，进行了严谨的对比分析，结论与定位如下。

---

## 二、 问题 1：为什么开启 HTTP 抓包时支付宝会极度卡顿？

### 1. 支付宝 10.8.30.8000 底层架构验证
在支付宝 10.8.30.8000 中，网络通信采用了**多通道网络传输架构**，包含以下核心传输组件：
- `com.alipay.mobile.common.transport.http.HttpWorker` (Native HTTP/1.x 传输)
- `com.alipay.mobile.common.transport.h5.H5HttpWorker` (H5 专用网络组件)
- `com.alipay.mobile.common.transport.http.inner.AndroidH2UrlConnection` (HTTP/2 协议栈)
- `com.alipay.mobile.dtnadapter.api.DtnHttpClient` (DTN 双通道传输)
- `com.alipay.mobile.nebulax.integration.mpaas.proxy.impl.TransportServiceImpl` (NebulaX 小程序代理)

### 2. Sesame 模块源码定位 (`HttpCaptureHook.kt` & `LifecycleManager.java`)
在 `HttpCaptureHook.kt` (L71-L78) 中：
```kotlin
hookAlipayTraffic(classLoader)
hookH5Plugin(classLoader)
hookStandardHttpConnection(classLoader)
hookOkHttpTraffic(classLoader)
hookARiverTraffic(classLoader)
hookDtnTraffic(classLoader)
```
当 `enableHttpCapture = true` 开启时，模块会对支付宝上述 **6 套网络传输组件同时进行 Hook**。

在每一个 HTTP 请求与响应流上：
1. **同步读取与字节流拷贝**：Hook 逻辑会在 IO 读写回调中同步将 `InputStream` 转换为字节数组；
2. **同步 Base64 编码与正则表达式匹配**：对响应体进行文本/二进制判断并做 Base64 转换；
3. **主线程反射序列化 (`LifecycleManager.java` L655, L697)**：
   每次 RPC 触发时，同步调用 `classLoader.loadClass("com.alibaba.fastjson.JSON")` 及 `toJSONString`。

**结论**：支付宝首页与农场在加载时会触发每秒数百次的网络与 RPC 请求。在底层网络 IO 回调中执行同步流拷贝、Base64 转换与主线程反射序列化，构成了导致支付宝页面卡顿的直接代码根因。

---

## 三、 问题 2：为什么 H5 RPC (`submitEvent` 等) 抓取到的 `Params` 是 `[]`？

### 1. 支付宝 10.8.30.8000 源码证据 (`RpcUtils.java`)
在支付宝 10.8.30.8000 反编译源码 `com/antgroup/lui/api/mxcDmanager/util/RpcUtils.java` 第 43-51 行中，展示了支付宝 H5/小程序底层通用的 RPC 执行逻辑：

```java
public static String executeRpc(String str, String str2, Map<String, String> map) {
    RpcService rpcService = (RpcService) AlipayApplication.getInstance()
        .getMicroApplicationContext()
        .findServiceByInterface(RpcService.class.getName());
    SimpleRpcService simpleRpcService = (SimpleRpcService) rpcService.getRpcProxy(SimpleRpcService.class);
    if (map != null && !map.isEmpty()) {
        rpcInvokeContext.setRequestHeaders(map);
    }
    if (TextUtils.isEmpty(str2)) {
        str2 = "[{}]";
    }
    return simpleRpcService.executeRPC(str, str2, (Map<String, String>) null);
}
```

其调用的 `SimpleRpcService.executeRPC` 方法形参定义如下：
- **`args[0]` (`str`)**：`operationType`（例如 `"com.alipay.gameevent.biz.rpc.submitEvent"`）；
- **`args[1]` (`str2`)**：**`requestData`（真正从 H5/小程序传进来的 JSON 请求参数字符串）**；
- **`args[2]` (`map`)**：`headers`（网关 Header 映射，通常为 `null` 或空 `Map`）。

### 2. Sesame 模块源码缺陷 (`LifecycleManager.java`)
在 `LifecycleManager.java` 第 653 行：
```java
Object[] args = (Object[]) param.args[2];
```
当拦截动态代理 `RpcInvocationHandler.invoke(proxy, method, args)` 时：
* 旧代码直接拿了第 3 个形参 `param.args[2]` 作为 RPC 的入参对象列表；
* 对于通用 H5 RPC 方法 `executeRPC(operationType, requestData, headers)`，`param.args[2]` 实际上是 `headers`（`null` 或空对象）；
* 因此，在格式化日志时，输出的 `Params:` 变成了 `[]`。

**结论**：`submitEvent` 和 `submitUserPlayDurationAction` 等 H5 RPC 的参数并不是空参数，而是真实存在于 `param.args[1]` 的 JSON 字符串中。

---

## 四、 优化修复方案建议

1. **解决卡顿问题**：
   - 缓存 FastJSON 的 Class 与静态 Method 引用，消除重复 `loadClass` 的反射开销；
   - 建立后台异步单线程队列 (`CaptureDispatcher`) 专门处理日志格式化与持久化，避免阻塞底层网络/RPC 回调线程。

2. **解决 H5 RPC 参数缺失问题**：
   - 在 `LifecycleManager.java` 拦截 RPC 时，增加对 `executeRPC` / `alipay.client.executerpc` 的专门提取分支；
   - 当检测到方法包含 `requestData` 参数时，优先提取 `args[1]` 字符串作为真实请求参数。
