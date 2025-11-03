package me.sodipto.phone_state_background

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
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

enum class CallType {
    INCOMING,OUTGOING;
}

enum class CallEvent {
    INCOMINGSTART,INCOMINGMISSED,INCOMINGRECEIVED,INCOMINGEND, OUTGOINGEND,OUTGOINGSTART;
}

class PhoneStateBackgroundListener internal constructor(
    private val context: Context,
    private val intent: Intent,
    private val flutterLoader: FlutterLoader
) : PhoneStateListener() {

    private var sBackgroundFlutterEngine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var callbackHandler: Long? = null
    private var callbackHandlerUser: Long? = null

    private var time: ZonedDateTime? = null
    private var callType: CallType? = null
    private var previousState: Int? = null
    private var cachedPhoneNumber: String? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @Synchronized
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                val duration = Duration.between(time ?: ZonedDateTime.now(), ZonedDateTime.now())
                // Always use cached number for call end events since incomingNumber may be unreliable in IDLE state
                var numberToUse = cachedPhoneNumber ?: ""

                if (previousState == TelephonyManager.CALL_STATE_OFFHOOK && callType == CallType.INCOMING) {
                    // Incoming call ended
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING ENDED) with number - $numberToUse")
                    notifyFlutterEngine(CallEvent.INCOMINGEND, duration.toMillis() / 1000, numberToUse)

                } else if(callType == CallType.OUTGOING) {
                    // Outgoing call ended
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (OUTGOING ENDED) with number - $numberToUse")
                    notifyFlutterEngine(CallEvent.OUTGOINGEND, duration.toMillis() / 1000, numberToUse)

                }
                else {
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING MISSED) with number - $numberToUse")
                    notifyFlutterEngine(CallEvent.INCOMINGMISSED, 0, numberToUse)
                }

                // Clear all state after call ends to prevent stale data
                callType = null
                previousState = TelephonyManager.CALL_STATE_IDLE
                cachedPhoneNumber = null // Clear cached number to prevent using stale data for next call
                numberToUse = ""

            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event STATE_OFFHOOK")
                // Phone didn't ring, so this is an outgoing call
                if (callType == null) {
                    callType = CallType.OUTGOING
                    // For outgoing calls, cache the number here when it's first available
                    if (!incomingNumber.isNullOrEmpty()) {
                        cachedPhoneNumber = incomingNumber
                    } else {
                        cachedPhoneNumber = ""
                    } 
                }

                // Get current time to use later to calculate the duration of the call
                time = ZonedDateTime.now()
                previousState = TelephonyManager.CALL_STATE_OFFHOOK
                var numberToUse = cachedPhoneNumber ?: ""

                if(callType == CallType.OUTGOING){
                   notifyFlutterEngine(CallEvent.OUTGOINGSTART, 0, incomingNumber ?: "")
                }
                else {
                    notifyFlutterEngine(CallEvent.INCOMINGRECEIVED, 0, incomingNumber ?: "")
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // Cache the number ONLY during RINGING state when it's fresh for incoming calls
                if (!incomingNumber.isNullOrEmpty()) {
                    cachedPhoneNumber = incomingNumber
                }
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event PHONE_RINGING number: $cachedPhoneNumber")
                callType = CallType.INCOMING
                previousState = TelephonyManager.CALL_STATE_RINGING
                notifyFlutterEngine(CallEvent.INCOMINGSTART, 0, incomingNumber ?: "")
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