import StoreKit
import XCTest
@testable import GamePlatform

@MainActor
final class ConsumablePurchasesTests: XCTestCase {
    private var store: FakeAppStore!
    private var purchases: ConsumablePurchases!

    override func setUp() async throws {
        store = FakeAppStore()
        purchases = ConsumablePurchases(store: store)
    }

    func testBuyReportsPurchasedWithTheStorePurchase() async {
        store.purchaseOutcome = .purchased(purchase(id: "1"))

        let result = await purchases.buy(productId: "gems_100", accountId: nil)

        XCTAssertEqual(result, .purchased(purchase(id: "1")))
    }

    func testBuyPassesTheAccountIdAsTheAppAccountToken() async {
        let accountId = "0F1E2D3C-4B5A-6978-8796-A5B4C3D2E1F0"

        _ = await purchases.buy(productId: "gems_100", accountId: accountId)

        XCTAssertEqual(store.purchaseCalls.map(\.accountToken), [UUID(uuidString: accountId)])
    }

    func testBuyRejectsAnAccountIdThatIsNotAUUIDWithoutCallingTheStore() async {
        let result = await purchases.buy(productId: "gems_100", accountId: "player-42")

        guard case .failed(let error) = result else { return XCTFail("expected failed, got \(result)") }
        XCTAssertEqual(error.kind, .developerError)
        XCTAssertTrue(store.purchaseCalls.isEmpty)
    }

    func testBuyMapsPendingCancelledAndUnavailableOutcomes() async {
        let expected: [(AppStorePurchaseOutcome, BuyResult)] = [
            (.pending, .pending),
            (.cancelled, .cancelled),
            (.productUnavailable, .productUnavailable),
        ]
        for (outcome, result) in expected {
            store.purchaseOutcome = outcome
            let actual = await purchases.buy(productId: "gems_100", accountId: nil)
            XCTAssertEqual(actual, result)
        }
    }

    func testBuyReportsTheStoreKitErrorKind() async {
        store.purchaseError = StoreKitError.networkError(URLError(.notConnectedToInternet))

        let result = await purchases.buy(productId: "gems_100", accountId: nil)

        guard case .failed(let error) = result else { return XCTFail("expected failed, got \(result)") }
        XCTAssertEqual(error.kind, .network)
    }

    func testASecondBuyWhileTheFirstIsOpenReportsAnotherBuyInProgress() async {
        store.holdPurchase = true
        let first = Task { await purchases.buy(productId: "gems_100", accountId: nil) }
        await store.waitForPurchaseCall()

        let second = await purchases.buy(productId: "gems_500", accountId: nil)

        XCTAssertEqual(second, .anotherBuyInProgress)
        store.releasePurchase(.cancelled)
        let firstResult = await first.value
        XCTAssertEqual(firstResult, .cancelled)
        XCTAssertEqual(store.purchaseCalls.count, 1)
    }

    func testCompleteReportsNotOwnedWhenNoUnfinishedTransactionHasTheId() async {
        store.unfinishedIds = ["1"]

        let completed = await purchases.complete(purchaseId: "1")
        let missing = await purchases.complete(purchaseId: "2")

        XCTAssertEqual(completed, .completed)
        XCTAssertEqual(missing, .notOwned)
    }

    func testProductsListsTheIdsTheStoreDidNotReturn() async {
        store.products = [product(id: "gems_100")]

        let result = await purchases.products(ids: ["gems_100", "gems_500"])

        XCTAssertEqual(result, .loaded(products: [product(id: "gems_100")], unavailableIds: ["gems_500"]))
    }

    func testUpdatesReachTheListener() async {
        let received = expectation(description: "update")
        var updates: [StorePurchase] = []
        purchases.startListening { purchase in
            updates.append(purchase)
            received.fulfill()
        }

        store.sendUpdate(purchase(id: "7"))

        await fulfillment(of: [received], timeout: 1)
        XCTAssertEqual(updates, [purchase(id: "7")])
    }

    private func purchase(id: String) -> StorePurchase {
        StorePurchase(
            productId: "gems_100",
            purchaseId: id,
            verificationData: "jws-\(id)",
            quantity: 1,
            purchaseDate: Date(timeIntervalSince1970: 1),
            accountToken: nil,
            localVerification: .verified
        )
    }

    private func product(id: String) -> StoreProduct {
        StoreProduct(productId: id, displayName: id, description: "", displayPrice: "$0.99", price: 0.99, currencyCode: "USD")
    }
}

@MainActor
private final class FakeAppStore: AppStoreClient {
    var purchaseOutcome: AppStorePurchaseOutcome = .cancelled
    var purchaseError: Error?
    var holdPurchase = false
    var products: [StoreProduct] = []
    var unfinishedIds: Set<String> = []
    private(set) var purchaseCalls: [(productId: String, accountToken: UUID?)] = []
    private var heldPurchase: CheckedContinuation<AppStorePurchaseOutcome, Never>?
    private var purchaseCallWaiter: CheckedContinuation<Void, Never>?
    private var updateContinuation: AsyncStream<StorePurchase>.Continuation?

    nonisolated func products(ids: [String]) async throws -> [StoreProduct] {
        await MainActor.run { products }
    }

    nonisolated func purchase(productId: String, accountToken: UUID?) async throws -> AppStorePurchaseOutcome {
        try await recordPurchase(productId: productId, accountToken: accountToken)
    }

    nonisolated func unfinishedConsumables() async -> [StorePurchase] { [] }

    nonisolated func finish(purchaseId: String) async -> Bool {
        await MainActor.run { unfinishedIds.remove(purchaseId) != nil }
    }

    nonisolated func updates() -> AsyncStream<StorePurchase> {
        let (stream, continuation) = AsyncStream<StorePurchase>.makeStream()
        MainActor.assumeIsolated { updateContinuation = continuation }
        return stream
    }

    func sendUpdate(_ purchase: StorePurchase) {
        updateContinuation?.yield(purchase)
    }

    func waitForPurchaseCall() async {
        if !purchaseCalls.isEmpty { return }
        await withCheckedContinuation { purchaseCallWaiter = $0 }
    }

    func releasePurchase(_ outcome: AppStorePurchaseOutcome) {
        heldPurchase?.resume(returning: outcome)
        heldPurchase = nil
    }

    private func recordPurchase(productId: String, accountToken: UUID?) async throws -> AppStorePurchaseOutcome {
        purchaseCalls.append((productId, accountToken))
        purchaseCallWaiter?.resume()
        purchaseCallWaiter = nil
        if let purchaseError { throw purchaseError }
        if holdPurchase {
            return await withCheckedContinuation { heldPurchase = $0 }
        }
        return purchaseOutcome
    }
}
