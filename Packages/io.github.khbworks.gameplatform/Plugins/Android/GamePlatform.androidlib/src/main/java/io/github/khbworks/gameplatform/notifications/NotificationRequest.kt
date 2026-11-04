package io.github.khbworks.gameplatform.notifications

import org.json.JSONObject

data class NotificationRequest(
    val id: String,
    val title: String,
    val body: String,
    val triggerAtMillis: Long,
    val channelId: String,
    // A drawable resource name. Without one, the notification uses the app icon.
    val smallIconName: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("triggerAtMillis", triggerAtMillis)
        .put("channelId", channelId)
        .put("smallIconName", smallIconName ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = NotificationRequest(
            id = json.getString("id"),
            title = json.getString("title"),
            body = json.getString("body"),
            triggerAtMillis = json.getLong("triggerAtMillis"),
            channelId = json.getString("channelId"),
            smallIconName = if (json.isNull("smallIconName")) null else json.getString("smallIconName"),
        )
    }
}

data class ScheduleResult(val timing: AlarmTiming, val delivery: NotificationDelivery)

enum class AlarmTiming {
    EXACT,

    // Android may delay the alarm to save battery. Android 14 and later deny exact alarms to most apps by default.
    INEXACT,
}

enum class NotificationDelivery {
    ALLOWED,

    // The user turned notifications off for the app or the channel, or hasn't granted POST_NOTIFICATIONS.
    BLOCKED,
}
