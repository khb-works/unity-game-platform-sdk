import Foundation
import StoreKit

public struct StorePurchase: Equatable {
    public let productId: String
    public let purchaseId: String
    // The JWS the game server sends to the App Store Server API.
    public let verificationData: String
    public let quantity: Int
    public let purchaseDate: Date
    public let accountToken: UUID?
    public let localVerification: LocalVerification

    public init(
        productId: String,
        purchaseId: String,
        verificationData: String,
        quantity: Int,
        purchaseDate: Date,
        accountToken: UUID?,
        localVerification: LocalVerification
    ) {
        self.productId = productId
        self.purchaseId = purchaseId
        self.verificationData = verificationData
        self.quantity = quantity
        self.purchaseDate = purchaseDate
        self.accountToken = accountToken
        self.localVerification = localVerification
    }
}

public enum LocalVerification: Equatable {
    case verified
    // StoreKit couldn't verify the signed transaction on the device. The server check decides.
    case failed(reason: String)
}

public struct StoreProduct: Equatable {
    public let productId: String
    public let displayName: String
    public let description: String
    public let displayPrice: String
    public let price: Decimal
    public let currencyCode: String
}

public enum BuyResult: Equatable {
    // The store has taken the payment. Grant the item on the game server, then call complete.
    case purchased(StorePurchase)
    // Ask to Buy or an extra payment step. The transaction arrives later as an update.
    case pending
    case cancelled
    case productUnavailable
    case anotherBuyInProgress
    case failed(StoreError)
}

public enum CompleteResult: Equatable {
    case completed
    // The transaction was already finished, or it doesn't belong to this Apple account.
    case notOwned
}

public enum ProductsResult: Equatable {
    case loaded(products: [StoreProduct], unavailableIds: [String])
    case failed(StoreError)
}

public struct StoreError: Error, Equatable {
    public let kind: StoreErrorKind
    public let message: String

    public init(kind: StoreErrorKind, message: String) {
        self.kind = kind
        self.message = message
    }

    public init(_ error: Error) {
        self.init(kind: StoreErrorKind(error), message: String(describing: error))
    }
}

// The raw values match the Android error kinds, so the C# side reads one set of names.
public enum StoreErrorKind: String {
    case userCanceled = "USER_CANCELED"
    case serviceUnavailable = "SERVICE_UNAVAILABLE"
    case billingUnavailable = "BILLING_UNAVAILABLE"
    case productUnavailable = "PRODUCT_UNAVAILABLE"
    case network = "NETWORK"
    case developerError = "DEVELOPER_ERROR"
    case unknown = "UNKNOWN"

    public init(_ error: Error) {
        switch error {
        case StoreKitError.userCancelled:
            self = .userCanceled
        case StoreKitError.networkError:
            self = .network
        case StoreKitError.systemError:
            self = .serviceUnavailable
        case StoreKitError.notAvailableInStorefront, Product.PurchaseError.productUnavailable:
            self = .productUnavailable
        case Product.PurchaseError.purchaseNotAllowed:
            self = .billingUnavailable
        case Product.PurchaseError.invalidQuantity,
             Product.PurchaseError.invalidOfferIdentifier,
             Product.PurchaseError.invalidOfferPrice,
             Product.PurchaseError.invalidOfferSignature,
             Product.PurchaseError.missingOfferParameters,
             Product.PurchaseError.ineligibleForOffer:
            self = .developerError
        default:
            self = .unknown
        }
    }
}
