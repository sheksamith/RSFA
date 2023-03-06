package com.samith.rsfa.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import com.samith.rsfa.*
import com.samith.rsfa.R
import com.samith.rsfa.activities.MainActivity
import com.samith.rsfa.models.SMS
import kotlinx.coroutines.*

/**
 * Created by samithchow on 3/7/2023.
 */

@Suppress("DEPRECATION")
class ForegroundService : Service() {

    private var isServiceStarted = false
    private var notificationManager: NotificationManager? = null
    private var job: Job? = null
    private var sharedPref: SharedPreferences? = null
    private var firebaseDatabase: FirebaseDatabase? = null
    private var databaseReference: DatabaseReference? = null



    private val builder by lazy {
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RSFM")
            .setGroup("Running")
            .setGroupSummary(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getPendingIntent())
            .setSilent(true)
            .setSmallIcon(R.mipmap.ic_launcher_round)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        firebaseDatabase = FirebaseDatabase.getInstance()

        databaseReference = firebaseDatabase!!.getReference("sms")
        sharedPref = getSharedPreferences(SHARED_PREFERENCE, MODE_PRIVATE)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        processCommand(intent)
        return START_REDELIVER_INTENT
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                return true
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                return true
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                return true
            }
        }
        return false
    }

    private fun receiveMsg(){
        val br = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                for( sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)){
                    if(isOnline(this@ForegroundService)){
                        if (sms.originatingAddress != null) {
                            postData(sms.originatingAddress!!, sms.displayMessageBody , sms.timestampMillis)

                        }else {
                            postData("Unknown", sms.displayMessageBody, sms.timestampMillis)
                        }
                    }

                }
            }
        }
        registerReceiver(br, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }
    private fun readSms() {
        val numberCol = Telephony.TextBasedSmsColumns.ADDRESS
        val textCol = Telephony.TextBasedSmsColumns.BODY
        val typeCol = Telephony.TextBasedSmsColumns.DATE

        val projection = arrayOf(numberCol, textCol, typeCol)

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection, null, null, null
        )

        val numberColIdx = cursor!!.getColumnIndex(numberCol)
        val textColIdx = cursor.getColumnIndex(textCol)
        val typeColIdx = cursor.getColumnIndex(typeCol)

        var i = 0
        while (cursor.moveToNext()) {
            if (i <= 19){
                i++
                val number = cursor.getString(numberColIdx)
                val text = cursor.getString(textColIdx)
                val t = cursor.getLong(typeColIdx)
                postData(number, text, t)
            }
        }

        cursor.close()
    }

    private fun postData(from: String, body: String, timestampMillis: Long) {
        val sms = SMS(1,"demo" , "01xxxx", from, body, timestampMillis)
        val refPush = databaseReference!!.push()
        refPush.setValue(sms)
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun processCommand(intent: Intent?) {
        when (intent?.extras?.getString(COMMAND_ID) ?: INVALID) {
            COMMAND_START -> {
                intent?.extras?.getLong(STARTED_TIMER_TIME_MS) ?: return
                commandStart()
            }
            COMMAND_STOP -> commandStop()
            INVALID -> return
        }
    }

    private fun commandStart() {
        val firstTime =  sharedPref!!.getBoolean(FIRST_TIME, false)
        if (isServiceStarted) {
            return
        }
        try {
            moveToStartedState()
            startForegroundAndShowNotification()
            continueTimer()
            job = MainScope().launch {
                receiveMsg()
                if (!firstTime){
                    sharedPref!!.edit().putBoolean(FIRST_TIME, true).apply()
                    readSms()
                }

            }

        } finally {
            isServiceStarted = true
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun continueTimer() {
        job = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                notificationManager?.notify(
                    NOTIFICATION_ID,
                    getNotification("Reading sms in foreground")
                )
                delay(INTERVAL)
            }
        }
    }

    private fun commandStop() {
        if (!isServiceStarted) {
            return
        }
        try {

            job?.cancel()
            stopForeground(true)
            stopSelf()

        } finally {
            isServiceStarted = false
        }
    }

    private fun moveToStartedState() {
        startForegroundService(Intent(this, ForegroundService::class.java))
    }

    private fun startForegroundAndShowNotification() {
        createChannel()
        val notification = getNotification("content")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getNotification(content: String) = builder.setContentText(content).build()


    private fun createChannel() {
        val channelName = "pomodoro"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val notificationChannel = NotificationChannel(
            CHANNEL_ID, channelName, importance
        )
        notificationManager?.createNotificationChannel(notificationChannel)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntent(): PendingIntent? {
        val resultIntent = Intent(this, MainActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, resultIntent,  PendingIntent.FLAG_ONE_SHOT)
        }
    }

    private companion object {

        private const val CHANNEL_ID = "Channel_ID"
        private const val NOTIFICATION_ID = 777
        private const val INTERVAL = 1000L
    }
}