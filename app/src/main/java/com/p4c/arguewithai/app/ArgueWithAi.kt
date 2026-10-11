package com.p4c.arguewithai.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.p4c.arguewithai.chat.prompts.FirstQuestionCache
import com.p4c.arguewithai.repository.ParticipantId
class ArgueWithAi : Application() {
    override fun onCreate() {
        super.onCreate()
        ParticipantId.init(this)
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()
        FirstQuestionCache.refillAsync(this)
    }
}