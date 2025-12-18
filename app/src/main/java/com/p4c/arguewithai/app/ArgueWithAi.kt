package com.p4c.arguewithai.app

import android.app.Application
import com.google.firebase.FirebaseApp
class ArgueWithAi : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}