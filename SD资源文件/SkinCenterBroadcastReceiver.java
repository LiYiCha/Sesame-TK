//
// Decompiled by Jadx (from NP Manager)
//
package com.alipay.mobile.onsitepaystatic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alipay.dexaop.DexAOPCenter;
import com.alipay.dexaop.DexAOPEntry;
import com.alipay.dexaop.stub.android.content.BroadcastReceiver_onReceive_androidcontentContext.androidcontentIntent_stub;
import com.alipay.instantrun.ChangeQuickRedirect;
import com.alipay.instantrun.ConstructorCode;
import com.alipay.instantrun.PatchProxy;
import com.alipay.mobile.framework.MpaasClassInfo;
import com.alipay.mobile.onsitepaystatic.skin.OspSkinModel;
import com.alipay.mobile.onsitepaystatic.util.OspLogUtil;

@MpaasClassInfo(BundleName = "android-phone-wallet-onsitepaystatic", ExportJarName = "unknown", Level = "product", Product = ":android-phone-wallet-onsitepaystatic")
public class SkinCenterBroadcastReceiver extends BroadcastReceiver implements androidcontentIntent_stub {
    /* renamed from: 支 */
    public static ChangeQuickRedirect f0;

    public SkinCenterBroadcastReceiver() {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            ConstructorCode proxy = PatchProxy.proxy(changeQuickRedirect, "0");
            if (proxy != null) {
                proxy.afterSuper(this);
            }
        }
    }

    private /* synthetic */ void __onReceive_stub_private(Context context, Intent intent) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{context, Context.class, intent, Intent.class, Void.TYPE}, this, changeQuickRedirect, "2").isSupported) {
                return;
            }
        }
        String str = "SKReceiver";
        String action = intent.getAction();
        try {
            OspLogUtil.info(str, "action = ".concat(String.valueOf(action)));
            if ("com.alipay.skincenter.resCacheUpdated".equals(action)) {
                String stringExtra = intent.getStringExtra("ospSkinModel");
                OspLogUtil.info(str, "modelStr = ".concat(String.valueOf(stringExtra)));
                if (!TextUtils.isEmpty(stringExtra)) {
                    ConfigUtilBiz.writeFacePaySkinModel((OspSkinModel) JSON.parseObject(stringExtra, new 1(this), new Feature[0]));
                }
            }
        } catch (Exception e) {
            OspLogUtil.error(str, "process skin err", e);
        }
    }

    public /* synthetic */ void __onReceive_stub(Context context, Intent intent) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{context, Context.class, intent, Intent.class, Void.TYPE}, this, changeQuickRedirect, "1").isSupported) {
                return;
            }
        }
        __onReceive_stub_private(context, intent);
    }

    public void onReceive(Context context, Intent intent) {
        ChangeQuickRedirect changeQuickRedirect = f0;
        if (changeQuickRedirect != null) {
            if (PatchProxy.proxy(new Object[]{context, Context.class, intent, Intent.class, Void.TYPE}, this, changeQuickRedirect, "3").isSupported) {
                return;
            }
        }
        if ((DexAOPCenter.sFlag & 2) != 0) {
            Class cls = SkinCenterBroadcastReceiver.class;
            if (getClass() == cls) {
                DexAOPEntry.android_content_BroadcastReceiver_onReceive_proxy(cls, this, context, intent);
                return;
            }
        }
        __onReceive_stub_private(context, intent);
    }
}


// 主题切换
public static boolean writeFacePaySkinModel(OspSkinModel ospSkinModel) {
    OspSkinModel ospSkinModel2;
    ChangeQuickRedirect changeQuickRedirect = f0;
    if (changeQuickRedirect != null) {
        ospSkinModel2 = ospSkinModel;
        PatchProxyResult proxy = PatchProxy.proxy(ospSkinModel2, null, changeQuickRedirect, "27", OspSkinModel.class, Boolean.TYPE);
        if (proxy.isSupported) {
            return ((Boolean) proxy.result).booleanValue();
        }
    }
    ospSkinModel2 = ospSkinModel;
    if (ospSkinModel2 == null) {
        return false;
    }
    d = ospSkinModel2;
    String str = a;
    StringBuilder stringBuilder = new StringBuilder("flush osp switches， result  = ");
    stringBuilder.append(d);
    CachedLogger.debug(str, stringBuilder.toString());
    putString("prefs_osp_config", "onsitepay_onsitepay_skin_10_2_23", a(d));
    return true;
}