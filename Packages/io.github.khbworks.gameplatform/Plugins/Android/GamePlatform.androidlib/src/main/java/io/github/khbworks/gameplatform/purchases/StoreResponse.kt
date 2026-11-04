package io.github.khbworks.gameplatform.purchases

import com.android.billingclient.api.BillingClient.BillingResponseCode

data class StoreResponse(val code: Int, val message: String) {
    val isOk: Boolean get() = code == BillingResponseCode.OK
}

fun StoreResponse.toError(): StoreError = StoreError(StoreErrorKind.fromResponseCode(code), code, message)

data class StoreError(val kind: StoreErrorKind, val responseCode: Int, val message: String)

enum class StoreErrorKind {
    USER_CANCELED,
    SERVICE_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    SERVICE_TIMEOUT,
    BILLING_UNAVAILABLE,
    PRODUCT_UNAVAILABLE,
    ALREADY_OWNED,
    NOT_OWNED,
    NETWORK,
    FEATURE_NOT_SUPPORTED,
    DEVELOPER_ERROR,
    UNKNOWN,
    ;

    companion object {
        @Suppress("DEPRECATION")
        fun fromResponseCode(code: Int): StoreErrorKind = when (code) {
            BillingResponseCode.USER_CANCELED -> USER_CANCELED
            BillingResponseCode.SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE
            BillingResponseCode.SERVICE_DISCONNECTED -> SERVICE_DISCONNECTED
            BillingResponseCode.SERVICE_TIMEOUT -> SERVICE_TIMEOUT
            BillingResponseCode.BILLING_UNAVAILABLE -> BILLING_UNAVAILABLE
            BillingResponseCode.ITEM_UNAVAILABLE -> PRODUCT_UNAVAILABLE
            BillingResponseCode.ITEM_ALREADY_OWNED -> ALREADY_OWNED
            BillingResponseCode.ITEM_NOT_OWNED -> NOT_OWNED
            BillingResponseCode.NETWORK_ERROR -> NETWORK
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> FEATURE_NOT_SUPPORTED
            BillingResponseCode.DEVELOPER_ERROR -> DEVELOPER_ERROR
            else -> UNKNOWN
        }
    }
}
