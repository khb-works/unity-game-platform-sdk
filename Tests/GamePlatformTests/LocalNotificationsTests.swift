import XCTest
@testable import GamePlatform

final class LocalNotificationsTests: XCTestCase {
    func testScheduleAddsTheRequestAndReportsTheDelivery() async {
        let center = FakeNotificationCenter(delivery: .notDetermined)
        let notifications = LocalNotifications(center: center)

        let result = await notifications.schedule(request)

        XCTAssertEqual(result, .scheduled(.notDetermined))
        XCTAssertEqual(center.added, [request])
    }

    func testScheduleReportsFailedWhenTheCenterRejectsTheRequest() async {
        let center = FakeNotificationCenter(delivery: .allowed)
        center.addError = URLError(.unknown)
        let notifications = LocalNotifications(center: center)

        let result = await notifications.schedule(request)

        guard case .failed = result else { return XCTFail("expected failed, got \(result)") }
    }

    func testRequestPermissionReportsGrantedOrDenied() async {
        let center = FakeNotificationCenter(delivery: .notDetermined)
        let notifications = LocalNotifications(center: center)

        center.authorizationAnswer = true
        let granted = await notifications.requestPermission()
        center.authorizationAnswer = false
        let denied = await notifications.requestPermission()

        XCTAssertEqual(granted, .granted)
        XCTAssertEqual(denied, .denied)
    }

    func testCancelRemovesTheRequestById() {
        let center = FakeNotificationCenter(delivery: .allowed)

        LocalNotifications(center: center).cancel(id: "stamina")

        XCTAssertEqual(center.removed, ["stamina"])
    }

    private let request = NotificationRequest(
        id: "stamina",
        title: "Stamina is full",
        body: "Come back and play",
        triggerDate: Date(timeIntervalSince1970: 5)
    )
}

private final class FakeNotificationCenter: LocalNotificationCenter {
    private let currentDelivery: NotificationDelivery
    var addError: Error?
    var authorizationAnswer = false
    private(set) var added: [NotificationRequest] = []
    private(set) var removed: [String] = []

    init(delivery: NotificationDelivery) {
        currentDelivery = delivery
    }

    func add(_ request: NotificationRequest) async throws {
        if let addError { throw addError }
        added.append(request)
    }

    func remove(id: String) {
        removed.append(id)
    }

    func delivery() async -> NotificationDelivery { currentDelivery }

    func requestAuthorization() async throws -> Bool { authorizationAnswer }
}
