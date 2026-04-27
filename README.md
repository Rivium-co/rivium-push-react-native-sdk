# RiviumPush React Native SDK

Real-time push notifications, in-app messages, inbox, A/B testing, and more for React Native apps. No Firebase dependency.

## Features

- Push Notifications (Android & iOS)
- In-App Messages (modal, banner, fullscreen)
- Inbox (persistent message center with read/archive)
- A/B Testing (experiments and variant assignment)
- Topic Subscriptions
- Silent Messages
- Notification Tap Handling
- Auto-reconnection with exponential backoff
- TypeScript support

## Installation

```bash
npm install rivium-push-react-native
# or
yarn add rivium-push-react-native
```

### Android Setup

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### iOS Setup

```bash
cd ios && pod install
```

Enable in Xcode -> Signing & Capabilities:
- Push Notifications
- Background Modes -> Remote notifications

## Quick Start

```typescript
import RiviumPush from 'rivium-push-react-native';

// Initialize on app start
await RiviumPush.init({
  apiKey: 'rv_live_your_api_key',  // Get from Rivium Console
});

// Listen for messages
const unsubscribe = RiviumPush.onMessage((message) => {
  console.log('Title:', message.title);
  console.log('Body:', message.body);
  console.log('Data:', message.data);
});

// Register device
await RiviumPush.register({ userId: 'user_123' });

// Clean up when done
unsubscribe();
```

## Configuration

```typescript
await RiviumPush.init({
  apiKey: 'rv_live_...',              // Required - from Rivium Console
  notificationIcon: 'ic_notification', // Optional - Android notification icon
  usePushKit: false,                   // Optional - iOS VoIP mode for calling apps
  showServiceNotification: true,       // Optional - foreground service notification (Android)
});
```

> **Note:** Connection configuration is automatically fetched from the server using your API key. No manual setup needed.

## React Hook Example

```typescript
import { useEffect, useState } from 'react';
import RiviumPush from 'rivium-push-react-native';

export function useRiviumPush(userId: string) {
  const [connected, setConnected] = useState(false);
  const [deviceId, setDeviceId] = useState<string | null>(null);

  useEffect(() => {
    const unsubMessage = RiviumPush.onMessage((msg) => {
      console.log('Message:', msg);
    });

    const unsubConnection = RiviumPush.onConnectionState(setConnected);
    const unsubRegistered = RiviumPush.onRegistered(setDeviceId);

    RiviumPush.register({ userId });

    return () => {
      unsubMessage();
      unsubConnection();
      unsubRegistered();
    };
  }, [userId]);

  return { connected, deviceId };
}
```

## Callbacks

All event handlers return an unsubscribe function for proper cleanup.

```typescript
// Receive push messages
const unsub = RiviumPush.onMessage((message) => {
  console.log(message.title, message.body);
  console.log('Data:', message.data);
  console.log('Silent:', message.silent);
});

// Connection status
RiviumPush.onConnectionState((connected) => {});

// Registration complete
RiviumPush.onRegistered((deviceId) => {});

// Errors
RiviumPush.onError((error) => {});

// Detailed errors with codes
RiviumPush.onDetailedError((error) => {
  console.log('Code:', error.code, 'Message:', error.message);
});

// Reconnection attempts
RiviumPush.onReconnecting((state) => {
  console.log('Attempt:', state.retryAttempt, 'Next in:', state.nextRetryMs, 'ms');
});

// Network state changes
RiviumPush.onNetworkState((state) => {
  console.log('Available:', state.isAvailable, 'Type:', state.networkType);
});

// App foreground/background
RiviumPush.onAppState((state) => {
  console.log('Foreground:', state.isInForeground);
});

// Notification tap handling
RiviumPush.onNotificationAction((event) => {
  console.log('Action:', event.actionId);
});
```

## Notification Tap Handling

```typescript
// Get the message that launched the app (from notification tap)
const message = await RiviumPush.getInitialMessage();
if (message) {
  // Navigate based on message.data
}
```

## Topics

```typescript
await RiviumPush.subscribeTopic('news');
await RiviumPush.subscribeTopic('promotions');
await RiviumPush.unsubscribeTopic('promotions');
```

## User Management

```typescript
// Set user ID after login
await RiviumPush.setUserId('user_123');

// Clear user ID on logout
await RiviumPush.clearUserId();

// Register with user ID and metadata
await RiviumPush.register({
  userId: 'user_123',
  metadata: { appVersion: '1.0.0', language: 'en' },
});
```

## In-App Messages

```typescript
// Trigger in-app messages on app open
await RiviumPush.triggerInAppOnAppOpen();

// Trigger custom event
await RiviumPush.triggerInAppEvent('purchase_complete', { amount: 29.99 });

// Listen for in-app messages
RiviumPush.onInAppMessageReady((message) => {
  console.log('In-app message:', message.name);
});

RiviumPush.onInAppButtonClick((message, button) => {
  console.log('Button clicked:', button.text);
});

// Fetch, show, dismiss
const messages = await RiviumPush.fetchInAppMessages();
await RiviumPush.showInAppMessage(messageId);
await RiviumPush.dismissInAppMessage();
```

## Inbox

```typescript
// Get inbox messages
const response = await RiviumPush.getInboxMessages({
  status: InboxMessageStatus.UNREAD,
  limit: 50,
});

// Mark as read
await RiviumPush.markInboxMessageAsRead(messageId);

// Archive
await RiviumPush.archiveInboxMessage(messageId);

// Get unread count
const count = await RiviumPush.getInboxUnreadCount();

// Mark all as read
await RiviumPush.markAllInboxMessagesAsRead();
```

## A/B Testing

```typescript
// Get active tests
const tests = await RiviumPush.getActiveABTests();

// Get variant for a test
const variant = await RiviumPush.getABTestVariant('test_id');
if (variant) {
  console.log('Variant:', variant.variantName);
  console.log('Control:', variant.isControlGroup);
}

// Track events
await RiviumPush.trackABTestImpression('test_id', 'variant_id');
await RiviumPush.trackABTestClicked('test_id', 'variant_id');

// Listen for variant assignments
RiviumPush.onABTestVariantAssigned((variant) => {
  console.log('Assigned to:', variant.variantName);
});
```

## Log Levels

```typescript
import { LogLevel } from 'rivium-push-react-native';

await RiviumPush.setLogLevel(LogLevel.DEBUG);   // Development
await RiviumPush.setLogLevel(LogLevel.ERROR);   // Production

// Available: NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE
```

## Utilities

```typescript
const connected = await RiviumPush.isConnected();
const deviceId = await RiviumPush.getDeviceId();
await RiviumPush.unregister();
RiviumPush.removeAllListeners();
```

## Example

See the [example](example/) directory for a complete working app with all features demonstrated.

The Push SDK works independently without VoIP. To trigger VoIP incoming call UI, send push with data:

```json
{
  "type": "voip_call",
  "callerName": "John Doe",
  "callerId": "user_456",
  "callerAvatar": "https://example.com/avatar.jpg",
  "callType": "video"
}
```

The `type: "voip_call"` is the system trigger (fixed). Caller data keys are configurable via VoIP SDK config.

## Links

- [Rivium Push](https://rivium.co/cloud/rivium-push) - Learn more about Rivium Push
- [Documentation](https://rivium.co/cloud/rivium-push/docs/quick-start) - Full documentation and guides
- [Rivium Console](https://console.rivium.co) - Manage your push notifications

## License

MIT License - see [LICENSE](LICENSE) for details.
