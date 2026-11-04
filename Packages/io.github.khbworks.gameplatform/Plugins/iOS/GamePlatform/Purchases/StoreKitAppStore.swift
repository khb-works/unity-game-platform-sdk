import Foundation
import StoreKit

public struct StoreKitAppStore: AppStoreClient {
    public init() {}

    public func products(ids: [String]) async throws -> [StoreProduct] {
        try await Product.products(for: ids)
            .filter { $0.type == .consumable }
            .map(StoreProduct.init)
    }

    public func purchase(productId: String, accountToken: UUID?) async throws -> AppStorePurchaseOutcome {
        guard let product = try await Product.products(for: [productId]).first(where: { $0.type == .consumable }) else {
            return .productUnavailable
        }
        var options: Set<Product.PurchaseOption> = []
        if let accountToken {
            options.insert(.appAccountToken(accountToken))
        }
        switch try await product.purchase(options: options) {
        case .success(let verification): return .purchased(StorePurchase(verification))
        case .pending: return .pending
        case .userCancelled: return .cancelled
        @unknown default: return .unknown
        }
    }

    public func unfinishedConsumables() async -> [StorePurchase] {
        var purchases: [StorePurchase] = []
        for await verification in Transaction.unfinished where verification.unsafePayloadValue.productType == .consumable {
            purchases.append(StorePurchase(verification))
        }
        return purchases
    }

    public func finish(purchaseId: String) async -> Bool {
        for await verification in Transaction.unfinished {
            let transaction = verification.unsafePayloadValue
            if String(transaction.id) == purchaseId {
                await transaction.finish()
                return true
            }
        }
        return false
    }

    public func updates() -> AsyncStream<StorePurchase> {
        AsyncStream { continuation in
            let task = Task {
                for await verification in Transaction.updates where verification.unsafePayloadValue.productType == .consumable {
                    continuation.yield(StorePurchase(verification))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

extension StorePurchase {
    init(_ verification: VerificationResult<Transaction>) {
        let transaction = verification.unsafePayloadValue
        let localVerification: LocalVerification
        switch verification {
        case .verified:
            localVerification = .verified
        case .unverified(_, let error):
            localVerification = .failed(reason: String(describing: error))
        }
        self.init(
            productId: transaction.productID,
            purchaseId: String(transaction.id),
            verificationData: verification.jwsRepresentation,
            quantity: transaction.purchasedQuantity,
            purchaseDate: transaction.purchaseDate,
            accountToken: transaction.appAccountToken,
            localVerification: localVerification
        )
    }
}

extension StoreProduct {
    init(_ product: Product) {
        self.init(
            productId: product.id,
            displayName: product.displayName,
            description: product.description,
            displayPrice: product.displayPrice,
            price: product.price,
            currencyCode: product.priceFormatStyle.currencyCode
        )
    }
}
