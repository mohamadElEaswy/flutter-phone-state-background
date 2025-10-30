package me.sodipto.phone_state_background

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.loader.FlutterLoader
import android.os.Handler


class PhoneStateBackgroundServiceReceiver : BroadcastReceiver() {
    private var telephony: TelephonyManager? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "New broadcast event received...")
        if (phoneStateBackgroundListener == null) {
            val flutterLoader = FlutterLoader()
            flutterLoader.startInitialization(context)
            flutterLoader.ensureInitializationComplete(context, null)
            telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            Handler().postDelayed({
                phoneStateBackgroundListener = PhoneStateBackgroundListener(context, intent, flutterLoader, telephony!!)
                // Register the callback with the new TelephonyCallback API
                telephony!!.registerTelephonyCallback(context.mainExecutor, phoneStateBackgroundListener!!)
            }, 400)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var phoneStateBackgroundListener: PhoneStateBackgroundListener? = null
    }
}