package com.example.prankcallapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.prankcallapp.broadcastreceiver.FakeCallReceiver
import com.example.prankcallapp.databaseclass.AppDatabase
import com.example.prankcallapp.dataclasses.ScheduledCall
import com.example.prankcallapp.serviceclass.FakeCallService
import kotlinx.coroutines.launch
import java.util.Calendar


class MainActivity : AppCompatActivity() {

    private lateinit var etCallerName: EditText
    private lateinit var etCallerNumber: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var datePicker: DatePicker
    private lateinit var scheduleCallButton: Button
    private lateinit var database: AppDatabase
    private var fakeCallService: FakeCallService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as FakeCallService.FakeCallBinder
            fakeCallService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            fakeCallService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle notification permission dialog
        if (intent.getBooleanExtra("SHOW_PERMISSION_DIALOG", false)) {
            showNotificationPermissionDialog()
        }

        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app-database").build()

        etCallerNumber = findViewById(R.id.etCallerNumber)
        etCallerName = findViewById(R.id.etCallerName)
        timePicker = findViewById(R.id.simpleTimePicker)
        datePicker = findViewById(R.id.datePicker)
        scheduleCallButton = findViewById(R.id.call_tv)

        createNotificationChannel()

        scheduleCallButton.setOnClickListener {
            val callerName = etCallerName.text.toString()
            val callerNumber = etCallerNumber.text.toString()
            val triggerTime = getTimeInMillis()

            // Check if the trigger time is valid
            if (triggerTime == -1L) {
                Toast.makeText(this, "The selected date or time is in the past. Cannot schedule the prank call.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Schedule the service only if the trigger time is valid
            scheduleService(triggerTime)

            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("This functionality requires 'Display over other apps' permission. Please grant it to proceed.")
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivityForResult(intent, FakeCallService.REQUEST_OVERLAY_PERMISSION)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                scheduleFakeCall(callerName, callerNumber, triggerTime)
            }
        }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Channel"
            val descriptionText = "Channel for Call notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("fake_call_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getTimeInMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, datePicker.year)
        calendar.set(Calendar.MONTH, datePicker.month)
        calendar.set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
        calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
        calendar.set(Calendar.MINUTE, timePicker.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val now = Calendar.getInstance()

        // If the selected date and time are before the current date and time, return -1 to indicate that the service should not trigger
        if (calendar.before(now)) {
            return -1
        }

        return calendar.timeInMillis
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleService(triggerTime: Long) {
        if (triggerTime == -1L) {
            // Do not trigger the service, optionally show a message to the user
            Toast.makeText(this, "Selected date is in the past. Please choose a future date.", Toast.LENGTH_LONG).show()
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, FakeCallReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent)

        // Optionally show a message to the user indicating the alarm is set
        Toast.makeText(this, "Service scheduled successfully.", Toast.LENGTH_LONG).show()
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FakeCallService.REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                val callerName = etCallerName.text.toString()
                val callerNumber = etCallerNumber.text.toString()
                val triggerTime = getTimeInMillis()

                // Check if the trigger time is valid
                if (triggerTime == -1L) {
                    Toast.makeText(this, "The selected date is in the past", Toast.LENGTH_SHORT).show()
                    return
                }

                scheduleFakeCall(callerName, callerNumber, triggerTime)
            }
        }
    }


    private fun scheduleFakeCall(callerName: String, callerNumber: String, triggerTime: Long) {
        lifecycleScope.launch {
            val scheduledCall = ScheduledCall(0, callerName, callerNumber, triggerTime)
            database.scheduledCallDao().insert(scheduledCall)
        }

        val hour = timePicker.hour
        val minute = timePicker.minute
        val amPm = if (hour < 12) "AM" else "PM"
        val hourIn12Format = if (hour % 12 == 0) 12 else hour % 12

        Toast.makeText(this, "Call scheduled for $hourIn12Format:$minute $amPm", Toast.LENGTH_SHORT).show()
        startFakeCallService(callerName, callerNumber, triggerTime)
    }

    private fun startFakeCallService(callerName: String, callerNumber: String, triggerTime: Long) {
        val serviceIntent = Intent(this, FakeCallService::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_NUMBER", callerNumber)
            putExtra("TRIGGER_TIME", triggerTime)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("This app requires notification permission to display fake call notifications. Please enable notifications for this app.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, FakeCallService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}





