package io.github.khbworks.gameplatform.purchases

interface PlayBilling {
    fun startConnection(events: PlayBillingEvents)

    fun queryProducts(productIds: List<String>, onResult: (ProductsResult) -> Unit)

    fun launchPurchaseFlow(productId: String, accountId: String?, onLaunched: (FlowLaunch) -> Unit)

    fun consume(purchaseToken: String, onResult: (StoreResponse) -> Unit)

    fun queryUnfinishedPurchases(onResult: (UnfinishedPurchasesResult) -> Unit)
}

interface PlayBillingEvents {
    fun onSetupFinished(response: StoreResponse)

    fun onDisconnected()

    fun onPurchasesUpdated(response: StoreResponse, purchases: List<StorePurchase>)
}

sealed interface FlowLaunch {
    data object Launched : FlowLaunch

    data object ProductUnavailable : FlowLaunch

    data class Failed(val error: StoreError) : FlowLaunch
}
