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

        mmsRcsContentObserver = MmsRcsContentObserver(
            Handler(Looper.getMainLooper()),
            this,
            database,
        )

        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms"),
            true,
            smsContentObserver,
        )
        contentResolver.registerContentObserver(
        val urisToObserve = listOf(
            Uri.parse("content://sms"),
            Uri.parse("content://mms"),
            Uri.parse("content://mms-sms"),
            Uri.parse("content://mms-sms/conversations")
        )

        for (uri in urisToObserve) {
            try {
                contentResolver.registerContentObserver(
                    uri,
                    true,
                    smsContentObserver,
                )
                Log.d("OnreadApplication", "Registered ContentObserver for $uri")
            } catch (e: SecurityException) {
                Log.w("OnreadApplication", "SecurityException registering ContentObserver for $uri", e)
            } catch (e: Exception) {
                Log.e("OnreadApplication", "Exception registering ContentObserver for $uri", e)
            }
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
