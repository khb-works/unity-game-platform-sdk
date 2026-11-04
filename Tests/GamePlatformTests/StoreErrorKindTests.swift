import StoreKit
import XCTest
@testable import GamePlatform

final class StoreErrorKindTests: XCTestCase {
    func testMapsTheStoreKitErrorsAPurchaseCanThrow() {
        let cases: [(Error, StoreErrorKind)] = [
            (StoreKitError.userCancelled, .userCanceled),
            (StoreKitError.networkError(URLError(.timedOut)), .network),
            (StoreKitError.notAvailableInStorefront, .productUnavailable),
            (Product.PurchaseError.productUnavailable, .productUnavailable),
            (Product.PurchaseError.purchaseNotAllowed, .billingUnavailable),
            (Product.PurchaseError.invalidQuantity, .developerError),
            (StoreKitError.unknown, .unknown),
        ]

        for (error, kind) in cases {
            XCTAssertEqual(StoreErrorKind(error), kind, "\(error)")
        }
    }

    func testRawValuesMatchTheAndroidErrorKinds() {
        XCTAssertEqual(StoreErrorKind.userCanceled.rawValue, "USER_CANCELED")
        XCTAssertEqual(StoreErrorKind.productUnavailable.rawValue, "PRODUCT_UNAVAILABLE")
    }
}
