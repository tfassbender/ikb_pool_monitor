package com.tfassbender.ikbpool

import android.app.Application

class IkbPoolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
