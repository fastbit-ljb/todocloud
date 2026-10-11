package com.todocloud.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.todocloud.app.branding.MascotStateManager
import com.todocloud.app.update.UpdateNotifier

class TodoCloudApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        UpdateNotifier.scheduleDailyCheck(this)
        com.todocloud.app.notification.DailyDigestNotifier.schedule(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        MascotStateManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        MascotStateManager.onAppBackgrounded(this)
    }
}
