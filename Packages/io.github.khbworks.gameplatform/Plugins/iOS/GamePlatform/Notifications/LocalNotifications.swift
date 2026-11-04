import Foundation

public struct NotificationRequest: Equatable {
    public let id: String
    public let title: String
    public let body: String
    public let triggerDate: Date

    public init(id: String, title: String, body: String, triggerDate: Date) {
        self.id = id
        self.title = title
        self.body = body
        self.triggerDate = triggerDate
    }
}

public enum NotificationDelivery: String {
    case allowed
    case blocked
    // The player hasn't answered the permission prompt yet. Notifications stay silent until they allow them.
    case notDetermined
}

public enum ScheduleResult: Equatable {
    case scheduled(NotificationDelivery)
    case failed(message: String)
}

public enum PermissionResult: Equatable {
    case granted
    case denied
    case failed(message: String)
}

public protocol LocalNotificationCenter {
    func add(_ request: NotificationRequest) async throws
    func remove(id: String)
    func delivery() async -> NotificationDelivery
    func requestAuthorization() async throws -> Bool
}

public final class LocalNotifications {
    private let center: LocalNotificationCenter

    public init(center: LocalNotificationCenter) {
        self.center = center
    }

    public func schedule(_ request: NotificationRequest) async -> ScheduleResult {
        do {
            try await center.add(request)
            return .scheduled(await center.delivery())
        } catch {
            return .failed(message: String(describing: error))
        }
    }

    public func cancel(id: String) {
        center.remove(id: id)
    }

    public func delivery() async -> NotificationDelivery {
        await center.delivery()
    }

    public func requestPermission() async -> PermissionResult {
        do {
            return try await center.requestAuthorization() ? .granted : .denied
        } catch {
            return .failed(message: String(describing: error))
        }
    }
}
