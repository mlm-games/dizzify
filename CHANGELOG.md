# Changelog

All notable changes to the Dizzify launcher project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Testing Infrastructure**: Added comprehensive testing dependencies (JUnit, Mockk, Turbine, Coroutines Test)
- **Logging System**: Integrated Timber for better error tracking and debugging
- **App Details Screen**: New screen displaying app information with actions (open app info, uninstall)
- **Favorites System**: Users can now mark apps as favorites with persistent storage via DataStore
- **Unit Tests**: Created `LauncherViewModelTest` with test infrastructure (`MainDispatcherRule`)

### Changed
- **Architecture Improvement**: Refactored `LauncherViewModel` to accept `AppRepository` via dependency injection for better testability
- **Error Handling**: Added proper error logging in `LauncherViewModel` for app loading and refresh operations
- **Build Configuration**: Updated JVM target to Java 21 for modern language features

### Fixed
- **Navigation**: Wired up App Details screen navigation that was previously a placeholder
- **TODO Cleanup**: Implemented favorites functionality that was marked as TODO in `TvHomeScreen`

## [v1.0.0] - 2025-12-26

### Initial Release
- Privacy-focused minimalist Android launcher
- App hiding by default to reduce screen time
- Quick actions (double-tap to lock, swipe gestures)
- Customizable app renaming and hiding
- Search with fuzzy matching and transliteration support
- Icon pack support
- TV-optimized UI with sidebar navigation
- Private space support (Android 15+)
- Material3 design with dark theme
- Multi-language support (18 locales)

### Technical Stack
- Kotlin 2.3.0
- Jetpack Compose with Material3
- Koin for dependency injection
- DataStore for settings persistence
- Navigation3 (experimental)
- Gradle 9.2.1 with version catalogs

[Unreleased]: https://github.com/mlm-games/dizzify/compare/v1.0.0...HEAD
[v1.0.0]: https://github.com/mlm-games/dizzify/releases/tag/v1.0.0
