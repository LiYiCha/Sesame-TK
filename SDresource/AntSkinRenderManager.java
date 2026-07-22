//
// Decompiled by Jadx (from NP Manager)
//
package com.alipay.mobile.skincenter.manage;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.alipay.instantrun.ChangeQuickRedirect;
import com.alipay.instantrun.ConstructorCode;
import com.alipay.instantrun.PatchProxy;
import com.alipay.mobile.common.logging.api.LoggerFactory;
import com.alipay.mobile.common.logging.api.trace.TraceLogger;
import com.alipay.mobile.framework.MpaasClassInfo;
import com.alipay.mobile.skincenter.api.R.drawable;
import com.alipay.mobile.skincenter.basic.AntSkinRenderProtocol;
import com.alipay.mobile.skincenter.basic.AntSkinStyle;
import com.alipay.mobile.skincenter.util.SCSkinStatusUtil;
import com.alipay.mobile.skincenter.view.AUSkinLottieView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@MpaasClassInfo(BundleName = "android-phone-wallet-skincenter", ExportJarName = "unknown", Level = "product", Product = ":android-phone-wallet-skincenter")
public class AntSkinRenderManager {
    public static final Map<WeakReference<AntSkinRenderProtocol>, SkinRenderToken> a = new ConcurrentHashMap();
    /* renamed from: 支 */
    public static ChangeQuickRedirect f0;

    public static void notifySkinDefault() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(null, changeQuickRedirect, "6", Void.TYPE).isSupported) {
            String str = "AntSkinRenderManager";
            LoggerFactory.getTraceLogger().info(str, "notifySkinDefault start");
            ArrayList arrayList = new ArrayList();
            for (Entry entry : a.entrySet()) {
                WeakReference weakReference = (WeakReference) entry.getKey();
                SkinRenderToken skinRenderToken = (SkinRenderToken) entry.getValue();
                if (weakReference.get() != null) {
                    try {
                        TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("notifySkinDefault, end renderWithDefaultStyle token:");
                        stringBuilder.append(skinRenderToken.b.token);
                        traceLogger.info(str, stringBuilder.toString());
                        ((AntSkinRenderProtocol) weakReference.get()).renderWithDefaultStyle(false);
                    } catch (Throwable th) {
                        LoggerFactory.getTraceLogger().error(str, "notifySkinDefault, renderWithDefaultStyle error", th);
                    }
                } else {
                    arrayList.add(weakReference);
                }
            }
            LoggerFactory.getTraceLogger().info(str, "notifySkinDefault end");
            a(arrayList);
        }
    }

    public static void setSkinStyle(View view, AntSkinStyle antSkinStyle) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{view, View.class, antSkinStyle, AntSkinStyle.class, Void.TYPE}, null, changeQuickRedirect, "7").isSupported) {
                return;
            }
        }
        if (view != null) {
            if (antSkinStyle != null) {
                if (view instanceof ImageView) {
                    ImageView imageView = (ImageView) view;
                    Drawable drawable = antSkinStyle.bgDrawable;
                    if (drawable != null) {
                        imageView.setImageDrawable(drawable);
                    }
                    drawable.M(imageView, antSkinStyle);
                } else if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    textView.setTextColor(new ColorStateList(new int[][]{new int[]{16842913}, new int[0]}, new int[]{antSkinStyle.selectedColor, antSkinStyle.color}));
                    drawable.M(textView, antSkinStyle);
                } else if (view instanceof AUSkinLottieView) {
                    AUSkinLottieView aUSkinLottieView = (AUSkinLottieView) view;
                    aUSkinLottieView.setPath(antSkinStyle);
                    drawable.M(aUSkinLottieView, antSkinStyle);
                } else {
                    drawable.M(view, antSkinStyle);
                }
            }
        }
    }

    public static void notifySkinChanged() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect == null || !PatchProxy.proxy(null, changeQuickRedirect, "5", Void.TYPE).isSupported) {
            String str = "AntSkinRenderManager";
            LoggerFactory.getTraceLogger().info(str, "notifySkinChanged start");
            ArrayList arrayList = new ArrayList();
            for (Entry entry : a.entrySet()) {
                WeakReference weakReference = (WeakReference) entry.getKey();
                AntSkinRenderProtocol antSkinRenderProtocol = (AntSkinRenderProtocol) weakReference.get();
                if (antSkinRenderProtocol != null) {
                    SkinRenderToken skinRenderToken = (SkinRenderToken) entry.getValue();
                    SkinStyleHelper.getSkinStyle((Context) skinRenderToken.a.get(), skinRenderToken.b, new b(skinRenderToken, antSkinRenderProtocol));
                } else {
                    arrayList.add(weakReference);
                }
            }
            LoggerFactory.getTraceLogger().info(str, "notifySkinChanged end");
            a(arrayList);
        }
    }

    public static void a(List<WeakReference<AntSkinRenderProtocol>> list) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        List list2;
        if (changeQuickRedirect != null) {
            list2 = list;
            if (PatchProxy.proxy(list2, null, changeQuickRedirect, "2", List.class, Void.TYPE).isSupported) {
                return;
            }
        }
        list2 = list;
        if (list2.size() != 0) {
            for (WeakReference remove : list2) {
                a.remove(remove);
            }
        }
    }

    public AntSkinRenderManager() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            ConstructorCode proxy = PatchProxy.proxy(changeQuickRedirect, "1");
            if (proxy != null) {
                proxy.afterSuper(this);
            }
        }
    }

    public static void bindSkinToken(Context context, String str, SkinToken skinToken, AntSkinRenderProtocol antSkinRenderProtocol, boolean z) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{context, Context.class, str, String.class, skinToken, SkinToken.class, antSkinRenderProtocol, AntSkinRenderProtocol.class, Boolean.valueOf(z), Boolean.TYPE, Void.TYPE}, null, changeQuickRedirect, "4").isSupported) {
                return;
            }
        }
        String str2 = "AntSkinRenderManager";
        if (antSkinRenderProtocol != null) {
            if (skinToken != null) {
                Boolean bool = (Boolean) SCSkinStatusUtil.a.get(str);
                Object obj = null;
                boolean booleanValue = bool == null ? false : bool.booleanValue();
                TraceLogger traceLogger = LoggerFactory.getTraceLogger();
                StringBuilder stringBuilder = new StringBuilder();
                String str3 = "bindSkinToken, start scene:";
                stringBuilder.append(str3);
                stringBuilder.append(str);
                String str4 = " , token:";
                stringBuilder.append(str4);
                stringBuilder.append(skinToken.token);
                stringBuilder.append(" , isSkinInitFinished:");
                stringBuilder.append(booleanValue);
                traceLogger.info(str2, stringBuilder.toString());
                a aVar = new a(str, skinToken, antSkinRenderProtocol);
                if (z) {
                    z = SCInnerManager.m().y(str, null);
                    if (!z) {
                        aVar.a(null, Boolean.FALSE);
                    } else if (booleanValue) {
                        String p = SCInnerManager.m().p(str, skinToken.token);
                        if ("image".equals(p) || "lottie".equals(p) || "video".equals(p) || "lottieVideo".equals(p)) {
                            obj = 1;
                        }
                        TraceLogger traceLogger2 = LoggerFactory.getTraceLogger();
                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append(str3);
                        stringBuilder2.append(str);
                        stringBuilder2.append(str4);
                        stringBuilder2.append(skinToken.token);
                        stringBuilder2.append(" , resType:");
                        stringBuilder2.append(p);
                        traceLogger2.info(str2, stringBuilder2.toString());
                        if (obj != null) {
                            SkinStyleHelper.getSkinStyle(context, skinToken, aVar);
                        } else {
                            aVar.a(SkinStyleHelper.getSkinStyleSync(context, skinToken, "theme", Boolean.valueOf(z)), Boolean.valueOf(z));
                        }
                    } else {
                        SkinStyleHelper.getSkinStyle(context, skinToken, aVar);
                    }
                } else {
                    SkinStyleHelper.getSkinStyle(context, skinToken, aVar);
                }
                Map map = a;
                WeakReference weakReference = new WeakReference(antSkinRenderProtocol);
                SkinRenderToken skinRenderToken = new SkinRenderToken();
                skinRenderToken.a = new WeakReference(context);
                skinRenderToken.b = skinToken;
                skinRenderToken.c = str;
                map.put(weakReference, skinRenderToken);
                return;
            }
        }
        LoggerFactory.getTraceLogger().info(str2, "bindSkinToken, protocol or token is null");
    }

    public static void bindSkinToken(Context context, SkinToken skinToken, AntSkinRenderProtocol antSkinRenderProtocol) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{context, Context.class, skinToken, SkinToken.class, antSkinRenderProtocol, AntSkinRenderProtocol.class, Void.TYPE}, null, changeQuickRedirect, "3").isSupported) {
                return;
            }
        }
        String str = "theme";
        boolean z = true;
        if (SCInnerManager.m().y(str, null)) {
            int i;
            if (!(skinToken == SkinToken.HOME_NAVI_BG || skinToken == SkinToken.HOME_SCAN_ICON || skinToken == SkinToken.HOME_PAY_COLLECT_ICON || skinToken == SkinToken.HOME_TRANSPORT_ICON || skinToken == SkinToken.HOME_POCKET_ICON || skinToken == SkinToken.HOME_PAY_ICON)) {
                if (skinToken != SkinToken.HOME_COLLECT_ICON) {
                    i = 0;
                    z = true ^ i;
                }
            }
            i = 1;
            z = true ^ i;
        }
        bindSkinToken(context, str, skinToken, antSkinRenderProtocol, z);
    }
}