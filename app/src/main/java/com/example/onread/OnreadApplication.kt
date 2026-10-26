package com.example.onread

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
            database,
        )

        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://mms-sms"),
                true,
                smsContentObserver,
            )
            contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                smsContentObserver,
            )
            contentResolver.registerContentObserver(
                Uri.parse("content://mms"),
                true,
                smsContentObserver,
            )
        } catch (e: SecurityException) {
            Log.w("OnreadApplication", "SecurityException registering ContentObserver on startup", e)
        } catch (e: Exception) {
            Log.e("OnreadApplication", "Exception registering ContentObserver on startup", e)
        }
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
