package me.sodipto.phone_state_background

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.time.Duration
import java.time.ZonedDateTime
import java.util.ArrayList
import android.os.Handler
import java.util.concurrent.Executor

enum class CallType {
    INCOMING,OUTGOING;
}

enum class CallEvent {
    INCOMINGSTART,INCOMINGMISSED,INCOMINGRECEIVED,INCOMINGEND, OUTGOINGEND,OUTGOINGSTART;
}

@RequiresApi(Build.VERSION_CODES.S)
class PhoneStateBackgroundListener internal constructor(
    private val context: Context,
    private val intent: Intent,
    private val flutterLoader: FlutterLoader,
    private val telephonyManager: TelephonyManager // Added for retry logic
) : TelephonyCallback(), TelephonyCallback.CallStateListener {

    private var sBackgroundFlutterEngine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var callbackHandler: Long? = null
    private var callbackHandlerUser: Long? = null

    private var time: ZonedDateTime? = null
    private var callType: CallType? = null
    private var previousState: Int? = null
    private var retryAttempts = 0
    private val maxRetryAttempts = 4
    private var currentIncomingNumber: String = "" // Store number only from RINGING state

    @RequiresApi(Build.VERSION_CODES.S)
    @Synchronized
    override fun onCallStateChanged(state: Int) {
        // New TelephonyCallback API doesn't provide incoming number for privacy reasons
        // We'll send empty string for phone numbers as they're not available in Android 12+
        Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Call state changed: $state")
        
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                val duration = Duration.between(time ?: ZonedDateTime.now(), ZonedDateTime.now())

                if (previousState == TelephonyManager.CALL_STATE_OFFHOOK && callType == CallType.INCOMING) {
                    // Incoming call ended - number not available in new API
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING ENDED)")
                    notifyFlutterEngine(CallEvent.INCOMINGEND, duration.toMillis() / 1000, "")
                } else if(callType == CallType.OUTGOING) {
                    // Outgoing call ended - no incoming number for outgoing calls
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (OUTGOING ENDED)")
                    notifyFlutterEngine(CallEvent.OUTGOINGEND, duration.toMillis() / 1000, "")
                }
                else {
                    // Incoming call missed - number not available in new API
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING MISSED)")
                    notifyFlutterEngine(CallEvent.INCOMINGMISSED, 0, "")
                }

                // Clear cached number after call ends
                currentIncomingNumber = ""
                callType = null
                previousState = TelephonyManager.CALL_STATE_IDLE
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event STATE_OFFHOOK")
                // Phone didn't ring, so this is an outgoing call
                if (callType == null)
                    callType = CallType.OUTGOING

                // Get current time to use later to calculate the duration of the call
                time = ZonedDateTime.now()
                previousState = TelephonyManager.CALL_STATE_OFFHOOK

                if(callType == CallType.OUTGOING){
                   // Outgoing call - no incoming number, always send empty string
                   notifyFlutterEngine(CallEvent.OUTGOINGSTART,0, "")
                }
                else {
                    // Incoming call received - number not available in new API
                    notifyFlutterEngine(CallEvent.INCOMINGRECEIVED,0, "")
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // Phone number not available in new TelephonyCallback API (Android 12+)
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event PHONE_RINGING (number not available in Android 12+)")
                callType = CallType.INCOMING
                previousState = TelephonyManager.CALL_STATE_RINGING
                notifyFlutterEngine(CallEvent.INCOMINGSTART,0, "")
            }
        }
    }

    private fun notifyFlutterEngine(type: CallEvent, duration: Long, number: String){
        val arguments = ArrayList<Any?>()

        // Initialize flutter engine
        if (sBackgroundFlutterEngine == null) {
            callbackHandler = context.getSharedPreferences(
                PhoneStateBackgroundPlugin.PLUGIN_NAME,
                Context.MODE_PRIVATE
            ).getLong(PhoneStateBackgroundPlugin.CALLBACK_SHAREDPREFERENCES_KEY, 0)
            callbackHandlerUser = context.getSharedPreferences(
                PhoneStateBackgroundPlugin.PLUGIN_NAME,
                Context.MODE_PRIVATE
            ).getLong(PhoneStateBackgroundPlugin.CALLBACK_USER_SHAREDPREFERENCES_KEY, 0)
            if (callbackHandler == 0L || callbackHandlerUser == 0L) {
                Log.e(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Fatal: No callback registered")
                return
            }
            Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Found callback handler $callbackHandler")
            Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Found user callback handler $callbackHandlerUser")

            // Retrieve the actual callback information needed to invoke it.
            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandler!!)
            if (callbackInfo == null) {
                Log.e(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Fatal: failed to find callback")
                return
            }
            sBackgroundFlutterEngine = FlutterEngine(context)
            val args = DartCallback(
                context.assets,
                flutterLoader.findAppBundlePath(),
                callbackInfo
            )

            // Start running callback dispatcher code in our background FlutterEngine instance.
            sBackgroundFlutterEngine!!.dartExecutor.executeDartCallback(args)
        }
        // Create the MethodChannel used to communicate between the callback
        // dispatcher and this instance.
        channel = MethodChannel(
            sBackgroundFlutterEngine!!.dartExecutor.binaryMessenger,
            PhoneStateBackgroundPlugin.PLUGIN_NAME + "_listner"
        )

        arguments.add(callbackHandler)
        arguments.add(callbackHandlerUser)
        arguments.add(type.toString())
        arguments.add(duration)
        arguments.add(number)
        channel!!.invokeMethod("call", arguments)
    }
}