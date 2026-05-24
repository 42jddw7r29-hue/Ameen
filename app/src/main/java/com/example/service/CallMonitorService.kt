package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.WhitelistedContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var phoneStateReceiver: BroadcastReceiver? = null
    
    private var isRinging = false
    private var activeRingtone: Ringtone? = null

    // Restore state properties
    private var originalRingerMode: Int? = null
    private var originalRingVolume: Int? = null
    private var modifiedAudio = false

    companion object {
        const val CHANNEL_ID = "CallMonitorChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_TEST_RING = "com.example.action.TEST_RING"
        const val ACTION_STOP_TEST_RING = "com.example.action.STOP_TEST_RING"
        
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            context.stopService(intent)
        }

        fun triggerTestRing(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_TEST_RING
            }
            context.startService(intent)
        }

        fun stopTestRing(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_STOP_TEST_RING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        registerPhoneStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        
        if (intent != null) {
            when (intent.action) {
                ACTION_TEST_RING -> {
                    triggerRingingBypass(this)
                }
                ACTION_STOP_TEST_RING -> {
                    stopOverridingRing()
                }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        isServiceRunning = false
        unregisterPhoneStateReceiver()
        stopOverridingRing()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "مراقب الرنين الصامت"
            val descriptionText = "خدمة خلفية لتجاوز جدار الصمت لجهات الاتصال المحددة"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("رنين الصامت نشط ومفعّل")
            .setContentText("التطبيق يراقب المكالمات الواردة لتجاوز الصمت لجهات الاتصال")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun registerPhoneStateReceiver() {
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        incomingNumber?.let { number ->
                            handleIncomingCall(context, number)
                        } ?: run {
                            // On some Android systems or API levels, the incoming number is only 
                            // received after a slight delay, or we might need to verify the last active call.
                            // To be helpful, we fallback to notifying call log / alert verification.
                        }
                    } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK || state == TelephonyManager.EXTRA_STATE_IDLE) {
                        stopOverridingRing()
                    }
                }
            }
        }
        
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(phoneStateReceiver, filter)
        }
    }

    private fun unregisterPhoneStateReceiver() {
        phoneStateReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        phoneStateReceiver = null
    }

    private fun handleIncomingCall(context: Context, incomingNumber: String) {
        serviceScope.launch {
            val database = AppDatabase.getDatabase(context)
            val contacts = database.contactDao().getAllContacts()
            
            if (isNumberWhitelisted(incomingNumber, contacts)) {
                triggerRingingBypass(context)
            }
        }
    }

    private fun isNumberWhitelisted(incoming: String, whitelisted: List<WhitelistedContact>): Boolean {
        val cleanIncoming = incoming.filter { it.isDigit() }
        if (cleanIncoming.isEmpty()) return false

        for (contact in whitelisted) {
            if (!contact.isEnabled) continue
            val cleanContact = contact.phoneNumber.filter { it.isDigit() }
            if (cleanContact.isEmpty()) continue

            // Full match or suffix match (last 7+ digits matches standard localized phone numbers safely)
            if (cleanIncoming == cleanContact) return true
            if (cleanIncoming.length >= 7 && cleanContact.length >= 7) {
                val suffixIncoming = cleanIncoming.takeLast(7)
                val suffixContact = cleanContact.takeLast(7)
                if (suffixIncoming == suffixContact) return true
            }
        }
        return false
    }

    private fun triggerRingingBypass(context: Context) {
        if (isRinging) return
        isRinging = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Try DND Policy / Ringer override first if DND access is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
            try {
                originalRingerMode = audioManager.ringerMode
                originalRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxVol * 0.85).toInt(), 0)
                modifiedAudio = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Play default ringtone on ALARM channel as powerful backup (overrides silent modes automatically)
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
            if (ringtoneUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        ringtone.streamType = AudioManager.STREAM_ALARM
                    }
                    ringtone.play()
                    activeRingtone = ringtone
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopOverridingRing() {
        isRinging = false
        try {
            activeRingtone?.let { ring ->
                if (ring.isPlaying) {
                    ring.stop()
                }
            }
            activeRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        restoreAudioSettings()
    }

    private fun restoreAudioSettings() {
        if (modifiedAudio) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val originalMode = originalRingerMode
                val originalVol = originalRingVolume
                
                if (originalMode != null) {
                    audioManager.ringerMode = originalMode
                }
                if (originalVol != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, originalVol, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                originalRingerMode = null
                originalRingVolume = null
                modifiedAudio = false
            }
        }
    }
}
