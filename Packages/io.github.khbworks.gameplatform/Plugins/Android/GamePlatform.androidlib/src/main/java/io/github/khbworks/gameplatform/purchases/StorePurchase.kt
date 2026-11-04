package io.github.khbworks.gameplatform.purchases

data class StorePurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    // Google Play assigns an order id only after the payment goes through.
    val orderId: String?,
    val quantity: Int,
    val purchaseTimeMillis: Long,
    val state: StorePurchaseState,
    val accountId: String?,
)

enum class StorePurchaseState {
    PURCHASED,
    PENDING,
    UNSPECIFIED,
}

data class StoreProduct(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
)
