package com.example.prankcallapp

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.prankcallapp.serviceclass.FakeCallService

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var callerNameTv: TextView
    private lateinit var callerNumberTv: TextView
    private lateinit var declineIcon: ImageView
    private lateinit var acceptIcon: ImageView

    private var fakeCallService: FakeCallService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FakeCallService.FakeCallBinder
            fakeCallService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fakeCallService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Put the code here
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setContentView(R.layout.activity_incoming_call)

        callerNameTv = findViewById(R.id.caller_name_tv)
        callerNumberTv = findViewById(R.id.caller_number_tv)
        declineIcon = findViewById(R.id.call_decline_icon)
        acceptIcon = findViewById(R.id.accept_icon)

        // Retrieve caller name and number from intent extras
        val callerName = intent.getStringExtra("CALLER_NAME")
        val callerNumber = intent.getStringExtra("CALLER_NUMBER")

        // Set caller name and number in TextViews
        callerNameTv.text = callerName
        callerNumberTv.text = callerNumber

        declineIcon.setOnClickListener {
            fakeCallService?.stopRingtone()
            Toast.makeText(this, "Call declined", Toast.LENGTH_LONG).show()
        }

        acceptIcon.setOnClickListener {
            // Handle accept action
            // Start the call activity or perform any other action
            // For example:
            // startActivity(Intent(this, CallActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, FakeCallService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}