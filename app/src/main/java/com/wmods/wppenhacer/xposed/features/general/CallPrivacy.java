package com.wmods.wppenhacer.xposed.features.general;

import android.os.Message;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallPrivacy extends Feature {
    public CallPrivacy(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    /**
     * @noinspection unchecked
     */
    @Override
    public void doHook() throws Throwable {

        var onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader);

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo = ((Message) param.args[0]).obj;
                Class<?> callInfoClass = XposedHelpers.findClass("com.whatsapp.voipcalling.CallInfo", classLoader);
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return;
                if ((boolean) XposedHelpers.callMethod(callinfo, "isCaller")) return;
                var userJid = XposedHelpers.callMethod(callinfo, "getPeerJid");
                var callId = XposedHelpers.callMethod(callinfo, "getCallId");
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                Tasker.sendTaskerEvent(WppCore.stripJID(WppCore.getRawString(userJid)), "call_received");
                var block = false;
                switch (type) {
                    case 0:
                        break;
                    case 1:
                        block = true;
                        break;
                    case 2:
                        block = checkCallBlock(userJid);
                        break;
                }
                if (!block) return;
                var clazzVoip = XposedHelpers.findClass("com.whatsapp.voipcalling.Voip", classLoader);
                var rejectType = prefs.getString("call_type", "no_internet");
                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                        XposedHelpers.callStaticMethod(clazzVoip, "rejectCall", callId, rejectType.equals("declined") ? null : rejectType);
                        param.setResult(true);
                        break;
                    case "ended":
                        try {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true);
                        } catch (NoSuchMethodError e) {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true, 0);
                        }
                        param.setResult(true);
                        break;
                    default:
                }
            }
        });

        XposedBridge.hookAllMethods(classLoader.loadClass("com.whatsapp.voipcalling.Voip"), "nativeHandleIncomingXmppOffer", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getString("call_type", "no_internet").equals("no_internet")) return;
                var userJid = param.args[0];
                log("Call received: " + param.args[0]);
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                var block = false;
                switch (type) {
                    case 0:
                        break;
                    case 1:
                        block = true;
                        break;
                    case 2:
                        block = checkCallBlock(userJid);
                        break;
                }
                if (!block) return;
                param.setResult(1);
            }
        });


    }

    public boolean checkCallBlock(Object userJid) throws IllegalAccessException, InvocationTargetException {
        var jid = WppCore.stripJID(WppCore.getRawString(userJid));
        if (jid != null && WppCore.stripJID(jid).equals(jid)) {
            jid = jid.split("\\.")[0] + "@s.whatsapp.net";
        }
        log("Checking call block for " + jid);
        var contactName = WppCore.getContactName(WppCore.createUserJid(jid));
        return contactName == null || contactName.equals(jid);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Privacy";
    }
}
