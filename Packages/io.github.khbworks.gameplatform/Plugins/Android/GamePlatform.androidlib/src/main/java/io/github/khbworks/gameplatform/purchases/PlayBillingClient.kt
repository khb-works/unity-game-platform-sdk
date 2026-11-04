package io.github.khbworks.gameplatform.purchases

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams

class PlayBillingClient(private val activity: Activity) : PlayBilling {
    private val mainThread = Handler(Looper.getMainLooper())
    private var events: PlayBillingEvents? = null

    private val client: BillingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener { result, purchases ->
            val response = result.toStoreResponse()
            val storePurchases = purchases.orEmpty().map { it.toStorePurchase() }
            mainThread.post { events?.onPurchasesUpdated(response, storePurchases) }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun startConnection(events: PlayBillingEvents) {
        this.events = events
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val response = result.toStoreResponse()
                    mainThread.post { events.onSetupFinished(response) }
                }

                override fun onBillingServiceDisconnected() {
                    mainThread.post { events.onDisconnected() }
                }
            },
        )
    }

    override fun queryProducts(productIds: List<String>, onResult: (ProductsResult) -> Unit) {
        queryProductDetails(productIds) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) {
                onResult(ProductsResult.Failed(result.toStoreResponse().toError()))
                return@queryProductDetails
            }
            val products = details.productDetailsList.mapNotNull { it.toStoreProduct() }
            val loadedIds = products.map { it.productId }.toSet()
            onResult(ProductsResult.Loaded(products, productIds.filterNot { it in loadedIds }))
        }
    }

    override fun launchPurchaseFlow(productId: String, accountId: String?, onLaunched: (FlowLaunch) -> Unit) {
        queryProductDetails(listOf(productId)) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) {
                onLaunched(FlowLaunch.Failed(result.toStoreResponse().toError()))
                return@queryProductDetails
            }
            val product = details.productDetailsList.firstOrNull { it.productId == productId }
            val offer = product?.firstOneTimeOffer()
            if (product == null || offer == null) {
                onLaunched(FlowLaunch.ProductUnavailable)
                return@queryProductDetails
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .apply { offer.offerToken?.let { setOfferToken(it) } }
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .apply { if (accountId != null) setObfuscatedAccountId(accountId) }
                .build()
            val launch = client.launchBillingFlow(activity, flowParams)
            onLaunched(
                if (launch.responseCode == BillingResponseCode.OK) {
                    FlowLaunch.Launched
                } else {
                    FlowLaunch.Failed(launch.toStoreResponse().toError())
                },
            )
        }
    }

    override fun consume(purchaseToken: String, onResult: (StoreResponse) -> Unit) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumeAsync(params) { result, _ ->
            val response = result.toStoreResponse()
            mainThread.post { onResult(response) }
        }
    }

    override fun queryUnfinishedPurchases(onResult: (UnfinishedPurchasesResult) -> Unit) {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            val outcome = if (result.responseCode == BillingResponseCode.OK) {
                UnfinishedPurchasesResult.Loaded(purchases.map { it.toStorePurchase() })
            } else {
                UnfinishedPurchasesResult.Failed(result.toStoreResponse().toError())
            }
            mainThread.post { onResult(outcome) }
        }
    }

    private fun queryProductDetails(
        productIds: List<String>,
        onResult: (BillingResult, QueryProductDetailsResult) -> Unit,
    ) {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, details ->
            mainThread.post { onResult(result, details) }
        }
    }
}

private fun BillingResult.toStoreResponse() = StoreResponse(responseCode, debugMessage)

private fun ProductDetails.firstOneTimeOffer(): ProductDetails.OneTimePurchaseOfferDetails? =
    oneTimePurchaseOfferDetailsList?.firstOrNull()

private fun ProductDetails.toStoreProduct(): StoreProduct? {
    val offer = firstOneTimeOffer() ?: return null
    return StoreProduct(
        productId = productId,
        title = title,
        description = description,
        formattedPrice = offer.formattedPrice,
        priceMicros = offer.priceAmountMicros,
        currencyCode = offer.priceCurrencyCode,
    )
}

private fun Purchase.toStorePurchase() = StorePurchase(
    productIds = products,
    purchaseToken = purchaseToken,
    orderId = orderId,
    quantity = quantity,
    purchaseTimeMillis = purchaseTime,
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> StorePurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> StorePurchaseState.PENDING
        else -> StorePurchaseState.UNSPECIFIED
    },
    accountId = accountIdentifiers?.obfuscatedAccountId,
)
