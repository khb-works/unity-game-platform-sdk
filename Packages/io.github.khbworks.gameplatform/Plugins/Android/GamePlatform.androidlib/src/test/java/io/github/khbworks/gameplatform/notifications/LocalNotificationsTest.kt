package io.github.khbworks.gameplatform.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNotificationsTest {
    private val alarms = FakeAlarms()
    private val store = InMemoryStore()
    private var delivery = NotificationDelivery.ALLOWED
    private val notifications = LocalNotifications(alarms, store) { delivery }

    @Test
    fun `schedule uses an exact alarm when the app may set one`() {
        alarms.exactAllowed = true

        val result = notifications.schedule(request("stamina"))

        assertEquals(ScheduleResult(AlarmTiming.EXACT, NotificationDelivery.ALLOWED), result)
        assertEquals(mapOf("stamina" to (5_000L to AlarmTiming.EXACT)), alarms.set)
    }

    @Test
    fun `schedule falls back to an inexact alarm and says so when exact alarms are denied`() {
        alarms.exactAllowed = false

        val result = notifications.schedule(request("stamina"))

        assertEquals(AlarmTiming.INEXACT, result.timing)
        assertEquals(5_000L to AlarmTiming.INEXACT, alarms.set["stamina"])
    }

    @Test
    fun `schedule still sets the alarm and reports BLOCKED when notifications are off`() {
        delivery = NotificationDelivery.BLOCKED

        val result = notifications.schedule(request("stamina"))

        assertEquals(NotificationDelivery.BLOCKED, result.delivery)
        assertTrue("stamina" in alarms.set)
    }

    @Test
    fun `cancel removes the alarm and the stored request`() {
        notifications.schedule(request("stamina"))

        notifications.cancel("stamina")

        assertTrue(alarms.set.isEmpty())
        assertNull(store.get("stamina"))
    }

    @Test
    fun `takeFired returns the stored request once`() {
        notifications.schedule(request("stamina"))

        assertEquals(request("stamina"), notifications.takeFired("stamina"))
        assertNull(notifications.takeFired("stamina"))
    }

    @Test
    fun `rescheduleAll sets an alarm again for every stored request`() {
        notifications.schedule(request("stamina"))
        notifications.schedule(request("daily-reward"))
        alarms.set.clear()

        val results = notifications.rescheduleAll()

        assertEquals(2, results.size)
        assertEquals(setOf("stamina", "daily-reward"), alarms.set.keys)
    }

    @Test
    fun `a request survives the JSON round trip, with and without an icon name`() {
        val withIcon = request("stamina").copy(smallIconName = "ic_stat_game")
        val withoutIcon = request("stamina")

        assertEquals(withIcon, NotificationRequest.fromJson(JSONObject(withIcon.toJson().toString())))
        assertEquals(withoutIcon, NotificationRequest.fromJson(JSONObject(withoutIcon.toJson().toString())))
    }

    private fun request(id: String) = NotificationRequest(
        id = id,
        title = "Stamina is full",
        body = "Come back and play",
        triggerAtMillis = 5_000L,
        channelId = "game",
        smallIconName = null,
    )
}

private class FakeAlarms : NotificationAlarms {
    var exactAllowed = true
    val set = mutableMapOf<String, Pair<Long, AlarmTiming>>()

    override fun canScheduleExact() = exactAllowed

    override fun set(id: String, triggerAtMillis: Long, timing: AlarmTiming) {
        set[id] = triggerAtMillis to timing
    }

    override fun cancel(id: String) {
        set.remove(id)
    }
}

private class InMemoryStore : ScheduledNotificationStore {
    private val requests = linkedMapOf<String, NotificationRequest>()

    override fun save(request: NotificationRequest) {
        requests[request.id] = request
    }

    override fun get(id: String) = requests[id]

    override fun all() = requests.values.toList()

    override fun remove(id: String) {
        requests.remove(id)
    }
}
