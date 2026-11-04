package io.github.khbworks.gameplatform.purchases

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumablePurchasesTest {
    private val billing = FakePlayBilling()
    private val updates = RecordingUpdates()
    private val purchases = ConsumablePurchases(billing, updates)

    @Test
    fun `buy connects first and launches the flow with the account id once setup finishes`() {
        val results = mutableListOf<BuyResult>()

        purchases.buy("gems_100", "account-hash", results::add)
        assertEquals(1, billing.connectionAttempts)
        assertTrue(billing.launches.isEmpty())

        billing.finishSetup()

        assertEquals(listOf("gems_100" to "account-hash"), billing.launches)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `buy reports Purchased when the update carries a paid purchase for the product`() {
        val results = connectedBuy("gems_100")

        billing.updatePurchases(BillingResponseCode.OK, purchase("gems_100", StorePurchaseState.PURCHASED))

        assertEquals(listOf(BuyResult.Purchased(purchase("gems_100", StorePurchaseState.PURCHASED))), results)
    }

    @Test
    fun `buy reports Pending when the payment is not finished`() {
        val results = connectedBuy("gems_100")

        billing.updatePurchases(BillingResponseCode.OK, purchase("gems_100", StorePurchaseState.PENDING))

        assertEquals(listOf(BuyResult.Pending(purchase("gems_100", StorePurchaseState.PENDING))), results)
    }

    @Test
    fun `buy reports Cancelled when the user closes the flow`() {
        val results = connectedBuy("gems_100")

        billing.updatePurchases(BillingResponseCode.USER_CANCELED)

        assertEquals(listOf<BuyResult>(BuyResult.Cancelled), results)
    }

    @Test
    fun `buy reports ALREADY_OWNED when an earlier purchase of the product was not consumed`() {
        val results = connectedBuy("gems_100")

        billing.updatePurchases(BillingResponseCode.ITEM_ALREADY_OWNED)

        val failed = results.single() as BuyResult.Failed
        assertEquals(StoreErrorKind.ALREADY_OWNED, failed.error.kind)
    }

    @Test
    fun `buy reports ProductUnavailable when the store has no offer for the product`() {
        billing.launchOutcome = FlowLaunch.ProductUnavailable

        val results = connectedBuy("unknown")

        assertEquals(listOf<BuyResult>(BuyResult.ProductUnavailable), results)
    }

    @Test
    fun `a second buy during an open flow reports AnotherBuyInProgress and doesn't launch`() {
        connectedBuy("gems_100")
        val second = mutableListOf<BuyResult>()

        purchases.buy("gems_500", null, second::add)

        assertEquals(listOf<BuyResult>(BuyResult.AnotherBuyInProgress), second)
        assertEquals(1, billing.launches.size)
    }

    @Test
    fun `an update for another product during a flow goes to the listener and keeps the flow open`() {
        val results = connectedBuy("gems_100")
        val other = purchase("gems_500", StorePurchaseState.PURCHASED)

        billing.updatePurchases(BillingResponseCode.OK, other)

        assertEquals(listOf(other), updates.purchases)
        assertTrue(results.isEmpty())

        billing.updatePurchases(BillingResponseCode.OK, purchase("gems_100", StorePurchaseState.PURCHASED))

        assertEquals(1, results.size)
    }

    @Test
    fun `a buy can start again after the previous flow reports its result`() {
        connectedBuy("gems_100")
        billing.updatePurchases(BillingResponseCode.USER_CANCELED)

        purchases.buy("gems_100", null) {}

        assertEquals(2, billing.launches.size)
    }

    @Test
    fun `updates outside a buy go to the listener`() {
        connect()
        val approved = purchase("gems_100", StorePurchaseState.PURCHASED)

        billing.updatePurchases(BillingResponseCode.OK, approved)

        assertEquals(listOf(approved), updates.purchases)
    }

    @Test
    fun `an error outside a buy goes to the listener as a failure`() {
        connect()

        billing.updatePurchases(BillingResponseCode.SERVICE_UNAVAILABLE)

        assertEquals(listOf(StoreErrorKind.SERVICE_UNAVAILABLE), updates.failures.map { it.kind })
    }

    @Test
    fun `a failed setup fails every request that waited for it`() {
        val products = mutableListOf<ProductsResult>()
        val buys = mutableListOf<BuyResult>()
        purchases.products(listOf("gems_100"), products::add)
        purchases.buy("gems_100", null, buys::add)

        billing.finishSetup(BillingResponseCode.BILLING_UNAVAILABLE)

        assertEquals(StoreErrorKind.BILLING_UNAVAILABLE, (products.single() as ProductsResult.Failed).error.kind)
        assertEquals(StoreErrorKind.BILLING_UNAVAILABLE, (buys.single() as BuyResult.Failed).error.kind)
        assertEquals(1, billing.connectionAttempts)
    }

    @Test
    fun `the next call connects again after a disconnect`() {
        connect()
        billing.disconnect()

        purchases.unfinishedPurchases {}

        assertEquals(2, billing.connectionAttempts)
    }

    @Test
    fun `complete consumes the purchase token and reports Completed`() {
        connect()
        val results = mutableListOf<CompleteResult>()

        purchases.complete("token-1", results::add)

        assertEquals(listOf("token-1"), billing.consumedTokens)
        assertEquals(listOf<CompleteResult>(CompleteResult.Completed), results)
    }

    @Test
    fun `complete reports NotOwned when the store no longer has the purchase`() {
        connect()
        billing.consumeResponse = StoreResponse(BillingResponseCode.ITEM_NOT_OWNED, "")
        val results = mutableListOf<CompleteResult>()

        purchases.complete("token-1", results::add)

        assertEquals(listOf<CompleteResult>(CompleteResult.NotOwned), results)
    }

    private fun connect() {
        purchases.unfinishedPurchases {}
        billing.finishSetup()
    }

    private fun connectedBuy(productId: String): MutableList<BuyResult> {
        connect()
        val results = mutableListOf<BuyResult>()
        purchases.buy(productId, null, results::add)
        return results
    }

    private fun purchase(productId: String, state: StorePurchaseState) = StorePurchase(
        productIds = listOf(productId),
        purchaseToken = "token-$productId",
        orderId = if (state == StorePurchaseState.PURCHASED) "GPA.$productId" else null,
        quantity = 1,
        purchaseTimeMillis = 1_000L,
        state = state,
        accountId = null,
    )
}

private class FakePlayBilling : PlayBilling {
    var connectionAttempts = 0
    val launches = mutableListOf<Pair<String, String?>>()
    val consumedTokens = mutableListOf<String>()
    var launchOutcome: FlowLaunch = FlowLaunch.Launched
    var consumeResponse = StoreResponse(BillingResponseCode.OK, "")
    private lateinit var events: PlayBillingEvents

    override fun startConnection(events: PlayBillingEvents) {
        connectionAttempts++
        this.events = events
    }

    override fun queryProducts(productIds: List<String>, onResult: (ProductsResult) -> Unit) {
        onResult(ProductsResult.Loaded(emptyList(), productIds))
    }

    override fun launchPurchaseFlow(productId: String, accountId: String?, onLaunched: (FlowLaunch) -> Unit) {
        launches += productId to accountId
        onLaunched(launchOutcome)
    }

    override fun consume(purchaseToken: String, onResult: (StoreResponse) -> Unit) {
        consumedTokens += purchaseToken
        onResult(consumeResponse)
    }

    override fun queryUnfinishedPurchases(onResult: (UnfinishedPurchasesResult) -> Unit) {
        onResult(UnfinishedPurchasesResult.Loaded(emptyList()))
    }

    fun finishSetup(code: Int = BillingResponseCode.OK) = events.onSetupFinished(StoreResponse(code, ""))

    fun disconnect() = events.onDisconnected()

    fun updatePurchases(code: Int, vararg purchases: StorePurchase) =
        events.onPurchasesUpdated(StoreResponse(code, ""), purchases.toList())
}

private class RecordingUpdates : PurchaseUpdateListener {
    val purchases = mutableListOf<StorePurchase>()
    val failures = mutableListOf<StoreError>()

    override fun onPurchaseUpdated(purchase: StorePurchase) {
        purchases += purchase
    }

    override fun onPurchaseUpdateFailed(error: StoreError) {
        failures += error
    }
}
