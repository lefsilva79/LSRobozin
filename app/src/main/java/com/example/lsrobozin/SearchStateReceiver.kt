package com.example.lsrobozin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint

@SuppressLint("UnsafeIntentLaunch", "UnspecifiedRegisterReceiverFlag")
class SearchStateReceiver : BroadcastReceiver() {
    private var callback: ((Boolean) -> Unit)? = null

    fun setCallback(callback: (Boolean) -> Unit) {
        this.callback = callback
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.example.lsrobozin.SEARCH_STATE_CHANGED") {
            val isSearching = intent.getBooleanExtra("is_searching", false)
            callback?.invoke(isSearching)
        }
    }
}