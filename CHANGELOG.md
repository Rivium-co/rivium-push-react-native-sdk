# Changelog

## [0.1.11] - 2026-09-14

### Added
- `autoRefresh` on the `init()` config (default `true`): keeps a registered device up to date on launch.
- The package reports itself as `react-native` with its version (`SDK_VERSION` export).
- iOS: delivery confirmation for foreground notifications.

### Changed
- Requires `RiviumPushSDK ~> 0.1.12` (iOS) and `rivium-push-android:0.1.12`.

### Fixed
- Android: delivery confirmations were never sent (the package used an older Android SDK).
- Podspec `source` pointed to a non-existent repository.

## [0.1.10] - 2026-08-23

### Added
- The app build number (Android `versionCode` / iOS `CFBundleVersion`) is now sent as a separate device attribute on every `register()` call. Filter on it in the dashboard's segment builder to target specific builds within the same release — useful for hotfix rollouts and staged releases.

## [0.1.9] - 2026-08-23

### Added
- Device attributes (app version, OS version, device model, language, country, timezone) are now sent automatically on every `register()` call. Use them as preset filters in the dashboard's segment builder to target specific app releases, OS versions, locales, or regions — no need to populate metadata yourself.

## [0.1.8] - 2026-08-18

### Fixed
- Android <14: crash on start (0.1.7 regression). Bumped native SDK to 0.1.8.

## [0.1.7] - 2026-08-17

### Fixed
- Android 15+: app crashed at boot (`ForegroundServiceStartNotAllowedException`). Bumped native Android SDK to 0.1.7 which switches the foreground service to `specialUse`.
