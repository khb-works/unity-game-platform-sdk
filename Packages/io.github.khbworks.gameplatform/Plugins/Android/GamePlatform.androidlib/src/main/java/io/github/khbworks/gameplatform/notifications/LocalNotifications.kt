package io.github.khbworks.gameplatform.notifications

import android.content.Context

interface NotificationAlarms {
    fun canScheduleExact(): Boolean

    fun set(id: String, triggerAtMillis: Long, timing: AlarmTiming)

    fun cancel(id: String)
}

interface ScheduledNotificationStore {
    fun save(request: NotificationRequest)

    fun get(id: String): NotificationRequest?

    fun all(): List<NotificationRequest>

    fun remove(id: String)
}

fun interface NotificationDeliveryCheck {
    fun delivery(channelId: String): NotificationDelivery
}

class LocalNotifications(
    private val alarms: NotificationAlarms,
    private val store: ScheduledNotificationStore,
    private val deliveryCheck: NotificationDeliveryCheck,
) {
    fun schedule(request: NotificationRequest): ScheduleResult {
        val timing = if (alarms.canScheduleExact()) AlarmTiming.EXACT else AlarmTiming.INEXACT
        store.save(request)
        alarms.set(request.id, request.triggerAtMillis, timing)
        return ScheduleResult(timing, deliveryCheck.delivery(request.channelId))
    }

    fun cancel(id: String) {
        alarms.cancel(id)
        store.remove(id)
    }

    // Android clears alarms on reboot. A trigger time already in the past fires right away.
    fun rescheduleAll(): List<ScheduleResult> = store.all().map(::schedule)

    fun takeFired(id: String): NotificationRequest? {
        val request = store.get(id) ?: return null
        store.remove(id)
        return request
    }

    companion object {
        fun create(context: Context): LocalNotifications {
            val appContext = context.applicationContext
            return LocalNotifications(
                alarms = AndroidNotificationAlarms(appContext),
                store = SharedPreferencesNotificationStore(appContext),
                deliveryCheck = AndroidNotificationDeliveryCheck(appContext),
            )
        }
    }
}
