package io.github.khbworks.gameplatform.purchases

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreErrorKindTest {
    @Test
    fun `maps the Play Billing response codes a consumable flow can return`() {
        val expected = mapOf(
            BillingResponseCode.USER_CANCELED to StoreErrorKind.USER_CANCELED,
            BillingResponseCode.ITEM_UNAVAILABLE to StoreErrorKind.PRODUCT_UNAVAILABLE,
            BillingResponseCode.ITEM_ALREADY_OWNED to StoreErrorKind.ALREADY_OWNED,
            BillingResponseCode.ITEM_NOT_OWNED to StoreErrorKind.NOT_OWNED,
            BillingResponseCode.NETWORK_ERROR to StoreErrorKind.NETWORK,
            BillingResponseCode.SERVICE_DISCONNECTED to StoreErrorKind.SERVICE_DISCONNECTED,
            BillingResponseCode.ERROR to StoreErrorKind.UNKNOWN,
        )

        assertEquals(expected, expected.mapValues { (code, _) -> StoreErrorKind.fromResponseCode(code) })
    }
}
