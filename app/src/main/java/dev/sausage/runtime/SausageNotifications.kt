package dev.sausage.runtime

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

internal data class SausageNotificationSpec(
    val scope: String,
    val applicationName: String,
    val id: String,
    val title: String,
    val body: String,
)

internal object SausageNotifications {
    fun show(
        context: Context,
        spec: SausageNotificationSpec,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            throw IllegalStateException("Notifications are disabled for Sausage in Android settings.")
        }
        post(context, manager, spec)
    }

    fun schedule(
        context: Context,
        spec: SausageNotificationSpec,
        atMillis: Long,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            throw IllegalStateException("Notifications are disabled for Sausage in Android settings.")
        }
        ensureChannel(context, spec)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            atMillis,
            checkNotNull(notificationPendingIntent(context, spec, create = true)),
        )
    }

    fun cancel(
        context: Context,
        scope: String,
        id: String,
    ) {
        val spec = SausageNotificationSpec(scope, "Sausage", id, "", "")
        val pendingIntent = notificationPendingIntent(context, spec, create = false)
        if (pendingIntent != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(scope, id))
    }

    @SuppressLint("MissingPermission")
    fun post(
        context: Context,
        manager: NotificationManager,
        spec: SausageNotificationSpec,
    ) {
        val channelId = ensureChannel(context, spec)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(spec.scope, spec.id) xor CONTENT_REQUEST_MASK,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_sausage_notification)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(Notification.BigTextStyle().bigText(spec.body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(notificationId(spec.scope, spec.id), notification)
    }

    private fun ensureChannel(
        context: Context,
        spec: SausageNotificationSpec,
    ): String {
        val channelId = channelId(spec.scope)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "${spec.applicationName} reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Local reminders created by ${spec.applicationName} in Sausage"
            },
        )
        return channelId
    }

    private fun notificationPendingIntent(
        context: Context,
        spec: SausageNotificationSpec,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, SausageNotificationReceiver::class.java).apply {
            action = "$NOTIFICATION_ACTION.${spec.scope.hashCode()}.${spec.id}"
            putExtra(EXTRA_SCOPE, spec.scope)
            putExtra(EXTRA_APPLICATION_NAME, spec.applicationName)
            putExtra(EXTRA_ID, spec.id)
            putExtra(EXTRA_TITLE, spec.title)
            putExtra(EXTRA_BODY, spec.body)
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            notificationId(spec.scope, spec.id),
            intent,
            flags,
        )
    }

    private fun channelId(scope: String): String = "sausage-${scope.hashCode().toUInt().toString(16)}"

    private fun notificationId(scope: String, id: String): Int =
        "$scope\u0000$id".hashCode() and Int.MAX_VALUE

    internal const val EXTRA_SCOPE = "scope"
    internal const val EXTRA_APPLICATION_NAME = "applicationName"
    internal const val EXTRA_ID = "id"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_BODY = "body"

    private const val NOTIFICATION_ACTION = "dev.sausage.runtime.NOTIFY"
    private const val CONTENT_REQUEST_MASK = 0x5A5A5A5A
}

internal class SausageNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scope = intent.getStringExtra(SausageNotifications.EXTRA_SCOPE) ?: return
        val applicationName = intent.getStringExtra(SausageNotifications.EXTRA_APPLICATION_NAME) ?: return
        val id = intent.getStringExtra(SausageNotifications.EXTRA_ID) ?: return
        val title = intent.getStringExtra(SausageNotifications.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(SausageNotifications.EXTRA_BODY) ?: return
        try {
            SausageNotifications.show(
                context,
                SausageNotificationSpec(scope, applicationName, id, title, body),
            )
        } catch (error: Exception) {
            Log.w("Sausage", "Could not deliver a scheduled notification", error)
        }
    }
}
