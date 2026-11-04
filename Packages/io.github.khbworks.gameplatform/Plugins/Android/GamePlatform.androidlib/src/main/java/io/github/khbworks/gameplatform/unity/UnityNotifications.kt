package io.github.khbworks.gameplatform.unity

import android.app.Activity
import android.content.Context
import io.github.khbworks.gameplatform.notifications.AndroidNotificationDeliveryCheck
import io.github.khbworks.gameplatform.notifications.EXTRA_NOTIFICATION_ID
import io.github.khbworks.gameplatform.notifications.LocalNotifications
import io.github.khbworks.gameplatform.notifications.NotificationChannels
import io.github.khbworks.gameplatform.notifications.NotificationRequest

class UnityNotifications(context: Context) {
    private val appContext = context.applicationContext
    private val notifications = LocalNotifications.create(appContext)

    fun createChannel(id: String, name: String, description: String) =
        NotificationChannels.create(appContext, id, name, description)

    fun schedule(
        id: String,
        title: String,
        body: String,
        triggerAtMillis: Long,
        channelId: String,
        smallIconName: String?,
    ): String {
        val request = NotificationRequest(id, title, body, triggerAtMillis, channelId, smallIconName)
        return notifications.schedule(request).toJson().toString()
    }

    fun cancel(id: String) = notifications.cancel(id)

    fun delivery(channelId: String): String =
        AndroidNotificationDeliveryCheck(appContext).delivery(channelId).name.lowercase()

    // The id of the notification the player tapped to open the app, or null when the app opened another way.
    fun launchNotificationId(activity: Activity): String? = activity.intent?.getStringExtra(EXTRA_NOTIFICATION_ID)
}
