package fansirsqi.xposed.sesame.data;
/**
 * 通用常量类。
 * 提供项目级共享常量，如包名、广播 action、关键类的完整名称。
 */
public class General {
    /**
     * 支付宝客户端的包名。
     */
    public static final String PACKAGE_NAME = "com.eg.android.AlipayGphone";
    /**
     * 模块 UI 向支付宝查询模块状态的广播 action。
     */
    public static final String ACTION_STATUS_QUERY = "com.eg.android.AlipayGphone.sesame.status";
    /**
     * 支付宝向模块 UI 回执模块状态的广播 action。
     */
    public static final String ACTION_STATUS_REPLY = "fansirsqi.xposed.sesame.status";
    /**
     * 当前使用的服务类名。
     */
    public static final String CURRENT_USING_SERVICE = "com.alipay.dexaop.power.RuntimePowerService";
    /**
     * 当前使用的活动（Activity）类名。
     */
    public static final String CURRENT_USING_ACTIVITY = "com.eg.android.AlipayGphone.AlipayLogin";
    /**
     * JSON对象的类名。
     */
    public static final String JSON_OBJECT_NAME = "com.alibaba.fastjson.JSONObject";
    /**
     * H5页面的类名。
     */
    public static final String H5PAGE_NAME = "com.alipay.mobile.h5container.api.H5Page";
    public static final String MODULE_PACKAGE_NAME = "fansirsqi.xposed.sesame";
    public static final String vitalityExchangeToday = "fansirsqi.xposed.sesame.XposedEntry";
    public static final String MODULE_PACKAGE_UI_ICON =  "fansirsqi.xposed.sesame.ui.MainActivityAlias";
}