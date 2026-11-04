package io.github.khbworks.gameplatform.purchases

import com.android.billingclient.api.BillingClient.BillingResponseCode

// Call every method on the main thread. PlayBilling implementations must also call back on it.
class ConsumablePurchases(
    private val billing: PlayBilling,
    private val updates: PurchaseUpdateListener,
) {
    private var connection: Connection = Connection.Disconnected
    private var activeBuy: ActiveBuy? = null

    fun products(productIds: List<String>, onResult: (ProductsResult) -> Unit) {
        whenConnected(onError = { onResult(ProductsResult.Failed(it)) }) {
            billing.queryProducts(productIds, onResult)
        }
    }

    fun buy(productId: String, accountId: String?, onResult: (BuyResult) -> Unit) {
        if (activeBuy != null) {
            onResult(BuyResult.AnotherBuyInProgress)
            return
        }
        val buy = ActiveBuy(productId, onResult)
        activeBuy = buy
        whenConnected(onError = { finish(buy, BuyResult.Failed(it)) }) {
            billing.launchPurchaseFlow(productId, accountId) { launch ->
                when (launch) {
                    FlowLaunch.Launched -> Unit
                    FlowLaunch.ProductUnavailable -> finish(buy, BuyResult.ProductUnavailable)
                    is FlowLaunch.Failed -> finish(buy, BuyResult.Failed(launch.error))
                }
            }
        }
    }

    fun complete(purchaseToken: String, onResult: (CompleteResult) -> Unit) {
        whenConnected(onError = { onResult(CompleteResult.Failed(it)) }) {
            billing.consume(purchaseToken) { response ->
                onResult(
                    when {
                        response.isOk -> CompleteResult.Completed
                        response.code == BillingResponseCode.ITEM_NOT_OWNED -> CompleteResult.NotOwned
                        else -> CompleteResult.Failed(response.toError())
                    },
                )
            }
        }
    }

    fun unfinishedPurchases(onResult: (UnfinishedPurchasesResult) -> Unit) {
        whenConnected(onError = { onResult(UnfinishedPurchasesResult.Failed(it)) }) {
            billing.queryUnfinishedPurchases(onResult)
        }
    }

    private val events = object : PlayBillingEvents {
        override fun onSetupFinished(response: StoreResponse) {
            val waiting = (connection as? Connection.Connecting)?.waiting.orEmpty()
            connection = if (response.isOk) Connection.Connected else Connection.Disconnected
            for (request in waiting) {
                if (response.isOk) request.action() else request.onError(response.toError())
            }
        }

        override fun onDisconnected() {
            connection = Connection.Disconnected
        }

        override fun onPurchasesUpdated(response: StoreResponse, purchases: List<StorePurchase>) {
            val buy = activeBuy
            if (buy == null) {
                reportOutsideBuy(response, purchases)
                return
            }
            when {
                response.isOk -> {
                    val (forBuy, others) = purchases.partition { buy.productId in it.productIds }
                    others.forEach(updates::onPurchaseUpdated)
                    forBuy.drop(1).forEach(updates::onPurchaseUpdated)
                    val purchase = forBuy.firstOrNull()
                    when {
                        purchase != null -> finish(buy, purchase.toBuyResult())
                        // Only other products were updated, so the flow for this product is still open.
                        others.isNotEmpty() -> Unit
                        else -> finish(buy, BuyResult.Failed(response.toError()))
                    }
                }
                response.code == BillingResponseCode.USER_CANCELED -> finish(buy, BuyResult.Cancelled)
                else -> finish(buy, BuyResult.Failed(response.toError()))
            }
        }
    }

    private fun reportOutsideBuy(response: StoreResponse, purchases: List<StorePurchase>) {
        if (response.isOk) {
            purchases.forEach(updates::onPurchaseUpdated)
        } else {
            updates.onPurchaseUpdateFailed(response.toError())
        }
    }

    private fun finish(buy: ActiveBuy, result: BuyResult) {
        if (activeBuy === buy) activeBuy = null
        buy.onResult(result)
    }

    private fun whenConnected(onError: (StoreError) -> Unit, action: () -> Unit) {
        val request = WaitingRequest(action, onError)
        when (val current = connection) {
            Connection.Connected -> action()
            is Connection.Connecting -> current.waiting += request
            Connection.Disconnected -> {
                connection = Connection.Connecting(mutableListOf(request))
                billing.startConnection(events)
            }
        }
    }

    private class ActiveBuy(val productId: String, val onResult: (BuyResult) -> Unit)

    private class WaitingRequest(val action: () -> Unit, val onError: (StoreError) -> Unit)

    private sealed interface Connection {
        data object Disconnected : Connection

        class Connecting(val waiting: MutableList<WaitingRequest>) : Connection

        data object Connected : Connection
    }
}

private fun StorePurchase.toBuyResult(): BuyResult = when (state) {
    StorePurchaseState.PURCHASED -> BuyResult.Purchased(this)
    StorePurchaseState.PENDING, StorePurchaseState.UNSPECIFIED -> BuyResult.Pending(this)
}
