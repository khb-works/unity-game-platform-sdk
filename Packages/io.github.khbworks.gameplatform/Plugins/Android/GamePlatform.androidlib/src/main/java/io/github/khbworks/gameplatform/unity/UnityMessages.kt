package io.github.khbworks.gameplatform.unity

import io.github.khbworks.gameplatform.notifications.ScheduleResult
import io.github.khbworks.gameplatform.purchases.BuyResult
import io.github.khbworks.gameplatform.purchases.CompleteResult
import io.github.khbworks.gameplatform.purchases.ProductsResult
import io.github.khbworks.gameplatform.purchases.StoreError
import io.github.khbworks.gameplatform.purchases.StoreProduct
import io.github.khbworks.gameplatform.purchases.StorePurchase
import io.github.khbworks.gameplatform.purchases.UnfinishedPurchasesResult
import org.json.JSONArray
import org.json.JSONObject

// The C# side reads these with JsonUtility, which needs one flat shape per message: a type field plus optional fields.
interface UnityMessageReceiver {
    fun onMessage(json: String)
}

internal fun BuyResult.toMessage(requestId: Int): JSONObject = when (this) {
    is BuyResult.Purchased -> message(requestId, "purchased").put("purchase", purchase.toJson())
    is BuyResult.Pending -> message(requestId, "pending").put("purchase", purchase.toJson())
    BuyResult.Cancelled -> message(requestId, "cancelled")
    BuyResult.ProductUnavailable -> message(requestId, "productUnavailable")
    BuyResult.AnotherBuyInProgress -> message(requestId, "anotherBuyInProgress")
    is BuyResult.Failed -> message(requestId, "failed").put("error", error.toJson())
}

internal fun CompleteResult.toMessage(requestId: Int): JSONObject = when (this) {
    CompleteResult.Completed -> message(requestId, "completed")
    CompleteResult.NotOwned -> message(requestId, "notOwned")
    is CompleteResult.Failed -> message(requestId, "failed").put("error", error.toJson())
}

internal fun ProductsResult.toMessage(requestId: Int): JSONObject = when (this) {
    is ProductsResult.Loaded -> message(requestId, "loaded")
        .put("products", JSONArray(products.map { it.toJson() }))
        .put("unavailableIds", JSONArray(unavailableIds))
    is ProductsResult.Failed -> message(requestId, "failed").put("error", error.toJson())
}

internal fun UnfinishedPurchasesResult.toMessage(requestId: Int): JSONObject = when (this) {
    is UnfinishedPurchasesResult.Loaded -> message(requestId, "loaded")
        .put("purchases", JSONArray(purchases.map { it.toJson() }))
    is UnfinishedPurchasesResult.Failed -> message(requestId, "failed").put("error", error.toJson())
}

internal fun purchaseUpdatedMessage(purchase: StorePurchase): JSONObject =
    message(UPDATE_REQUEST_ID, "purchaseUpdated").put("purchase", purchase.toJson())

internal fun purchaseUpdateFailedMessage(error: StoreError): JSONObject =
    message(UPDATE_REQUEST_ID, "purchaseUpdateFailed").put("error", error.toJson())

internal fun ScheduleResult.toJson(): JSONObject = JSONObject()
    .put("timing", timing.name.lowercase())
    .put("delivery", delivery.name.lowercase())

internal fun StorePurchase.toJson(): JSONObject = JSONObject()
    .put("store", "googlePlay")
    .put("productIds", JSONArray(productIds))
    .put("purchaseId", purchaseToken)
    .put("verificationData", purchaseToken)
    .put("orderId", orderId ?: JSONObject.NULL)
    .put("quantity", quantity)
    .put("purchaseTimeMillis", purchaseTimeMillis)
    .put("state", state.name.lowercase())
    .put("accountId", accountId ?: JSONObject.NULL)

private fun StoreProduct.toJson(): JSONObject = JSONObject()
    .put("productId", productId)
    .put("title", title)
    .put("description", description)
    .put("formattedPrice", formattedPrice)
    .put("priceMicros", priceMicros)
    .put("currencyCode", currencyCode)

private fun StoreError.toJson(): JSONObject = JSONObject()
    .put("kind", kind.name)
    .put("code", responseCode)
    .put("message", message)

private fun message(requestId: Int, type: String): JSONObject = JSONObject()
    .put("requestId", requestId)
    .put("type", type)

// Request ids from C# start at 1, so 0 marks a purchase update that no request asked for.
internal const val UPDATE_REQUEST_ID = 0
