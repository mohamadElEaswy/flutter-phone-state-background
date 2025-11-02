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
import android.os.Handler

enum class CallType {
    INCOMING,OUTGOING;
}

enum class CallEvent {
    INCOMINGSTART,INCOMINGMISSED,INCOMINGRECEIVED,INCOMINGEND, OUTGOINGEND,OUTGOINGSTART;
}

class PhoneStateBackgroundListener internal constructor(
    private val context: Context,
    private val intent: Intent,
    private val flutterLoader: FlutterLoader,
    private val telephonyManager: TelephonyManager // Added for retry logic
) : PhoneStateListener() {

    private var sBackgroundFlutterEngine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var callbackHandler: Long? = null
    private var callbackHandlerUser: Long? = null

    private var time: ZonedDateTime? = null
    private var callType: CallType? = null
    private var previousState: Int? = null
<<<<<<< Updated upstream
    private var retryAttempts = 0
    private val maxRetryAttempts = 2
    private var currentIncomingNumber: String = "" // Store number only from RINGING state
=======
    private var currentPhoneNumber: String? = null
>>>>>>> Stashed changes

    @RequiresApi(Build.VERSION_CODES.O)
    @Synchronized
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        if (state == TelephonyManager.CALL_STATE_RINGING && (incomingNumber == null || incomingNumber.isEmpty())) {
            if (retryAttempts < maxRetryAttempts) {
                retryAttempts++
                Handler().postDelayed({
                    telephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE)
                }, 400)
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Retry listen for incoming number, attempt: $retryAttempts")
                return
            } else {
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Max retry reached, giving up!")
            }
        } else {
            retryAttempts = 0
        }
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                val duration = Duration.between(time ?: ZonedDateTime.now(), ZonedDateTime.now())
                val phoneNumber = currentPhoneNumber ?: ""

                if (previousState == TelephonyManager.CALL_STATE_OFFHOOK && callType == CallType.INCOMING) {
<<<<<<< Updated upstream
                    // Incoming call ended - use stored number from RINGING state
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING ENDED) with number - $currentIncomingNumber")
                    notifyFlutterEngine(CallEvent.INCOMINGEND, duration.toMillis() / 1000, currentIncomingNumber)
                } else if(callType == CallType.OUTGOING) {
                    // Outgoing call ended - no incoming number for outgoing calls
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (OUTGOING ENDED)")
                    notifyFlutterEngine(CallEvent.OUTGOINGEND, duration.toMillis() / 1000, "")
                }
                else {
                    // Incoming call missed - use stored number from RINGING state
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING MISSED) with number - $currentIncomingNumber")
                    notifyFlutterEngine(CallEvent.INCOMINGMISSED, 0, currentIncomingNumber)
                }

                // Clear cached number after call ends
                currentIncomingNumber = ""
=======
                    // Incoming call ended
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING ENDED) with number - $phoneNumber")
                    notifyFlutterEngine(CallEvent.INCOMINGEND, duration.toMillis() / 1000, phoneNumber)
                } else if(callType == CallType.OUTGOING) {
                    // Outgoing call ended
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (OUTGOING ENDED) with number - $phoneNumber")
                    notifyFlutterEngine(CallEvent.OUTGOINGEND, duration.toMillis() / 1000, phoneNumber)
                }
                else {
                    Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event IDLE (INCOMING MISSED) with number - $phoneNumber")
                    notifyFlutterEngine(CallEvent.INCOMINGMISSED, 0, phoneNumber)
                }

                // Clear call session data
>>>>>>> Stashed changes
                callType = null
                currentPhoneNumber = null
                previousState = TelephonyManager.CALL_STATE_IDLE
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Phone didn't ring, so this is an outgoing call
                if (callType == null) {
                    // New outgoing call starting - clear any old data first
                    currentPhoneNumber = null
                    callType = CallType.OUTGOING
                }
                
                // Capture phone number if available and not already set
                if (!incomingNumber.isNullOrEmpty() && currentPhoneNumber == null) {
                    currentPhoneNumber = incomingNumber
                }
                
                val phoneNumber = currentPhoneNumber ?: ""
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event STATE_OFFHOOK with number - $phoneNumber")

                // Get current time to use later to calculate the duration of the call
                time = ZonedDateTime.now()
                previousState = TelephonyManager.CALL_STATE_OFFHOOK

                if(callType == CallType.OUTGOING){
<<<<<<< Updated upstream
                   // Outgoing call - no incoming number, always send empty string
                   notifyFlutterEngine(CallEvent.OUTGOINGSTART,0, "")
                }
                else {
                    // Incoming call received - use stored number from RINGING state
                    notifyFlutterEngine(CallEvent.INCOMINGRECEIVED,0, currentIncomingNumber)
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // Store the incoming number ONLY from RINGING state (fresh from OS)
                currentIncomingNumber = incomingNumber ?: ""
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event PHONE_RINGING number: $currentIncomingNumber")
                callType = CallType.INCOMING
                previousState = TelephonyManager.CALL_STATE_RINGING
                notifyFlutterEngine(CallEvent.INCOMINGSTART,0, currentIncomingNumber)
=======
                   notifyFlutterEngine(CallEvent.OUTGOINGSTART, 0, phoneNumber)
                }
                else {
                    notifyFlutterEngine(CallEvent.INCOMINGRECEIVED, 0, phoneNumber)
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // New call starting - clear any old data first
                if (callType == null) {
                    currentPhoneNumber = null
                }
                
                // Capture phone number from ringing state (most reliable for incoming calls)
                currentPhoneNumber = if (!incomingNumber.isNullOrEmpty()) incomingNumber else ""
                
                Log.d(PhoneStateBackgroundPlugin.PLUGIN_NAME, "Phone State event PHONE_RINGING number: $currentPhoneNumber")
                callType = CallType.INCOMING
                previousState = TelephonyManager.CALL_STATE_RINGING
                notifyFlutterEngine(CallEvent.INCOMINGSTART, 0, currentPhoneNumber ?: "")
>>>>>>> Stashed changes
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