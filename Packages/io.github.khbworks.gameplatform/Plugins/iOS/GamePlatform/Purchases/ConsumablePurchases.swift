import Foundation

public protocol AppStoreClient {
    func products(ids: [String]) async throws -> [StoreProduct]
    func purchase(productId: String, accountToken: UUID?) async throws -> AppStorePurchaseOutcome
    func unfinishedConsumables() async -> [StorePurchase]
    // Returns false when no unfinished transaction has this id.
    func finish(purchaseId: String) async -> Bool
    func updates() -> AsyncStream<StorePurchase>
}

public enum AppStorePurchaseOutcome: Equatable {
    case purchased(StorePurchase)
    case pending
    case cancelled
    case productUnavailable
    case unknown
}

@MainActor
public final class ConsumablePurchases {
    private let store: AppStoreClient
    private var buyInProgress = false
    private var updatesTask: Task<Void, Never>?

    public init(store: AppStoreClient) {
        self.store = store
    }

    // Start this at launch. StoreKit delivers Ask to Buy approvals and purchases from other devices here,
    // and also sends every unfinished transaction once right after the listener starts.
    public func startListening(onUpdate: @escaping @MainActor (StorePurchase) -> Void) {
        updatesTask?.cancel()
        let updates = store.updates()
        updatesTask = Task { @MainActor in
            for await purchase in updates {
                onUpdate(purchase)
            }
        }
    }

    public func products(ids: [String]) async -> ProductsResult {
        do {
            let products = try await store.products(ids: ids)
            let loadedIds = Set(products.map(\.productId))
            return .loaded(products: products, unavailableIds: ids.filter { !loadedIds.contains($0) })
        } catch {
            return .failed(StoreError(error))
        }
    }

    // accountId must be a UUID string. The App Store keeps it as the transaction's appAccountToken.
    public func buy(productId: String, accountId: String?) async -> BuyResult {
        if buyInProgress { return .anotherBuyInProgress }
        var accountToken: UUID?
        if let accountId {
            guard let token = UUID(uuidString: accountId) else {
                return .failed(StoreError(kind: .developerError, message: "accountId must be a UUID on the App Store: \(accountId)"))
            }
            accountToken = token
        }
        buyInProgress = true
        defer { buyInProgress = false }
        do {
            switch try await store.purchase(productId: productId, accountToken: accountToken) {
            case .purchased(let purchase): return .purchased(purchase)
            case .pending: return .pending
            case .cancelled: return .cancelled
            case .productUnavailable: return .productUnavailable
            case .unknown: return .failed(StoreError(kind: .unknown, message: "StoreKit returned an unknown purchase result."))
            }
        } catch {
            return .failed(StoreError(error))
        }
    }

    public func complete(purchaseId: String) async -> CompleteResult {
        await store.finish(purchaseId: purchaseId) ? .completed : .notOwned
    }

    public func unfinishedPurchases() async -> [StorePurchase] {
        await store.unfinishedConsumables()
    }
}
