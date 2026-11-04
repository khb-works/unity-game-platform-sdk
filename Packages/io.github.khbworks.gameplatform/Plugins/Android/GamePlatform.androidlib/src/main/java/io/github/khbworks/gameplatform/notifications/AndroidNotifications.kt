package io.github.khbworks.gameplatform.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

class AndroidNotificationAlarms(private val context: Context) : NotificationAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun set(id: String, triggerAtMillis: Long, timing: AlarmTiming) {
        val intent = alarmIntent(id)
        when (timing) {
            AlarmTiming.EXACT -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
            AlarmTiming.INEXACT -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        }
    }

    override fun cancel(id: String) {
        alarmManager.cancel(alarmIntent(id))
        NotificationManagerCompat.from(context).cancel(id, SHOWN_NOTIFICATION_ID)
    }

    private fun alarmIntent(id: String): PendingIntent {
        // The data URI keeps one PendingIntent per notification id.
        val intent = Intent(context, NotificationAlarmReceiver::class.java).setData(notificationUri(id))
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AndroidNotificationDeliveryCheck(private val context: Context) : NotificationDeliveryCheck {
    override fun delivery(channelId: String): NotificationDelivery {
        val manager = NotificationManagerCompat.from(context)
        val channelBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        return if (manager.areNotificationsEnabled() && !channelBlocked) {
            NotificationDelivery.ALLOWED
        } else {
            NotificationDelivery.BLOCKED
        }
    }
}

class SharedPreferencesNotificationStore(context: Context) : ScheduledNotificationStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(request: NotificationRequest) {
        preferences.edit().putString(request.id, request.toJson().toString()).apply()
    }

    override fun get(id: String): NotificationRequest? =
        preferences.getString(id, null)?.let { NotificationRequest.fromJson(JSONObject(it)) }

    override fun all(): List<NotificationRequest> =
        preferences.all.values.filterIsInstance<String>().map { NotificationRequest.fromJson(JSONObject(it)) }

    override fun remove(id: String) {
        preferences.edit().remove(id).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "io.github.khbworks.gameplatform.notifications"
    }
}

object NotificationChannels {
    fun create(context: Context, id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = description
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.schemeSpecificPart ?: return
        val request = LocalNotifications.create(context).takeFired(id) ?: return
        show(context, request)
    }

    private fun show(context: Context, request: NotificationRequest) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, request.channelId)
            .setSmallIcon(smallIcon(context, request.smallIconName))
            .setContentTitle(request.title)
            .setContentText(request.body)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, request.id))
            .build()
        try {
            manager.notify(request.id, SHOWN_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check above and notify.
        }
    }

    private fun smallIcon(context: Context, name: String?): Int {
        val named = name?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
        return if (named != 0) named else context.applicationInfo.icon
    }

    private fun openAppIntent(context: Context, id: String): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(EXTRA_NOTIFICATION_ID, id)
        // Unity reads the intent data as a deep link, so the id goes in the identifier instead of the data URI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) launch.setIdentifier(id)
        return PendingIntent.getActivity(
            context,
            id.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        LocalNotifications.create(context).rescheduleAll()
    }
}

const val EXTRA_NOTIFICATION_ID = "io.github.khbworks.gameplatform.NOTIFICATION_ID"

private const val SHOWN_NOTIFICATION_ID = 1

private fun notificationUri(id: String): Uri = Uri.fromParts("gameplatform-notification", id, null)
