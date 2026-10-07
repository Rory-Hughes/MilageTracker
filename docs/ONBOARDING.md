# MileageTracker — Onboarding Guide

A Canada-first mileage and vehicle-expense tracking Android app using automatic GPS-based trip detection, CRA-compliant reporting, and multi-vehicle management.

> Onboarding map for contributors: architecture layers, module conventions, and
> the change workflows that must be followed.

## Project Overview

- **Domain**: Mileage and vehicle-expense tracking for Canadian drivers (self-employed, small business, admins) with CRA-compliant logbook output and per-province/territory rates.
- **Primary language**: Kotlin (727 files). Secondary: Markdown, XML, JSON, YAML, properties, JS (Firebase Functions).
- **Frameworks**: Jetpack Compose · Room · Hilt · Firebase (Firestore/Storage/Functions/Auth) · WorkManager · Kotlin Coroutines · Navigation Compose · DataStore · Detekt · KSP · Google Maps & Places · MLKit · Coil · MockK · JUnit · GitHub Actions · AGP 9.2 · Kotlin 2.3.
- **Toolchain**: JVM 11, Min SDK 29, Target SDK 36, Compile SDK 37.

## Architecture Layers

Ten layers, derived from the import graph + DDD directory structure:

| # | Layer | Files | What lives here |
|---|---|---:|---|
| 1 | **Domain Layer** | 147 | Pure business logic — trip/vehicle/CRA models, use cases (single-purpose `suspend operator fun invoke()`), repository **interfaces**, domain services and validators. No Android dependencies. |
| 2 | **Data Layer** | 114 | Persistence — Room entities/DAOs/migrations, Firebase Firestore/Storage/Functions clients, DataStore preference repositories, and **repository implementations** that aggregate local + remote sources behind domain interfaces. |
| 3 | **UI Layer** | 146 | Jetpack Compose screens, `*ViewModel` classes exposing `StateFlow`, reusable components, Material 3 theme, navigation graphs, and the entry-point `MainActivity`. |
| 4 | **Background Services & Workers** | 16 | `DriveTrackingService` (foreground GPS), Activity Recognition receivers/registrars, and WorkManager `CoroutineWorker`s for trip buffer flushing, sync outbox, report cleanup, address backfill. |
| 5 | **Dependency Injection** | 20 | Hilt modules (`DatabaseModule`, `RepositoryModule`, `TrackingModule`, `SyncModule`, `AuthModule`, `TeamModule`, `LocalizationModule`, `NetworkModule`) plus the `MileageApplication` Hilt bootstrap class. |
| 6 | **Testing** | 281 | Unit tests (`src/test`) with MockK + `runTest`. Instrumented tests (`src/androidTest`) using a Hilt `TestMileageApplication` + custom `HiltTestRunner` to exercise Room migrations and DAOs against a real SQLite. |
| 7 | **Android Resources & Manifest** | 24 | `AndroidManifest.xml`, drawables, themes, `values/strings.xml` (English) and `values-fr/strings.xml` (French). |
| 8 | **Build & Tooling** | 23 | Gradle build scripts, `libs.versions.toml`, Detekt config, ProGuard rules, and Room schema snapshots. |
| 9 | **Firebase Backend** | 8 | `functions/index.js` (callable HTTPS handlers) + Firebase project config (`firebase.json`, security rules, indexes). |
| 10 | **CI/CD & Documentation** | 27 | `.github/workflows/android-ci.yml` (Detekt + tests + assemble) + `docs/` phase plans and PRD. |

## Key Concepts

These are the patterns and decisions that pervade the codebase. Internalize these before reading the code and most files will read fluidly.

### Strict DDD layering
The Kotlin package tree under `app/src/main/java/com/example/mileagetracker/` is split into `domain/`, `data/`, `ui/`, `service/`, `worker/`, `di/`. The domain layer is framework-free; it defines `*Repository` interfaces that the data layer implements. UI never reaches into data — it goes through ViewModels and use cases.

### UseCase = `suspend operator fun invoke()`
Single-purpose, constructor-injected classes named `XyzUseCase` with one `invoke()` method. Compose them in ViewModels. Examples: `PersistTrackedDriveUseCase`, `CalculateReimbursementUseCase`, `RecoverInterruptedTripUseCase`.

### Repository = interface in `domain/`, impl in `data/`
`DriveRepository` (interface) lives in `domain/repository/`. `DriveRepositoryImpl` lives in `data/repository/` and is bound by Hilt via `RepositoryModule`. Implementations frequently aggregate Room + Firebase + DataStore.

### Offline-first sync via outbox
Local writes go to Room immediately. `SyncOutboxWorker` (a `CoroutineWorker`) drains pending operations to Firestore via the `applySyncBatch` Cloud Function. `FirebaseSyncRepository` and `SyncRestoreApplier` handle the protocol; conflicts and restores are explicit.

### Crash-safe trip capture
GPS samples land in `TripBufferStore` (a JSON write-ahead buffer in a `buffered_trips` Room table) before the main persistence path runs. `TripBufferFlushWorker` and `FlushTripBufferUseCase` recover any buffered trips after a crash via `RecoverInterruptedTripUseCase`.

### CRA two-tier reimbursement
`CalculateReimbursementUseCase` applies the federal CRA formula in **integer cents** (not floats), splits kilometres at the first-tier threshold, and applies region-specific rates (province/territory overrides supported). `CraRateSeeder` populates the `CraRateEntity` table at app startup.

### Multi-region Firestore
Most Firestore traffic targets `us-central1`; team features target `northamerica-northeast1`. Both are exposed as named singletons via `TeamModule`. Memory cache is enabled via `MemoryCacheSettings`.

### Activity Recognition driven auto-tracking
`ActivityTransitionRegistrar` subscribes to Google Play Services Activity Recognition. `ActivityRecognitionReceiver` auto-starts/stops `DriveTrackingService` on driving transitions. Manual mode is also supported.

### Single-Activity Compose nav
`MainActivity` is a single-Activity host. `MainAppNavHost` is the root NavHost, `MainAppShell` provides the bottom-nav scaffold, and routes are type-safe `Screen` sealed types with `MainGraphRoutes` consumed by feature subgraphs (e.g. `ReportsGraphDestinations`).

### Schema integrity is enforced
Room schemas in `app/schemas/` are KSP-generated — never hand-edited; let the build regenerate them after a version bump. Every entity change requires a new `Migration_X_Y` class and an instrumented test.

### Soft deletes + audit
`isDeleted` flags rather than hard `DELETE`. `TripAuditEntity` versions user edits with `originalValue`/`newValue` for the CRA audit trail.

## Guided Tour (14 steps)

Follow these in order. Each step lists the actual files the graph identified as the canonical entry points for that area.

| # | Title | Key files |
|--:|---|---|
| 1 | **Project Overview & Conventions** | [CLAUDE.md](../CLAUDE.md) · [docs/canadian-mileage-app-prd.md](canadian-mileage-app-prd.md) · [docs/NAVIGATION_ARCHITECTURE.md](NAVIGATION_ARCHITECTURE.md) |
| 2 | **App Boot & Hilt Wiring** | [MileageApplication.kt](../app/src/main/java/com/example/mileagetracker/MileageApplication.kt) · [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) |
| 3 | **Dependency Injection Modules** | [DatabaseModule.kt](../app/src/main/java/com/example/mileagetracker/di/DatabaseModule.kt) · [RepositoryModule.kt](../app/src/main/java/com/example/mileagetracker/di/RepositoryModule.kt) · [TrackingModule.kt](../app/src/main/java/com/example/mileagetracker/di/TrackingModule.kt) · [SyncModule.kt](../app/src/main/java/com/example/mileagetracker/di/SyncModule.kt) · [AuthModule.kt](../app/src/main/java/com/example/mileagetracker/di/AuthModule.kt) |
| 4 | **Activity, Navigation & Main Shell** | [MainActivity.kt](../app/src/main/java/com/example/mileagetracker/MainActivity.kt) · [MainAppShell.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/MainAppShell.kt) · [MainAppNavHost.kt](../app/src/main/java/com/example/mileagetracker/navigation/MainAppNavHost.kt) · [Screen.kt](../app/src/main/java/com/example/mileagetracker/navigation/Screen.kt) |
| 5 | **Domain Models & Repository Contracts** | [DriveRepository.kt](../app/src/main/java/com/example/mileagetracker/domain/repository/DriveRepository.kt) · [VehicleRepository.kt](../app/src/main/java/com/example/mileagetracker/domain/repository/VehicleRepository.kt) · [RateRepository.kt](../app/src/main/java/com/example/mileagetracker/domain/repository/RateRepository.kt) · [CraRateRegion.kt](../app/src/main/java/com/example/mileagetracker/domain/model/CraRateRegion.kt) |
| 6 | **Core Use Cases** | [PersistTrackedDriveUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/drive/PersistTrackedDriveUseCase.kt) · [AutoClassifyTripUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/classification/AutoClassifyTripUseCase.kt) · [CalculateReimbursementUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/rate/CalculateReimbursementUseCase.kt) · [RecoverInterruptedTripUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/tracking/RecoverInterruptedTripUseCase.kt) |
| 7 | **Room Database & Entities** | [MileageDatabase.kt](../app/src/main/java/com/example/mileagetracker/data/local/MileageDatabase.kt) · [DriveEntity.kt](../app/src/main/java/com/example/mileagetracker/data/local/entity/DriveEntity.kt) · [VehicleEntity.kt](../app/src/main/java/com/example/mileagetracker/data/local/entity/VehicleEntity.kt) · [CraRateSeeder.kt](../app/src/main/java/com/example/mileagetracker/data/seeder/CraRateSeeder.kt) |
| 8 | **Repositories: Local + Firebase** | [RateRepositoryImpl.kt](../app/src/main/java/com/example/mileagetracker/data/repository/RateRepositoryImpl.kt) · [SettingsRepositoryImpl.kt](../app/src/main/java/com/example/mileagetracker/data/repository/SettingsRepositoryImpl.kt) · [FirebaseSyncRepository.kt](../app/src/main/java/com/example/mileagetracker/data/repository/FirebaseSyncRepository.kt) · [SyncRestoreApplier.kt](../app/src/main/java/com/example/mileagetracker/data/repository/SyncRestoreApplier.kt) |
| 9 | **GPS Tracking Pipeline** | [DriveTrackingService.kt](../app/src/main/java/com/example/mileagetracker/service/DriveTrackingService.kt) · [ActivityTransitionRegistrar.kt](../app/src/main/java/com/example/mileagetracker/service/ActivityTransitionRegistrar.kt) · [ActivityRecognitionReceiver.kt](../app/src/main/java/com/example/mileagetracker/service/ActivityRecognitionReceiver.kt) · [LiveLocationBus.kt](../app/src/main/java/com/example/mileagetracker/service/LiveLocationBus.kt) |
| 10 | **Background Workers** | [TripBufferFlushWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/TripBufferFlushWorker.kt) · [SyncOutboxWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/SyncOutboxWorker.kt) · [ReimbursementSnapshotBackfillWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/ReimbursementSnapshotBackfillWorker.kt) · [ReportHistoryCleanupWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/ReportHistoryCleanupWorker.kt) |
| 11 | **UI Layer: Trip Detail Flow** *(reference vertical slice)* | [TripDetailScreen.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/TripDetailScreen.kt) · [TripDetailScreenContent.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/TripDetailScreenContent.kt) · [TripDetailViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/TripDetailViewModel.kt) |
| 12 | **Testing Strategy** | [HiltTestRunner.kt](../app/src/androidTest/java/com/example/mileagetracker/HiltTestRunner.kt) · [TestMileageApplication.kt](../app/src/androidTest/java/com/example/mileagetracker/TestMileageApplication.kt) · [TripDetailViewModelTest.kt](../app/src/test/java/com/example/mileagetracker/ui/viewmodel/TripDetailViewModelTest.kt) · [SyncOutboxWorkerTest.kt](../app/src/test/java/com/example/mileagetracker/worker/SyncOutboxWorkerTest.kt) |
| 13 | **Build, Versions & CI** | [libs.versions.toml](../gradle/libs.versions.toml) · [app/build.gradle.kts](../app/build.gradle.kts) · [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml) · [config/detekt/detekt.yml](../config/detekt/detekt.yml) |
| 14 | **Firebase Backend** | [firebase.json](../firebase.json) · [firestore.indexes.json](../firestore.indexes.json) · [functions/index.js](../functions/index.js) |

## File Map (top files per layer)

The full graph has 806 files. These are the ones with the highest connectivity or complexity per layer — read these first when exploring a layer.

### Domain Layer
- [GetTripSummaryUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/drive/GetTripSummaryUseCase.kt) — annual/quarterly/monthly trip summaries with CRA reimbursement totals
- [CalculateReimbursementUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/rate/CalculateReimbursementUseCase.kt) — CRA two-tier formula in integer cents
- [GetReimbursementForecastUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/GetReimbursementForecastUseCase.kt) — per-driver monthly forecasts
- [CheckSimplifiedLogbookEligibilityUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/export/CheckSimplifiedLogbookEligibilityUseCase.kt) — sample-period progress for simplified logbook method
- [VehicleValidator.kt](../app/src/main/java/com/example/mileagetracker/domain/validation/VehicleValidator.kt) · [DriveValidator.kt](../app/src/main/java/com/example/mileagetracker/domain/validation/DriveValidator.kt) — pure business-rule validators

### Data Layer
- [MileageDatabase.kt](../app/src/main/java/com/example/mileagetracker/data/local/MileageDatabase.kt) — central Room aggregate; **165 edges** (highest in the project)
- [DriveEntity.kt](../app/src/main/java/com/example/mileagetracker/data/local/entity/DriveEntity.kt) — single recorded drive; **120 edges**
- [FirebaseSyncRepository.kt](../app/src/main/java/com/example/mileagetracker/data/repository/FirebaseSyncRepository.kt) — offline-first outbox/restore stack
- [TripBufferStore.kt](../app/src/main/java/com/example/mileagetracker/data/local/TripBufferStore.kt) — JSON write-ahead buffer for crash-safe trip capture
- [SettingsRepositoryImpl.kt](../app/src/main/java/com/example/mileagetracker/data/repository/SettingsRepositoryImpl.kt) — composes 6+ DataStore sub-repositories via Kotlin delegation
- [AttachmentRepositoryImpl.kt](../app/src/main/java/com/example/mileagetracker/data/repository/AttachmentRepositoryImpl.kt) — attachment file copies to internal storage

### UI Layer
- [TripDetailViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/TripDetailViewModel.kt) — orchestrates ~20 use cases/repositories
- [TripDetailScreenContent.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/TripDetailScreenContent.kt) — the most representative composable
- [DriveViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/DriveViewModel.kt) — unclassified drives, YTD distance, CRA deductions
- [AuthViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/AuthViewModel.kt) — Firebase Auth + sign-out readiness + legacy-claim preview
- [SettingsViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/SettingsViewModel.kt) — every preference, plus CRA rate diagnostics
- [ReportsGraphDestinations.kt](../app/src/main/java/com/example/mileagetracker/navigation/ReportsGraphDestinations.kt) — all reports-feature nav destinations
- [MainAppShell.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/MainAppShell.kt) — drawer + scaffold + NavHost host

### Background Services & Workers
- [DriveTrackingService.kt](../app/src/main/java/com/example/mileagetracker/service/DriveTrackingService.kt) — foreground GPS streaming
- [DriveTrackingPersistenceSupport.kt](../app/src/main/java/com/example/mileagetracker/service/DriveTrackingPersistenceSupport.kt) — snapshot → DriveEntity persistence with duplicate detection
- [ActivityTransitionRegistrar.kt](../app/src/main/java/com/example/mileagetracker/service/ActivityTransitionRegistrar.kt) — Activity Recognition subscription
- [SyncOutboxWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/SyncOutboxWorker.kt) — drains outbox to Firestore
- [TripAddressBackfillWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/TripAddressBackfillWorker.kt) · [ReimbursementSnapshotBackfillWorker.kt](../app/src/main/java/com/example/mileagetracker/worker/ReimbursementSnapshotBackfillWorker.kt) — historical data backfill

### Dependency Injection
- [MileageApplication.kt](../app/src/main/java/com/example/mileagetracker/MileageApplication.kt) — Hilt bootstrap; CRA rate seeding, worker scheduling, Places init
- [RepositoryModule.kt](../app/src/main/java/com/example/mileagetracker/di/RepositoryModule.kt) — every domain ↔ data binding
- [DatabaseModule.kt](../app/src/main/java/com/example/mileagetracker/di/DatabaseModule.kt) — Room database + all DAO exposures
- [TeamModule.kt](../app/src/main/java/com/example/mileagetracker/di/TeamModule.kt) — multi-region Firestore singletons
- [LocalizationModule.kt](../app/src/main/java/com/example/mileagetracker/di/LocalizationModule.kt) · [NetworkModule.kt](../app/src/main/java/com/example/mileagetracker/di/NetworkModule.kt)

### Testing
- [HiltTestRunner.kt](../app/src/androidTest/java/com/example/mileagetracker/HiltTestRunner.kt) + [TestMileageApplication.kt](../app/src/androidTest/java/com/example/mileagetracker/TestMileageApplication.kt) — Hilt-enabled instrumented test harness
- [ExportLogbookToPdfUseCaseTest.kt](../app/src/androidTest/java/com/example/mileagetracker/domain/usecase/export/ExportLogbookToPdfUseCaseTest.kt) — PDF generation tests against real `PdfDocument`
- [GetReimbursementForecastUseCaseTest.kt](../app/src/androidTest/java/com/example/mileagetracker/domain/usecase/GetReimbursementForecastUseCaseTest.kt) — comprehensive CRA + custom policy coverage
- [FlushTripBufferUseCaseTest.kt](../app/src/androidTest/java/com/example/mileagetracker/domain/usecase/tracking/FlushTripBufferUseCaseTest.kt) — end-to-end crash-recovery flow
- [SyncRestoreApplierTest.kt](../app/src/test/java/com/example/mileagetracker/data/repository/SyncRestoreApplierTest.kt) — restore protocol coverage

### Firebase Backend
- [functions/index.js](../functions/index.js) — callable HTTPS handlers: `applySyncBatch`, account deletion, route estimation, reverse geocoding, health telemetry
- [firestore.rules](../firestore.rules) + [storage.rules](../storage.rules) — per-user `users/{uid}` subtree access

### Build, CI & Docs
- [gradle/libs.versions.toml](../gradle/libs.versions.toml) — version catalog
- [app/build.gradle.kts](../app/build.gradle.kts) — Kotlin/Compose/Hilt/KSP/Room/Firebase/Secrets wiring
- [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml) — Detekt + tests + assemble
- [config/detekt/detekt.yml](../config/detekt/detekt.yml) — enforced rules
- [docs/canadian-mileage-app-prd.md](canadian-mileage-app-prd.md) — Product Requirements Doc
- [docs/ALL_PHASES_IMPLEMENTATION_PLAN.md](ALL_PHASES_IMPLEMENTATION_PLAN.md) — 8-phase roadmap
- [docs/task2-3Output.md](task2-3Output.md) — canonical naming conventions

## Complexity Hotspots — Read Carefully

These files have both `complex` rating AND high graph connectivity. They're load-bearing: changes here ripple far. Sorted by total edge degree (in + out).

| Rank | File | Layer | Degree | What to know |
|---:|---|---|---:|---|
| 1 | [MileageDatabase.kt](../app/src/main/java/com/example/mileagetracker/data/local/MileageDatabase.kt) | Data | 165 | Single Room aggregate. **Touching this means a schema migration + paired instrumented test.** Schemas in `app/schemas/` are protected. |
| 2 | [DriveEntity.kt](../app/src/main/java/com/example/mileagetracker/data/local/entity/DriveEntity.kt) | Data | 120 | Foundation entity used everywhere. Field changes need migration + audit considerations. |
| 3 | [ReportsGraphDestinations.kt](../app/src/main/java/com/example/mileagetracker/navigation/ReportsGraphDestinations.kt) | UI | 32 | All reports-feature nav destinations. Adding a screen here touches MainGraphRoutes + Screen sealed type. |
| 4 | [TripDetailViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/TripDetailViewModel.kt) | UI | 31 | Orchestrates ~20 use cases. The trip detail flow is the project's reference vertical — break it and you break a lot. |
| 5 | [TripDetailScreenContent.kt](../app/src/main/java/com/example/mileagetracker/ui/screen/TripDetailScreenContent.kt) | UI | 29 | The complex composable that pairs with the ViewModel above. |
| 6 | [GetTripSummaryUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/drive/GetTripSummaryUseCase.kt) | Domain | 28 | Powers the reports + dashboard. CRA rate math lives here. |
| 7 | [DriveViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/DriveViewModel.kt) | UI | 27 | Dashboard backbone (YTD distance, unclassified drives, deductions). |
| 8 | [AuthViewModel.kt](../app/src/main/java/com/example/mileagetracker/ui/viewmodel/AuthViewModel.kt) | UI | 25 | Firebase Auth + sign-out readiness + sync push. Auth bugs are user-visible immediately. |
| 9 | [CalculateReimbursementUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/rate/CalculateReimbursementUseCase.kt) | Domain | 24 | The CRA formula. Wrong here → every report wrong. Tested heavily; keep it that way. |
| 10 | [FirebaseSyncRepository.kt](../app/src/main/java/com/example/mileagetracker/data/repository/FirebaseSyncRepository.kt) | Data | 24 | The whole offline-first protocol. Outbox + restore + conflict handling. |
| 11 | [GetReimbursementForecastUseCase.kt](../app/src/main/java/com/example/mileagetracker/domain/usecase/GetReimbursementForecastUseCase.kt) | Domain | 22 | CRA + custom policies + territorial overrides + approval offsets. |
| 12 | [DriveTrackingPersistenceSupport.kt](../app/src/main/java/com/example/mileagetracker/service/DriveTrackingPersistenceSupport.kt) | Background | 20 | Snapshot-to-persistence layer with duplicate detection. Race conditions hide here. |
| 13 | [TripBufferStore.kt](../app/src/main/java/com/example/mileagetracker/data/local/TripBufferStore.kt) | Data | 18 | Crash-safety write-ahead log. The crash-safe trip capture invariant lives here. |

### Layers with many complex files (by raw count)

- **Testing**: 98 complex tests (mostly comprehensive instrumented tests — they're complex *because* they exercise full flows)
- **UI Layer**: 57 complex files (Compose screens + ViewModels carry the surface area)
- **Data Layer**: 12 complex files
- **Domain Layer**: 8 complex files

## How to navigate further

- **Visual exploration**: run `/understand-anything:understand-dashboard` to launch the interactive web dashboard for clicking through the graph.
- **Ask questions**: `/understand-anything:understand-chat` lets you query the graph in natural language.
- **Deep-dive a file**: `/understand-anything:understand-explain <path>` for a focused explanation.
- **Custom skills already in this repo**:
  - `/new-usecase` — scaffolds a new domain use case + test + DI binding
  - `/room-migration` — scaffolds a Room migration + paired instrumented test
