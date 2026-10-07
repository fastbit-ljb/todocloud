package com.todocloud.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.todocloud.app.branding.MascotStateManager

class TodoCloudApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        MascotStateManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        MascotStateManager.onAppBackgrounded(this)
    }
}
