# Changelog

## [0.1.8] - 2026-08-18

### Fixed
- Android <14: crash on start (0.1.7 regression). Bumped native SDK to 0.1.8.

## [0.1.7] - 2026-08-17

### Fixed
- Android 15+: app crashed at boot (`ForegroundServiceStartNotAllowedException`). Bumped native Android SDK to 0.1.7 which switches the foreground service to `specialUse`.
