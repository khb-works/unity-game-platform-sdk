package io.github.khbworks.gameplatform.unity

import android.app.Activity
import android.os.Handler
import android.os.Looper
import io.github.khbworks.gameplatform.purchases.ConsumablePurchases
import io.github.khbworks.gameplatform.purchases.PlayBillingClient
import io.github.khbworks.gameplatform.purchases.PurchaseUpdateListener
import io.github.khbworks.gameplatform.purchases.StoreError
import io.github.khbworks.gameplatform.purchases.StorePurchase

// Unity calls these methods on its own main thread, which isn't the Android main thread.
// Every call moves to the Android main thread, where ConsumablePurchases keeps its state.
class UnityPurchases(activity: Activity, private val receiver: UnityMessageReceiver) {
    private val mainThread = Handler(Looper.getMainLooper())

    private val purchases = ConsumablePurchases(
        billing = PlayBillingClient(activity),
        updates = object : PurchaseUpdateListener {
            override fun onPurchaseUpdated(purchase: StorePurchase) =
                receiver.onMessage(purchaseUpdatedMessage(purchase).toString())

            override fun onPurchaseUpdateFailed(error: StoreError) =
                receiver.onMessage(purchaseUpdateFailedMessage(error).toString())
        },
    )

    fun products(requestId: Int, productIds: Array<String>) {
        mainThread.post {
            purchases.products(productIds.toList()) { receiver.onMessage(it.toMessage(requestId).toString()) }
        }
    }

    fun buy(requestId: Int, productId: String, accountId: String?) {
        mainThread.post {
            purchases.buy(productId, accountId) { receiver.onMessage(it.toMessage(requestId).toString()) }
        }
    }

    fun complete(requestId: Int, purchaseId: String) {
        mainThread.post {
            purchases.complete(purchaseId) { receiver.onMessage(it.toMessage(requestId).toString()) }
        }
    }

    fun unfinishedPurchases(requestId: Int) {
        mainThread.post {
            purchases.unfinishedPurchases { receiver.onMessage(it.toMessage(requestId).toString()) }
        }
    }
}
