package io.github.khbworks.gameplatform.unity

import io.github.khbworks.gameplatform.purchases.BuyResult
import io.github.khbworks.gameplatform.purchases.CompleteResult
import io.github.khbworks.gameplatform.purchases.StoreError
import io.github.khbworks.gameplatform.purchases.StoreErrorKind
import io.github.khbworks.gameplatform.purchases.StorePurchase
import io.github.khbworks.gameplatform.purchases.StorePurchaseState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityMessagesTest {
    @Test
    fun `a purchased result carries the request id, the type and the purchase`() {
        val message = BuyResult.Purchased(purchase(StorePurchaseState.PURCHASED, orderId = "GPA.1")).toMessage(7)

        assertEquals(7, message.getInt("requestId"))
        assertEquals("purchased", message.getString("type"))
        val purchase = message.getJSONObject("purchase")
        assertEquals("googlePlay", purchase.getString("store"))
        assertEquals("token-1", purchase.getString("purchaseId"))
        assertEquals("token-1", purchase.getString("verificationData"))
        assertEquals("GPA.1", purchase.getString("orderId"))
        assertEquals("purchased", purchase.getString("state"))
    }

    @Test
    fun `a purchase without an order id or account id sends null, not an empty string`() {
        val purchase = BuyResult.Pending(purchase(StorePurchaseState.PENDING, orderId = null))
            .toMessage(1)
            .getJSONObject("purchase")

        assertTrue(purchase.isNull("orderId"))
        assertTrue(purchase.isNull("accountId"))
    }

    @Test
    fun `a failed result carries the error kind, code and message`() {
        val error = StoreError(StoreErrorKind.ALREADY_OWNED, 7, "Item is already owned.")

        val message = CompleteResult.Failed(error).toMessage(3)

        assertEquals("failed", message.getString("type"))
        val json = message.getJSONObject("error")
        assertEquals("ALREADY_OWNED", json.getString("kind"))
        assertEquals(7, json.getInt("code"))
    }

    @Test
    fun `a result without a payload has only the request id and the type`() {
        val message = BuyResult.Cancelled.toMessage(2)

        assertEquals(setOf("requestId", "type"), message.keys().asSequence().toSet())
        assertFalse(message.has("purchase"))
    }

    @Test
    fun `an update that no request asked for uses request id 0`() {
        val message = purchaseUpdatedMessage(purchase(StorePurchaseState.PURCHASED, orderId = "GPA.1"))

        assertEquals(0, message.getInt("requestId"))
        assertEquals("purchaseUpdated", message.getString("type"))
        assertEquals("gems_100", JSONObject(message.toString()).getJSONObject("purchase").getJSONArray("productIds")[0])
    }

    private fun purchase(state: StorePurchaseState, orderId: String?) = StorePurchase(
        productIds = listOf("gems_100"),
        purchaseToken = "token-1",
        orderId = orderId,
        quantity = 1,
        purchaseTimeMillis = 1_000L,
        state = state,
        accountId = null,
    )
}
