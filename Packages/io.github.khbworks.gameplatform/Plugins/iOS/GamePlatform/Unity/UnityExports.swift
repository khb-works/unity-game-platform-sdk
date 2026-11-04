import Foundation

// C# calls these through [DllImport("__Internal")]. On iOS, Unity runs its player loop on the main thread,
// so the callback reaches C# on the same thread that called in.
public typealias GamePlatformMessageCallback = @convention(c) (UnsafePointer<CChar>) -> Void

@MainActor
private enum UnityBridge {
    static var callback: GamePlatformMessageCallback?
    static let purchases = ConsumablePurchases(store: StoreKitAppStore())
    static let notifications = LocalNotifications(center: UserNotificationCenter())

    static func send(_ message: [String: Any]) {
        guard let callback else { return }
        // The pointer is valid only during the call. The C# side copies the string before it returns.
        UnityMessages.encode(message).withCString { callback($0) }
    }
}

@_cdecl("GamePlatform_Initialize")
public func gamePlatformInitialize(_ callback: GamePlatformMessageCallback) {
    Task { @MainActor in
        UnityBridge.callback = callback
        UnityBridge.purchases.startListening { purchase in
            UnityBridge.send(UnityMessages.purchaseUpdatedMessage(purchase))
        }
    }
}

@_cdecl("GamePlatform_Purchases_Products")
public func gamePlatformPurchasesProducts(_ requestId: Int32, _ productIdsJson: UnsafePointer<CChar>) {
    let json = String(cString: productIdsJson)
    let productIds = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String] ?? []
    Task { @MainActor in
        let result = await UnityBridge.purchases.products(ids: productIds)
        UnityBridge.send(UnityMessages.message(result, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Purchases_Buy")
public func gamePlatformPurchasesBuy(_ requestId: Int32, _ productId: UnsafePointer<CChar>, _ accountId: UnsafePointer<CChar>?) {
    let productId = String(cString: productId)
    let accountId = accountId.map { String(cString: $0) }
    Task { @MainActor in
        let result = await UnityBridge.purchases.buy(productId: productId, accountId: accountId)
        UnityBridge.send(UnityMessages.message(result, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Purchases_Complete")
public func gamePlatformPurchasesComplete(_ requestId: Int32, _ purchaseId: UnsafePointer<CChar>) {
    let purchaseId = String(cString: purchaseId)
    Task { @MainActor in
        let result = await UnityBridge.purchases.complete(purchaseId: purchaseId)
        UnityBridge.send(UnityMessages.message(result, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Purchases_Unfinished")
public func gamePlatformPurchasesUnfinished(_ requestId: Int32) {
    Task { @MainActor in
        let purchases = await UnityBridge.purchases.unfinishedPurchases()
        UnityBridge.send(UnityMessages.unfinishedMessage(purchases, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Notifications_Schedule")
public func gamePlatformNotificationsSchedule(
    _ requestId: Int32,
    _ id: UnsafePointer<CChar>,
    _ title: UnsafePointer<CChar>,
    _ body: UnsafePointer<CChar>,
    _ triggerAtMillis: Int64
) {
    let request = NotificationRequest(
        id: String(cString: id),
        title: String(cString: title),
        body: String(cString: body),
        triggerDate: Date(timeIntervalSince1970: TimeInterval(triggerAtMillis) / 1000)
    )
    Task { @MainActor in
        let result = await UnityBridge.notifications.schedule(request)
        UnityBridge.send(UnityMessages.message(result, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Notifications_Cancel")
public func gamePlatformNotificationsCancel(_ id: UnsafePointer<CChar>) {
    let id = String(cString: id)
    Task { @MainActor in
        UnityBridge.notifications.cancel(id: id)
    }
}

@_cdecl("GamePlatform_Notifications_RequestPermission")
public func gamePlatformNotificationsRequestPermission(_ requestId: Int32) {
    Task { @MainActor in
        let result = await UnityBridge.notifications.requestPermission()
        UnityBridge.send(UnityMessages.message(result, requestId: requestId))
    }
}

@_cdecl("GamePlatform_Notifications_Delivery")
public func gamePlatformNotificationsDelivery(_ requestId: Int32) {
    Task { @MainActor in
        let delivery = await UnityBridge.notifications.delivery()
        UnityBridge.send(UnityMessages.message(delivery, requestId: requestId))
    }
}
