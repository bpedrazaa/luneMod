package com.demonlab.lune.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.demonlab.lune.R
import com.demonlab.lune.ui.activities.Lune

/**
 * Progress notification for the tag pass, so a long scan is visible without
 * the app in front. Updates only between batches, so posting is cheap.
 */
object ScanNotifier {

    private const val CHANNEL_ID = "library_scan"
    private const val NOTIFICATION_ID = 4402

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.mod_scan_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.mod_scan_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun builder(context: Context, title: String, text: String): NotificationCompat.Builder {
        val open = Intent(context, Lune::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setSilent(true)
    }

    fun update(context: Context, processed: Int, total: Int) {
        if (!canPost(context)) return
        ensureChannel(context)
        val notification = builder(
            context,
            context.getString(R.string.mod_scan_notification_title),
            context.getString(R.string.mod_scan_progress, processed, total)
        )
            .setProgress(total.coerceAtLeast(1), processed.coerceAtLeast(0), false)
            .setOngoing(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun finish(context: Context, songCount: Int) {
        if (!canPost(context)) return
        ensureChannel(context)
        val notification = builder(
            context,
            context.getString(R.string.mod_scan_notification_done),
            context.getString(R.string.mod_scan_ready, songCount)
        )
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
