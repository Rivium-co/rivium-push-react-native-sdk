/**
 * RiviumPush React Native Module
 * Bridge between React Native and native Android SDK
 */
package co.rivium.push.reactnative

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

// Import from native SDK
import co.rivium.push.sdk.RiviumPush
import co.rivium.push.sdk.RiviumPushCallbackAdapter
import co.rivium.push.sdk.RiviumPushConfig
import co.rivium.push.sdk.RiviumPushError
import co.rivium.push.sdk.RiviumPushLogLevel
import co.rivium.push.sdk.RiviumPushMessage
import co.rivium.push.sdk.RiviumPushService
import co.rivium.push.sdk.inapp.InAppMessage
import co.rivium.push.sdk.inapp.InAppButton
import co.rivium.push.sdk.inapp.InAppMessageCallback
import co.rivium.push.sdk.inbox.InboxFilter
import co.rivium.push.sdk.inbox.InboxMessage
import co.rivium.push.sdk.inbox.InboxMessageStatus
import co.rivium.push.sdk.abtesting.ABTestingCallback
import co.rivium.push.sdk.abtesting.ABTestVariant
import co.rivium.push.sdk.abtesting.ABTestSummary

class RiviumPushReactNativeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    companion object {
        const val TAG = "RiviumPushRN"
        const val NAME = "RiviumPushReactNative"
    }

    private var listenerCount = 0
    private var isInForeground = true
    private var lifecycleObserverRegistered = false

    init {
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName(): String = NAME

    override fun getConstants(): MutableMap<String, Any> {
        return hashMapOf(
            "LOG_LEVEL_NONE" to "none",
            "LOG_LEVEL_ERROR" to "error",
            "LOG_LEVEL_WARNING" to "warning",
            "LOG_LEVEL_INFO" to "info",
            "LOG_LEVEL_DEBUG" to "debug",
            "LOG_LEVEL_VERBOSE" to "verbose"
        )
    }

    @ReactMethod
    fun init(configMap: ReadableMap, promise: Promise) {
        try {
            val config = RiviumPushConfig(
                apiKey = configMap.getString("apiKey") ?: throw Exception("apiKey is required"),
                notificationIcon = configMap.getString("notificationIcon"),
                showServiceNotification = if (configMap.hasKey("showServiceNotification"))
                    configMap.getBoolean("showServiceNotification") else true,
                showNotificationInForeground = if (configMap.hasKey("showNotificationInForeground"))
                    configMap.getBoolean("showNotificationInForeground") else true
            )

            // Initialize native SDK
            RiviumPush.init(reactApplicationContext, config)

            // Set up callbacks to forward events to React Native
            RiviumPush.setCallback(object : RiviumPushCallbackAdapter() {
                override fun onMessageReceived(message: RiviumPushMessage) {
                    Log.d(TAG, "Message received: ${message.title}")
                    sendEvent("onMessage", messageToMap(message))
                }

                override fun onConnectionStateChanged(connected: Boolean) {
                    Log.d(TAG, "Connection state changed: $connected")
                    sendEvent("onConnectionState", connected)
                }

                override fun onRegistered(deviceId: String) {
                    Log.d(TAG, "Device registered: $deviceId")
                    sendEvent("onRegistered", deviceId)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error: $error")
                    sendEvent("onError", error)
                }

                override fun onDetailedError(error: RiviumPushError) {
                    Log.e(TAG, "Detailed error: ${error.code} - ${error.message}")
                    sendEvent("onDetailedError", Arguments.createMap().apply {
                        putInt("code", error.code)
                        putString("message", error.message)
                        putString("details", error.details)
                    })
                }

                override fun onReconnecting(attempt: Int, nextRetryMs: Long) {
                    Log.d(TAG, "Reconnecting: attempt=$attempt, nextRetry=${nextRetryMs}ms")
                    val map = Arguments.createMap().apply {
                        putInt("retryAttempt", attempt)
                        putDouble("nextRetryMs", nextRetryMs.toDouble())
                    }
                    sendEvent("onReconnecting", map)
                }

                override fun onNetworkStateChanged(isAvailable: Boolean, networkType: String) {
                    Log.d(TAG, "Network state: available=$isAvailable, type=$networkType")
                    val map = Arguments.createMap().apply {
                        putBoolean("isAvailable", isAvailable)
                        putString("networkType", networkType)
                    }
                    sendEvent("onNetworkState", map)
                }

                override fun onAppStateChanged(isInForeground: Boolean) {
                    Log.d(TAG, "App state: foreground=$isInForeground")
                    val map = Arguments.createMap().apply {
                        putBoolean("isInForeground", isInForeground)
                    }
                    sendEvent("onAppState", map)
                }

                override fun onAppUpdated(previousVersion: String, currentVersion: String, needsReregistration: Boolean) {
                    Log.d(TAG, "App updated: $previousVersion -> $currentVersion")
                    val map = Arguments.createMap().apply {
                        putString("previousVersion", previousVersion)
                        putString("currentVersion", currentVersion)
                        putBoolean("needsReregistration", needsReregistration)
                    }
                    sendEvent("onAppUpdated", map)
                }
            })

            // Set up in-app message callback
            RiviumPush.setInAppMessageCallback(object : InAppMessageCallback {
                override fun onMessageReady(message: InAppMessage) {
                    Log.d(TAG, "In-app message ready: ${message.name}")
                    sendEvent("onInAppMessageReady", inAppMessageToMap(message))
                }

                override fun onButtonClicked(message: InAppMessage, button: InAppButton) {
                    Log.d(TAG, "In-app button clicked: ${button.text}")
                    val map = Arguments.createMap().apply {
                        putMap("message", inAppMessageToMap(message))
                        putMap("button", inAppButtonToMap(button))
                    }
                    sendEvent("onInAppButtonClick", map)
                }

                override fun onMessageDismissed(message: InAppMessage) {
                    Log.d(TAG, "In-app message dismissed: ${message.name}")
                    sendEvent("onInAppMessageDismissed", inAppMessageToMap(message))
                }

                override fun onError(error: String) {
                    Log.e(TAG, "In-app message error: $error")
                    sendEvent("onError", error)
                }
            })

            // Set up A/B testing callback
            RiviumPush.setABTestingCallback(object : ABTestingCallback {
                override fun onVariantAssigned(variant: ABTestVariant) {
                    Log.d(TAG, "A/B test variant assigned: ${variant.variantName}")
                    sendEvent("onABTestVariantAssigned", abTestVariantToMap(variant))
                }

                override fun onError(testId: String?, error: String) {
                    Log.e(TAG, "A/B test error for $testId: $error")
                    val map = Arguments.createMap().apply {
                        putString("testId", testId ?: "")
                        putString("error", error)
                    }
                    sendEvent("onABTestError", map)
                }
            })

            // Set up inbox callback for real-time inbox updates
            RiviumPush.getInboxManager().setCallback(object : co.rivium.push.sdk.inbox.InboxCallback {
                override fun onMessageReceived(message: co.rivium.push.sdk.inbox.InboxMessage) {
                    Log.d(TAG, "Inbox message received: ${message.content.title}")
                    val map = Arguments.createMap().apply {
                        putString("id", message.id)
                        putString("title", message.content.title)
                        putString("body", message.content.body)
                        putString("category", message.category)
                        putString("status", message.status.name.lowercase())
                        putString("createdAt", message.createdAt)
                    }
                    sendEvent("onInboxMessageReceived", map)
                }

                override fun onMessageStatusChanged(messageId: String, status: co.rivium.push.sdk.inbox.InboxMessageStatus) {
                    Log.d(TAG, "Inbox message status changed: $messageId -> $status")
                    val map = Arguments.createMap().apply {
                        putString("messageId", messageId)
                        putString("status", status.name.lowercase())
                    }
                    sendEvent("onInboxMessageStatusChanged", map)
                }
            })

            // Set up lifecycle observer
            setupLifecycleObserver()

            Log.d(TAG, "RiviumPush SDK initialized with native SDK")
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}")
            promise.reject("INIT_ERROR", e.message)
        }
    }

    @ReactMethod
    fun register(userId: String?, metadata: ReadableMap?, promise: Promise) {
        try {
            val metadataMap = metadata?.let { map ->
                val result = mutableMapOf<String, Any>()
                val iterator = map.keySetIterator()
                while (iterator.hasNextKey()) {
                    val key = iterator.nextKey()
                    when (map.getType(key)) {
                        ReadableType.String -> result[key] = map.getString(key) ?: ""
                        ReadableType.Number -> result[key] = map.getDouble(key)
                        ReadableType.Boolean -> result[key] = map.getBoolean(key)
                        else -> {}
                    }
                }
                result
            }

            RiviumPush.register(userId, metadataMap)
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Register error: ${e.message}")
            promise.reject("REGISTER_ERROR", e.message)
        }
    }

    @ReactMethod
    fun unregister(promise: Promise) {
        try {
            RiviumPush.unregister()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("UNREGISTER_ERROR", e.message)
        }
    }

    @ReactMethod
    fun subscribeTopic(topic: String, promise: Promise) {
        try {
            RiviumPush.subscribeTopic(topic)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SUBSCRIBE_ERROR", e.message)
        }
    }

    @ReactMethod
    fun unsubscribeTopic(topic: String, promise: Promise) {
        try {
            RiviumPush.unsubscribeTopic(topic)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("UNSUBSCRIBE_ERROR", e.message)
        }
    }

    @ReactMethod
    fun isConnected(promise: Promise) {
        try {
            val connected = RiviumPush.isConnected()
            promise.resolve(connected)
        } catch (e: Exception) {
            promise.reject("CONNECTION_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getDeviceId(promise: Promise) {
        try {
            val deviceId = RiviumPush.getDeviceId()
            promise.resolve(deviceId)
        } catch (e: Exception) {
            promise.reject("DEVICE_ID_ERROR", e.message)
        }
    }

    @ReactMethod
    fun setUserId(userId: String, promise: Promise) {
        try {
            RiviumPush.setUserId(userId)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SET_USER_ID_ERROR", e.message)
        }
    }

    @ReactMethod
    fun clearUserId(promise: Promise) {
        try {
            RiviumPush.clearUserId()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CLEAR_USER_ID_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getInitialMessage(promise: Promise) {
        try {
            val message = RiviumPush.getInitialMessage()
            if (message != null) {
                RiviumPush.clearInitialMessage()
                promise.resolve(messageToMap(message))
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("INITIAL_MESSAGE_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getClickedAction(promise: Promise) {
        try {
            val action = RiviumPush.getClickedAction()
            if (action != null) {
                val map = Arguments.createMap().apply {
                    action.forEach { (k, v) ->
                        if (v != null) putString(k, v) else putNull(k)
                    }
                }
                promise.resolve(map)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("CLICKED_ACTION_ERROR", e.message)
        }
    }

    @ReactMethod
    fun setLogLevel(level: String, promise: Promise) {
        try {
            val logLevel = when (level.lowercase()) {
                "none" -> RiviumPushLogLevel.NONE
                "error" -> RiviumPushLogLevel.ERROR
                "warning" -> RiviumPushLogLevel.WARNING
                "info" -> RiviumPushLogLevel.INFO
                "debug" -> RiviumPushLogLevel.DEBUG
                "verbose" -> RiviumPushLogLevel.VERBOSE
                else -> RiviumPushLogLevel.DEBUG
            }
            RiviumPush.setLogLevel(logLevel)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("LOG_LEVEL_ERROR", e.message)
        }
    }

    // ==================== In-App Messages ====================

    @ReactMethod
    fun fetchInAppMessages(promise: Promise) {
        try {
            RiviumPush.fetchInAppMessages { messages ->
                val array = Arguments.createArray()
                messages.forEach { message ->
                    array.pushMap(inAppMessageToMap(message))
                }
                promise.resolve(array)
            }
        } catch (e: Exception) {
            promise.reject("FETCH_INAPP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun triggerInAppOnAppOpen(promise: Promise) {
        try {
            RiviumPush.triggerInAppOnAppOpen()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TRIGGER_INAPP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun triggerInAppEvent(eventName: String, properties: ReadableMap?, promise: Promise) {
        try {
            val propsMap = properties?.let { map ->
                val result = mutableMapOf<String, Any>()
                val iterator = map.keySetIterator()
                while (iterator.hasNextKey()) {
                    val key = iterator.nextKey()
                    when (map.getType(key)) {
                        ReadableType.String -> result[key] = map.getString(key) ?: ""
                        ReadableType.Number -> result[key] = map.getDouble(key)
                        ReadableType.Boolean -> result[key] = map.getBoolean(key)
                        else -> {}
                    }
                }
                result
            }
            RiviumPush.triggerInAppEvent(eventName, propsMap)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TRIGGER_INAPP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun triggerInAppOnSessionStart(promise: Promise) {
        try {
            RiviumPush.triggerInAppOnSessionStart()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TRIGGER_INAPP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun showInAppMessage(messageId: String, promise: Promise) {
        try {
            RiviumPush.showInAppMessage(messageId)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SHOW_INAPP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun dismissInAppMessage(promise: Promise) {
        try {
            RiviumPush.dismissInAppMessage()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DISMISS_INAPP_ERROR", e.message)
        }
    }

    // ==================== Inbox ====================

    @ReactMethod
    fun getInboxMessages(filterMap: ReadableMap?, promise: Promise) {
        try {
            val status = filterMap?.getString("status")?.let {
                when (it.lowercase()) {
                    "unread" -> InboxMessageStatus.UNREAD
                    "read" -> InboxMessageStatus.READ
                    "archived" -> InboxMessageStatus.ARCHIVED
                    else -> null
                }
            }
            val category = filterMap?.getString("category")
            val limit = if (filterMap?.hasKey("limit") == true) filterMap.getInt("limit") else 50
            val offset = if (filterMap?.hasKey("offset") == true) filterMap.getInt("offset") else 0

            val filter = InboxFilter(
                status = status,
                category = category,
                limit = limit,
                offset = offset
            )

            RiviumPush.getInboxMessages(
                filter = filter,
                onSuccess = { response ->
                    val map = Arguments.createMap().apply {
                        val messagesArray = Arguments.createArray()
                        response.messages.forEach { msg ->
                            messagesArray.pushMap(inboxMessageToMap(msg))
                        }
                        putArray("messages", messagesArray)
                        putInt("total", response.total)
                        putInt("unreadCount", response.unreadCount)
                    }
                    promise.resolve(map)
                },
                onError = { error ->
                    promise.reject("INBOX_ERROR", error)
                }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getInboxMessage(messageId: String, promise: Promise) {
        try {
            RiviumPush.getInboxMessage(
                messageId = messageId,
                onSuccess = { message ->
                    promise.resolve(inboxMessageToMap(message))
                },
                onError = { error ->
                    promise.reject("INBOX_ERROR", error)
                }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun markInboxMessageAsRead(messageId: String, promise: Promise) {
        try {
            RiviumPush.markInboxMessageAsRead(
                messageId = messageId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun archiveInboxMessage(messageId: String, promise: Promise) {
        try {
            RiviumPush.archiveInboxMessage(
                messageId = messageId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun deleteInboxMessage(messageId: String, promise: Promise) {
        try {
            RiviumPush.deleteInboxMessage(
                messageId = messageId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun markMultipleInboxMessages(messageIds: ReadableArray, statusStr: String, promise: Promise) {
        try {
            val ids = mutableListOf<String>()
            for (i in 0 until messageIds.size()) {
                messageIds.getString(i)?.let { ids.add(it) }
            }

            val status = when (statusStr.lowercase()) {
                "read" -> InboxMessageStatus.READ
                "archived" -> InboxMessageStatus.ARCHIVED
                "deleted" -> InboxMessageStatus.DELETED
                else -> InboxMessageStatus.READ
            }

            RiviumPush.markMultipleInboxMessages(
                messageIds = ids,
                status = status,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun markAllInboxMessagesAsRead(promise: Promise) {
        try {
            RiviumPush.markAllInboxMessagesAsRead(
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getInboxUnreadCount(promise: Promise) {
        try {
            val count = RiviumPush.getInboxUnreadCount()
            promise.resolve(count)
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun fetchInboxUnreadCount(promise: Promise) {
        try {
            RiviumPush.fetchInboxUnreadCount(
                onSuccess = { count -> promise.resolve(count) },
                onError = { error -> promise.reject("INBOX_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getCachedInboxMessages(promise: Promise) {
        try {
            val messages = RiviumPush.getCachedInboxMessages()
            val array = Arguments.createArray()
            messages.forEach { msg ->
                array.pushMap(inboxMessageToMap(msg))
            }
            promise.resolve(array)
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    @ReactMethod
    fun clearInboxCache(promise: Promise) {
        try {
            RiviumPush.clearInboxCache()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("INBOX_ERROR", e.message)
        }
    }

    // ==================== A/B Testing ====================

    @ReactMethod
    fun getActiveABTests(promise: Promise) {
        try {
            RiviumPush.getActiveABTests(
                onSuccess = { tests ->
                    val array = Arguments.createArray()
                    tests.forEach { test ->
                        array.pushMap(abTestSummaryToMap(test))
                    }
                    promise.resolve(array)
                },
                onError = { error ->
                    promise.reject("ABTEST_ERROR", error)
                }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getABTestVariant(testId: String, forceRefresh: Boolean, promise: Promise) {
        try {
            RiviumPush.getABTestVariant(
                testId = testId,
                forceRefresh = forceRefresh,
                onSuccess = { variant ->
                    promise.resolve(abTestVariantToMap(variant))
                },
                onError = { error ->
                    promise.reject("ABTEST_ERROR", error)
                }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getCachedABTestVariant(testId: String, promise: Promise) {
        try {
            val variant = RiviumPush.getCachedABTestVariant(testId)
            if (variant != null) {
                promise.resolve(abTestVariantToMap(variant))
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun trackABTestImpression(testId: String, variantId: String, promise: Promise) {
        try {
            RiviumPush.trackABTestImpression(
                testId = testId,
                variantId = variantId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("ABTEST_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun trackABTestOpened(testId: String, variantId: String, promise: Promise) {
        try {
            RiviumPush.trackABTestOpened(
                testId = testId,
                variantId = variantId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("ABTEST_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun trackABTestClicked(testId: String, variantId: String, promise: Promise) {
        try {
            RiviumPush.trackABTestClicked(
                testId = testId,
                variantId = variantId,
                onSuccess = { promise.resolve(null) },
                onError = { error -> promise.reject("ABTEST_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun trackABTestDisplay(testId: String, variantId: String, promise: Promise) {
        try {
            // trackDisplay = impression + opened (same as Flutter plugin)
            RiviumPush.trackABTestImpression(
                testId = testId,
                variantId = variantId,
                onSuccess = {
                    RiviumPush.trackABTestOpened(
                        testId = testId,
                        variantId = variantId,
                        onSuccess = { promise.resolve(null) },
                        onError = { error -> promise.reject("ABTEST_ERROR", error) }
                    )
                },
                onError = { error -> promise.reject("ABTEST_ERROR", error) }
            )
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    @ReactMethod
    fun clearABTestCache(promise: Promise) {
        try {
            RiviumPush.clearABTestingCache()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ABTEST_ERROR", e.message)
        }
    }

    // ==================== Service Notification ====================

    @ReactMethod
    fun isServiceNotificationHidden(promise: Promise) {
        try {
            promise.resolve(RiviumPush.isServiceNotificationHidden())
        } catch (e: Exception) {
            promise.reject("NOTIFICATION_ERROR", e.message)
        }
    }

    @ReactMethod
    fun openServiceNotificationSettings(promise: Promise) {
        try {
            RiviumPush.openServiceNotificationSettings()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("NOTIFICATION_ERROR", e.message)
        }
    }

    // ==================== Event Listeners ====================

    @ReactMethod
    fun addListener(eventName: String) {
        listenerCount++
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount -= count
    }

    // ==================== Lifecycle ====================

    override fun onHostResume() {
        isInForeground = true
        reactApplicationContext.currentActivity?.let { RiviumPush.setCurrentActivity(it) }
    }

    override fun onHostPause() {
        isInForeground = false
    }

    override fun onHostDestroy() {
        RiviumPush.setCurrentActivity(null)
    }

    // ==================== Private Helpers ====================

    private fun setupLifecycleObserver() {
        if (lifecycleObserverRegistered) return

        try {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!isInForeground) {
                            isInForeground = true
                            if (!RiviumPush.isConnected()) {
                                RiviumPushService.reconnectNow()
                            }
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        isInForeground = false
                    }
                    else -> {}
                }
            }
            // addObserver must be called on the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                    lifecycleObserverRegistered = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add lifecycle observer: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup lifecycle observer: ${e.message}")
        }
    }

    private fun sendEvent(eventName: String, params: Any?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun messageToMap(message: RiviumPushMessage): WritableMap {
        return Arguments.createMap().apply {
            putString("title", message.title)
            putString("body", message.body)
            putBoolean("silent", message.silent)
            message.imageUrl?.let { putString("imageUrl", it) }
            message.iconUrl?.let { putString("iconUrl", it) }
            message.deepLink?.let { putString("deepLink", it) }
            message.badge?.let { putInt("badge", it) }
            message.badgeAction?.let { putString("badgeAction", it) }
            message.sound?.let { putString("sound", it) }
            message.threadId?.let { putString("threadId", it) }
            message.collapseKey?.let { putString("collapseKey", it) }
            message.category?.let { putString("category", it) }
            message.priority?.let { putString("priority", it) }
            message.ttl?.let { putInt("ttl", it) }
            message.messageId?.let { putString("messageId", it) }
            message.campaignId?.let { putString("campaignId", it) }

            message.data?.let { data ->
                val dataMap = Arguments.createMap()
                data.forEach { (k, v) ->
                    when (v) {
                        is String -> dataMap.putString(k, v)
                        is Number -> dataMap.putDouble(k, v.toDouble())
                        is Boolean -> dataMap.putBoolean(k, v)
                        else -> dataMap.putString(k, v.toString())
                    }
                }
                putMap("data", dataMap)
            }

            message.actions?.let { actions ->
                val actionsArray = Arguments.createArray()
                actions.forEach { action ->
                    val actionMap = Arguments.createMap().apply {
                        putString("id", action.id)
                        putString("title", action.title)
                        action.action?.let { putString("action", it) }
                        action.icon?.let { putString("icon", it) }
                    }
                    actionsArray.pushMap(actionMap)
                }
                putArray("actions", actionsArray)
            }
        }
    }

    private fun inAppMessageToMap(message: InAppMessage): WritableMap {
        return Arguments.createMap().apply {
            putString("id", message.id)
            putString("name", message.name)
            putString("type", message.type.value)
            putMap("content", inAppContentToMap(message.content))
            putString("triggerType", message.triggerType.value)
            message.triggerEvent?.let { putString("triggerEvent", it) }
            message.startDate?.let { putDouble("startDate", it.toDouble()) }
            message.endDate?.let { putDouble("endDate", it.toDouble()) }
            putInt("maxImpressions", message.maxImpressions)
            putInt("minSessionCount", message.minSessionCount)
            putInt("delaySeconds", message.delaySeconds)
            putInt("priority", message.priority)
        }
    }

    private fun inAppContentToMap(content: co.rivium.push.sdk.inapp.InAppMessageContent): WritableMap {
        return Arguments.createMap().apply {
            putString("title", content.title)
            putString("body", content.body)
            content.imageUrl?.let { putString("imageUrl", it) }
            content.backgroundColor?.let { putString("backgroundColor", it) }
            content.textColor?.let { putString("textColor", it) }

            val buttonsArray = Arguments.createArray()
            content.buttons.forEach { button ->
                buttonsArray.pushMap(inAppButtonToMap(button))
            }
            putArray("buttons", buttonsArray)
        }
    }

    private fun inAppButtonToMap(button: InAppButton): WritableMap {
        return Arguments.createMap().apply {
            putString("id", button.id)
            putString("text", button.text)
            putString("action", button.action.value)
            button.value?.let { putString("value", it) }
            putString("style", button.style.value)
        }
    }

    private fun inboxMessageToMap(message: InboxMessage): WritableMap {
        return Arguments.createMap().apply {
            putString("id", message.id)
            message.userId?.let { putString("userId", it) }
            message.deviceId?.let { putString("deviceId", it) }

            val contentMap = Arguments.createMap().apply {
                putString("title", message.content.title)
                putString("body", message.content.body)
                message.content.imageUrl?.let { putString("imageUrl", it) }
                message.content.iconUrl?.let { putString("iconUrl", it) }
                message.content.deepLink?.let { putString("deepLink", it) }
                message.content.data?.let { data ->
                    val dataMap = Arguments.createMap()
                    data.forEach { (k, v) ->
                        when (v) {
                            is String -> dataMap.putString(k, v)
                            is Number -> dataMap.putDouble(k, v.toDouble())
                            is Boolean -> dataMap.putBoolean(k, v)
                            else -> dataMap.putString(k, v.toString())
                        }
                    }
                    putMap("data", dataMap)
                }
            }
            putMap("content", contentMap)

            putString("status", message.status.name.lowercase())
            message.category?.let { putString("category", it) }
            message.expiresAt?.let { putString("expiresAt", it) }
            message.readAt?.let { putString("readAt", it) }
            putString("createdAt", message.createdAt)
            message.updatedAt?.let { putString("updatedAt", it) }
        }
    }

    private fun abTestSummaryToMap(summary: ABTestSummary): WritableMap {
        return Arguments.createMap().apply {
            putString("id", summary.id)
            putString("name", summary.name)
            putInt("variantCount", summary.variantCount)
            putBoolean("hasControlGroup", summary.hasControlGroup)
        }
    }

    private fun abTestVariantToMap(variant: ABTestVariant): WritableMap {
        return Arguments.createMap().apply {
            putString("testId", variant.testId)
            putString("variantId", variant.variantId)
            putString("variantName", variant.variantName)
            putBoolean("isControlGroup", variant.isControlGroup)
            variant.content?.let { content ->
                val contentMap = Arguments.createMap().apply {
                    content.title?.let { putString("title", it) }
                    content.body?.let { putString("body", it) }
                    content.imageUrl?.let { putString("imageUrl", it) }
                    content.deepLink?.let { putString("deepLink", it) }
                    content.data?.let { data ->
                        val dataMap = Arguments.createMap()
                        data.forEach { (k, v) ->
                            when (v) {
                                is String -> dataMap.putString(k, v)
                                is Number -> dataMap.putDouble(k, v.toDouble())
                                is Boolean -> dataMap.putBoolean(k, v)
                                else -> dataMap.putString(k, v.toString())
                            }
                        }
                        putMap("data", dataMap)
                    }
                    content.actions?.let { actions ->
                        val actionsArray = Arguments.createArray()
                        actions.forEach { action ->
                            val actionMap = Arguments.createMap().apply {
                                putString("id", action.id)
                                putString("title", action.title)
                                putString("action", action.action)
                            }
                            actionsArray.pushMap(actionMap)
                        }
                        putArray("actions", actionsArray)
                    }
                }
                putMap("content", contentMap)
            }
        }
    }

    override fun invalidate() {
        super.invalidate()
        RiviumPush.setCurrentActivity(null)
    }
}
