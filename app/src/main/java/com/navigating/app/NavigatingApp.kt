package com.navigating.app

import android.app.Application
import org.osmdroid.config.Configuration

class NavigatingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = packageName
    }
}
