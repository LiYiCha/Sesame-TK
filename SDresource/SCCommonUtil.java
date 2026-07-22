//
// Decompiled by Jadx (from NP Manager)
//
package com.alipay.mobile.skincenter.util;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Keep;
import android.text.TextUtils;
import com.alipay.dexaop.DexAOPEntry;
import com.alipay.instantrun.ChangeQuickRedirect;
import com.alipay.instantrun.ConstructorCode;
import com.alipay.instantrun.PatchProxy;
import com.alipay.instantrun.PatchProxyResult;
import com.alipay.mobile.common.logging.api.LoggerFactory;
import com.alipay.mobile.common.logging.api.trace.TraceLogger;
import com.alipay.mobile.framework.AlipayApplication;
import com.alipay.mobile.framework.LauncherApplicationAgent;
import com.alipay.mobile.framework.MicroApplicationContext;
import com.alipay.mobile.framework.MpaasClassInfo;
import com.alipay.mobile.framework.locale.LocaleHelper;
import com.alipay.mobile.framework.pipeline.TaskControlManager;
import com.alipay.mobile.framework.service.common.SchemeService;
import com.alipay.mobile.framework.service.common.TaskScheduleService;
import com.alipay.mobile.framework.service.common.TaskScheduleService.ScheduleType;
import com.alipay.mobile.framework.service.ext.security.AccountService;
import com.alipay.mobile.framework.settings.SettingsManager;
import com.alipay.mobile.skincenter.rpc.SkinDiyTransformPB;
import com.alipay.mobile.skincenter.rpc.SkinSceneInfoPB;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

@Keep
@MpaasClassInfo(BundleName = "android-phone-wallet-skincenter", ExportJarName = "unknown", Level = "product", Product = ":android-phone-wallet-skincenter")
public class SCCommonUtil {
    private static final String SC_PREFS_FILE = "prefs_skincenter_file.txt";
    private static AccountService accountService;
    private static Application application;
    private static List<String> clientSupportScenes;
    private static boolean hasSetSkin;
    private static MicroApplicationContext microApplicationContext;
    private static List<String> noNeedSetScenes;
    private static List<String> otherSupportScenes;
    private static String sCurrentVersionName;
    private static TaskScheduleService taskScheduleService;
    /* renamed from: 支 */
    public static ChangeQuickRedirect f0;

    public static int compareWalletVersion(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, Integer.TYPE}, null, changeQuickRedirect, "3");
            if (proxy.isSupported) {
                return ((Integer) proxy.result).intValue();
            }
        }
        String str3 = "\\.";
        try {
            if (!TextUtils.isEmpty(str2)) {
                String[] split = str.split(str3);
                String[] split2 = str2.split(str3);
                int min = Math.min(split.length, split2.length);
                int i = 0;
                while (i < min) {
                    int parseInt = Integer.parseInt(split[i]) - Integer.parseInt(split2[i]);
                    if (parseInt > 0) {
                        break;
                    } else if (parseInt < 0) {
                        break;
                    } else {
                        i++;
                    }
                }
                if (i >= split.length) {
                    return i < split2.length ? -1 : 0;
                }
            }
            return 1;
        } catch (Throwable th) {
            LoggerFactory.getTraceLogger().error("version compare failed", th);
            return -1;
        }
    }

    public static String getSceneName(String str, List<SkinSceneInfoPB> list) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, list, List.class, String.class}, null, changeQuickRedirect, "14");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (list != null) {
                for (SkinSceneInfoPB skinSceneInfoPB : list) {
                    if (TextUtils.equals(str, skinSceneInfoPB.scene)) {
                        return skinSceneInfoPB.sceneName;
                    }
                }
            }
        }
        return "";
    }

    public static SkinDiyTransformPB getSkinDiyTransform(List<SkinDiyTransformPB> list, String str) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{list, List.class, str, String.class, SkinDiyTransformPB.class}, null, changeQuickRedirect, "15");
            if (proxy.isSupported) {
                return (SkinDiyTransformPB) proxy.result;
            }
        }
        if (list != null) {
            if (list.size() != 0) {
                for (SkinDiyTransformPB skinDiyTransformPB : list) {
                    if (TextUtils.equals(str, skinDiyTransformPB.scene)) {
                        return skinDiyTransformPB;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isCanUpgradeForScene(String str, List<SkinSceneInfoPB> list) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, list, List.class, Boolean.TYPE}, null, changeQuickRedirect, "24");
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        if (!TextUtils.isEmpty(str)) {
            if (list != null) {
                for (SkinSceneInfoPB skinSceneInfoPB : list) {
                    if (TextUtils.equals(str, skinSceneInfoPB.scene)) {
                        Boolean bool = skinSceneInfoPB.canUpgrade;
                        if (!(bool == null || skinSceneInfoPB.support == null)) {
                            if (bool.booleanValue() && !skinSceneInfoPB.support.booleanValue()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void putInt(String str, int i) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, Integer.valueOf(i), Integer.TYPE, Void.TYPE}, null, changeQuickRedirect, "31").isSupported) {
                return;
            }
        }
        Editor edit = DexAOPEntry.android_content_Context_getSharedPreferences_ANTSP_proxy(getApplication(), SC_PREFS_FILE, 0).edit();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(str);
        stringBuilder.append(getCurrentUserId());
        edit.putInt(stringBuilder.toString(), i).apply();
    }

    public static String currentVersionName() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "5", String.class);
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (TextUtils.isEmpty(sCurrentVersionName)) {
            Application application = getApplication();
            try {
                sCurrentVersionName = application.getPackageManager().getPackageInfo(application.getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                LoggerFactory.getTraceLogger().error("failed to read wallet info", e);
                sCurrentVersionName = "0.0.0.0000";
            }
        }
        return sCurrentVersionName;
    }

    public static void initScenes() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null && PatchProxy.proxy(null, changeQuickRedirect, "22", Void.TYPE).isSupported) {
            return;
        }
        if (clientSupportScenes == null || otherSupportScenes == null) {
            ArrayList arrayList = new ArrayList();
            clientSupportScenes = arrayList;
            arrayList.add("ltp");
            clientSupportScenes.add("theme");
            clientSupportScenes.add("avatarFrame");
            clientSupportScenes.add("aptrip");
            arrayList = new ArrayList();
            otherSupportScenes = arrayList;
            arrayList.add("widget");
            otherSupportScenes.add("redpack");
            otherSupportScenes.add("emoji");
        }
    }

    public static boolean internationalNewStyleRollback() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "23", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        String str = "SCConfigUtil";
        if (SCConfigUtil.i == null) {
            try {
                String string = PreferenceManager.getDefaultSharedPreferences(LoggerFactory.getLogContext().getApplicationContext()).getString("international_new_style_rollback", "0");
                TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("international_new_style_rollback get config:");
                stringBuilder.append(string);
                traceLogger.info(str, stringBuilder.toString());
                SCConfigUtil.i = new AtomicBoolean("1".equals(string));
            } catch (Throwable th) {
                SCConfigUtil.i = new AtomicBoolean(false);
                LoggerFactory.getTraceLogger().error(str, "international_new_style_rollback get config error", th);
            }
        }
        return SCConfigUtil.i.get();
    }

    public static boolean isINT() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "25", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        if (!internationalNewStyleRollback()) {
            AccountService accountService = (AccountService) AlipayApplication.getInstance().getMicroApplicationContext().findServiceByInterface(AccountService.class.getName());
            if (accountService != null) {
                return TextUtils.equals(accountService.getCurRegion(), "INT");
            }
        }
        return false;
    }

    public static boolean isSeniorsVersion() {
        boolean isAppMode;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "28", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        String str = "SCCommonUtil";
        try {
            isAppMode = SettingsManager.getInstance().getSettings().isAppMode("bigFontSize");
        } catch (Throwable th) {
            LoggerFactory.getTraceLogger().error(str, "get isSeniorsVersion error", th);
            isAppMode = false;
        }
        TraceLogger traceLogger = LoggerFactory.getTraceLogger();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("isSeniorsVersion: ");
        stringBuilder.append(isAppMode);
        traceLogger.info(str, stringBuilder.toString());
        return isAppMode;
    }

    public static boolean equalWalletVersionThird(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, null, changeQuickRedirect, "6", String.class, Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        str2 = str;
        String[] split = str2.split(",");
        String currentVersionName = currentVersionName();
        int i = 0;
        if (TextUtils.isEmpty(currentVersionName)) {
            return false;
        }
        boolean startsWith = currentVersionName.startsWith(str2);
        if (!(startsWith || split == null || split.length <= 0)) {
            int length = split.length;
            while (i < length) {
                if (currentVersionName.startsWith(split[i])) {
                    return true;
                }
                i++;
            }
        }
        return startsWith;
    }

    public static int getInt(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, null, changeQuickRedirect, "10", String.class, Integer.TYPE);
            if (proxy.isSupported) {
                return ((Integer) proxy.result).intValue();
            }
        }
        str2 = str;
        SharedPreferences android_content_Context_getSharedPreferences_ANTSP_proxy = DexAOPEntry.android_content_Context_getSharedPreferences_ANTSP_proxy(getApplication(), SC_PREFS_FILE, 0);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(str2);
        stringBuilder.append(getCurrentUserId());
        return android_content_Context_getSharedPreferences_ANTSP_proxy.getInt(stringBuilder.toString(), 0);
    }

    public static boolean nativeJump(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, null, changeQuickRedirect, "29", String.class, Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        str2 = str;
        return ((SchemeService) getMicroApplicationContext().findServiceByInterface(SchemeService.class.getName())).process(Uri.parse(str2)) == 0;
    }

    public static void processUrl(String str) {
        CharSequence charSequence;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            charSequence = str;
            if (PatchProxy.proxy(charSequence, null, changeQuickRedirect, "30", String.class, Void.TYPE).isSupported) {
                return;
            }
        }
        charSequence = str;
        if (!TextUtils.isEmpty(charSequence)) {
            if (charSequence.toLowerCase().startsWith("http")) {
                Bundle bundle = new Bundle();
                bundle.putString("url", charSequence);
                getMicroApplicationContext().startApp("20002084", "20000067", bundle);
                return;
            }
            nativeJump(charSequence);
        }
    }

    public static Application getApplication() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "7", Application.class);
            if (proxy.isSupported) {
                return (Application) proxy.result;
            }
        }
        if (application == null) {
            application = LauncherApplicationAgent.getInstance().getApplicationContext();
        }
        return application;
    }

    public static String getCurrentUserId() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "9", String.class);
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (accountService == null) {
            accountService = (AccountService) getMicroApplicationContext().findServiceByInterface(AccountService.class.getName());
        }
        AccountService accountService = accountService;
        return accountService != null ? accountService.getCurrentLoginUserId() : "";
    }

    public static MicroApplicationContext getMicroApplicationContext() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "11", MicroApplicationContext.class);
            if (proxy.isSupported) {
                return (MicroApplicationContext) proxy.result;
            }
        }
        if (microApplicationContext == null) {
            microApplicationContext = LauncherApplicationAgent.getInstance().getMicroApplicationContext();
        }
        return microApplicationContext;
    }

    public static boolean hasSetSkin() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "19", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        if (!hasSetSkin) {
            return false;
        }
        hasSetSkin = false;
        return true;
    }

    public static void initNoNeedSetScenes() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if ((changeQuickRedirect == null || !PatchProxy.proxy(null, changeQuickRedirect, "21", Void.TYPE).isSupported) && noNeedSetScenes == null) {
            ArrayList arrayList = new ArrayList();
            noNeedSetScenes = arrayList;
            arrayList.add("redpack");
            noNeedSetScenes.add("emoji");
        }
    }

    public static boolean isLanguageEnglish() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "26", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        return 4 == LocaleHelper.getInstance().getCurrentLanguage();
    }

    public static boolean containsNoNeedSetScenes(String str) {
        Object obj;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            obj = str;
            PatchProxyResult proxy = PatchProxy.proxy(obj, null, changeQuickRedirect, "4", String.class, Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        obj = str;
        return getNoNeedSetScenes().contains(obj);
    }

    public SCCommonUtil() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            ConstructorCode proxy = PatchProxy.proxy(changeQuickRedirect, "0");
            if (proxy != null) {
                proxy.afterSuper(this);
            }
        }
    }

    public static List<String> getClientSupportScenes() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "8", List.class);
            if (proxy.isSupported) {
                return (List) proxy.result;
            }
        }
        initScenes();
        return clientSupportScenes;
    }

    public static List<String> getNoNeedSetScenes() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "12", List.class);
            if (proxy.isSupported) {
                return (List) proxy.result;
            }
        }
        initNoNeedSetScenes();
        return noNeedSetScenes;
    }

    public static List<String> getOtherSupportScenes() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "13", List.class);
            if (proxy.isSupported) {
                return (List) proxy.result;
            }
        }
        initScenes();
        return otherSupportScenes;
    }

    public static boolean hasSetSkinRaw() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(null, changeQuickRedirect, "20", Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        return hasSetSkin;
    }

    public static void tagSetSkin() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(null, changeQuickRedirect, "34", Void.TYPE).isSupported) {
            hasSetSkin = true;
        }
    }

    public static void backgroundExecute(ScheduleType scheduleType, Runnable runnable) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{scheduleType, ScheduleType.class, runnable, Runnable.class, Void.TYPE}, null, changeQuickRedirect, "1").isSupported) {
                return;
            }
        }
        try {
            if (taskScheduleService == null) {
                taskScheduleService = (TaskScheduleService) getMicroApplicationContext().findServiceByInterface(TaskScheduleService.class.getName());
            }
            ThreadPoolExecutor acquireExecutor = taskScheduleService.acquireExecutor(scheduleType);
            TaskControlManager.getInstance().start();
            DexAOPEntry.executorExecuteProxy(acquireExecutor, runnable);
            TaskControlManager.getInstance().end();
        } catch (Throwable th) {
            LoggerFactory.getTraceLogger().error("SCCommonUtil", "backgroundExecute error", th);
        }
    }

    public static String getString(String str, String str2, String str3) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, str3, String.class, String.class}, null, changeQuickRedirect, "18");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        if (TextUtils.isEmpty(str3)) {
            str3 = getCurrentUserId();
        }
        SharedPreferences android_content_Context_getSharedPreferences_ANTSP_proxy = DexAOPEntry.android_content_Context_getSharedPreferences_ANTSP_proxy(getApplication(), SC_PREFS_FILE, 0);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(str2);
        stringBuilder.append(str3);
        return android_content_Context_getSharedPreferences_ANTSP_proxy.getString(stringBuilder.toString(), null);
    }

    public static void putString(String str, String str2, String str3) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, str3, String.class, Void.TYPE}, null, changeQuickRedirect, "33").isSupported) {
                return;
            }
        }
        Editor edit = DexAOPEntry.android_content_Context_getSharedPreferences_ANTSP_proxy(getApplication(), SC_PREFS_FILE, 0).edit();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(str);
        stringBuilder.append(str2);
        edit.putString(stringBuilder.toString(), str3).apply();
    }

    public static void backgroundExecute(Runnable runnable) {
        Runnable runnable2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            runnable2 = runnable;
            if (PatchProxy.proxy(runnable2, null, changeQuickRedirect, "2", Runnable.class, Void.TYPE).isSupported) {
                return;
            }
        }
        runnable2 = runnable;
        backgroundExecute(ScheduleType.URGENT, runnable2);
    }

    public static String getString(String str) {
        String str2;
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            str2 = str;
            PatchProxyResult proxy = PatchProxy.proxy(str2, null, changeQuickRedirect, "16", String.class, String.class);
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        str2 = str;
        return getString(str2, null);
    }

    public static String getString(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, String.class}, null, changeQuickRedirect, "17");
            if (proxy.isSupported) {
                return (String) proxy.result;
            }
        }
        return getString(SC_PREFS_FILE, str, str2);
    }

    public static void putString(String str, String str2) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{str, String.class, str2, String.class, Void.TYPE}, null, changeQuickRedirect, "32").isSupported) {
                return;
            }
        }
        putString(str, getCurrentUserId(), str2);
    }

    public static boolean isNetworkError(int i) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            PatchProxyResult proxy = PatchProxy.proxy(Integer.valueOf(i), null, changeQuickRedirect, "27", Integer.TYPE, Boolean.TYPE);
            if (proxy.isSupported) {
                return ((Boolean) proxy.result).booleanValue();
            }
        }
        if (!(i == 0 || i == 1 || i == 2 || i == 13 || i == 46 || i == 15 || i == 16)) {
            switch (i) {
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                    break;
                default:
                    return false;
            }
        }
        return true;
    }
}