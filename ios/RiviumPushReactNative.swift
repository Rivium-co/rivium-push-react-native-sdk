/**
 * RiviumPush React Native iOS Module
 * Thin wrapper around the native RiviumPush iOS SDK
 * This is similar to how the Android module wraps the AAR
 */
import Foundation
import React
import UserNotifications
import RiviumPushSDK

@objc(RiviumPushReactNative)
class RiviumPushReactNative: RCTEventEmitter {

    private var hasListeners = false
    private static var instance: RiviumPushReactNative?
    private var showNotificationInForeground: Bool = true
    private var pendingEvents: [(name: String, body: Any?)] = []

    override init() {
        super.init()
        RiviumPushReactNative.instance = self
    }

    override static func moduleName() -> String! {
        return "RiviumPushReactNative"
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func supportedEvents() -> [String]! {
        return [
            "onMessage",
            "onConnectionState",
            "onRegistered",
            "onError",
            "onDetailedError",
            "onReconnecting",
            "onNetworkState",
            "onAppState",
            "onAppUpdated",
            "onNotificationAction",
            "onInAppMessageReady",
            "onInAppButtonClick",
            "onInAppMessageDismissed",
            "onInboxMessageReceived",
            "onInboxMessageStatusChanged",
            "onABTestVariantAssigned",
            "onABTestError"
        ]
    }

    override func startObserving() {
        hasListeners = true
        // Flush any events that fired before JS listeners were ready
        for event in pendingEvents {
            sendEvent(withName: event.name, body: event.body)
        }
        pendingEvents.removeAll()
    }

    override func stopObserving() {
        hasListeners = false
    }

    // MARK: - Core Methods

    @objc(init:resolver:rejecter:)
    func initialize(configDict: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let apiKey = configDict["apiKey"] as? String else {
            reject("INIT_ERROR", "API key is required", nil)
            return
        }

        let config = RiviumPushConfig(
            apiKey: apiKey,
            usePushKit: configDict["usePushKit"] as? Bool ?? false,
            showNotificationInForeground: configDict["showNotificationInForeground"] as? Bool ?? true,
            autoConnect: configDict["autoConnect"] as? Bool ?? true
        )

        self.showNotificationInForeground = configDict["showNotificationInForeground"] as? Bool ?? true

        // Set up delegate before initializing so events are captured
        RiviumPush.shared.delegate = self

        RiviumPush.shared.initialize(config: config)

        // Set up callbacks
        RiviumPush.shared.setInboxCallback(self)
        RiviumPush.shared.setInAppMessageCallback(self)
        RiviumPush.shared.setABTestingDelegate(self)

        // Set notification center delegate for foreground display
        UNUserNotificationCenter.current().delegate = self

        // Request notification permission and wait for result before resolving
        let usePushKit = configDict["usePushKit"] as? Bool ?? false
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted && !usePushKit {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
            resolve(nil)
        }
    }

    @objc(register:metadata:resolver:rejecter:)
    func register(userId: String?, metadata: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let metadataDict = metadata as? [String: String]
        RiviumPush.shared.register(userId: userId, metadata: metadataDict)
        resolve(nil)
    }

    @objc(unregister:rejecter:)
    func unregister(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.unregister()
        resolve(nil)
    }

    @objc(isConnected:rejecter:)
    func isConnected(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(RiviumPush.shared.isConnected)
    }

    @objc(getDeviceId:rejecter:)
    func getDeviceId(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(RiviumPush.shared.getDeviceId())
    }

    @objc(getSubscriptionId:rejecter:)
    func getSubscriptionId(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(RiviumPush.shared.getSubscriptionId())
    }

    @objc(setLogLevel:resolver:rejecter:)
    func setLogLevel(level: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.setLogLevel(RiviumPushLogLevel.fromString(level))
        resolve(nil)
    }

    // MARK: - Topic Subscriptions

    @objc(subscribeTopic:resolver:rejecter:)
    func subscribeTopic(topic: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.subscribeTopic(topic)
        resolve(nil)
    }

    @objc(unsubscribeTopic:resolver:rejecter:)
    func unsubscribeTopic(topic: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.unsubscribeTopic(topic)
        resolve(nil)
    }

    // MARK: - User Management

    @objc(setUserId:resolver:rejecter:)
    func setUserId(userId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.setUserId(userId)
        resolve(nil)
    }

    @objc(clearUserId:rejecter:)
    func clearUserId(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.clearUserId()
        resolve(nil)
    }

    // MARK: - Initial Message

    @objc(getInitialMessage:rejecter:)
    func getInitialMessage(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        if let message = RiviumPush.shared.getInitialMessage() {
            RiviumPush.shared.clearInitialMessage()
            resolve(message.toDictionary())
        } else {
            resolve(nil)
        }
    }

    // MARK: - In-App Messages

    @objc(fetchInAppMessages:rejecter:)
    func fetchInAppMessages(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.fetchInAppMessages { messages in
            resolve(messages.map { $0.toDictionary() })
        }
    }

    @objc(triggerInAppOnAppOpen:rejecter:)
    func triggerInAppOnAppOpen(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.triggerInAppOnAppOpen()
        resolve(nil)
    }

    @objc(triggerInAppEvent:properties:resolver:rejecter:)
    func triggerInAppEvent(eventName: String, properties: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let propsDict = properties as? [String: Any]
        RiviumPush.shared.triggerInAppEvent(eventName, properties: propsDict)
        resolve(nil)
    }

    @objc(triggerInAppOnSessionStart:rejecter:)
    func triggerInAppOnSessionStart(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.triggerInAppOnSessionStart()
        resolve(nil)
    }

    @objc(showInAppMessage:resolver:rejecter:)
    func showInAppMessage(messageId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.showInAppMessage(messageId)
        resolve(nil)
    }

    @objc(dismissInAppMessage:rejecter:)
    func dismissInAppMessage(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.dismissInAppMessage()
        resolve(nil)
    }

    // MARK: - Inbox

    @objc(getInboxMessages:resolver:rejecter:)
    func getInboxMessages(filter: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        var inboxFilter = InboxFilter()

        if let filterDict = filter as? [String: Any] {
            inboxFilter = InboxFilter(
                status: filterDict["status"] != nil ? InboxMessageStatus.fromString(filterDict["status"] as! String) : nil,
                category: filterDict["category"] as? String,
                limit: filterDict["limit"] as? Int ?? 50,
                offset: filterDict["offset"] as? Int ?? 0
            )
        }

        RiviumPush.shared.getInboxMessages(
            filter: inboxFilter,
            onSuccess: { response in
                resolve(response.toDictionary())
            },
            onError: { error in
                reject("ERROR", error, nil)
            }
        )
    }

    @objc(getInboxMessage:resolver:rejecter:)
    func getInboxMessage(messageId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.getInboxMessage(
            messageId: messageId,
            onSuccess: { message in resolve(message.toDictionary()) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(markInboxMessageAsRead:resolver:rejecter:)
    func markInboxMessageAsRead(messageId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.markInboxMessageAsRead(
            messageId: messageId,
            onSuccess: { resolve(nil) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(archiveInboxMessage:resolver:rejecter:)
    func archiveInboxMessage(messageId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.archiveInboxMessage(
            messageId: messageId,
            onSuccess: { resolve(nil) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(deleteInboxMessage:resolver:rejecter:)
    func deleteInboxMessage(messageId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.deleteInboxMessage(
            messageId: messageId,
            onSuccess: { resolve(nil) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(markMultipleInboxMessages:status:resolver:rejecter:)
    func markMultipleInboxMessages(messageIds: [String], status: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.markMultipleInboxMessages(
            messageIds: messageIds,
            status: InboxMessageStatus.fromString(status),
            onSuccess: { resolve(nil) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(markAllInboxMessagesAsRead:rejecter:)
    func markAllInboxMessagesAsRead(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.markAllInboxMessagesAsRead(
            onSuccess: { resolve(nil) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(getInboxUnreadCount:rejecter:)
    func getInboxUnreadCount(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(RiviumPush.shared.getInboxUnreadCount())
    }

    @objc(fetchInboxUnreadCount:rejecter:)
    func fetchInboxUnreadCount(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.fetchInboxUnreadCount(
            onSuccess: { count in resolve(count) },
            onError: { error in reject("ERROR", error, nil) }
        )
    }

    @objc(getCachedInboxMessages:rejecter:)
    func getCachedInboxMessages(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(RiviumPush.shared.getCachedInboxMessages().map { $0.toDictionary() })
    }

    @objc(clearInboxCache:rejecter:)
    func clearInboxCache(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.clearInboxCache()
        resolve(nil)
    }

    // MARK: - A/B Testing

    @objc(getActiveABTests:rejecter:)
    func getActiveABTests(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.getActiveABTests { result in
            switch result {
            case .success(let tests):
                resolve(tests.map { $0.toDictionary() })
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(getABTestVariant:forceRefresh:resolver:rejecter:)
    func getABTestVariant(testId: String, forceRefresh: Bool, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.getABTestVariant(testId: testId, forceRefresh: forceRefresh) { result in
            switch result {
            case .success(let variant):
                resolve(variant.toDictionary())
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(getCachedABTestVariant:resolver:rejecter:)
    func getCachedABTestVariant(testId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let variant = RiviumPush.shared.getCachedABTestVariant(testId: testId)
        resolve(variant?.toDictionary())
    }

    @objc(trackABTestImpression:variantId:resolver:rejecter:)
    func trackABTestImpression(testId: String, variantId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.trackABTestImpression(testId: testId, variantId: variantId) { result in
            switch result {
            case .success:
                resolve(nil)
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(trackABTestOpened:variantId:resolver:rejecter:)
    func trackABTestOpened(testId: String, variantId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.trackABTestOpened(testId: testId, variantId: variantId) { result in
            switch result {
            case .success:
                resolve(nil)
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(trackABTestClicked:variantId:resolver:rejecter:)
    func trackABTestClicked(testId: String, variantId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.trackABTestClicked(testId: testId, variantId: variantId) { result in
            switch result {
            case .success:
                resolve(nil)
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(trackABTestDisplay:variantId:resolver:rejecter:)
    func trackABTestDisplay(testId: String, variantId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let variant = ABTestVariant(testId: testId, variantId: variantId, variantName: "", content: nil)
        RiviumPush.shared.trackABTestDisplay(variant: variant) { result in
            switch result {
            case .success:
                resolve(nil)
            case .failure(let error):
                reject("ABTEST_ERROR", error.localizedDescription, nil)
            }
        }
    }

    @objc(clearABTestCache:rejecter:)
    func clearABTestCache(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        RiviumPush.shared.clearABTestCache()
        resolve(nil)
    }

    // MARK: - Event Emission

    private func emitEvent(_ name: String, body: Any?) {
        if hasListeners {
            sendEvent(withName: name, body: body)
        } else {
            pendingEvents.append((name: name, body: body))
        }
    }
}

// MARK: - RiviumPushDelegate
extension RiviumPushReactNative: RiviumPushDelegate {
    func riviumPush(_ riviumPush: RiviumPush, didReceiveMessage message: RiviumPushMessage) {
        emitEvent("onMessage", body: message.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didChangeConnectionState connected: Bool) {
        emitEvent("onConnectionState", body: connected)
    }

    func riviumPush(_ riviumPush: RiviumPush, didRegisterWithDeviceId deviceId: String) {
        emitEvent("onRegistered", body: deviceId)
    }

    func riviumPush(_ riviumPush: RiviumPush, didReceiveVoIPToken token: String) {
        // Internal - not exposed to React Native
    }

    func riviumPush(_ riviumPush: RiviumPush, didFailWithError error: Error) {
        emitEvent("onError", body: error.localizedDescription)
    }

    func riviumPush(_ riviumPush: RiviumPush, didFailWithDetailedError error: RiviumPushError) {
        emitEvent("onDetailedError", body: error.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didStartReconnecting state: ReconnectionState) {
        emitEvent("onReconnecting", body: state.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didChangeNetworkState state: NetworkState) {
        emitEvent("onNetworkState", body: state.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didChangeAppState state: AppState) {
        emitEvent("onAppState", body: state.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didDetectAppUpdate info: AppUpdateInfo) {
        emitEvent("onAppUpdated", body: info.toDictionary())
    }

    func riviumPush(_ riviumPush: RiviumPush, didReceiveNotificationAction action: NotificationAction, forMessage message: RiviumPushMessage) {
        emitEvent("onNotificationAction", body: [
            "action": action.toDictionary(),
            "message": message.toDictionary()
        ])
    }
}

// MARK: - InAppMessageCallback
extension RiviumPushReactNative: InAppMessageCallback {
    func inAppMessageReady(_ message: InAppMessage) {
        emitEvent("onInAppMessageReady", body: message.toDictionary())
    }

    func inAppMessageButtonClicked(_ message: InAppMessage, button: InAppButton) {
        emitEvent("onInAppButtonClick", body: [
            "message": message.toDictionary(),
            "button": button.toDictionary()
        ])
    }

    func inAppMessageDismissed(_ message: InAppMessage) {
        emitEvent("onInAppMessageDismissed", body: message.toDictionary())
    }

    func inAppMessageError(_ error: String) {
        emitEvent("onError", body: error)
    }
}

// MARK: - InboxCallback
extension RiviumPushReactNative: InboxCallback {
    func inboxMessageReceived(_ message: InboxMessage) {
        emitEvent("onInboxMessageReceived", body: message.toDictionary())
    }

    func inboxMessageStatusChanged(messageId: String, status: InboxMessageStatus) {
        emitEvent("onInboxMessageStatusChanged", body: [
            "messageId": messageId,
            "status": status.rawValue
        ])
    }
}

// MARK: - ABTestingDelegate
extension RiviumPushReactNative: ABTestingDelegate {
    func abTestingManager(_ manager: ABTestingManager, didAssignVariant variant: ABTestVariant) {
        emitEvent("onABTestVariantAssigned", body: variant.toDictionary())
    }

    func abTestingManager(_ manager: ABTestingManager, didFailWithError error: Error, forTest testId: String?) {
        emitEvent("onABTestError", body: [
            "testId": testId ?? "",
            "error": error.localizedDescription
        ])
    }
}

// MARK: - UNUserNotificationCenterDelegate
extension RiviumPushReactNative: UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if showNotificationInForeground {
            if #available(iOS 14.0, *) {
                completionHandler([.banner, .sound, .badge])
            } else {
                completionHandler([.alert, .sound, .badge])
            }
        } else {
            completionHandler([])
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo

        // Forward to native SDK for notification response handling
        RiviumPush.shared.handleNotificationResponse(userInfo: userInfo, actionIdentifier: response.actionIdentifier)

        // Auto-track A/B test click from APNs payload (abTestId/variantId are at top level)
        if let abTestId = userInfo["abTestId"] as? String,
           let variantId = userInfo["variantId"] as? String,
           !abTestId.isEmpty, !variantId.isEmpty {
            RiviumPush.shared.trackABTestClicked(testId: abTestId, variantId: variantId) { _ in }
        }

        if let message = RiviumPushMessage.from(payload: userInfo) {
            emitEvent("onNotificationTapped", body: message.toDictionary())
        }
        completionHandler()
    }
}

// MARK: - APNs Token Forwarding
extension RiviumPushReactNative {
    @objc static func didRegisterForRemoteNotificationsWithDeviceToken(_ deviceToken: Data) {
        RiviumPush.shared.setAPNsToken(deviceToken)
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Log.d("ReactNative", "APNs token forwarded: \(String(token.prefix(20)))...")
    }

    @objc static func didFailToRegisterForRemoteNotificationsWithError(_ error: Error) {
        Log.d("ReactNative", "APNs registration failed: \(error.localizedDescription)")
    }
}
