package io.github.khbworks.gameplatform.purchases

sealed interface BuyResult {
    // The store has taken the payment. Grant the item on the game server, then call complete.
    data class Purchased(val purchase: StorePurchase) : BuyResult

    // The payment isn't finished yet (for example, cash at a store). Don't grant the item.
    data class Pending(val purchase: StorePurchase) : BuyResult

    data object Cancelled : BuyResult

    data object ProductUnavailable : BuyResult

    data object AnotherBuyInProgress : BuyResult

    data class Failed(val error: StoreError) : BuyResult
}

sealed interface CompleteResult {
    data object Completed : CompleteResult

    // The purchase was already consumed, or this account never owned it.
    data object NotOwned : CompleteResult

    data class Failed(val error: StoreError) : CompleteResult
}

sealed interface ProductsResult {
    data class Loaded(val products: List<StoreProduct>, val unavailableIds: List<String>) : ProductsResult

    data class Failed(val error: StoreError) : ProductsResult
}

sealed interface UnfinishedPurchasesResult {
    data class Loaded(val purchases: List<StorePurchase>) : UnfinishedPurchasesResult

    data class Failed(val error: StoreError) : UnfinishedPurchasesResult
}

interface PurchaseUpdateListener {
    // A purchase that arrived outside a buy call, such as a pending payment that went through.
    fun onPurchaseUpdated(purchase: StorePurchase)

    fun onPurchaseUpdateFailed(error: StoreError)
}
