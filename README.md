# Unity Game Platform SDK

A Unity plugin for consumable in-app purchases and local notifications on Android and iOS.

Status: the native layers (Kotlin for Android, Swift for iOS) and their tests are done. The C# API for Unity and the sample scene aren't written yet.

## Purchases

The plugin never consumes a purchase on its own. The game server grants the item first:

1. The game calls `buy`. The store shows its payment sheet.
2. The plugin returns the purchase with a `purchaseId` and `verificationData`.
3. The game server verifies `verificationData` with Google Play or the App Store and grants the item. It keys the grant by `purchaseId`, so a retry doesn't grant twice.
4. The game calls `complete(purchaseId)`. Only then does the plugin consume the purchase (Android) or finish the transaction (iOS).

At launch, the game asks for unfinished purchases and repeats steps 3 and 4 for each one.

| Case | What happens |
|---|---|
| The app closes after the payment and before the server grants the item | The purchase stays unconsumed and comes back from the unfinished purchases at the next launch. |
| The server granted the item, but `complete` failed | The same purchase comes back at the next launch. The server sees a `purchaseId` it already granted, so the game only calls `complete` again. |
| The payment is pending (cash at a store on Android, Ask to Buy on iOS) | `buy` returns `Pending`. The paid purchase arrives later as a purchase update. |
| Google Play sends a purchase for another product while the payment sheet is open | It goes to the purchase update listener, not to the open `buy`. |
| The game calls `buy` while another `buy` is open | `AnotherBuyInProgress`, and no second payment sheet. |
| An earlier purchase of the same product was never consumed (Android) | `buy` fails with `ALREADY_OWNED`. The unfinished purchase is the one to grant and complete. |
| One store account plays with several game accounts | The game passes a UUID `accountId`. It's stored with the purchase (`obfuscatedAccountId` on Google Play, `appAccountToken` on the App Store), so the server can check which game account paid. |

## Local notifications

- `schedule`, `cancel` and the current delivery status (allowed, blocked, or not determined on iOS).
- Android uses an exact alarm when the app may set one, and an inexact alarm otherwise. The result says which. Android 14 and later deny exact alarms to most apps by default. Scheduled notifications are set again after a reboot.
- iOS asks for permission with `requestPermission`.

## Layout

| Path | What it is |
|---|---|
| `Packages/io.github.khbworks.gameplatform` | The Unity package |
| `.../Plugins/Android/GamePlatform.androidlib` | Kotlin sources. Unity builds this folder as a module of its Gradle project. |
| `.../Plugins/iOS/GamePlatform` | Swift sources. Unity copies them into its Xcode project. |
| `android-build` | A Gradle build that includes the androidlib folder, for unit tests outside Unity |
| `Package.swift`, `Tests/GamePlatformTests` | A Swift package over the same Swift sources, for tests outside Unity |

Both native layers send results to C# as JSON messages with the same fields, so the C# side reads one format.

## Requirements

- Android: minSdk 23, Google Play Billing Library 8.3.0
- iOS: 15.0 or later (StoreKit 2)

## Running the tests

```bash
cd android-build && ./gradlew :gameplatform:testDebugUnitTest
```

```bash
swift test
```

```bash
xcodebuild test -scheme GamePlatform -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

## License

MIT
