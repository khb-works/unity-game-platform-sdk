import Foundation

// The same message shapes as the Android plugin: a request id and a type, plus the fields that type needs.
// Request ids from C# start at 1, so 0 marks a purchase update that no request asked for.
enum UnityMessages {
    static let updateRequestId: Int32 = 0

    static func message(_ result: BuyResult, requestId: Int32) -> [String: Any] {
        switch result {
        case .purchased(let purchase): return base(requestId, "purchased").merging(["purchase": json(purchase)]) { $1 }
        case .pending: return base(requestId, "pending")
        case .cancelled: return base(requestId, "cancelled")
        case .productUnavailable: return base(requestId, "productUnavailable")
        case .anotherBuyInProgress: return base(requestId, "anotherBuyInProgress")
        case .failed(let error): return base(requestId, "failed").merging(["error": json(error)]) { $1 }
        }
    }

    static func message(_ result: CompleteResult, requestId: Int32) -> [String: Any] {
        switch result {
        case .completed: return base(requestId, "completed")
        case .notOwned: return base(requestId, "notOwned")
        }
    }

    static func message(_ result: ProductsResult, requestId: Int32) -> [String: Any] {
        switch result {
        case .loaded(let products, let unavailableIds):
            return base(requestId, "loaded").merging([
                "products": products.map(json),
                "unavailableIds": unavailableIds,
            ]) { $1 }
        case .failed(let error):
            return base(requestId, "failed").merging(["error": json(error)]) { $1 }
        }
    }

    static func unfinishedMessage(_ purchases: [StorePurchase], requestId: Int32) -> [String: Any] {
        base(requestId, "loaded").merging(["purchases": purchases.map(json)]) { $1 }
    }

    static func purchaseUpdatedMessage(_ purchase: StorePurchase) -> [String: Any] {
        base(updateRequestId, "purchaseUpdated").merging(["purchase": json(purchase)]) { $1 }
    }

    static func message(_ result: ScheduleResult, requestId: Int32) -> [String: Any] {
        switch result {
        case .scheduled(let delivery):
            // iOS has no inexact mode. A time interval trigger fires at the requested time.
            return base(requestId, "scheduled").merging(["timing": "exact", "delivery": delivery.rawValue]) { $1 }
        case .failed(let message):
            return base(requestId, "failed").merging(["error": ["kind": "UNKNOWN", "message": message]]) { $1 }
        }
    }

    static func message(_ result: PermissionResult, requestId: Int32) -> [String: Any] {
        switch result {
        case .granted: return base(requestId, "granted")
        case .denied: return base(requestId, "denied")
        case .failed(let message):
            return base(requestId, "failed").merging(["error": ["kind": "UNKNOWN", "message": message]]) { $1 }
        }
    }

    static func message(_ delivery: NotificationDelivery, requestId: Int32) -> [String: Any] {
        base(requestId, "delivery").merging(["delivery": delivery.rawValue]) { $1 }
    }

    static func json(_ purchase: StorePurchase) -> [String: Any] {
        var json: [String: Any] = [
            "store": "appStore",
            "productIds": [purchase.productId],
            "purchaseId": purchase.purchaseId,
            "verificationData": purchase.verificationData,
            "orderId": NSNull(),
            "quantity": purchase.quantity,
            "purchaseTimeMillis": Int64(purchase.purchaseDate.timeIntervalSince1970 * 1000),
            "state": "purchased",
            "accountId": purchase.accountToken.map { $0.uuidString.lowercased() } ?? NSNull(),
        ]
        if case .failed(let reason) = purchase.localVerification {
            json["localVerificationFailure"] = reason
        }
        return json
    }

    static func encode(_ message: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: message, options: [.sortedKeys]),
              let text = String(data: data, encoding: .utf8) else {
            preconditionFailure("Unity messages hold only JSON values: \(message)")
        }
        return text
    }

    private static func json(_ product: StoreProduct) -> [String: Any] {
        [
            "productId": product.productId,
            "title": product.displayName,
            "description": product.description,
            "formattedPrice": product.displayPrice,
            "priceMicros": NSDecimalNumber(decimal: product.price * 1_000_000).int64Value,
            "currencyCode": product.currencyCode,
        ]
    }

    private static func json(_ error: StoreError) -> [String: Any] {
        ["kind": error.kind.rawValue, "message": error.message]
    }

    private static func base(_ requestId: Int32, _ type: String) -> [String: Any] {
        ["requestId": requestId, "type": type]
    }
}
