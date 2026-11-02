package com.CEN4090c.aisight

import android.app.Application
import com.meta.wearable.dat.core.Wearables

class AiSightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearables.initialize(this)
    }
}