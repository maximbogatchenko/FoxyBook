package com.foxybook.app.core.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TtsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, TtsService::class.java).apply {
            this.action = action
        }
        context.startService(serviceIntent)
    }
}
