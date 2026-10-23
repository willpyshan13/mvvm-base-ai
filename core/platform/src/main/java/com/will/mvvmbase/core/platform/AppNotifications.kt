package com.will.mvvmbase.core.platform

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build

object AppNotifications {
    fun show(context: Context, destination: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("examples", context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, 100, destination,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, "examples") else Notification.Builder(context)
        manager.notify(100, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_body))
            .setAutoCancel(true).setContentIntent(pending).build())
    }
}
