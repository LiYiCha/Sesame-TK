//
// Decompiled by Jadx (from NP Manager)
//
package com.alipay.mobile.skincenter.manage;

import android.text.TextUtils;
import com.alibaba.fastjson.JSON;
import com.alipay.dexaop.DexAOPEntry;
import com.alipay.instantrun.ChangeQuickRedirect;
import com.alipay.instantrun.ConstructorCode;
import com.alipay.instantrun.PatchProxy;
import com.alipay.instantrun.PatchProxyResult;
import com.alipay.mobile.common.logging.api.LoggerFactory;
import com.alipay.mobile.common.logging.api.trace.TraceLogger;
import com.alipay.mobile.framework.MpaasClassInfo;
import com.alipay.mobile.framework.service.common.TaskScheduleService.ScheduleType;
import com.alipay.mobile.skincenter.api.R.drawable;
import com.alipay.mobile.skincenter.model.SCAPTripPassModel;
import com.alipay.mobile.skincenter.model.SCCacheInfoModel;
import com.alipay.mobile.skincenter.rpc.aptrip.core.model.rpc.MetaInfoPB;
import com.alipay.mobile.skincenter.util.SCCommonUtil;
import com.alipay.mobile.skincenter.util.SCConfigUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@MpaasClassInfo(BundleName = "android-phone-wallet-skincenter", ExportJarName = "unknown", Level = "product", Product = ":android-phone-wallet-skincenter")
public class APTripPassSkinImpl implements APTripPassSkin {
    /* renamed from: 支 */
    public static ChangeQuickRedirect f0;
    public boolean a;

    public void a(List<String> list) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        List list2;
        if (changeQuickRedirect != null) {
            list2 = list;
            if (PatchProxy.proxy(list2, this, changeQuickRedirect, "1", List.class, Void.TYPE).isSupported) {
                return;
            }
        }
        list2 = list;
        String str = "APTripPassManager";
        if (list2 != null) {
            if (!list2.isEmpty()) {
                SCAPTripPassModel sCAPTripPassModel;
                String f = SCConfigUtil.f("sc_aptripPass_config");
                if (TextUtils.isEmpty(f)) {
                    sCAPTripPassModel = new SCAPTripPassModel();
                    sCAPTripPassModel.updateInterval = 86400;
                } else {
                    sCAPTripPassModel = (SCAPTripPassModel) JSON.parseObject(f, SCAPTripPassModel.class);
                }
                boolean z = false;
                for (String j : list2) {
                    SCCacheInfoModel j2 = SCInnerManager.m().j(j);
                    if (j2 != null) {
                        if (!TextUtils.isEmpty(j2.md5)) {
                            String str2 = j2.skinId;
                            if (j2.isDiySkin) {
                                str2 = j2.outMetaId;
                            }
                            long j3 = sCAPTripPassModel.forceUpdate;
                            TraceLogger traceLogger;
                            StringBuilder stringBuilder;
                            if (j3 > 0 && j3 >= j2.cacheTime) {
                                traceLogger = LoggerFactory.getTraceLogger();
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("queryCurrentSkinIfNeed, forceUpdate skinId:");
                                stringBuilder.append(str2);
                                traceLogger.info(str, stringBuilder.toString());
                                f(false, list2);
                                return;
                            } else if (sCAPTripPassModel.updateInterval <= 0 || (System.currentTimeMillis() / 1000) - j2.cacheTime <= sCAPTripPassModel.updateInterval) {
                                List list3 = sCAPTripPassModel.invalidSkins;
                                if (list3 == null || !list3.contains(j2.skinId)) {
                                    TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                                    StringBuilder stringBuilder2 = new StringBuilder();
                                    stringBuilder2.append("apTrip skin:");
                                    stringBuilder2.append(j2.usageScene);
                                    traceLogger2.info(str, stringBuilder2.toString());
                                } else {
                                    traceLogger = LoggerFactory.getTraceLogger();
                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("queryCurrentSkinIfNeed, disabledSkinId:");
                                    stringBuilder.append(j2.skinId);
                                    traceLogger.info(str, stringBuilder.toString());
                                    f(false, list2);
                                    return;
                                }
                            } else {
                                TraceLogger traceLogger3 = LoggerFactory.getTraceLogger();
                                StringBuilder stringBuilder3 = new StringBuilder();
                                stringBuilder3.append("queryCurrentSkinIfNeed, interval:");
                                stringBuilder3.append(sCAPTripPassModel.updateInterval);
                                traceLogger3.info(str, stringBuilder3.toString());
                                f(false, list2);
                                return;
                            }
                        }
                    }
                    z = true;
                }
                if (z) {
                    f(false, list2);
                    LoggerFactory.getTraceLogger().info(str, "no cache info local！");
                }
                return;
            }
        }
        LoggerFactory.getTraceLogger().debug(str, "checkUpdate:scenes is null!");
    }

    public void d(List<String> list) {
        List list2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            list2 = list;
            if (PatchProxy.proxy(list2, this, changeQuickRedirect, "4", List.class, Void.TYPE).isSupported) {
                return;
            }
        }
        list2 = list;
        Map map = SCInnerManager.m().g;
        if (map != null) {
            if (!map.isEmpty()) {
                String str;
                Iterator it = ((HashMap) b()).entrySet().iterator();
                Object obj = null;
                while (true) {
                    str = "APTripPassManager";
                    if (!it.hasNext()) {
                        break;
                    }
                    Entry entry = (Entry) it.next();
                    if (!list2.contains(entry.getKey())) {
                        SCCacheInfoModel sCCacheInfoModel = (SCCacheInfoModel) entry.getValue();
                        StringBuilder stringBuilder;
                        if (TextUtils.isEmpty(sCCacheInfoModel.md5) && TextUtils.equals(sCCacheInfoModel.skinId, "DEFAULT")) {
                            TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("invalidSkinsByConfig,skin has invalid：");
                            stringBuilder.append((String) entry.getKey());
                            traceLogger.debug(str, stringBuilder.toString());
                        } else {
                            TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("invalidSkinsByConfig, invalid model:");
                            stringBuilder.append((String) entry.getKey());
                            traceLogger2.debug(str, stringBuilder.toString());
                            c(sCCacheInfoModel);
                            obj = 1;
                        }
                    }
                }
                if (obj != null) {
                    if (SCConfigUtil.c()) {
                        String toJSONString = JSON.toJSONString(map);
                        SCCommonUtil.putString("cached_skin_info_v2", toJSONString);
                        TraceLogger traceLogger3 = LoggerFactory.getTraceLogger();
                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append("save skin cache:");
                        stringBuilder2.append(toJSONString);
                        traceLogger3.info(str, stringBuilder2.toString());
                    }
                }
            }
        }
    }

    public void e(List<MetaInfoPB> list, Map<String, SCCacheInfoModel> map) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{list, List.class, map, Map.class, Void.TYPE}, this, changeQuickRedirect, "5").isSupported) {
                return;
            }
        }
        if (map != null) {
            if (!map.isEmpty()) {
                ArrayList arrayList = new ArrayList();
                for (MetaInfoPB metaInfoPB : list) {
                    arrayList.add(metaInfoPB.scene);
                }
                for (Entry entry : map.entrySet()) {
                    if (!arrayList.contains(entry.getKey())) {
                        SCCacheInfoModel sCCacheInfoModel = (SCCacheInfoModel) entry.getValue();
                        TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("invalidSkinsByRPC, invalid model:");
                        stringBuilder.append((String) entry.getKey());
                        String stringBuilder2 = stringBuilder.toString();
                        String str = "APTripPassManager";
                        traceLogger.debug(str, stringBuilder2);
                        c(sCCacheInfoModel);
                        if (SCConfigUtil.c()) {
                            try {
                                File u = SCInnerManager.m().u(sCCacheInfoModel.userId);
                                if (u.exists()) {
                                    drawable.k(new File(u, sCCacheInfoModel.usageScene));
                                    drawable.l(sCCacheInfoModel.usageScene, null);
                                    SCInnerManager.m().c(sCCacheInfoModel.usageScene);
                                    TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                                    StringBuilder stringBuilder3 = new StringBuilder();
                                    stringBuilder3.append("deleteLocalSkinRes,usageScene:");
                                    stringBuilder3.append(sCCacheInfoModel.usageScene);
                                    traceLogger2.debug(str, stringBuilder3.toString());
                                }
                            } catch (Throwable th) {
                                traceLogger = LoggerFactory.getTraceLogger();
                                StringBuilder stringBuilder4 = new StringBuilder();
                                stringBuilder4.append("deleteLocalSkinRes,usageScene error:");
                                stringBuilder4.append(sCCacheInfoModel.usageScene);
                                traceLogger.error(str, stringBuilder4.toString(), th);
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<String, SCCacheInfoModel> b() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(this, changeQuickRedirect, "2", Map.class);
            if (proxy.isSupported) {
                return (Map) proxy.result;
            }
        }
        Map map = SCInnerManager.m().g;
        if (map != null) {
            if (!map.isEmpty()) {
                HashMap hashMap = new HashMap();
                for (Entry entry : map.entrySet()) {
                    if (TextUtils.equals("aptripPass", (CharSequence) entry.getKey()) || ((String) entry.getKey()).startsWith("atmospheric_")) {
                        hashMap.put((String) entry.getKey(), (SCCacheInfoModel) entry.getValue());
                    }
                }
                return hashMap;
            }
        }
        return new HashMap();
    }

    public void c(SCCacheInfoModel sCCacheInfoModel) {
        SCCacheInfoModel sCCacheInfoModel2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCCacheInfoModel2 = sCCacheInfoModel;
            if (PatchProxy.proxy(sCCacheInfoModel2, this, changeQuickRedirect, "3", SCCacheInfoModel.class, Void.TYPE).isSupported) {
                return;
            }
        }
        sCCacheInfoModel2 = sCCacheInfoModel;
        String str = "APTripPassManager";
        if (TextUtils.isEmpty(sCCacheInfoModel2.md5)) {
            LoggerFactory.getTraceLogger().debug(str, "invalidCacheInfoModel duplicate invalid model");
            return;
        }
        TraceLogger traceLogger = LoggerFactory.getTraceLogger();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("invalidCacheInfoModel model：");
        stringBuilder.append(sCCacheInfoModel2.usageScene);
        traceLogger.debug(str, stringBuilder.toString());
        String str2 = "DEFAULT";
        sCCacheInfoModel2.userSkinId = str2;
        sCCacheInfoModel2.skinId = str2;
        sCCacheInfoModel2.md5 = "";
    }

    public void f(boolean z, List<String> list) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{Boolean.valueOf(z), Boolean.TYPE, list, List.class, Void.TYPE}, this, changeQuickRedirect, "6").isSupported) {
                return;
            }
        }
        ScheduleType scheduleType = ScheduleType.RPC;
        a aVar = new a(this, list, z);
        DexAOPEntry.java_lang_Runnable_newInstance_Created(aVar);
        SCCommonUtil.backgroundExecute(scheduleType, aVar);
    }

    public APTripPassSkinImpl() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            ConstructorCode proxy = PatchProxy.proxy(changeQuickRedirect, "0");
            if (proxy != null) {
                proxy.afterSuper(this);
                return;
            }
        }
        this.a = false;
    }
}