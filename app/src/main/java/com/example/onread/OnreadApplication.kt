package com.example.onread

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.onread.data.database.AppDatabase
import com.example.onread.observer.SmsContentObserver
import com.example.onread.util.NotificationHelper

class OnreadApplication : Application() {

    private lateinit var smsContentObserver: SmsContentObserver

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        val database = AppDatabase.getDatabase(this)
        smsContentObserver = SmsContentObserver(
            Handler(Looper.getMainLooper()),
            this,
            database
        )

        contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            smsContentObserver
        )
        contentResolver.registerContentObserver(
            Uri.parse("content://mms"),
            true,
            smsContentObserver
        )
        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms"),
            true,
            smsContentObserver
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            contentResolver.unregisterContentObserver(smsContentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
