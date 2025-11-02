package me.sodipto.phone_state_background

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import io.flutter.embedding.engine.loader.FlutterLoader
import android.os.Handler


class PhoneStateBackgroundServiceReceiver : BroadcastReceiver() {
    private var telephony: TelephonyManager? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "New broadcast event received...")
        if (phoneStateBackgroundListener == null) {
            val flutterLoader = FlutterLoader()
            flutterLoader.startInitialization(context)
            flutterLoader.ensureInitializationComplete(context, null)
            telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            Handler().postDelayed({phoneStateBackgroundListener = PhoneStateBackgroundListener(context, intent, flutterLoader, telephony!!)}, 400)
            Handler().postDelayed({telephony!!.listen(phoneStateBackgroundListener, PhoneStateListener.LISTEN_CALL_STATE)}, 400)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var phoneStateBackgroundListener: PhoneStateBackgroundListener? = null
    }
}