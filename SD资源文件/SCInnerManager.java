//
// Decompiled by Jadx (from NP Manager)
//
package com.alipay.mobile.skincenter.manage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alipay.android.phone.mobilecommon.multimedia.api.MultimediaFileService;
import com.alipay.android.phone.mobilecommon.multimedia.api.MultimediaImageService;
import com.alipay.android.phone.mobilecommon.multimedia.file.data.APFileReq;
import com.alipay.android.phone.mobilecommon.multimedia.graphics.data.APImageLoadRequest;
import com.alipay.android.phone.mobilecommon.multimedia.graphics.data.CutScaleType;
import com.alipay.dexaop.DexAOPEntry;
import com.alipay.instantrun.ChangeQuickRedirect;
import com.alipay.instantrun.ConstructorCode;
import com.alipay.instantrun.PatchProxy;
import com.alipay.instantrun.PatchProxyResult;
import com.alipay.mobile.antui.tokens.AUTokenManager;
import com.alipay.mobile.common.helper.ZipHelper;
import com.alipay.mobile.common.logging.api.LoggerFactory;
import com.alipay.mobile.common.logging.api.antevent.AntEvent.Builder;
import com.alipay.mobile.common.logging.api.trace.TraceLogger;
import com.alipay.mobile.framework.LauncherApplicationAgent;
import com.alipay.mobile.framework.MpaasClassInfo;
import com.alipay.mobile.framework.degrade.GradeBizName;
import com.alipay.mobile.framework.degrade.GradeReqInfo;
import com.alipay.mobile.framework.degrade.TaskGradeController;
import com.alipay.mobile.framework.service.common.TaskScheduleService.ScheduleType;
import com.alipay.mobile.framework.settings.SettingsManager;
import com.alipay.mobile.rome.longlinkservice.ISyncCallback;
import com.alipay.mobile.skincenter.api.R.drawable;
import com.alipay.mobile.skincenter.basic.AntSkinStyle;
import com.alipay.mobile.skincenter.model.OspSkinCompatModel;
import com.alipay.mobile.skincenter.model.SCCacheInfoModel;
import com.alipay.mobile.skincenter.model.SCMetaModel;
import com.alipay.mobile.skincenter.model.SkinDiyInfoModel;
import com.alipay.mobile.skincenter.obfuscated.d.a;
import com.alipay.mobile.skincenter.obfuscated.d.b;
import com.alipay.mobile.skincenter.obfuscated.d.d;
import com.alipay.mobile.skincenter.obfuscated.d.e;
import com.alipay.mobile.skincenter.obfuscated.e.c;
import com.alipay.mobile.skincenter.rpc.ResourceInfoPB;
import com.alipay.mobile.skincenter.rpc.SkinCenterRpcService;
import com.alipay.mobile.skincenter.rpc.SkinDetailInfoPB;
import com.alipay.mobile.skincenter.rpc.SkinDiyMetaInfoPB;
import com.alipay.mobile.skincenter.rpc.SkinDiyPositionInfoPB;
import com.alipay.mobile.skincenter.rpc.SkinDiyPositionMetaPB;
import com.alipay.mobile.skincenter.rpc.SkinDiySVCInfoPB;
import com.alipay.mobile.skincenter.rpc.SkinDiyTransformPB;
import com.alipay.mobile.skincenter.rpc.request.EntryStringString;
import com.alipay.mobile.skincenter.rpc.request.MapStringString;
import com.alipay.mobile.skincenter.rpc.request.SkinDetailReqPB;
import com.alipay.mobile.skincenter.rpc.response.GetUserSkinDetailResPB;
import com.alipay.mobile.skincenter.util.SCCommonUtil;
import com.alipay.mobile.skincenter.util.SCConfigUtil;
import com.alipay.mobile.skincenter.util.SCSkinStatusUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@MpaasClassInfo(BundleName = "android-phone-wallet-skincenter", ExportJarName = "unknown", Level = "product", Product = ":android-phone-wallet-skincenter")
public class SCInnerManager {
    public static final String a;
    public static volatile SCInnerManager b = null;
    /* renamed from: 支 */
    public static ChangeQuickRedirect f0;
    public final AtomicBoolean c;
    public final ConcurrentHashMap<String, String> d;
    public boolean e;
    public ISyncCallback f;
    public final Map<String, SCCacheInfoModel> g;
    public final Map<String, SCMetaModel> h;
    public final Map<String, Boolean> i;
    public String j;
    public final APTripPassSkin k;
    public boolean l;
    public Bitmap m;

    /* JADX WARNING: Removed duplicated region for block: B:85:0x0208  */
    /* JADX WARNING: Removed duplicated region for block: B:72:0x01d4 A:{Catch:{ all -> 0x0204 }} */
    /* JADX WARNING: Removed duplicated region for block: B:89:0x021c  */
    /* JADX WARNING: Removed duplicated region for block: B:88:0x021b A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:88:0x021b A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:89:0x021c  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean h(SCCacheInfoModel sCCacheInfoModel, Map<String, ResourceInfoPB> map, boolean z, String str, Map<String, List<SkinDiyInfoModel>> map2, boolean z2, boolean z3) {
        Throwable th;
        SCCacheInfoModel sCCacheInfoModel2 = sCCacheInfoModel;
        Map<String, ResourceInfoPB> map3 = map;
        String str2 = str;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{sCCacheInfoModel2, SCCacheInfoModel.class, map3, Map.class, Boolean.valueOf(z), Boolean.TYPE, str2, String.class, map2, Map.class, Boolean.valueOf(z2), Boolean.TYPE, Boolean.valueOf(z3), Boolean.TYPE, Boolean.TYPE}, this, changeQuickRedirect, "22");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        boolean z4 = false;
        String str3 = "SkinCenterManager";
        if (SCCommonUtil.compareWalletVersion(SCCommonUtil.currentVersionName(), sCCacheInfoModel2.versionLimit) < 0) {
            LoggerFactory.getTraceLogger().info(str3, "not support version");
            return false;
        }
        boolean z5;
        boolean z6;
        TraceLogger traceLogger;
        StringBuilder stringBuilder;
        boolean z7;
        List d = d(map3);
        ArrayList arrayList = new ArrayList(map3.keySet());
        boolean z8 = true;
        if (!z) {
            if ("true".equals(SCConfigUtil.f("skin_center_download_all_res_rollback"))) {
            }
            z5 = z4;
            z6 = z8;
            traceLogger = LoggerFactory.getTraceLogger();
            stringBuilder = new StringBuilder();
            stringBuilder.append("needDownloadScenes: ");
            stringBuilder.append(arrayList.toString());
            traceLogger.info(str3, stringBuilder.toString());
            if (f(sCCacheInfoModel2.userId, d, arrayList)) {
                z7 = z3;
                try {
                    L(sCCacheInfoModel2.skinId, 7, z7);
                    try {
                        File file = new File(u(sCCacheInfoModel2.userId), "tmp");
                        F(file);
                        drawable.k(file);
                    } catch (Throwable th2) {
                        LoggerFactory.getTraceLogger().error(str3, "downloadScenesRes, deleteFile error", th2);
                        L(sCCacheInfoModel2.skinId, 12, z7);
                    }
                    return z5;
                } catch (Throwable th3) {
                    th2 = th3;
                    LoggerFactory.getTraceLogger().error(str3, "downloadScenesRes, downloadRes error", th2);
                    L(sCCacheInfoModel2.skinId, 7, z7);
                    if (z) {
                    }
                }
            } else {
                z7 = z3;
                if (z) {
                    return z6;
                }
                M(sCCacheInfoModel2, map3, str2, map2, z7);
                return z6;
            }
        }
        SCCacheInfoModel j = j(sCCacheInfoModel2.usageScene);
        if (j != null && j.md5 != null && TextUtils.equals(sCCacheInfoModel2.userSkinId, j.userSkinId) && arrayList.size() > 0) {
            int size = arrayList.size() - 1;
            while (size >= 0) {
                String str4 = (String) arrayList.get(size);
                String str5 = "DEFAULT";
                boolean z9;
                if (j.isDiySkin) {
                    String str6;
                    if (z2) {
                        z9 = z4;
                    } else {
                        z9 = z4;
                        if (!SCConfigUtil.m(j.outMetaId, j.cacheTime)) {
                            z4 = z9;
                            str6 = j.usageScene;
                            if (str6 != null && ((str6.contains(str4) || j.usageScene.equals(str5)) && TextUtils.equals(j.md5, str2) && !r5)) {
                                arrayList.remove(size);
                                traceLogger = LoggerFactory.getTraceLogger();
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("downloadScenesRes, diySkin not needDownScene:");
                                stringBuilder.append(str4);
                                traceLogger.info(str3, stringBuilder.toString());
                            }
                            z6 = z8;
                            z5 = z9;
                        }
                    }
                    z4 = z8;
                    str6 = j.usageScene;
                    arrayList.remove(size);
                    traceLogger = LoggerFactory.getTraceLogger();
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("downloadScenesRes, diySkin not needDownScene:");
                    stringBuilder.append(str4);
                    traceLogger.info(str3, stringBuilder.toString());
                    z6 = z8;
                    z5 = z9;
                } else {
                    z9 = z4;
                    CharSequence charSequence = null;
                    String str7 = map3.get(str4) == null ? null : ((ResourceInfoPB) map3.get(str4)).md5;
                    if (map3.get(str4) != null) {
                        charSequence = ((ResourceInfoPB) map3.get(str4)).appSquareMd5;
                    }
                    TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                    z6 = z8;
                    StringBuilder stringBuilder2 = new StringBuilder();
                    z5 = z9;
                    stringBuilder2.append("downloadScenesRes, scene:");
                    stringBuilder2.append(str4);
                    stringBuilder2.append(" , resourceMd5:");
                    stringBuilder2.append(str7);
                    stringBuilder2.append(" , cachedInfo.md5:");
                    stringBuilder2.append(j.md5);
                    traceLogger2.info(str3, stringBuilder2.toString());
                    if (!z2) {
                        String str8 = j.usageScene;
                        if (str8 != null && ((str8.contains(str4) || j.usageScene.equals(str5)) && TextUtils.equals(j.md5, str7) && TextUtils.equals(j.appSquareMd5, charSequence))) {
                            arrayList.remove(size);
                        }
                    }
                }
                size--;
                z8 = z6;
                z4 = z5;
            }
        }
        z5 = z4;
        z6 = z8;
        traceLogger = LoggerFactory.getTraceLogger();
        stringBuilder = new StringBuilder();
        stringBuilder.append("needDownloadScenes: ");
        stringBuilder.append(arrayList.toString());
        traceLogger.info(str3, stringBuilder.toString());
        try {
            if (f(sCCacheInfoModel2.userId, d, arrayList)) {
            }
        } catch (Throwable th4) {
            th2 = th4;
            z7 = z3;
            LoggerFactory.getTraceLogger().error(str3, "downloadScenesRes, downloadRes error", th2);
            L(sCCacheInfoModel2.skinId, 7, z7);
            if (z) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:119:0x0335  */
    /* JADX WARNING: Removed duplicated region for block: B:118:0x0333  */
    /* JADX WARNING: Removed duplicated region for block: B:122:0x0358  */
    /* JADX WARNING: Removed duplicated region for block: B:126:0x0377  */
    /* JADX WARNING: Removed duplicated region for block: B:125:0x0376  */
    /* JADX WARNING: Removed duplicated region for block: B:129:0x037f  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x02a2  */
    /* JADX WARNING: Removed duplicated region for block: B:97:0x029f  */
    /* JADX WARNING: Removed duplicated region for block: B:103:0x02e9  */
    /* JADX WARNING: Removed duplicated region for block: B:157:0x0459  */
    /* JADX WARNING: Removed duplicated region for block: B:140:0x03c6  */
    /* JADX WARNING: Removed duplicated region for block: B:97:0x029f  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x02a2  */
    /* JADX WARNING: Removed duplicated region for block: B:103:0x02e9  */
    /* JADX WARNING: Removed duplicated region for block: B:140:0x03c6  */
    /* JADX WARNING: Removed duplicated region for block: B:157:0x0459  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x02a2  */
    /* JADX WARNING: Removed duplicated region for block: B:97:0x029f  */
    /* JADX WARNING: Removed duplicated region for block: B:103:0x02e9  */
    /* JADX WARNING: Removed duplicated region for block: B:157:0x0459  */
    /* JADX WARNING: Removed duplicated region for block: B:140:0x03c6  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void M(SCCacheInfoModel sCCacheInfoModel, Map map, String str, Map map2, boolean z) {
        String str2;
        Throwable th;
        String toJSONString;
        TraceLogger traceLogger;
        StringBuilder stringBuilder;
        SCCacheInfoModel sCCacheInfoModel2 = sCCacheInfoModel;
        Map map3 = map;
        String str3 = str;
        Map map4 = map2;
        boolean z2 = z;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{sCCacheInfoModel2, SCCacheInfoModel.class, map3, Map.class, str3, String.class, map4, Map.class, Boolean.valueOf(z2), Boolean.TYPE, Void.TYPE}, this, changeQuickRedirect, "14").isSupported) {
                return;
            }
        }
        String str4 = "theme";
        String str5 = "SkinCenterManager";
        List d = d(map3);
        ArrayList arrayList = new ArrayList(map3.keySet());
        try {
            int size;
            List list;
            String str6;
            int i;
            String str7;
            File u = u(sCCacheInfoModel2.userId);
            SCCacheInfoModel j = j(sCCacheInfoModel2.usageScene);
            if (j != null) {
                Map map5 = j.resMd5Map;
                if (map5 != null) {
                    for (String str8 : map5.keySet()) {
                        Map map6 = sCCacheInfoModel2.resMd5Map;
                        if (map6 == null || !map6.containsKey(str8)) {
                            drawable.k(new File(u, str8));
                            drawable.l(str8, null);
                            c(str8);
                        }
                    }
                }
            }
            if (!arrayList.contains(sCCacheInfoModel2.usageScene)) {
                drawable.k(new File(u, sCCacheInfoModel2.usageScene));
                drawable.l(sCCacheInfoModel2.usageScene, null);
                c(sCCacheInfoModel2.usageScene);
            }
            try {
                size = arrayList.size();
                str2 = null;
                int i2 = 0;
                while (i2 < size) {
                    try {
                        ArrayList arrayList2;
                        int i3;
                        int i4 = i2 + 1;
                        String str9 = (String) arrayList.get(i2);
                        if (TextUtils.isEmpty(str9)) {
                            list = d;
                        } else {
                            File w;
                            try {
                                w = w(sCCacheInfoModel2.userId, str9);
                                list = d;
                            } catch (Throwable th2) {
                                TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                                StringBuilder stringBuilder2 = new StringBuilder();
                                list = d;
                                try {
                                    stringBuilder2.append("unzipScenesRes, getZipOutFile error scene:");
                                    stringBuilder2.append(str9);
                                    traceLogger2.error(str5, stringBuilder2.toString(), th2);
                                    L(sCCacheInfoModel2.skinId, 8, z2);
                                    w = null;
                                } catch (Exception e) {
                                    th2 = e;
                                    LoggerFactory.getTraceLogger().error(str5, "unzipScenesRes, unzipScenesRes error", th2);
                                    L(sCCacheInfoModel2.skinId, 8, z2);
                                    str6 = str2;
                                    if (sCCacheInfoModel2.isDiySkin) {
                                    }
                                    this.g.put(sCCacheInfoModel2.usageScene, sCCacheInfoModel2);
                                    toJSONString = JSON.toJSONString(this.g);
                                    traceLogger = LoggerFactory.getTraceLogger();
                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("save skin cache:");
                                    stringBuilder.append(toJSONString);
                                    traceLogger.info(str5, stringBuilder.toString());
                                    SCCommonUtil.putString("cached_skin_info_v2", toJSONString);
                                    str3 = "ltp";
                                    if (TextUtils.equals(sCCacheInfoModel2.usageScene, str3)) {
                                    }
                                    if (TextUtils.equals(sCCacheInfoModel2.usageScene, str4)) {
                                    }
                                }
                            }
                            if (w != null) {
                                if (w.exists()) {
                                    StringBuilder stringBuilder3;
                                    arrayList2 = arrayList;
                                    o(str9, sCCacheInfoModel2, true).updateCacheTime(sCCacheInfoModel2.cacheTime);
                                    drawable.k(new File(u, str9));
                                    StringBuilder stringBuilder4 = new StringBuilder();
                                    stringBuilder4.append(q(u, str9, sCCacheInfoModel2.userSkinId).getAbsolutePath());
                                    stringBuilder4.append(File.separator);
                                    String stringBuilder5 = stringBuilder4.toString();
                                    ZipHelper.unZip(w.getAbsolutePath(), stringBuilder5);
                                    File file = new File(stringBuilder5);
                                    if (file.exists() && file.isDirectory()) {
                                        File[] listFiles = file.listFiles();
                                        if (listFiles != null) {
                                            int length = listFiles.length;
                                            File[] fileArr = listFiles;
                                            i = 0;
                                            while (i < length) {
                                                int i5;
                                                int i6;
                                                File file2 = fileArr[i];
                                                if (file2 == null || !file2.exists()) {
                                                    i5 = i;
                                                    i6 = length;
                                                } else {
                                                    i5 = i;
                                                    i6 = length;
                                                    if (file2.getName().endsWith(".zip")) {
                                                        stringBuilder3 = new StringBuilder();
                                                        stringBuilder3.append(stringBuilder5);
                                                        i3 = size;
                                                        stringBuilder3.append(file2.getName().split("\\.")[0]);
                                                        stringBuilder3.append("_zip");
                                                        stringBuilder3.append(File.separator);
                                                        ZipHelper.unZip(file2.getAbsolutePath(), stringBuilder3.toString());
                                                        drawable.k(file2);
                                                        i = i5 + 1;
                                                        length = i6;
                                                        size = i3;
                                                    }
                                                }
                                                i3 = size;
                                                i = i5 + 1;
                                                length = i6;
                                                size = i3;
                                            }
                                        }
                                    }
                                    i3 = size;
                                    drawable.I(map4, str9, stringBuilder5);
                                    stringBuilder5 = drawable.N(w, str9, map4);
                                    if (!TextUtils.isEmpty(stringBuilder5)) {
                                        str2 = stringBuilder5;
                                    }
                                    drawable.k(w);
                                    try {
                                        drawable.k(k(sCCacheInfoModel2.userId, str9));
                                    } catch (Throwable th22) {
                                        LoggerFactory.getTraceLogger().error(str5, "unzipScenesRes, delete diy temp file error", th22);
                                        L(sCCacheInfoModel2.skinId, 12, z2);
                                    }
                                    if (TextUtils.equals(str4, str9)) {
                                        try {
                                            str6 = sCCacheInfoModel2.userId;
                                            stringBuilder4 = new StringBuilder();
                                            stringBuilder4.append(str9);
                                            stringBuilder4.append("_appSquare");
                                            w = w(str6, stringBuilder4.toString());
                                        } catch (Throwable th222) {
                                            TraceLogger traceLogger3 = LoggerFactory.getTraceLogger();
                                            stringBuilder3 = new StringBuilder();
                                            stringBuilder3.append("unzipScenesRes, getZipOutFile appSquare error scene:");
                                            stringBuilder3.append(str9);
                                            traceLogger3.error(str5, stringBuilder3.toString(), th222);
                                            L(sCCacheInfoModel2.skinId, 8, z2);
                                            w = null;
                                        }
                                        if (w != null && w.exists()) {
                                            stringBuilder4 = new StringBuilder();
                                            stringBuilder4.append(q(u, str9, sCCacheInfoModel2.userSkinId).getAbsolutePath());
                                            str7 = File.separator;
                                            stringBuilder4.append(str7);
                                            stringBuilder4.append("appSquare");
                                            stringBuilder4.append(str7);
                                            ZipHelper.unZip(w.getAbsolutePath(), stringBuilder4.toString());
                                            drawable.k(w);
                                        }
                                    }
                                    c(str9);
                                    arrayList = arrayList2;
                                    i2 = i4;
                                    d = list;
                                    size = i3;
                                }
                            }
                        }
                        arrayList2 = arrayList;
                        i3 = size;
                        arrayList = arrayList2;
                        i2 = i4;
                        d = list;
                        size = i3;
                    } catch (Exception e2) {
                        th222 = e2;
                        list = d;
                        LoggerFactory.getTraceLogger().error(str5, "unzipScenesRes, unzipScenesRes error", th222);
                        L(sCCacheInfoModel2.skinId, 8, z2);
                        str6 = str2;
                        if (sCCacheInfoModel2.isDiySkin) {
                        }
                        this.g.put(sCCacheInfoModel2.usageScene, sCCacheInfoModel2);
                        toJSONString = JSON.toJSONString(this.g);
                        traceLogger = LoggerFactory.getTraceLogger();
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("save skin cache:");
                        stringBuilder.append(toJSONString);
                        traceLogger.info(str5, stringBuilder.toString());
                        SCCommonUtil.putString("cached_skin_info_v2", toJSONString);
                        str3 = "ltp";
                        if (TextUtils.equals(sCCacheInfoModel2.usageScene, str3)) {
                        }
                        if (TextUtils.equals(sCCacheInfoModel2.usageScene, str4)) {
                        }
                    }
                }
                list = d;
            } catch (Exception e3) {
                th222 = e3;
                list = d;
                str2 = null;
                LoggerFactory.getTraceLogger().error(str5, "unzipScenesRes, unzipScenesRes error", th222);
                L(sCCacheInfoModel2.skinId, 8, z2);
                str6 = str2;
                if (sCCacheInfoModel2.isDiySkin) {
                }
                this.g.put(sCCacheInfoModel2.usageScene, sCCacheInfoModel2);
                toJSONString = JSON.toJSONString(this.g);
                traceLogger = LoggerFactory.getTraceLogger();
                stringBuilder = new StringBuilder();
                stringBuilder.append("save skin cache:");
                stringBuilder.append(toJSONString);
                traceLogger.info(str5, stringBuilder.toString());
                SCCommonUtil.putString("cached_skin_info_v2", toJSONString);
                str3 = "ltp";
                if (TextUtils.equals(sCCacheInfoModel2.usageScene, str3)) {
                }
                if (TextUtils.equals(sCCacheInfoModel2.usageScene, str4)) {
                }
            }
            str6 = str2;
            if (sCCacheInfoModel2.isDiySkin) {
                sCCacheInfoModel2.md5 = str3;
            } else {
                ResourceInfoPB resourceInfoPB = (ResourceInfoPB) map3.get(sCCacheInfoModel2.usageScene);
                if (resourceInfoPB != null) {
                    sCCacheInfoModel2.md5 = resourceInfoPB.md5;
                    sCCacheInfoModel2.appSquareMd5 = resourceInfoPB.appSquareMd5;
                }
            }
            this.g.put(sCCacheInfoModel2.usageScene, sCCacheInfoModel2);
            toJSONString = JSON.toJSONString(this.g);
            traceLogger = LoggerFactory.getTraceLogger();
            stringBuilder = new StringBuilder();
            stringBuilder.append("save skin cache:");
            stringBuilder.append(toJSONString);
            traceLogger.info(str5, stringBuilder.toString());
            SCCommonUtil.putString("cached_skin_info_v2", toJSONString);
            str3 = "ltp";
            if (TextUtils.equals(sCCacheInfoModel2.usageScene, str3)) {
                Intent file3 = new File("com.alipay.skincenter.resCacheUpdated");
                OspSkinCompatModel ospSkinCompatModel = new OspSkinCompatModel();
                ArrayList arrayList3 = (ArrayList) list;
                int size2 = arrayList3.size();
                i = 0;
                int i7 = i;
                while (i7 < size2) {
                    Object obj = arrayList3.get(i7);
                    i7++;
                    ResourceInfoPB resourceInfoPB2 = (ResourceInfoPB) obj;
                    if (str3.equals(resourceInfoPB2.scene)) {
                        ospSkinCompatModel.md5 = resourceInfoPB2.md5;
                        i = 1;
                    }
                }
                if (i != 0) {
                    StringBuilder stringBuilder6;
                    String stringBuilder7;
                    if (!TextUtils.isEmpty(sCCacheInfoModel2.usageScene)) {
                        if (sCCacheInfoModel2.usageScene.contains(str3)) {
                            size2 = 0;
                            str7 = "DEFAULT";
                            ospSkinCompatModel.skinId = size2 == 0 ? str7 : sCCacheInfoModel2.userSkinId;
                            ospSkinCompatModel.minWalletVersion = sCCacheInfoModel2.versionLimit;
                            stringBuilder6 = new StringBuilder();
                            stringBuilder6.append("ltp_");
                            stringBuilder6.append(SCCommonUtil.getCurrentUserId());
                            stringBuilder7 = stringBuilder6.toString();
                            if (!TextUtils.isEmpty(str6)) {
                                StringBuilder stringBuilder8 = new StringBuilder();
                                stringBuilder8.append(stringBuilder7);
                                stringBuilder8.append("_");
                                stringBuilder8.append(str6);
                                stringBuilder7 = stringBuilder8.toString();
                            }
                            ospSkinCompatModel.outDirName = stringBuilder7;
                            ospSkinCompatModel.userId = SCCommonUtil.getCurrentUserId();
                            if (size2 != 0) {
                                str7 = sCCacheInfoModel2.skinId;
                            }
                            ospSkinCompatModel.skinStyleId = str7;
                            if (sCCacheInfoModel2.isDiySkin) {
                                ospSkinCompatModel.skinStyleId = sCCacheInfoModel2.userSkinId;
                            }
                        }
                    }
                    size2 = 1;
                    str7 = "DEFAULT";
                    if (size2 == 0) {
                    }
                    ospSkinCompatModel.skinId = size2 == 0 ? str7 : sCCacheInfoModel2.userSkinId;
                    ospSkinCompatModel.minWalletVersion = sCCacheInfoModel2.versionLimit;
                    stringBuilder6 = new StringBuilder();
                    stringBuilder6.append("ltp_");
                    stringBuilder6.append(SCCommonUtil.getCurrentUserId());
                    stringBuilder7 = stringBuilder6.toString();
                    if (TextUtils.isEmpty(str6)) {
                    }
                    ospSkinCompatModel.outDirName = stringBuilder7;
                    ospSkinCompatModel.userId = SCCommonUtil.getCurrentUserId();
                    if (size2 != 0) {
                    }
                    ospSkinCompatModel.skinStyleId = str7;
                    if (sCCacheInfoModel2.isDiySkin) {
                    }
                }
                file3.putExtra("ospSkinModel", JSON.toJSONString(ospSkinCompatModel));
                LocalBroadcastManager.getInstance(LauncherApplicationAgent.getInstance().getApplicationContext()).sendBroadcast(file3);
                if (!TextUtils.isEmpty(str6)) {
                    int size3 = arrayList3.size();
                    size = 0;
                    while (size < size3) {
                        Object obj2 = arrayList3.get(size);
                        size++;
                        if (str3.equals(((ResourceInfoPB) obj2).scene)) {
                            drawable.l(str3, str6);
                        }
                    }
                }
            }
            if (TextUtils.equals(sCCacheInfoModel2.usageScene, str4)) {
                str6 = SCCommonUtil.getCurrentUserId();
                toJSONString = sCCacheInfoModel2.skinId;
                if (!TextUtils.isEmpty(sCCacheInfoModel2.md5)) {
                    SCCommonUtil.putString("cached_skin_theme", toJSONString);
                    this.d.put(str6, toJSONString);
                    traceLogger = LoggerFactory.getTraceLogger();
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("save hasThemeSkinEnable skinId:");
                    stringBuilder.append(toJSONString);
                    stringBuilder.append(", currentUserId:");
                    stringBuilder.append(str6);
                    traceLogger.info(str5, stringBuilder.toString());
                }
                if (SCCommonUtil.isSeniorsVersion()) {
                    LoggerFactory.getTraceLogger().info(str5, "set theme skin isSeniors");
                } else if (!TextUtils.isEmpty(sCCacheInfoModel2.userId) && !TextUtils.equals(sCCacheInfoModel2.userId, str6)) {
                    TraceLogger traceLogger4 = LoggerFactory.getTraceLogger();
                    StringBuilder stringBuilder9 = new StringBuilder();
                    stringBuilder9.append("user change skin userId:");
                    stringBuilder9.append(sCCacheInfoModel2.userId);
                    stringBuilder9.append(" ,currentUserId:");
                    stringBuilder9.append(str6);
                    traceLogger4.info(str5, stringBuilder9.toString());
                } else if (y(str4, null)) {
                    o(str4, null, true);
                    G(sCCacheInfoModel2.materialId);
                } else {
                    I();
                }
            } else if (!TextUtils.equals(sCCacheInfoModel2.usageScene, str3)) {
                StringBuilder stringBuilder10 = new StringBuilder();
                stringBuilder10.append("com.alipay.skincenter.skinUpdated.");
                stringBuilder10.append(sCCacheInfoModel2.usageScene);
                Intent file4 = new File(stringBuilder10.toString());
                file4.putExtra("skinId", sCCacheInfoModel2.skinId);
                file4.putExtra("userSkinId", sCCacheInfoModel2.userSkinId);
                if (!TextUtils.isEmpty(sCCacheInfoModel2.materialId)) {
                    file4.putExtra("materialId", sCCacheInfoModel2.materialId);
                }
                LocalBroadcastManager.getInstance(LauncherApplicationAgent.getInstance().getApplicationContext()).sendBroadcast(file4);
            }
        } catch (Throwable unused) {
            LoggerFactory.getTraceLogger().error(str5, "unzipScenesRes, getUserDir failed before unzip.");
            L(sCCacheInfoModel2.skinId, 11, z2);
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:32:0x0114  */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x00fa  */
    /* JADX WARNING: Removed duplicated region for block: B:34:0x0118  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public Object D(Context context, AntSkinStyle antSkinStyle, String str, String str2, Map<String, String> map, boolean z, boolean z2, String str3) {
        AntSkinStyle antSkinStyle2 = antSkinStyle;
        String str4 = str;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{context, Context.class, antSkinStyle2, AntSkinStyle.class, str4, String.class, str2, String.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, Boolean.valueOf(z2), Boolean.TYPE, str3, String.class, Object.class}, this, changeQuickRedirect, "5");
            if (proxy.isSupported) {
                return proxy.result;
            }
        }
        if (!TextUtils.isEmpty(str4)) {
            if (!TextUtils.isEmpty(str2)) {
                boolean z3;
                Object loadResSync;
                boolean isDarkMode = context != null ? AUTokenManager.isDarkMode(context) : false;
                long elapsedRealtime = SystemClock.elapsedRealtime();
                if (isDarkMode) {
                    String str5 = "SCConfigUtil";
                    String str6 = "";
                    if (SCConfigUtil.f == null) {
                        try {
                            String string = PreferenceManager.getDefaultSharedPreferences(LoggerFactory.getLogContext().getApplicationContext()).getString("skin_center_dark_adapter_rb", str6);
                            TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                            StringBuilder stringBuilder = new StringBuilder();
                            stringBuilder.append("skin_center_dark_adapter_rb get config:");
                            stringBuilder.append(string);
                            traceLogger.info(str5, stringBuilder.toString());
                            SCConfigUtil.f = string;
                        } catch (Throwable th) {
                            SCConfigUtil.f = str6;
                            LoggerFactory.getTraceLogger().error(str5, "skin_center_dark_adapter_rb get config error", th);
                        }
                    }
                    if (!TextUtils.equals(str4, SCConfigUtil.f)) {
                        boolean z4 = true;
                        z3 = z4;
                        loadResSync = o(str4, null, z4).loadResSync(str2, map, z, z2, str3, true, null);
                        if (antSkinStyle2 != null) {
                            antSkinStyle2.isDarkRes = z3;
                        }
                        if (loadResSync != null) {
                            str5 = str2;
                            loadResSync = o(str4, null, z3).loadResSync(str5, map, z, z2, str3, false, null);
                            if (antSkinStyle2 != null) {
                                antSkinStyle2.isDarkRes = false;
                            }
                        } else {
                            str5 = str2;
                        }
                        if (loadResSync != null) {
                            s(str4, str5, SystemClock.elapsedRealtime() - elapsedRealtime);
                        }
                        return loadResSync;
                    }
                }
                z3 = true;
                loadResSync = null;
                if (loadResSync != null) {
                }
                if (loadResSync != null) {
                }
                return loadResSync;
            }
        }
        return null;
    }

    /* JADX WARNING: Removed duplicated region for block: B:14:0x005c  */
    /* JADX WARNING: Removed duplicated region for block: B:21:0x007f  */
    /* JADX WARNING: Removed duplicated region for block: B:45:0x0105 A:{Catch:{ all -> 0x0133 }} */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public Map<String, ResourceInfoPB> l(String str, List<ResourceInfoPB> list, String str2, boolean z) {
        int i;
        HashMap hashMap;
        String str3;
        String str4;
        SkinCenterRpcService y;
        SkinDetailReqPB skinDetailReqPB;
        GetUserSkinDetailResPB querySkinDetail;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, list, List.class, str2, String.class, Boolean.valueOf(z), Boolean.TYPE, Map.class}, this, changeQuickRedirect, "26");
            if (proxy.isSupported) {
                return (Map) proxy.result;
            }
        }
        ArrayList clientSupportScenes = SCCommonUtil.getClientSupportScenes();
        if (!TextUtils.isEmpty(str2)) {
            if (!"skinCenter".equals(str2)) {
                i = 0;
                if (i == 0) {
                    clientSupportScenes = new ArrayList();
                    clientSupportScenes.add(str2);
                }
                hashMap = new HashMap();
                str3 = "SkinCenterManager";
                str4 = "DEFAULT";
                if (!(TextUtils.isEmpty(str) || list == null)) {
                    for (ResourceInfoPB resourceInfoPB : list) {
                        String str5 = resourceInfoPB.scene;
                        String f = SCConfigUtil.f("sc_aptripPass_check_md5");
                        if (!(TextUtils.isEmpty(f) ? TextUtils.equals(str5, "aptripPass") : Boolean.parseBoolean(f)) ? false : TextUtils.isEmpty(resourceInfoPB.md5)) {
                            TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                            StringBuilder stringBuilder = new StringBuilder();
                            stringBuilder.append("filter empty md5 res:");
                            stringBuilder.append(resourceInfoPB.scene);
                            traceLogger.debug(str3, stringBuilder.toString());
                        } else if (clientSupportScenes.contains(resourceInfoPB.scene) && (str4.equals(str) || str.contains(resourceInfoPB.scene))) {
                            hashMap.put(resourceInfoPB.scene, resourceInfoPB);
                        }
                    }
                }
                if (!(i == 0 || str4.equals(str) || hashMap.size() == clientSupportScenes.size())) {
                    y = drawable.y();
                    skinDetailReqPB = new SkinDetailReqPB();
                    skinDetailReqPB.skinId = str4;
                    querySkinDetail = y.querySkinDetail(skinDetailReqPB);
                    if (querySkinDetail != null) {
                        SkinDetailInfoPB skinDetailInfoPB = querySkinDetail.skinDetailInfo;
                        if (skinDetailInfoPB != null) {
                            List<ResourceInfoPB> list2 = skinDetailInfoPB.resourceList;
                            if (list2 != null) {
                                for (ResourceInfoPB resourceInfoPB2 : list2) {
                                    if (clientSupportScenes.contains(resourceInfoPB2.scene) && hashMap.get(resourceInfoPB2.scene) == null) {
                                        hashMap.put(resourceInfoPB2.scene, resourceInfoPB2);
                                    }
                                }
                            }
                        }
                    }
                }
                return hashMap;
            }
        }
        i = 1;
        if (i == 0) {
        }
        hashMap = new HashMap();
        str3 = "SkinCenterManager";
        str4 = "DEFAULT";
        for (ResourceInfoPB resourceInfoPB3 : list) {
        }
        try {
            y = drawable.y();
            skinDetailReqPB = new SkinDetailReqPB();
            skinDetailReqPB.skinId = str4;
            querySkinDetail = y.querySkinDetail(skinDetailReqPB);
            if (querySkinDetail != null) {
            }
        } catch (Throwable th) {
            LoggerFactory.getTraceLogger().error(str3, "getDownloadResources, querySkinDetail default error", th);
            L(str4, 1, z);
        }
        return hashMap;
    }

    public Object B(String str, String str2, Map<String, String> map, boolean z, boolean z2, String str3) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, Boolean.valueOf(z2), Boolean.TYPE, str3, String.class, Object.class}, this, changeQuickRedirect, "3");
            if (proxy.isSupported) {
                return proxy.result;
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (!TextUtils.isEmpty(str2)) {
                long elapsedRealtime = SystemClock.elapsedRealtime();
                Object loadResSync = o(str, null, true).loadResSync(str2, map, z, z2, str3);
                if (loadResSync != null) {
                    s(str, str2, SystemClock.elapsedRealtime() - elapsedRealtime);
                }
                return loadResSync;
            }
        }
        return null;
    }

    public boolean g(SCCacheInfoModel sCCacheInfoModel, Map<String, ResourceInfoPB> map, boolean z, String str, Map<String, List<SkinDiyInfoModel>> map2, boolean z2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{sCCacheInfoModel, SCCacheInfoModel.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, str, String.class, map2, Map.class, Boolean.valueOf(z2), Boolean.TYPE, Boolean.TYPE}, this, changeQuickRedirect, "21");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        return h(sCCacheInfoModel, map, z, str, map2, false, z2);
    }

    public Object A(String str, String str2, Map<String, String> map, boolean z, boolean z2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, Boolean.valueOf(z2), Boolean.TYPE, Object.class}, this, changeQuickRedirect, "2");
            if (proxy.isSupported) {
                return proxy.result;
            }
        }
        long elapsedRealtime = SystemClock.elapsedRealtime();
        String str3 = str;
        String str4 = str2;
        Object B = B(str3, str4, map, z, z2, "");
        if (B != null) {
            s(str3, str4, SystemClock.elapsedRealtime() - elapsedRealtime);
        }
        return B;
    }

    public boolean f(String str, List<ResourceInfoPB> list, List<String> list2) {
        TraceLogger traceLogger;
        StringBuilder stringBuilder;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, list, List.class, list2, List.class, Boolean.TYPE}, this, changeQuickRedirect, "20");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        String str2 = "SkinCenterManager";
        MultimediaFileService multimediaFileService = (MultimediaFileService) LauncherApplicationAgent.getInstance().getMicroApplicationContext().findServiceByInterface(MultimediaFileService.class.getName());
        if (multimediaFileService == null) {
            return false;
        }
        for (ResourceInfoPB resourceInfoPB : list) {
            if (list2.contains(resourceInfoPB.scene)) {
                if (!TextUtils.isEmpty(resourceInfoPB.md5)) {
                    try {
                        File w = w(str, resourceInfoPB.scene);
                        APFileReq aPFileReq = new APFileReq();
                        aPFileReq.setCloudId(resourceInfoPB.link);
                        aPFileReq.setSavePath(w.getAbsolutePath());
                        aPFileReq.setMd5(resourceInfoPB.md5);
                        String str3 = "skinCenter";
                        int retCode = multimediaFileService.downLoadSync(aPFileReq, null, str3).getRetCode();
                        TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append("downloadRes, scene:");
                        stringBuilder2.append(resourceInfoPB.scene);
                        stringBuilder2.append(" , downloadResCode:");
                        stringBuilder2.append(retCode);
                        stringBuilder2.append(", link: ");
                        stringBuilder2.append(resourceInfoPB.link);
                        traceLogger2.info(str2, stringBuilder2.toString());
                        if (retCode != 0) {
                            drawable.k(w);
                            return false;
                        } else if (!(TextUtils.isEmpty(resourceInfoPB.appSquareMd5) || TextUtils.isEmpty(resourceInfoPB.appSquareLink))) {
                            try {
                                StringBuilder stringBuilder3 = new StringBuilder();
                                stringBuilder3.append(resourceInfoPB.scene);
                                stringBuilder3.append("_appSquare");
                                File w2 = w(str, stringBuilder3.toString());
                                APFileReq aPFileReq2 = new APFileReq();
                                aPFileReq2.setCloudId(resourceInfoPB.appSquareLink);
                                aPFileReq2.setSavePath(w2.getAbsolutePath());
                                aPFileReq2.setMd5(resourceInfoPB.appSquareMd5);
                                int retCode2 = multimediaFileService.downLoadSync(aPFileReq2, null, str3).getRetCode();
                                TraceLogger traceLogger3 = LoggerFactory.getTraceLogger();
                                StringBuilder stringBuilder4 = new StringBuilder();
                                stringBuilder4.append("downloadRes, appSquare scene:");
                                stringBuilder4.append(resourceInfoPB.scene);
                                stringBuilder4.append(" , appSquareResCode:");
                                stringBuilder4.append(retCode2);
                                stringBuilder4.append(", appSquareLink: ");
                                stringBuilder4.append(resourceInfoPB.appSquareLink);
                                traceLogger3.info(str2, stringBuilder4.toString());
                                if (retCode2 != 0) {
                                    drawable.k(w);
                                    drawable.k(w2);
                                    return false;
                                }
                            } catch (Throwable th) {
                                traceLogger = LoggerFactory.getTraceLogger();
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("downloadRes, getZipOutFile for appSquare error, scene:");
                                stringBuilder.append(resourceInfoPB.scene);
                                traceLogger.error(str2, stringBuilder.toString(), th);
                                return false;
                            }
                        }
                    } catch (Throwable th2) {
                        traceLogger = LoggerFactory.getTraceLogger();
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("downloadRes, getZipOutFile error, scene:");
                        stringBuilder.append(resourceInfoPB.scene);
                        traceLogger.error(str2, stringBuilder.toString(), th2);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public SCMetaModel o(String str, SCCacheInfoModel sCCacheInfoModel, boolean z) {
        StringBuilder stringBuilder;
        SCMetaModel file;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, sCCacheInfoModel, SCCacheInfoModel.class, Boolean.valueOf(z), Boolean.TYPE, SCMetaModel.class}, this, changeQuickRedirect, "29");
            if (proxy.isSupported) {
                return (SCMetaModel) proxy.result;
            }
        }
        Object obj = TextUtils.equals(str, "ltp_cashier") ? "ltp" : str;
        SCMetaModel sCMetaModel = z ? (SCMetaModel) this.h.get(obj) : null;
        if (sCMetaModel != null) {
            return sCMetaModel;
        }
        SCMetaModel sCMetaModel2;
        synchronized (SCInnerManager.class) {
            if (sCCacheInfoModel == null) {
                try {
                    if (!this.c.get()) {
                        K();
                    }
                    sCCacheInfoModel = j(obj);
                } catch (Throwable th) {
                    Throwable th2 = th;
                }
            }
            if (sCCacheInfoModel != null) {
                sCMetaModel2 = new SCMetaModel(obj, sCCacheInfoModel.skinId, sCCacheInfoModel.userSkinId, sCCacheInfoModel.md5, sCCacheInfoModel.cacheTime, q(u(SCCommonUtil.getCurrentUserId()), obj, null));
                sCMetaModel2.setBizScene(str);
                this.h.put(obj, sCMetaModel2);
                SCSkinStatusUtil.a.put(obj, Boolean.TRUE);
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("getMetaModel, put meta cache: ");
                stringBuilder2.append(obj);
                LoggerFactory.getTraceLogger().info("SkinCenterManager", stringBuilder2.toString());
            } else {
                file = new File(obj);
                file.setBizScene(str);
                stringBuilder = new StringBuilder();
                stringBuilder.append("getMetaModel, skinInfoMode is null scene: ");
                stringBuilder.append(obj);
                LoggerFactory.getTraceLogger().info("SkinCenterManager", stringBuilder.toString());
                sCMetaModel2 = file;
            }
        }
        return sCMetaModel2;
    }

    /* JADX WARNING: Missing block: B:42:0x0112, code:
            if (android.text.TextUtils.equals(o(r9, null, true).skinId, "DEFAULT") != false) goto L_0x0114;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean y(String str, Map<String, String> map) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, map, Map.class, Boolean.TYPE}, this, changeQuickRedirect, "39");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        if (!TextUtils.isEmpty(str)) {
            String str2 = " , hasEnableSkin:";
            String str3 = "hasEnableSkin scene:";
            String str4 = "SkinCenterManager";
            boolean l;
            TraceLogger traceLogger;
            if (TextUtils.equals(str, "theme")) {
                try {
                    l = SCConfigUtil.l();
                    TraceLogger traceLogger2;
                    StringBuilder stringBuilder;
                    if (l) {
                        traceLogger2 = LoggerFactory.getTraceLogger();
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("hasEnableSkin false theme skin isThemeSkinRollBack:");
                        stringBuilder.append(l);
                        traceLogger2.info(str4, stringBuilder.toString());
                        return false;
                    }
                    String currentUserId = SCCommonUtil.getCurrentUserId();
                    if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(this.j) || TextUtils.equals(currentUserId, this.j)) {
                        boolean isEmpty = TextUtils.isEmpty(t(currentUserId));
                        int i = isEmpty ^ 1;
                        if (isEmpty || !SCCommonUtil.isSeniorsVersion()) {
                            traceLogger = LoggerFactory.getTraceLogger();
                            StringBuilder stringBuilder2 = new StringBuilder();
                            stringBuilder2.append(str3);
                            stringBuilder2.append(str);
                            stringBuilder2.append(str2);
                            stringBuilder2.append(i);
                            stringBuilder2.append(", currentUserId:");
                            stringBuilder2.append(currentUserId);
                            traceLogger.info(str4, stringBuilder2.toString());
                            return i;
                        }
                        LoggerFactory.getTraceLogger().info(str4, "hasEnableSkin theme skin, isSeniorsVersion true");
                        return false;
                    }
                    traceLogger2 = LoggerFactory.getTraceLogger();
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("user changed cacheUserId:");
                    stringBuilder.append(this.j);
                    stringBuilder.append(" , currentUserId:");
                    stringBuilder.append(currentUserId);
                    traceLogger2.info(str4, stringBuilder.toString());
                    b();
                    return false;
                } catch (Exception e) {
                    LoggerFactory.getTraceLogger().error(str4, "hasEnableSkin theme error", e);
                    return false;
                }
            }
            if (str.startsWith("atmospheric_")) {
                if (SCConfigUtil.b()) {
                }
            }
            if (TextUtils.equals(str, "ltp_cashier") && SCConfigUtil.a()) {
                LoggerFactory.getTraceLogger().info(str4, "hasEnableSkin ltp_cashier rollback");
                return false;
            }
            l = o(str, null, true).hasEnableSkin(map);
            traceLogger = LoggerFactory.getTraceLogger();
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(str3);
            stringBuilder3.append(str);
            stringBuilder3.append(str2);
            stringBuilder3.append(l);
            traceLogger.info(str4, stringBuilder3.toString());
            return l;
        }
        return false;
    }

    public void K() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(this, changeQuickRedirect, "12", Void.TYPE).isSupported) {
            String str = "SkinCenterManager";
            LoggerFactory.getTraceLogger().info(str, "readSkinInfoFromLocalCache");
            String currentUserId = SCCommonUtil.getCurrentUserId();
            this.j = currentUserId;
            String string = SharedPreferences.getString("cached_skin_info_v2", currentUserId);
            if (TextUtils.isEmpty(string)) {
                string = SharedPreferences.getString("cached_skin_info", currentUserId);
                SCCacheInfoModel sCCacheInfoModel = new SCCacheInfoModel();
                if (!TextUtils.isEmpty(string)) {
                    LoggerFactory.getTraceLogger().info(str, string);
                    sCCacheInfoModel = (SCCacheInfoModel) JSON.parseObject(string, new d(this), new Feature[0]);
                }
                this.g.putAll(sCCacheInfoModel.splitCacheScene());
                currentUserId = SharedPreferences.getString("cached_skin_info_aptrip", currentUserId);
                SCCacheInfoModel sCCacheInfoModel2 = new SCCacheInfoModel();
                if (!TextUtils.isEmpty(currentUserId)) {
                    LoggerFactory.getTraceLogger().info(str, currentUserId);
                    sCCacheInfoModel2 = (SCCacheInfoModel) JSON.parseObject(currentUserId, new e(this), new Feature[0]);
                }
                this.g.putAll(sCCacheInfoModel2.splitCacheScene());
                this.c.set(true);
                return;
            }
            TraceLogger traceLogger = LoggerFactory.getTraceLogger();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("cache skin info:");
            stringBuilder.append(string);
            traceLogger.info(str, stringBuilder.toString());
            this.g.putAll((Map) JSON.parseObject(string, new c(this), new Feature[0]));
            this.c.set(true);
        }
    }

    public Map<String, Object> C(String str, List<String> list, Map<String, String> map, boolean z) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, list, List.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, Map.class}, this, changeQuickRedirect, "4");
            if (proxy.isSupported) {
                return (Map) proxy.result;
            }
        }
        HashMap hashMap = new HashMap();
        if (list != null) {
            if (list.size() > 0) {
                for (String str2 : list) {
                    String str3 = str;
                    Map<String, String> map2 = map;
                    boolean z2 = z;
                    Object A = A(str3, str2, map2, false, z2);
                    if (A != null) {
                        hashMap.put(str2, A);
                    }
                    str = str3;
                    map = map2;
                    z = z2;
                }
            }
        }
        return hashMap;
    }

    public void c(String str) {
        SCInnerManager sCInnerManager;
        CharSequence charSequence;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            charSequence = str;
            if (PatchProxy.proxy(charSequence, sCInnerManager, changeQuickRedirect, "17", String.class, Void.TYPE).isSupported) {
                return;
            }
        }
        sCInnerManager = this;
        charSequence = str;
        str = TextUtils.equals(charSequence, "ltp_cashier") ? "ltp" : charSequence;
        SkinImageCacheManager a = SkinImageCacheManager.a();
        a.b.trimToSize(0);
        a.b = a.b();
        SCMetaModel.clearCachedInfo(str);
        if ("theme".equals(str)) {
            sCInnerManager.d.clear();
        }
        SCMetaModel sCMetaModel = (SCMetaModel) sCInnerManager.h.get(str);
        if (sCMetaModel != null) {
            sCInnerManager.h.remove(str);
            SCSkinStatusUtil.a.remove(str);
        }
        TraceLogger traceLogger = LoggerFactory.getTraceLogger();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("remove meta cache, scene:");
        stringBuilder.append(str);
        stringBuilder.append(" , metaModel:");
        stringBuilder.append(sCMetaModel);
        traceLogger.info("SkinCenterManager", stringBuilder.toString());
    }

    public Bitmap i(String str) {
        SCInnerManager sCInnerManager;
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, sCInnerManager, changeQuickRedirect, "23", String.class, Bitmap.class);
            if (proxy.isSupported) {
                return (Bitmap) proxy.result;
            }
        }
        sCInnerManager = this;
        str2 = str;
        MultimediaImageService multimediaImageService = (MultimediaImageService) LauncherApplicationAgent.getInstance().getMicroApplicationContext().findServiceByInterface(MultimediaImageService.class.getName());
        if (multimediaImageService == null) {
            return null;
        }
        sCInnerManager.m = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        APImageLoadRequest aPImageLoadRequest = new APImageLoadRequest();
        aPImageLoadRequest.cutScaleType = CutScaleType.NONE;
        aPImageLoadRequest.path = str2;
        aPImageLoadRequest.displayer = new a(this, countDownLatch);
        aPImageLoadRequest.callback = new a(this, countDownLatch);
        multimediaImageService.loadImage(aPImageLoadRequest, "skinCenter");
        try {
            countDownLatch.await();
        } catch (Throwable e) {
            LoggerFactory.getTraceLogger().error("SkinCenterManager", "getBitmapForUrl error", e);
        }
        return sCInnerManager.m;
    }

    public File k(String str, String str2) {
        File file;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, File.class}, this, changeQuickRedirect, "25");
            if (proxy.isSupported) {
                return (File) proxy.result;
            }
        }
        File u = u(str);
        if (!"true".equalsIgnoreCase(SCConfigUtil.f("config_key_diy_tmp_path_rb"))) {
            if (!TextUtils.isEmpty(str2)) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(a);
                stringBuilder.append(File.separator);
                stringBuilder.append(str2);
                str2 = stringBuilder.toString();
                file = new File(u, str2);
                F(file);
                return file;
            }
        }
        str2 = a;
        file = new File(u, str2);
        F(file);
        return file;
    }

    public String n(String str, MapStringString mapStringString) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, mapStringString, MapStringString.class, String.class}, this, changeQuickRedirect, "28");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (mapStringString != null) {
            List list = mapStringString.entries;
            if (list != null) {
                if (list.size() != 0) {
                    for (EntryStringString entryStringString : mapStringString.entries) {
                        if (entryStringString != null) {
                            if (TextUtils.equals(str, entryStringString.key)) {
                                return entryStringString.value;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public String r(String str, Map<String, String> map) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, map, Map.class, String.class}, this, changeQuickRedirect, "32");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        String str2 = "";
        if (TextUtils.isEmpty(str)) {
            return str2;
        }
        if (!TextUtils.equals(str, "theme")) {
            return o(str, null, true).getSkinId(map);
        }
        if (y(str, null)) {
            return t(SCCommonUtil.getCurrentUserId());
        }
        LoggerFactory.getTraceLogger().info("SkinCenterManager", "getSkinId, theme skin not hasEnableSkin");
        return str2;
    }

    public File w(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, File.class}, this, changeQuickRedirect, "37");
            if (proxy.isSupported) {
                return (File) proxy.result;
            }
        }
        File file = new File(u(str), "tmp");
        F(file);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(str2);
        stringBuilder.append(".zip");
        return new File(file, stringBuilder.toString());
    }

    public Object z(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, Object.class}, this, changeQuickRedirect, "40");
            if (proxy.isSupported) {
                return proxy.result;
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (!TextUtils.isEmpty(str2)) {
                if (!TextUtils.equals(str, "ltp_cashier") || !SCConfigUtil.a()) {
                    return o(str, null, true).loadMetaInfo(str2);
                }
            }
        }
        return null;
    }

    public void E(String str, long j, int i) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, Long.valueOf(j), Long.TYPE, Integer.valueOf(i), Integer.TYPE, Void.TYPE}, this, changeQuickRedirect, "6").isSupported) {
                return;
            }
        }
        Builder builder = new Builder();
        builder.setEventID("10101067");
        builder.setBizType("middle");
        builder.setLoggerLevel(2);
        builder.addExtParam("scene", str);
        builder.addExtParam("cost", String.valueOf(j));
        builder.addExtParam("error_code", String.valueOf(i));
        builder.build().send();
    }

    public void L(String str, int i, boolean z) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, Integer.valueOf(i), Integer.TYPE, Boolean.valueOf(z), Boolean.TYPE, Void.TYPE}, this, changeQuickRedirect, "13").isSupported) {
                return;
            }
        }
        int b = c.b(i);
        String c = c.c(i);
        Builder builder = new Builder();
        builder.setEventID("10101482");
        builder.setBizType("middle");
        builder.setLoggerLevel(2);
        builder.addExtParam("skinId", str);
        builder.addExtParam("code", String.valueOf(b));
        builder.addExtParam("msg", c);
        builder.addExtParam("userSet", String.valueOf(z));
        builder.build().send();
    }

    public File q(File file, String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{file, File.class, str, String.class, str2, String.class, File.class}, this, changeQuickRedirect, "31");
            if (proxy.isSupported) {
                return (File) proxy.result;
            }
        }
        File file2 = new File(file, str);
        if (!TextUtils.equals("theme", str)) {
            return file2;
        }
        if (TextUtils.isEmpty(str2)) {
            str2 = v(str);
        }
        return new File(file2, str2);
    }

    public void s(String str, String str2, long j) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, Long.valueOf(j), Long.TYPE, Void.TYPE}, this, changeQuickRedirect, "33").isSupported) {
                return;
            }
        }
        Builder builder = new Builder();
        builder.setEventID("10101293");
        builder.setBizType("middle");
        builder.setLoggerLevel(2);
        builder.addExtParam("scene", str);
        builder.addExtParam("skinId", r(str, null));
        builder.addExtParam("position", str2);
        builder.addExtParam("resType", p(str, str2));
        builder.addExtParam("cost", String.valueOf(j));
        builder.build().send();
    }

    /* JADX WARNING: Removed duplicated region for block: B:251:0x06b2 A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:251:0x06b2 A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:251:0x06b2 A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:251:0x06b2 A:{SYNTHETIC} */
    /* JADX WARNING: Missing block: B:31:0x00bb, code:
            if (r9 == false) goto L_0x00bd;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean e(SCCacheInfoModel sCCacheInfoModel, SkinDiyMetaInfoPB skinDiyMetaInfoPB, Map<String, List<SkinDiyInfoModel>> map, boolean z, boolean z2) {
        boolean z3;
        String str;
        String str2;
        Throwable th;
        TraceLogger traceLogger;
        StringBuilder stringBuilder;
        ArrayList arrayList;
        SkinDiyMetaInfoPB skinDiyMetaInfoPB2;
        SkinDiyMetaInfoPB skinDiyMetaInfoPB3;
        SCCacheInfoModel sCCacheInfoModel2 = sCCacheInfoModel;
        SkinDiyMetaInfoPB skinDiyMetaInfoPB4 = skinDiyMetaInfoPB;
        boolean z4 = z2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{sCCacheInfoModel2, SCCacheInfoModel.class, skinDiyMetaInfoPB4, SkinDiyMetaInfoPB.class, map, Map.class, Boolean.valueOf(z), Boolean.TYPE, Boolean.valueOf(z4), Boolean.TYPE, Boolean.TYPE}, this, changeQuickRedirect, "19");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        boolean z5 = true;
        if (skinDiyMetaInfoPB4 != null) {
            SkinDiyTransformPB skinDiyTransform = SCCommonUtil.getSkinDiyTransform(skinDiyMetaInfoPB4.transforms, sCCacheInfoModel2.usageScene);
            if (skinDiyTransform != null) {
                if (SCCommonUtil.compareWalletVersion(SCCommonUtil.currentVersionName(), sCCacheInfoModel2.versionLimit) < 0) {
                    L(sCCacheInfoModel2.skinId, 14, z4);
                    return false;
                }
                SCCacheInfoModel j = j(sCCacheInfoModel2.usageScene);
                if (!(z || j == null || j.md5 == null || !TextUtils.equals(sCCacheInfoModel2.userSkinId, j.userSkinId) || !j.isDiySkin)) {
                    boolean m = SCConfigUtil.m(j.outMetaId, j.cacheTime);
                    if (j.usageScene != null) {
                        if (TextUtils.equals(j.md5, sCCacheInfoModel2.md5)) {
                            if (TextUtils.equals(j.appSquareMd5, sCCacheInfoModel2.appSquareMd5)) {
                            }
                        }
                    }
                }
                String str3 = sCCacheInfoModel2.userId;
                String str4 = sCCacheInfoModel2.usageScene;
                List list = skinDiyTransform.skinDiySVCInfos;
                String str5 = sCCacheInfoModel2.skinId;
                String str6 = skinDiyTransform.scene;
                String str7 = "SkinCenterManager";
                if (list == null || list.size() == 0) {
                    z3 = true;
                } else {
                    long j2;
                    StringBuilder stringBuilder2;
                    String str8;
                    ArrayList arrayList2 = new ArrayList();
                    GradeReqInfo gradeReqInfo = new GradeReqInfo(GradeBizName.MOBILEAIX, "skinCenter", 2001);
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    TaskGradeController.getInstance().runInCurrentThread(gradeReqInfo, new a(this, countDownLatch));
                    try {
                        countDownLatch.await();
                    } catch (InterruptedException e) {
                        LoggerFactory.getTraceLogger().error(str7, "getGradeController error", e);
                    }
                    boolean z6 = this.l;
                    TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                    StringBuilder stringBuilder3 = new StringBuilder();
                    stringBuilder3.append("downloadDiyRes, isGradeOnLowLevel:");
                    stringBuilder3.append(z6);
                    traceLogger2.info(str7, stringBuilder3.toString());
                    long elapsedRealtime = SystemClock.elapsedRealtime();
                    Iterator it = list.iterator();
                    loop0:
                    while (it.hasNext()) {
                        Iterator it2;
                        SkinDiySVCInfoPB skinDiySVCInfoPB = (SkinDiySVCInfoPB) it.next();
                        if (skinDiySVCInfoPB == null) {
                            it2 = it;
                            j2 = elapsedRealtime;
                        } else {
                            String str9;
                            String str10;
                            boolean z7;
                            String str11 = skinDiySVCInfoPB.sceneMeta;
                            it2 = it;
                            String str12 = skinDiySVCInfoPB.sceneMetaOpType;
                            j2 = elapsedRealtime;
                            String str13 = skinDiySVCInfoPB.sceneMetaPosition;
                            SkinDiyPositionInfoPB skinDiyPositionInfoPB = skinDiySVCInfoPB.positionInfo;
                            if (skinDiyPositionInfoPB != null) {
                                List list2 = skinDiyPositionInfoPB.positionMetas;
                                if (list2 != null) {
                                    if (list2.size() != 0) {
                                        String str14 = "color";
                                        String str15;
                                        SkinDiyInfoModel skinDiyInfoModel;
                                        if ("replacePic".equals(str12)) {
                                            boolean z8;
                                            ArrayList arrayList3;
                                            str13 = " , sceneMeta:";
                                            CharSequence charSequence = "pic";
                                            ArrayList arrayList4 = arrayList2;
                                            String str16 = " , isGradeOnLowLevel:";
                                            String str17;
                                            String str18;
                                            TraceLogger traceLogger3;
                                            String str19;
                                            StringBuilder stringBuilder4;
                                            CharSequence charSequence2;
                                            CharSequence charSequence3;
                                            String str20;
                                            int i;
                                            Integer num;
                                            int i2;
                                            File file;
                                            if (TextUtils.equals("merge", skinDiyPositionInfoPB.opType)) {
                                                try {
                                                    int intValue;
                                                    int i3;
                                                    Bitmap bitmap;
                                                    int i4 = SCConfigUtil.i();
                                                    if (z6) {
                                                        i4 = SCConfigUtil.g();
                                                    }
                                                    str17 = str6;
                                                    str18 = str12;
                                                    int i5 = i4;
                                                    traceLogger3 = LoggerFactory.getTraceLogger();
                                                    str19 = str3;
                                                    stringBuilder4 = new StringBuilder();
                                                    str15 = str14;
                                                    stringBuilder4.append("doOpMerge, quality:");
                                                    stringBuilder4.append(i5);
                                                    stringBuilder4.append(str16);
                                                    stringBuilder4.append(z6);
                                                    traceLogger3.error(str7, stringBuilder4.toString());
                                                    Iterator it3 = skinDiyPositionInfoPB.positionMetas.iterator();
                                                    Bitmap bitmap2 = null;
                                                    while (it3.hasNext()) {
                                                        try {
                                                            SkinDiyPositionMetaPB skinDiyPositionMetaPB = (SkinDiyPositionMetaPB) it3.next();
                                                            if (skinDiyPositionMetaPB != null) {
                                                                Iterator it4;
                                                                int i6;
                                                                int i7;
                                                                int intValue2;
                                                                Bitmap j3;
                                                                int i8;
                                                                int i9;
                                                                str3 = n(skinDiyPositionMetaPB.positionPic, skinDiyMetaInfoPB4.assetPool);
                                                                Integer num2 = skinDiyPositionMetaPB.scaleY;
                                                                int intValue3 = num2 != null ? num2.intValue() : 0;
                                                                num2 = skinDiyPositionMetaPB.scaleX;
                                                                intValue = num2 != null ? num2.intValue() : 0;
                                                                if (!TextUtils.equals(charSequence, skinDiyPositionMetaPB.positionMetaType)) {
                                                                    it4 = it3;
                                                                    str6 = str15;
                                                                    if (TextUtils.equals(str6, skinDiyPositionMetaPB.positionMetaType)) {
                                                                        int i10;
                                                                        String str21;
                                                                        str15 = str6;
                                                                        String n = n(skinDiyPositionMetaPB.positionStartColor, skinDiyMetaInfoPB4.assetPool);
                                                                        str6 = n(skinDiyPositionMetaPB.positionEndColor, skinDiyMetaInfoPB4.assetPool);
                                                                        int intValue4 = skinDiyPositionMetaPB.angle.intValue();
                                                                        String str22 = str6;
                                                                        List list3 = skinDiyPositionMetaPB.transparencyList;
                                                                        if (list3 != null) {
                                                                            i6 = intValue4;
                                                                            if (list3.size() >= 2) {
                                                                                charSequence2 = charSequence;
                                                                                i10 = intValue3;
                                                                                i7 = intValue;
                                                                                str21 = str22;
                                                                                intValue = i6;
                                                                                i6 = (int) ((((double) ((Integer) skinDiyPositionMetaPB.transparencyList.get(1)).intValue()) / 100.0d) * 255.0d);
                                                                                intValue2 = (int) ((((double) ((Integer) skinDiyPositionMetaPB.transparencyList.get(0)).intValue()) / 100.0d) * 255.0d);
                                                                                j3 = drawable.j(n, str21, intValue, intValue2, i6, i7, i10);
                                                                                i8 = i7;
                                                                                i9 = i10;
                                                                            }
                                                                        } else {
                                                                            i6 = intValue4;
                                                                        }
                                                                        charSequence2 = charSequence;
                                                                        i10 = intValue3;
                                                                        i7 = intValue;
                                                                        str21 = str22;
                                                                        intValue = i6;
                                                                        intValue2 = 255;
                                                                        i6 = 255;
                                                                        j3 = drawable.j(n, str21, intValue, intValue2, i6, i7, i10);
                                                                        i8 = i7;
                                                                        i9 = i10;
                                                                    } else {
                                                                        charSequence2 = charSequence;
                                                                        str15 = str6;
                                                                        i9 = intValue3;
                                                                        i8 = intValue;
                                                                        j3 = null;
                                                                    }
                                                                } else if (TextUtils.isEmpty(str3)) {
                                                                    E(str4, SystemClock.elapsedRealtime() - j2, 1);
                                                                    traceLogger2 = LoggerFactory.getTraceLogger();
                                                                    stringBuilder2 = new StringBuilder();
                                                                    stringBuilder2.append("downloadDiyRes, merge not found pic for assetPoll ,positionPic:");
                                                                    stringBuilder2.append(skinDiyPositionMetaPB.positionPic);
                                                                    stringBuilder2.append(str13);
                                                                    stringBuilder2.append(str11);
                                                                    traceLogger2.error(str7, stringBuilder2.toString());
                                                                    L(str5, 2, z4);
                                                                    z5 = false;
                                                                    break;
                                                                } else {
                                                                    it4 = it3;
                                                                    j3 = i(str3);
                                                                    i8 = intValue;
                                                                    charSequence2 = charSequence;
                                                                    i9 = intValue3;
                                                                }
                                                                if (j3 == null) {
                                                                    E(str4, SystemClock.elapsedRealtime() - j2, 2);
                                                                    traceLogger2 = LoggerFactory.getTraceLogger();
                                                                    stringBuilder2 = new StringBuilder();
                                                                    stringBuilder2.append("downloadDiyRes, merge download pic fail ,positionPic:");
                                                                    stringBuilder2.append(skinDiyPositionMetaPB.positionPic);
                                                                    stringBuilder2.append(str13);
                                                                    stringBuilder2.append(str11);
                                                                    traceLogger2.error(str7, stringBuilder2.toString());
                                                                    L(str5, 3, z4);
                                                                    z5 = false;
                                                                    break;
                                                                }
                                                                SkinDiyPositionInfoPB skinDiyPositionInfoPB2;
                                                                charSequence3 = charSequence2;
                                                                num2 = skinDiyPositionMetaPB.transparency;
                                                                if (num2 == null || num2.intValue() <= 0) {
                                                                    str20 = str13;
                                                                    i = 255;
                                                                } else {
                                                                    str20 = str13;
                                                                    i = (int) ((((double) skinDiyPositionMetaPB.transparency.intValue()) / 100.0d) * 255.0d);
                                                                }
                                                                str14 = " ,alpha:";
                                                                str8 = " ,width:";
                                                                str = str5;
                                                                str5 = " ,height:";
                                                                str2 = str4;
                                                                str4 = " ,picUrl:";
                                                                if (bitmap2 == null) {
                                                                    i3 = i5;
                                                                    try {
                                                                        if (skinDiyPositionInfoPB.positionMetas.size() > 0) {
                                                                            j3 = drawable.K(j3, i9, i8, i, z6);
                                                                            TraceLogger traceLogger4 = LoggerFactory.getTraceLogger();
                                                                            bitmap2 = j3;
                                                                            stringBuilder2 = new StringBuilder();
                                                                            skinDiyPositionInfoPB2 = skinDiyPositionInfoPB;
                                                                            stringBuilder2.append("scaledBackgroundBitmap positionPic:");
                                                                            stringBuilder2.append(skinDiyPositionMetaPB.positionPic);
                                                                            stringBuilder2.append(str4);
                                                                            stringBuilder2.append(str3);
                                                                            stringBuilder2.append(str5);
                                                                            stringBuilder2.append(i9);
                                                                            stringBuilder2.append(str8);
                                                                            stringBuilder2.append(i8);
                                                                            stringBuilder2.append(str14);
                                                                            stringBuilder2.append(i);
                                                                            traceLogger4.info(str7, stringBuilder2.toString());
                                                                        } else {
                                                                            skinDiyPositionInfoPB2 = skinDiyPositionInfoPB;
                                                                            j3 = drawable.L(j3, i9, i8, i, z6);
                                                                            traceLogger2 = LoggerFactory.getTraceLogger();
                                                                            StringBuilder stringBuilder5 = new StringBuilder();
                                                                            bitmap2 = j3;
                                                                            stringBuilder5.append("scaledBitmap positionPic:");
                                                                            stringBuilder5.append(skinDiyPositionMetaPB.positionPic);
                                                                            stringBuilder5.append(str4);
                                                                            stringBuilder5.append(str3);
                                                                            stringBuilder5.append(str5);
                                                                            stringBuilder5.append(i9);
                                                                            stringBuilder5.append(str8);
                                                                            stringBuilder5.append(i8);
                                                                            stringBuilder5.append(str14);
                                                                            stringBuilder5.append(i);
                                                                            traceLogger2.info(str7, stringBuilder5.toString());
                                                                        }
                                                                        bitmap2 = bitmap2;
                                                                        intValue = str11;
                                                                        z8 = z6;
                                                                    } catch (Throwable th2) {
                                                                        th = th2;
                                                                        E(str2, SystemClock.elapsedRealtime() - j2, 3);
                                                                        traceLogger = LoggerFactory.getTraceLogger();
                                                                        stringBuilder = new StringBuilder();
                                                                        stringBuilder.append("downloadDiyRes, merge pic error, sceneMeta:");
                                                                        stringBuilder.append(str11);
                                                                        traceLogger.error(str7, stringBuilder.toString(), th);
                                                                        L(str, 4, z2);
                                                                        z5 = false;
                                                                        return z5;
                                                                    }
                                                                }
                                                                skinDiyPositionInfoPB2 = skinDiyPositionInfoPB;
                                                                i3 = i5;
                                                                try {
                                                                    num = skinDiyPositionMetaPB.x;
                                                                    intValue2 = num != null ? num.intValue() : 0;
                                                                    num = skinDiyPositionMetaPB.y;
                                                                    i6 = num != null ? num.intValue() : 0;
                                                                    Bitmap bitmap3 = j3;
                                                                    intValue3 = i9;
                                                                    intValue = i8;
                                                                    z8 = z6;
                                                                    i7 = i;
                                                                    Bitmap bitmap4 = bitmap2;
                                                                    i8 = intValue3;
                                                                    int i11 = intValue;
                                                                    i5 = intValue2;
                                                                    i = i7;
                                                                    bitmap2 = drawable.H(bitmap2, bitmap3, intValue3, intValue, intValue2, i6, i7, z8);
                                                                    i2 = i6;
                                                                    traceLogger = LoggerFactory.getTraceLogger();
                                                                    bitmap = bitmap4;
                                                                    stringBuilder = new StringBuilder();
                                                                    intValue = str11;
                                                                    stringBuilder.append("mergeBitmap positionPic:");
                                                                    stringBuilder.append(skinDiyPositionMetaPB.positionPic);
                                                                    stringBuilder.append(str4);
                                                                    stringBuilder.append(str3);
                                                                    stringBuilder.append(str5);
                                                                    stringBuilder.append(i8);
                                                                    stringBuilder.append(str8);
                                                                    stringBuilder.append(i11);
                                                                    stringBuilder.append(" ,fgTargetX:");
                                                                    stringBuilder.append(i5);
                                                                    stringBuilder.append(" ,fgTargetY:");
                                                                    stringBuilder.append(i2);
                                                                    stringBuilder.append(str14);
                                                                    stringBuilder.append(i);
                                                                    traceLogger.info(str7, stringBuilder.toString());
                                                                    bitmap.recycle();
                                                                    bitmap3.recycle();
                                                                } catch (Throwable th3) {
                                                                    th = th3;
                                                                    str11 = intValue;
                                                                    E(str2, SystemClock.elapsedRealtime() - j2, 3);
                                                                    traceLogger = LoggerFactory.getTraceLogger();
                                                                    stringBuilder = new StringBuilder();
                                                                    stringBuilder.append("downloadDiyRes, merge pic error, sceneMeta:");
                                                                    stringBuilder.append(str11);
                                                                    traceLogger.error(str7, stringBuilder.toString(), th);
                                                                    L(str, 4, z2);
                                                                    z5 = false;
                                                                    return z5;
                                                                }
                                                                skinDiyMetaInfoPB4 = skinDiyMetaInfoPB;
                                                                z4 = z2;
                                                                str11 = intValue;
                                                                z6 = z8;
                                                                str5 = str;
                                                                str4 = str2;
                                                                i5 = i3;
                                                                skinDiyPositionInfoPB = skinDiyPositionInfoPB2;
                                                                it3 = it4;
                                                                charSequence = charSequence3;
                                                                str13 = str20;
                                                            }
                                                        } catch (Throwable th4) {
                                                            th = th4;
                                                            intValue = str11;
                                                            str2 = str4;
                                                            str = str5;
                                                            str11 = intValue;
                                                            E(str2, SystemClock.elapsedRealtime() - j2, 3);
                                                            traceLogger = LoggerFactory.getTraceLogger();
                                                            stringBuilder = new StringBuilder();
                                                            stringBuilder.append("downloadDiyRes, merge pic error, sceneMeta:");
                                                            stringBuilder.append(str11);
                                                            traceLogger.error(str7, stringBuilder.toString(), th);
                                                            L(str, 4, z2);
                                                            z5 = false;
                                                            return z5;
                                                        }
                                                    }
                                                    charSequence3 = charSequence;
                                                    intValue = str11;
                                                    z8 = z6;
                                                    str2 = str4;
                                                    i3 = i5;
                                                    str = str5;
                                                    bitmap = bitmap2;
                                                    str9 = str17;
                                                    str10 = str19;
                                                    str11 = intValue;
                                                    file = new File(k(str10, str9), str11);
                                                    Bitmap bitmap5 = bitmap;
                                                    drawable.e(file.getAbsolutePath(), bitmap5, !"false".equals(SCConfigUtil.f("skin_diy_pic_use_png")) ? CompressFormat.PNG : CompressFormat.JPEG, i3);
                                                    if (bitmap5 != null) {
                                                        bitmap5.recycle();
                                                    }
                                                    SkinDiyInfoModel skinDiyInfoModel2 = new SkinDiyInfoModel();
                                                    skinDiyInfoModel2.diyMetaType = charSequence3;
                                                    skinDiyInfoModel2.sceneMetaOpType = str18;
                                                    skinDiyInfoModel2.sceneMeta = str11;
                                                    skinDiyInfoModel2.diyPicFile = file;
                                                    arrayList3 = arrayList4;
                                                    arrayList3.add(skinDiyInfoModel2);
                                                    z6 = z2;
                                                    str4 = str;
                                                    str8 = str2;
                                                } catch (Throwable th5) {
                                                    th = th5;
                                                    str2 = str4;
                                                    str = str5;
                                                    E(str2, SystemClock.elapsedRealtime() - j2, 3);
                                                    traceLogger = LoggerFactory.getTraceLogger();
                                                    stringBuilder = new StringBuilder();
                                                    stringBuilder.append("downloadDiyRes, merge pic error, sceneMeta:");
                                                    stringBuilder.append(str11);
                                                    traceLogger.error(str7, stringBuilder.toString(), th);
                                                    L(str, 4, z2);
                                                    z5 = false;
                                                    return z5;
                                                }
                                            }
                                            charSequence2 = charSequence;
                                            str9 = str6;
                                            str10 = str3;
                                            z8 = z6;
                                            str6 = str12;
                                            str20 = str13;
                                            arrayList3 = arrayList4;
                                            z6 = z4;
                                            str8 = str4;
                                            str4 = str5;
                                            str5 = "scale";
                                            if (!TextUtils.equals("render", skinDiyPositionInfoPB.opType)) {
                                                if (TextUtils.equals(str5, skinDiyPositionInfoPB.opType)) {
                                                }
                                            }
                                            SkinDiyPositionMetaPB skinDiyPositionMetaPB2 = (SkinDiyPositionMetaPB) skinDiyPositionInfoPB.positionMetas.get(0);
                                            if (skinDiyPositionMetaPB2 == null) {
                                                it = it2;
                                                str6 = str9;
                                                arrayList2 = arrayList3;
                                                str5 = str4;
                                                elapsedRealtime = j2;
                                                z5 = true;
                                                str3 = str10;
                                                str4 = str8;
                                                z4 = z6;
                                                z6 = z8;
                                                skinDiyMetaInfoPB4 = skinDiyMetaInfoPB;
                                            } else {
                                                if (TextUtils.equals(charSequence2, skinDiyPositionMetaPB2.positionMetaType)) {
                                                    i = SCConfigUtil.i();
                                                    if (z8) {
                                                        i = SCConfigUtil.g();
                                                    }
                                                    arrayList4 = arrayList3;
                                                    TraceLogger traceLogger5 = LoggerFactory.getTraceLogger();
                                                    str18 = str6;
                                                    StringBuilder stringBuilder6 = new StringBuilder();
                                                    charSequence3 = charSequence2;
                                                    stringBuilder6.append("doOpNotNeedMerge, quality:");
                                                    stringBuilder6.append(i);
                                                    stringBuilder6.append(str16);
                                                    z7 = z8;
                                                    stringBuilder6.append(z7);
                                                    traceLogger5.error(str7, stringBuilder6.toString());
                                                    str6 = n(skinDiyPositionMetaPB2.positionPic, skinDiyMetaInfoPB.assetPool);
                                                    if (TextUtils.isEmpty(str6)) {
                                                        E(str8, SystemClock.elapsedRealtime() - j2, 1);
                                                        traceLogger2 = LoggerFactory.getTraceLogger();
                                                        StringBuilder stringBuilder7 = new StringBuilder();
                                                        stringBuilder7.append("downloadDiyRes, render not found pic for assetPoll ,positionPic:");
                                                        stringBuilder7.append(skinDiyPositionMetaPB2.positionPic);
                                                        stringBuilder7.append(str20);
                                                        stringBuilder7.append(str11);
                                                        traceLogger2.error(str7, stringBuilder7.toString());
                                                        L(str4, 2, z6);
                                                        z3 = true;
                                                        arrayList = arrayList4;
                                                    } else {
                                                        str14 = str20;
                                                        z3 = true;
                                                        try {
                                                            Bitmap i12 = i(str6);
                                                            if (i12 == null) {
                                                                try {
                                                                    str19 = str10;
                                                                    str17 = str9;
                                                                    E(str8, SystemClock.elapsedRealtime() - j2, 2);
                                                                    traceLogger2 = LoggerFactory.getTraceLogger();
                                                                    stringBuilder2 = new StringBuilder();
                                                                    stringBuilder2.append("downloadDiyRes, render download pic fail ,positionPic:");
                                                                    stringBuilder2.append(skinDiyPositionMetaPB2.positionPic);
                                                                    stringBuilder2.append(str14);
                                                                    stringBuilder2.append(str11);
                                                                    traceLogger2.error(str7, stringBuilder2.toString());
                                                                    L(str4, 3, z6);
                                                                    arrayList = arrayList4;
                                                                    str9 = str17;
                                                                    str10 = str19;
                                                                } catch (Throwable th6) {
                                                                    th = th6;
                                                                    arrayList = arrayList4;
                                                                    str9 = str17;
                                                                    str10 = str19;
                                                                    E(str8, SystemClock.elapsedRealtime() - j2, 3);
                                                                    traceLogger3 = LoggerFactory.getTraceLogger();
                                                                    stringBuilder4 = new StringBuilder();
                                                                    stringBuilder4.append("downloadDiyRes, render download pic error ,positionPic:");
                                                                    stringBuilder4.append(skinDiyPositionMetaPB2.positionPic);
                                                                    stringBuilder4.append(str14);
                                                                    stringBuilder4.append(str11);
                                                                    traceLogger3.error(str7, stringBuilder4.toString(), th);
                                                                    L(str4, 4, z6);
                                                                    i2 = z3;
                                                                    if (i2 != 0) {
                                                                    }
                                                                    skinDiyMetaInfoPB2 = skinDiyMetaInfoPB;
                                                                    skinDiyMetaInfoPB3 = skinDiyMetaInfoPB2;
                                                                    str3 = str10;
                                                                    skinDiyMetaInfoPB4 = skinDiyMetaInfoPB3;
                                                                    str11 = str4;
                                                                    str4 = str8;
                                                                    z4 = z6;
                                                                    z6 = z7;
                                                                    arrayList2 = arrayList;
                                                                    str5 = str11;
                                                                    it = it2;
                                                                    str6 = str9;
                                                                    z5 = z3;
                                                                    elapsedRealtime = j2;
                                                                }
                                                            } else {
                                                                str19 = str10;
                                                                str17 = str9;
                                                                if (TextUtils.equals(str5, skinDiyPositionInfoPB.opType)) {
                                                                    num = skinDiyPositionMetaPB2.transparency;
                                                                    i2 = (num == null || num.intValue() <= 0) ? 255 : (skinDiyPositionMetaPB2.transparency.intValue() / 100) * 255;
                                                                    i12 = drawable.L(i12, skinDiyPositionMetaPB2.scaleY.intValue(), skinDiyPositionMetaPB2.scaleX.intValue(), i2, z7);
                                                                }
                                                                str9 = str17;
                                                                str10 = str19;
                                                                file = new File(k(str10, str9), str11);
                                                                drawable.e(file.getAbsolutePath(), i12, CompressFormat.PNG, i);
                                                                if (i12 != null) {
                                                                    i12.recycle();
                                                                }
                                                                skinDiyInfoModel = new SkinDiyInfoModel();
                                                                skinDiyInfoModel.diyMetaType = charSequence3;
                                                                skinDiyInfoModel.sceneMetaOpType = str18;
                                                                skinDiyInfoModel.sceneMeta = str11;
                                                                skinDiyInfoModel.diyPicFile = file;
                                                                arrayList = arrayList4;
                                                                try {
                                                                    arrayList.add(skinDiyInfoModel);
                                                                } catch (Throwable th7) {
                                                                    th = th7;
                                                                }
                                                            }
                                                        } catch (Throwable th8) {
                                                            th = th8;
                                                            arrayList = arrayList4;
                                                            E(str8, SystemClock.elapsedRealtime() - j2, 3);
                                                            traceLogger3 = LoggerFactory.getTraceLogger();
                                                            stringBuilder4 = new StringBuilder();
                                                            stringBuilder4.append("downloadDiyRes, render download pic error ,positionPic:");
                                                            stringBuilder4.append(skinDiyPositionMetaPB2.positionPic);
                                                            stringBuilder4.append(str14);
                                                            stringBuilder4.append(str11);
                                                            traceLogger3.error(str7, stringBuilder4.toString(), th);
                                                            L(str4, 4, z6);
                                                            i2 = z3;
                                                            if (i2 != 0) {
                                                            }
                                                            skinDiyMetaInfoPB2 = skinDiyMetaInfoPB;
                                                            skinDiyMetaInfoPB3 = skinDiyMetaInfoPB2;
                                                            str3 = str10;
                                                            skinDiyMetaInfoPB4 = skinDiyMetaInfoPB3;
                                                            str11 = str4;
                                                            str4 = str8;
                                                            z4 = z6;
                                                            z6 = z7;
                                                            arrayList2 = arrayList;
                                                            str5 = str11;
                                                            it = it2;
                                                            str6 = str9;
                                                            z5 = z3;
                                                            elapsedRealtime = j2;
                                                        }
                                                    }
                                                    i2 = z3;
                                                    if (i2 != 0) {
                                                        z5 = false;
                                                        break;
                                                    }
                                                }
                                                arrayList = arrayList3;
                                                z7 = z8;
                                                z3 = true;
                                                i2 = 0;
                                                if (i2 != 0) {
                                                }
                                            }
                                            arrayList = arrayList3;
                                            z7 = z8;
                                            z3 = true;
                                        } else {
                                            boolean z9 = z6;
                                            z6 = z4;
                                            str8 = str4;
                                            str4 = str5;
                                            arrayList = arrayList2;
                                            z7 = z9;
                                            str9 = str6;
                                            str10 = str3;
                                            str3 = str12;
                                            str15 = str14;
                                            z3 = true;
                                            if ("replaceMeta".equals(str3) || "replaceMetaResource".equals(str3)) {
                                                SkinDiyPositionMetaPB skinDiyPositionMetaPB3 = (SkinDiyPositionMetaPB) skinDiyPositionInfoPB.positionMetas.get(0);
                                                if (skinDiyPositionMetaPB3 != null) {
                                                    skinDiyInfoModel = new SkinDiyInfoModel();
                                                    skinDiyInfoModel.diyMetaType = str15;
                                                    skinDiyInfoModel.sceneMetaPosition = str13;
                                                    skinDiyInfoModel.sceneMetaOpType = str3;
                                                    skinDiyInfoModel.sceneMeta = str11;
                                                    skinDiyMetaInfoPB2 = skinDiyMetaInfoPB;
                                                    skinDiyInfoModel.startColor = n(skinDiyPositionMetaPB3.positionStartColor, skinDiyMetaInfoPB2.assetPool);
                                                    skinDiyInfoModel.endColor = n(skinDiyPositionMetaPB3.positionEndColor, skinDiyMetaInfoPB2.assetPool);
                                                    Integer num3 = skinDiyPositionMetaPB3.angle;
                                                    skinDiyInfoModel.gradient = num3 == null ? 0 : num3.intValue();
                                                    skinDiyInfoModel.transparencyList = skinDiyPositionMetaPB3.transparencyList;
                                                    arrayList.add(skinDiyInfoModel);
                                                    skinDiyMetaInfoPB3 = skinDiyMetaInfoPB2;
                                                    str3 = str10;
                                                    skinDiyMetaInfoPB4 = skinDiyMetaInfoPB3;
                                                    str11 = str4;
                                                    str4 = str8;
                                                    z4 = z6;
                                                    z6 = z7;
                                                    arrayList2 = arrayList;
                                                    str5 = str11;
                                                    it = it2;
                                                    str6 = str9;
                                                    z5 = z3;
                                                    elapsedRealtime = j2;
                                                }
                                            }
                                        }
                                        skinDiyMetaInfoPB2 = skinDiyMetaInfoPB;
                                        skinDiyMetaInfoPB3 = skinDiyMetaInfoPB2;
                                        str3 = str10;
                                        skinDiyMetaInfoPB4 = skinDiyMetaInfoPB3;
                                        str11 = str4;
                                        str4 = str8;
                                        z4 = z6;
                                        z6 = z7;
                                        arrayList2 = arrayList;
                                        str5 = str11;
                                        it = it2;
                                        str6 = str9;
                                        z5 = z3;
                                        elapsedRealtime = j2;
                                    }
                                }
                            }
                            str9 = str3;
                            skinDiyMetaInfoPB2 = skinDiyMetaInfoPB4;
                            str10 = str9;
                            boolean z10 = z6;
                            z6 = z4;
                            str8 = str4;
                            str4 = str5;
                            arrayList = arrayList2;
                            z7 = z10;
                            str9 = str6;
                            z3 = true;
                            skinDiyMetaInfoPB3 = skinDiyMetaInfoPB2;
                            str3 = str10;
                            skinDiyMetaInfoPB4 = skinDiyMetaInfoPB3;
                            str11 = str4;
                            str4 = str8;
                            z4 = z6;
                            z6 = z7;
                            arrayList2 = arrayList;
                            str5 = str11;
                            it = it2;
                            str6 = str9;
                            z5 = z3;
                            elapsedRealtime = j2;
                        }
                        it = it2;
                        elapsedRealtime = j2;
                        z5 = true;
                    }
                    z3 = z5;
                    str8 = str4;
                    j2 = elapsedRealtime;
                    map.put(str8, arrayList2);
                    E(str8, SystemClock.elapsedRealtime() - j2, 0);
                    traceLogger2 = LoggerFactory.getTraceLogger();
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("downloadDiyRes success scene:");
                    stringBuilder2.append(str8);
                    traceLogger2.info(str7, stringBuilder2.toString());
                }
                z5 = z3;
                return z5;
            }
        }
        return true;
    }

    public SCInnerManager() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            ConstructorCode proxy = PatchProxy.proxy(changeQuickRedirect, "1");
            if (proxy != null) {
                proxy.afterSuper(this);
                return;
            }
        }
        this.c = new AtomicBoolean(false);
        this.d = new ConcurrentHashMap();
        this.g = new ConcurrentHashMap();
        this.h = new ConcurrentHashMap();
        this.i = new ConcurrentHashMap();
        this.l = false;
        this.k = new APTripPassSkinImpl();
        SettingsManager.getInstance().registerSettingsChangeListener(new d(this));
        AUTokenManager.registerUiModeChange(new b(this));
    }

    public void b() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(this, changeQuickRedirect, "16", Void.TYPE).isSupported) {
            this.c.set(false);
            this.d.clear();
            SCSkinStatusUtil.a.clear();
            this.g.clear();
            this.h.clear();
            SkinImageCacheManager a = SkinImageCacheManager.a();
            a.b.trimToSize(0);
            a.b = a.b();
            LoggerFactory.getTraceLogger().info("SkinCenterManager", "clear meta cache");
        }
    }

    public void G(String str) {
        SCInnerManager sCInnerManager;
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            str2 = str;
            if (PatchProxy.proxy(str2, sCInnerManager, changeQuickRedirect, "8", String.class, Void.TYPE).isSupported) {
                return;
            }
        }
        sCInnerManager = this;
        str2 = str;
        LoggerFactory.getTraceLogger().info("SkinCenterManager", "notifyThemeSkin");
        String str3 = "theme";
        str = r(str3, null);
        String v = v(str3);
        if (TextUtils.isEmpty(str2)) {
            if (!sCInnerManager.c.get()) {
                K();
            }
            SCCacheInfoModel j = j(str3);
            str2 = j == null ? "" : j.materialId;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        g gVar = new g(this, str, v, str2);
        DexAOPEntry.java_lang_Runnable_newInstance_Created(gVar);
        if (SCConfigUtil.d()) {
            DexAOPEntry.hanlerPostAtFrontOfQueueProxy(handler, gVar);
        } else {
            DexAOPEntry.hanlerPostProxy(handler, gVar);
        }
    }

    public List<ResourceInfoPB> d(Map<String, ResourceInfoPB> map) {
        Map map2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            map2 = map;
            PatchProxyResult proxy = PatchProxy.proxy(map2, this, changeQuickRedirect, "18", Map.class, List.class);
            if (proxy.isSupported) {
                return (List) proxy.result;
            }
        }
        map2 = map;
        ArrayList arrayList = new ArrayList();
        for (String str : map2.keySet()) {
            arrayList.add((ResourceInfoPB) map2.get(str));
        }
        return arrayList;
    }

    public SCCacheInfoModel j(String str) {
        SCInnerManager sCInnerManager;
        CharSequence charSequence;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            charSequence = str;
            PatchProxyResult proxy = PatchProxy.proxy(charSequence, sCInnerManager, changeQuickRedirect, "24", String.class, SCCacheInfoModel.class);
            if (proxy.isSupported) {
                return (SCCacheInfoModel) proxy.result;
            }
        }
        sCInnerManager = this;
        charSequence = str;
        if (TextUtils.isEmpty(charSequence)) {
            return null;
        }
        Object obj = (!TextUtils.equals(charSequence, "ltp_cashier") || SCConfigUtil.a()) ? charSequence : "ltp";
        return (SCCacheInfoModel) sCInnerManager.g.get(obj);
    }

    public String t(String str) {
        SCInnerManager sCInnerManager;
        Object obj;
        CharSequence charSequence;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            obj = str;
            PatchProxyResult proxy = PatchProxy.proxy(obj, sCInnerManager, changeQuickRedirect, "34", String.class, String.class);
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        sCInnerManager = this;
        obj = str;
        String str2 = "";
        if (sCInnerManager.d.containsKey(obj)) {
            charSequence = (String) sCInnerManager.d.get(obj);
        } else {
            charSequence = SharedPreferences.getString("cached_skin_theme", obj);
            if (charSequence == null) {
                charSequence = str2;
            }
            sCInnerManager.d.put(obj, charSequence);
        }
        return TextUtils.equals(charSequence, "DEFAULT") ? str2 : charSequence;
    }

    public File u(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, this, changeQuickRedirect, "35", String.class, File.class);
            if (proxy.isSupported) {
                return (File) proxy.result;
            }
        }
        str2 = str;
        File file = new File(LauncherApplicationAgent.getInstance().getFilesDir(), "skin_center_dir");
        if (TextUtils.isEmpty(str2)) {
            str2 = SCCommonUtil.getCurrentUserId();
        }
        File file2 = new File(file, str2);
        F(file2);
        return file2;
    }

    public void a(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, Void.TYPE}, this, changeQuickRedirect, "15").isSupported) {
                return;
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (!TextUtils.isEmpty(str2)) {
                o(str, null, true).changeInteractiveRes(str2);
            }
        }
    }

    public String p(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, String.class}, this, changeQuickRedirect, "30");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (!TextUtils.isEmpty(str2)) {
                return o(str, null, true).getResTypeSync(str2);
            }
        }
        return null;
    }

    public static SCInnerManager m() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "27", SCInnerManager.class);
            if (proxy.isSupported) {
                return (SCInnerManager) proxy.result;
            }
        }
        if (b == null) {
            synchronized (SCInnerManager.class) {
                try {
                    if (b == null) {
                        b = new SCInnerManager();
                    }
                } finally {
                }
            }
        }
        return b;
    }

    public void H() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(this, changeQuickRedirect, "9", Void.TYPE).isSupported) {
            if (!this.c.get()) {
                K();
            }
            if (y("theme", null)) {
                G(null);
            } else {
                I();
            }
        }
    }

    public void I() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(this, changeQuickRedirect, "10", Void.TYPE).isSupported) {
            LoggerFactory.getTraceLogger().info("SkinCenterManager", "notifyThemeSkinDefault");
            Handler handler = new Handler(Looper.getMainLooper());
            f fVar = new f(this);
            DexAOPEntry.java_lang_Runnable_newInstance_Created(fVar);
            if (SCConfigUtil.d()) {
                DexAOPEntry.hanlerPostAtFrontOfQueueProxy(handler, fVar);
            } else {
                DexAOPEntry.hanlerPostProxy(handler, fVar);
            }
        }
    }

    public String v(String str) {
        SCInnerManager sCInnerManager;
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            sCInnerManager = this;
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, sCInnerManager, changeQuickRedirect, "36", String.class, String.class);
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        sCInnerManager = this;
        str2 = str;
        if (!sCInnerManager.c.get()) {
            K();
        }
        SCCacheInfoModel j = j(str2);
        return j == null ? "" : j.userSkinId;
    }

    public void F(File file) {
        File file2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            file2 = file;
            if (PatchProxy.proxy(file2, this, changeQuickRedirect, "7", File.class, Void.TYPE).isSupported) {
                return;
            }
        }
        file2 = file;
        if (!file2.exists()) {
            file2.mkdirs();
        }
    }

    public boolean x(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, this, changeQuickRedirect, "38", String.class, Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        str2 = str;
        return y(str2, null);
    }

    static {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("tmp");
        stringBuilder.append(File.separator);
        stringBuilder.append("diy");
        a = stringBuilder.toString();
    }

    public void J() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(this, changeQuickRedirect, "11", Void.TYPE).isSupported) {
            ScheduleType scheduleType = ScheduleType.RPC;
            String str = "";
            e eVar = new e(this, str, str);
            DexAOPEntry.java_lang_Runnable_newInstance_Created(eVar);
            SCCommonUtil.backgroundExecute(scheduleType, eVar);
        }
    }
}