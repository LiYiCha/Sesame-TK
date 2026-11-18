package fansirsqi.xposed.sesame.task.exchange

import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.otherTask2.eleme.ElemeAuth
import fansirsqi.xposed.sesame.task.otherTask2.eleme.ElemeMtopUtil
import fansirsqi.xposed.sesame.task.otherTask2.eleme.ElemeTask
import fansirsqi.xposed.sesame.task.otherTask2.eleme.LuckyStarRpcCall
import fansirsqi.xposed.sesame.entity.ElemeMallEntity
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 饿了么幸运星兑换商城秒杀任务
 *
 * 接入项目统一秒杀框架 BaseFlashSaleTask：在「开抢整点」前 waitTimeField（默认 200ms）唤醒，
 * 使用缓存的商城商品目录构建兑换项，整点发车调用 mall.exchange，失败自动重试。
 * 商品目录来自 ElemeMallEntity（由幸运星任务每日拉取/用户手动「拉取最新」写入），
 * 兑换目标来自 ElemeTask 的勾选集合（与设置页【兑换商城】弹窗内的勾选框一致）。
 *
 * 本任务由 FlashSaleModule 统一调度，不单独注册为模型页卡片。
 */
class ElemeMallEX : BaseFlashSaleTask() {

    /** 由 FlashSaleModule 注入：勾选的兑换商品 ID 集合 */
    var elemeMallList: SelectModelField? = null
    /** 由 FlashSaleModule 注入：秒杀开抢整点（0-23） */
    var targetHourField: IntegerModelField? = null

    override fun getName(): String = "饿了么商城秒杀"
    override fun getGroup(): ModelGroup = ModelGroup.EXCHANGE
    override fun getIcon(): String = "Default.png"

    override fun getTargetHour(): Long = (targetHourField?.value ?: 10).toLong()

    override val exchangeMode: ExchangeMode
        get() = ExchangeMode.MULTI // 多商品模式：逐个兑换勾选商品，互不影响

    override val completedKey: String
        get() = "ElemeMallEX"

    companion object {
        private const val TAG = "ElemeMallEX"

        /**
         * 供秒杀设置页商品列表使用：只读本地商城缓存，绝不发起网络请求。
         * 返回 MemberBenefit(id=商品exchangeId, name=名称[所需星数])。
         */
        @JvmStatic
        fun getExchangeItemListForUI(): List<MemberBenefit> {
            val items = runCatching { ElemeMallEntity.getList().filter { it.id != "0" } }.getOrDefault(emptyList())
            if (items.isEmpty()) {
                return listOf(MemberBenefit("", "暂无数据（请在【饿了么→兑换商城】点「拉取最新」）"))
            }
            return items.map { MemberBenefit(it.id, "${it.pureTitle.ifEmpty { it.name }} [${it.consumeAmount}星]") }
        }
    }

    /**
     * 预构建兑换项：从商城缓存取商品详情与勾选集合，填充 exchangeParams 供整点发车使用。
     * 参数与 LuckyStarRpcCall.mallExchange 完全一致（对齐 幸运星.py mall_exchange 规范）。
     */
    override fun buildExchangeItems(): List<ExchangeItem> {
        // 优先读【兑换商城】弹窗内的勾选集（与设置页共享）；秒杀页自带列表作为补充，两处勾选并集生效
        val selected = (runCatching { ElemeTask.getElemeExchangeItems().value }.getOrNull() ?: emptySet()) +
            (elemeMallList?.value?.filter { it.isNotEmpty() }?.toSet() ?: emptySet())
        if (selected.isEmpty()) {
            Log.other("饿了么", "[商城秒杀] 未勾选兑换商品，任务结束")
            return emptyList()
        }
        val items = runCatching { ElemeMallEntity.getList().filter { it.id != "0" } }.getOrDefault(emptyList())
        // 星数基于最近一次拉取快照（-1 表示未知，此时不做星数拦截，交由服务端判定）
        var budget = ElemeMallEntity.getLastStarAmount()
        val out = mutableListOf<ExchangeItem>()
        for (item in items) {
            if (item.id !in selected) continue
            // 仅兑换可兑换状态且参数齐全的商品，避免整点发出无效请求
            if (item.exchangeStatus !in listOf("", "INIT", "EXCHANGE_ABLE", "CAN_EXCHANGE", "ONLINE")) continue
            if (item.actId.isEmpty() || item.exchangeCollectionId.isEmpty() || item.consumeAmount <= 0) continue
            // 星数不足绝不发请求：多商品按顺序扣减预算，超出余量的商品直接跳过
            if (budget >= 0 && item.consumeAmount > budget) {
                Log.other("饿了么", "[商城秒杀] 跳过 [${item.pureTitle.ifEmpty { item.name }}]：需 ${item.consumeAmount} 星，当前仅 $budget 星")
                continue
            }
            if (budget >= 0) budget -= item.consumeAmount
            val params = JSONArray().put(JSONObject().apply {
                put("actId", item.actId)
                put("exchangeCollectionId", item.exchangeCollectionId)
                put("exchangeId", item.id)
                put("actCode", item.actCode)
            })
            out.add(ExchangeItem(item.id, 0.0, item.consumeAmount, item.pureTitle.ifEmpty { item.name }).apply {
                exchangeParams = params
            })
        }
        if (out.isEmpty()) {
            Log.other("饿了么", "[商城秒杀] 勾选商品均不可兑换或参数不全（请先在【兑换商城】点「拉取最新」并勾选）")
        }
        return out
    }

    /**
     * 整点发车前（提前 8 秒）重新拉取一次商城目录。
     * 商品目录可能来自数小时前的缓存，补货后 actId、exchangeCollectionId、兑换状态都可能变化，
     * 用旧目录发车必然失败；刷新失败时沿用本地缓存继续，不影响发车。
     */
    override fun onBeforeFire() {
        val selected = runCatching { ElemeTask.getElemeExchangeItems().value }.getOrNull() ?: emptySet()
        if (selected.isEmpty()) return
        runCatching {
            val init = ElemeAuth.ensureCookie()
            if (init.isEmpty()) return@runCatching
            val cookie = ElemeAuth.validateOrRefreshCookie(init) ?: init
            val resp = LuckyStarRpcCall.queryMallList(cookie)
            if (resp == null) {
                val err = ElemeMtopUtil.lastError ?: "无响应"
                val raw = ElemeMtopUtil.lastResponse ?: "无原始响应"
                Log.other("饿了么", "[商城秒杀] 发车前刷新商品目录失败，沿用本地缓存: $err\n完整响应:\n$raw")
                return@runCatching
            }
            ElemeMallEntity.updateFromMallResponse(resp)
            ElemeMallEntity.recordFetchSnapshot(System.currentTimeMillis(), ElemeMallEntity.getLastStarAmount())
            Log.other("饿了么", "[商城秒杀] 发车前已刷新商品目录")
        }.onFailure {
            Log.error(TAG, "发车前刷新商品目录异常: ${it.message}")
        }
    }

    /** 发车前刷新参数（整点时用最新缓存，避免目录过期）；返回 true 表示可继续兑换 */
    override fun tryExchange(item: ExchangeItem): Boolean {
        val cached = runCatching { ElemeMallEntity.getItem(item.code) }.getOrNull() ?: return false
        if (cached.actId.isEmpty() || cached.exchangeCollectionId.isEmpty()) return false
        item.exchangeParams = JSONArray().put(JSONObject().apply {
            put("actId", cached.actId)
            put("exchangeCollectionId", cached.exchangeCollectionId)
            put("exchangeId", cached.id)
            put("actCode", cached.actCode)
        })
        return true
    }

    /** 实际发送兑换请求：复用已验证的 mall.exchange 封装（含 asac/站点参数） */
    override fun sendExchangeRequest(params: JSONArray, item: ExchangeItem): Boolean {
        val p = params.optJSONObject(0) ?: return false
        val cookie = runCatching {
            val init = ElemeAuth.ensureCookie()
            if (init.isEmpty()) "" else (ElemeAuth.validateOrRefreshCookie(init) ?: init)
        }.getOrDefault("")
        if (cookie.isEmpty()) {
            Log.other("饿了么", "[商城秒杀] 未获取到有效饿了么凭据，无法兑换 [${item.name}]")
            return false
        }
        val res = LuckyStarRpcCall.mallExchange(
            cookie,
            p.optString("actId"),
            p.optString("exchangeCollectionId"),
            p.optString("exchangeId"),
            p.optString("actCode")
        )
        return if (res.isSuccess) {
            Log.other("饿了么", "[商城秒杀] 成功兑换 [${item.name}] (-${item.cost} 星)")
            true
        } else {
            // 兑换接口从未有成功抓包，失败时必须给出完整响应，否则无法判断是参数缺失、库存还是账号受限
            val rawResp = res.rawJson?.toString() ?: ElemeMtopUtil.lastResponse ?: res.errorMsg
            val detail = "[商城秒杀] ❌ 兑换[${item.name}]失败: ${res.errorCode}::${res.errorMsg}\n完整响应:\n$rawResp"
            Log.error(TAG, detail)
            Log.other("饿了么", detail)
            false
        }
    }
}
