package me.sodipto.phone_state_background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CallOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    companion object {
        const val EXTRA_CALL_TYPE = "CALL_TYPE"
        const val EXTRA_PHONE_NUMBER = "PHONE_NUMBER"
        const val ACTION_HIDE_OVERLAY = "HIDE_OVERLAY"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_OVERLAY) {
            hideOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE)
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        
        if (callType != null && phoneNumber != null) {
            showOverlay(callType, phoneNumber)
        }
        
        return START_STICKY
    }

    private fun showOverlay(callType: String, phoneNumber: String) {
        if (overlayView != null) {
            // Update existing overlay
            updateOverlayContent(callType, phoneNumber)
            return
        }
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Create overlay view
        overlayView = createOverlayView(callType, phoneNumber)
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dpToPx(20)
        
        try {
            windowManager?.addView(overlayView, params)
            Log.d("CallOverlayService", "Overlay displayed for $callType - $phoneNumber")
        } catch (e: Exception) {
            Log.e("CallOverlayService", "Failed to add overlay view", e)
        }
    }

    private fun createOverlayView(callType: String, phoneNumber: String): View {
        val context = this
        
        Log.d("CallOverlayService", "Creating overlay with callType: $callType, phoneNumber: $phoneNumber")
        
        // Main container
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(15), dpToPx(20), dpToPx(20))
            setBackgroundColor(Color.parseColor("#DD000000"))
            elevation = dpToPx(10).toFloat()
        }
        
        // Top row with close button on left
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dpToPx(15))
        }
        
        // Close button at top left
        val closeButton = Button(context).apply {
            text = "✕ CLOSE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF4444"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(15), dpToPx(8), dpToPx(15), dpToPx(8))
            setOnClickListener {
                Log.d("CallOverlayService", "Close button clicked")
                hideOverlay()
            }
        }
        topRow.addView(closeButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Spacer
        val spacer = View(context)
        topRow.addView(spacer, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))
        
        container.addView(topRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Call type header
        val callTypeDisplay = when {
            callType.contains("INCOMING") -> "INCOMING CALL"
            callType.contains("OUTGOING") -> "OUTGOING CALL"
            else -> "PHONE CALL"
        }
        
        val callTypeText = TextView(context).apply {
            text = callTypeDisplay
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        container.addView(callTypeText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(20)
        })
        
        Log.d("CallOverlayService", "Call type displayed: $callTypeDisplay")
        
        // Fetch contact info
        val contactInfo = getContactInfo(phoneNumber)
        val contactName = contactInfo["name"]
        val contactType = contactInfo["type"]
        val contactLabel = contactInfo["label"]
        
        // Contact name (if available)
        if (contactName != null && contactName != phoneNumber) {
            val nameText = TextView(context).apply {
                text = contactName
                setTextColor(Color.WHITE)
                textSize = 28f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            container.addView(nameText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(15)
            })
            Log.d("CallOverlayService", "Contact name displayed: $contactName")
        } else {
            Log.d("CallOverlayService", "No contact name found for: $phoneNumber")
        }
        
        // Phone number - ALWAYS display this
        val numberText = TextView(context).apply {
            text = phoneNumber
            setTextColor(Color.WHITE)
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        container.addView(numberText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(15)
        })
        
        Log.d("CallOverlayService", "Phone number displayed: $phoneNumber")
        
        // Phone type (if available)
        if (contactType != null) {
            val typeText = TextView(context).apply {
                text = "Type: $contactType${if (contactLabel != null) " ($contactLabel)" else ""}"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
            }
            container.addView(typeText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(15)
            })
            Log.d("CallOverlayService", "Phone type: $contactType, label: $contactLabel")
        }
        
        // Timestamp
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val timeText = TextView(context).apply {
            text = "Time: $timestamp"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        container.addView(timeText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Make overlay draggable
        makeDraggable(container)
        
        Log.d("CallOverlayService", "Overlay view created successfully")
        return container
    }
    
    private fun updateOverlayContent(callType: String, phoneNumber: String) {
        // Since we're keeping overlays persistent, we recreate rather than update
        // Just log that a new call came in
        Log.d("CallOverlayService", "New call detected while overlay exists: $callType - $phoneNumber")
    }

    private fun getContactInfo(phoneNumber: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.TYPE,
            ContactsContract.PhoneLookup.LABEL
        )
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                result["name"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                
                val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.TYPE))
                result["type"] = when (typeInt) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work Fax"
                    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home Fax"
                    ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
                    ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
                    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "Custom"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Work Mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER -> "Work Pager"
                    else -> "Unknown"
                }
                
                result["label"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LABEL))
                
                Log.d("CallOverlayService", "Contact found - Name: ${result["name"]}, Type: ${result["type"]}, Label: ${result["label"]}")
            } else {
                Log.d("CallOverlayService", "No contact found for number: $phoneNumber")
            }
        } catch (e: Exception) {
            Log.e("CallOverlayService", "Error getting contact info for $phoneNumber", e)
        } finally {
            cursor?.close()
        }
        
        return result
    }

    private fun makeDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        initialX = it.x
                        initialY = it.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        it.x = initialX + (event.rawX - initialTouchX).toInt()
                        it.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, it)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hideOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                Log.d("CallOverlayService", "Overlay hidden")
            }
        } catch (e: Exception) {
            Log.e("CallOverlayService", "Error hiding overlay", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
    
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

