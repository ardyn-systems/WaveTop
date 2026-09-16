package com.ardyn.wavetop.survey.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

internal fun registerExported(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}
