import XCTest
@testable import GamePlatform

final class UnityMessagesTests: XCTestCase {
    func testAPurchasedResultUsesTheSameFieldsAsAndroid() throws {
        let message = try decode(UnityMessages.message(.purchased(purchase(accountToken: nil)), requestId: 7))

        XCTAssertEqual(message["requestId"] as? Int, 7)
        XCTAssertEqual(message["type"] as? String, "purchased")
        let purchase = try XCTUnwrap(message["purchase"] as? [String: Any])
        XCTAssertEqual(purchase["store"] as? String, "appStore")
        XCTAssertEqual(purchase["productIds"] as? [String], ["gems_100"])
        XCTAssertEqual(purchase["purchaseId"] as? String, "2000000123")
        XCTAssertEqual(purchase["verificationData"] as? String, "header.payload.signature")
        XCTAssertEqual(purchase["purchaseTimeMillis"] as? Int, 1_500)
        XCTAssertEqual(purchase["state"] as? String, "purchased")
    }

    func testMissingOrderIdAndAccountIdAreNullNotEmptyStrings() throws {
        let purchase = try XCTUnwrap(
            try decode(UnityMessages.message(.purchased(purchase(accountToken: nil)), requestId: 1))["purchase"] as? [String: Any]
        )

        XCTAssertTrue(purchase["orderId"] is NSNull)
        XCTAssertTrue(purchase["accountId"] is NSNull)
    }

    func testTheAccountTokenIsSentAsALowercaseUUID() throws {
        let token = UUID(uuidString: "0F1E2D3C-4B5A-6978-8796-A5B4C3D2E1F0")
        let purchase = try XCTUnwrap(
            try decode(UnityMessages.message(.purchased(purchase(accountToken: token)), requestId: 1))["purchase"] as? [String: Any]
        )

        XCTAssertEqual(purchase["accountId"] as? String, "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    }

    func testALocalVerificationFailureIsSentWithThePurchase() throws {
        var failed = purchase(accountToken: nil)
        failed = StorePurchase(
            productId: failed.productId,
            purchaseId: failed.purchaseId,
            verificationData: failed.verificationData,
            quantity: failed.quantity,
            purchaseDate: failed.purchaseDate,
            accountToken: nil,
            localVerification: .failed(reason: "invalidSignature")
        )

        let purchase = try XCTUnwrap(try decode(UnityMessages.message(.purchased(failed), requestId: 1))["purchase"] as? [String: Any])

        XCTAssertEqual(purchase["localVerificationFailure"] as? String, "invalidSignature")
    }

    func testAPurchaseUpdateUsesRequestIdZero() throws {
        let message = try decode(UnityMessages.purchaseUpdatedMessage(purchase(accountToken: nil)))

        XCTAssertEqual(message["requestId"] as? Int, 0)
        XCTAssertEqual(message["type"] as? String, "purchaseUpdated")
    }

    func testAFailedResultCarriesTheErrorKind() throws {
        let error = StoreError(kind: .network, message: "offline")

        let message = try decode(UnityMessages.message(BuyResult.failed(error), requestId: 2))

        XCTAssertEqual(message["type"] as? String, "failed")
        XCTAssertEqual((message["error"] as? [String: Any])?["kind"] as? String, "NETWORK")
    }

    func testProductPricesAreSentInMicros() throws {
        let product = StoreProduct(productId: "gems_100", displayName: "100 Gems", description: "", displayPrice: "$1.99", price: 1.99, currencyCode: "USD")

        let message = try decode(UnityMessages.message(ProductsResult.loaded(products: [product], unavailableIds: []), requestId: 3))

        let products = try XCTUnwrap(message["products"] as? [[String: Any]])
        XCTAssertEqual(products.first?["priceMicros"] as? Int, 1_990_000)
    }

    private func decode(_ message: [String: Any]) throws -> [String: Any] {
        let text = UnityMessages.encode(message)
        return try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])
    }

    private func purchase(accountToken: UUID?) -> StorePurchase {
        StorePurchase(
            productId: "gems_100",
            purchaseId: "2000000123",
            verificationData: "header.payload.signature",
            quantity: 1,
            purchaseDate: Date(timeIntervalSince1970: 1.5),
            accountToken: accountToken,
            localVerification: .verified
        )
    }
}
