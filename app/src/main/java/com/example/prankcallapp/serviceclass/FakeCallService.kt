package com.example.prankcallapp.serviceclass

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.prankcallapp.R
import android.os.*
import androidx.core.content.ContextCompat
import com.example.prankcallapp.IncomingCallActivity
import com.example.prankcallapp.MainActivity
import com.example.prankcallapp.broadcastreceiver.FakeCallReceiver

class FakeCallService : Service() {

    private var isPlaying = false
    private lateinit var ringtone: Ringtone
    private val handler = Handler(Looper.getMainLooper())
    private val binder = FakeCallBinder()
    private var overlayView: View? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class FakeCallBinder : Binder() {
        fun getService(): FakeCallService = this@FakeCallService
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callerName = intent?.getStringExtra("CALLER_NAME")
        val callerNumber = intent?.getStringExtra("CALLER_NUMBER")
        val triggerTime = intent?.getLongExtra("TRIGGER_TIME", 0L) ?: 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                showNotificationPermissionDialog()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification(callerName, callerNumber)
        startForeground(1, notification)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FakeCallService::WakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)

        if (triggerTime > System.currentTimeMillis()) {
            scheduleAlarm(triggerTime, callerName, callerNumber)
        } else {
            startFakeCall(callerName, callerNumber)
        }

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showNotificationPermissionDialog() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("SHOW_PERMISSION_DIALOG", true)
        }
        startActivity(intent)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(triggerTime: Long, callerName: String?, callerNumber: String?) {
        val intent = Intent(this, FakeCallReceiver::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_NUMBER", callerNumber)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startFakeCall(callerName: String?, callerNumber: String?) {
        if (callerName != null && callerNumber != null && !isPlaying) {
            isPlaying = true
            playFakeCall()
            showIncomingCallActivity(callerName, callerNumber)
            if (Settings.canDrawOverlays(this)) {
                showOverlay(callerName, callerNumber)
            }
        }
    }

    private fun showIncomingCallActivity(callerName: String, callerNumber: String) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_NUMBER", callerNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        startActivity(intent)
    }

    @SuppressLint("InflateParams")
    private fun showOverlay(callerName: String, callerNumber: String) {
        if (!Settings.canDrawOverlays(this)) {
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        overlayView = inflater.inflate(R.layout.activity_incoming_call, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)

        val tvCallerName = overlayView?.findViewById<TextView>(R.id.caller_name_tv)
        val tvCallerNumber = overlayView?.findViewById<TextView>(R.id.caller_number_tv)
        val btnAccept = overlayView?.findViewById<ImageView>(R.id.accept_icon)
        val btnDecline = overlayView?.findViewById<ImageView>(R.id.call_decline_icon)

        tvCallerName?.text = callerName
        tvCallerNumber?.text = callerNumber

        btnAccept?.setOnClickListener {
            // Handle call acceptance
            stopSelf()
        }

        btnDecline?.setOnClickListener {
            // Handle call decline
            Toast.makeText(this, "Call Declined", Toast.LENGTH_SHORT).show()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(overlayView)
            stopRingtone()
            stopSelf()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun buildNotification(callerName: String?, callerNumber: String?): Notification {
        val channelId = "fake_call_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Fake Call from $callerName")
            .setContentText("Incoming call from $callerNumber")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Fake Call Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val notificationChannel = NotificationChannel(channelId, channelName, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }

        return notificationBuilder.build()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun playFakeCall() {
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
        ringtone.play()

        val callDuration = 35000L

        handler.postDelayed({
            if (ringtone.isPlaying) {
                ringtone.stop()
            }
            isPlaying = false
            stopSelf()
        }, callDuration)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)

        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }

        if (overlayView != null && overlayView?.isAttachedToWindow == true) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(overlayView)
        }

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    fun stopRingtone() {
        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }
        handler.removeCallbacksAndMessages(null)
        isPlaying = false

        if (overlayView != null && overlayView?.isAttachedToWindow == true) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(overlayView)
        }

        stopSelf()
    }

    companion object {
        const val REQUEST_OVERLAY_PERMISSION = 1234
    }
}








