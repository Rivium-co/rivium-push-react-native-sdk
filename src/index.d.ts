/**
 * RiviumPush React Native SDK
 * Push notifications that work everywhere - Firebase alternative
 */

/**
 * Configuration for initializing RiviumPush SDK
 *
 * Only apiKey is required. MQTT configuration is automatically
 * fetched from the server during initialization.
 */
export interface RiviumPushConfig {
  /** Your RiviumPush API key (starts with rv_live_) */
  apiKey: string;
  /** Android notification icon resource name (e.g., "ic_notification") */
  notificationIcon?: string;
  /** Enable PushKit VoIP for iOS (default: true for calling apps) */
  usePushKit?: boolean;
  /** Show persistent foreground service notification on Android (default: true) */
  showServiceNotification?: boolean;
}

/**
 * Push notification message
 */
export interface RiviumPushMessage {
  /** Notification title */
  title: string;
  /** Notification body */
  body: string;
  /** Custom data payload */
  data?: Record<string, any>;
  /** If true, message was delivered silently without notification */
  silent?: boolean;
}

/**
 * Error from RiviumPush SDK
 */
export interface RiviumPushError {
  /** Error code for programmatic handling */
  code: number;
  /** Human-readable error message */
  message: string;
  /** Additional error details */
  details?: string;
}

/**
 * Reconnection state during auto-retry
 */
export interface ReconnectionState {
  /** Current retry attempt number */
  retryAttempt: number;
  /** Time until next retry in milliseconds */
  nextRetryMs: number;
}

/**
 * Network state information
 */
export interface NetworkState {
  /** Whether network is available */
  isAvailable: boolean;
  /** Type of network connection */
  networkType: 'wifi' | 'cellular' | 'ethernet' | 'unknown' | 'none';
}

/**
 * App state information
 */
export interface AppState {
  /** Whether app is in foreground */
  isInForeground: boolean;
}

/**
 * App update information
 */
export interface AppUpdateInfo {
  /** Previous app version */
  previousVersion: string;
  /** Current app version */
  currentVersion: string;
  /** Whether re-registration is needed */
  needsReregistration: boolean;
}

/**
 * Log levels for SDK logging
 */
export enum LogLevel {
  /** No logging */
  NONE = 'none',
  /** Only errors */
  ERROR = 'error',
  /** Errors and warnings */
  WARNING = 'warning',
  /** Errors, warnings, and info */
  INFO = 'info',
  /** All messages including debug */
  DEBUG = 'debug',
  /** Everything including traces */
  VERBOSE = 'verbose',
}

export type OnMessageCallback = (message: RiviumPushMessage) => void;
export type OnConnectionStateCallback = (connected: boolean) => void;
export type OnRegisteredCallback = (deviceId: string) => void;
export type OnErrorCallback = (error: string) => void;
export type OnDetailedErrorCallback = (error: RiviumPushError) => void;
export type OnReconnectingCallback = (state: ReconnectionState) => void;
export type OnNetworkStateCallback = (state: NetworkState) => void;
export type OnAppStateCallback = (state: AppState) => void;
export type OnAppUpdatedCallback = (info: AppUpdateInfo) => void;

declare class RiviumPush {
  /**
   * Initialize the RiviumPush SDK
   */
  init(config: RiviumPushConfig): Promise<void>;

  /**
   * Register device for push notifications
   */
  register(options?: {
    userId?: string;
    metadata?: Record<string, string>;
  }): Promise<void>;

  /**
   * Unregister device and stop push service
   */
  unregister(): Promise<void>;

  /**
   * Subscribe to a topic
   */
  subscribeTopic(topic: string): Promise<void>;

  /**
   * Unsubscribe from a topic
   */
  unsubscribeTopic(topic: string): Promise<void>;

  /**
   * Check if MQTT connection is active
   */
  isConnected(): Promise<boolean>;

  /**
   * Get current device ID
   */
  getDeviceId(): Promise<string | null>;

  /**
   * Get the per-install subscription ID issued by the server during registration.
   * Returns `null` until registration succeeds at least once.
   */
  getSubscriptionId(): Promise<string | null>;

  /**
   * Set user ID for current device
   */
  setUserId(userId: string): Promise<void>;

  /**
   * Clear user ID
   */
  clearUserId(): Promise<void>;

  /**
   * Get the message that launched the app
   */
  getInitialMessage(): Promise<RiviumPushMessage | null>;

  /**
   * Set log level
   */
  setLogLevel(level: LogLevel): Promise<void>;

  /**
   * Set callback for receiving push messages
   */
  onMessage(callback: OnMessageCallback): () => void;

  /**
   * Set callback for connection state changes
   */
  onConnectionState(callback: OnConnectionStateCallback): () => void;

  /**
   * Set callback for registration success
   */
  onRegistered(callback: OnRegisteredCallback): () => void;

  /**
   * Set callback for errors
   */
  onError(callback: OnErrorCallback): () => void;

  /**
   * Set callback for detailed errors
   */
  onDetailedError(callback: OnDetailedErrorCallback): () => void;

  /**
   * Set callback for reconnection state
   */
  onReconnecting(callback: OnReconnectingCallback): () => void;

  /**
   * Set callback for network state changes
   */
  onNetworkState(callback: OnNetworkStateCallback): () => void;

  /**
   * Set callback for app state changes
   */
  onAppState(callback: OnAppStateCallback): () => void;

  /**
   * Set callback for app update detection
   */
  onAppUpdated(callback: OnAppUpdatedCallback): () => void;

  /**
   * Remove all event listeners
   */
  removeAllListeners(): void;
}

declare const riviumPush: RiviumPush;
export default riviumPush;
export { RiviumPush };
