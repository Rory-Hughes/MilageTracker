# MileageTracker Architecture

This document is an onboarding map for the Android application. It is based on
the live project structure, navigation docs, and Room inventory.

## System Overview

```mermaid
flowchart TB
    User["Driver / Manager"]
    MainActivity["MainActivity<br/>Compose entry point"]
    MileageApplication["MileageApplication<br/>process startup"]
    MainAppShell["MainAppShell<br/>authenticated shell"]
    MainAppNavHost["MainAppNavHost<br/>feature graph registry"]
    FeatureGraphs["Feature navigation graphs<br/>dashboard, trips, reports, vehicles, account, team"]
    Screens["Compose screens and components"]
    ViewModels["Hilt ViewModels<br/>StateFlow UI state and events"]
    Domain["Domain layer<br/>use cases, models, repository interfaces"]
    Repositories["Data repositories<br/>local, remote, preferences, sync"]
    Room["Room: MileageDatabase<br/>DAOs, migrations, schema snapshots"]
    DataStore["DataStore preferences<br/>settings, locale, defaults"]
    Firebase["Firebase<br/>Auth, Firestore, Storage, Functions, App Check"]
    GoogleServices["Google services<br/>Maps, Places, Activity Recognition, Fused Location"]
    WorkManager["WorkManager<br/>periodic and backfill workers"]
    TrackingService["DriveTrackingService<br/>foreground GPS trip capture"]

    User --> MainActivity
    MainActivity --> MainAppShell
    MainActivity --> Screens
    MileageApplication --> GoogleServices
    MileageApplication --> WorkManager
    MileageApplication --> TrackingService
    MileageApplication --> Firebase
    MainAppShell --> MainAppNavHost
    MainAppNavHost --> FeatureGraphs
    FeatureGraphs --> Screens
    Screens --> ViewModels
    ViewModels --> Domain
    Domain --> Repositories
    Repositories --> Room
    Repositories --> DataStore
    Repositories --> Firebase
    Repositories --> GoogleServices
    WorkManager --> Domain
    TrackingService --> Domain
    TrackingService --> GoogleServices
```

`MainActivity` owns the first user-facing decision: onboarding, login, or the
authenticated app shell. `MileageApplication` owns process-level setup such as
App Check, Places initialization, locale restoration, CRA rate seeding,
interrupted trip recovery, Activity Recognition registration, and worker
scheduling.

## Layered Dependencies

```mermaid
flowchart TB
    subgraph Entry["Entry and Shell"]
        MainActivity2["MainActivity / MileageApp"]
        Shell2["MainAppShell"]
        Navigation2["navigation/*Graph.kt"]
    end

    subgraph UI["UI Layer"]
        Screens2["ui/screen and ui/component"]
        ViewModels2["ui/viewmodel"]
    end

    subgraph DomainLayer["Domain Layer"]
        UseCases2["domain/usecase<br/>constructor-injected use cases"]
        Models2["domain/model"]
        RepoContracts2["domain/repository interfaces"]
        DomainServices2["domain/service and helpers"]
    end

    subgraph DataLayer["Data Layer"]
        RepoImpls2["data/repository implementations"]
        LocalStores2["data/local stores<br/>ActiveTripStore, TripBufferStore"]
        Preferences2["data/preferences repositories"]
        Remote2["data/remote and sync repositories"]
    end

    subgraph Persistence["Persistence and External Systems"]
        Room2["MileageDatabase<br/>DriveDao, VehicleDao, SyncDao, etc."]
        DataStore2["DataStore preferences"]
        Firebase2["Firebase services"]
        Maps2["Google Maps / Places / Directions"]
    end

    subgraph DI["Dependency Injection"]
        Hilt2["Hilt modules<br/>DatabaseModule, RepositoryModule, SyncModule, TrackingModule, feature modules"]
    end

    MainActivity2 --> Shell2
    Shell2 --> Navigation2
    Navigation2 --> Screens2
    Screens2 --> ViewModels2
    ViewModels2 --> UseCases2
    UseCases2 --> Models2
    UseCases2 --> RepoContracts2
    UseCases2 --> DomainServices2
    RepoContracts2 --> RepoImpls2
    RepoImpls2 --> LocalStores2
    RepoImpls2 --> Preferences2
    RepoImpls2 --> Remote2
    LocalStores2 --> Room2
    Preferences2 --> DataStore2
    Remote2 --> Firebase2
    RepoImpls2 --> Maps2
    Hilt2 -. provides .-> ViewModels2
    Hilt2 -. binds .-> RepoContracts2
    Hilt2 -. provides .-> Room2
    Hilt2 -. configures .-> Firebase2
```

Use cases are mostly resolved through `@Inject constructor` dependencies. This
checkout does not use a single central `UseCaseModule`; explicit providers are
kept in feature modules where qualifiers, context, or worker infrastructure
need them.

## Runtime Trip Capture

```mermaid
sequenceDiagram
    participant AR as ActivityRecognitionReceiver
    participant App as MileageApplication
    participant Service as DriveTrackingService
    participant Location as FusedLocationProviderClient
    participant Active as ActiveTripStore
    participant Buffer as TripBufferStore
    participant Persist as PersistTrackedDriveUseCase
    participant Room as MileageDatabase
    participant Flush as TripBufferFlushWorker
    participant Sync as SyncOutboxWorker
    participant Cloud as FirebaseSyncRepository

    App->>AR: register activity transitions when eligible
    AR->>Service: start auto tracking command
    Service->>Active: begin durable active session
    Service->>Location: request high-accuracy location updates
    Location-->>Service: route points
    Service->>Active: checkpoint active route points
    Service-->>Service: calculate distance and live tracking state
    AR->>Service: stop tracking command
    Service->>Buffer: enqueue write-ahead trip buffer
    Service->>Persist: persist completed trip snapshot
    Persist->>Room: write drive, route points, audit, sync metadata
    Service->>Buffer: remove buffer after successful persist
    Service->>Active: clear active session
    Flush->>Buffer: drain buffered trips
    Flush->>Persist: retry local trip persistence
    Sync->>Cloud: push sync outbox when signed in, consented, and connected
```

The trip path is designed for local durability first. The foreground service
checkpoints active route points while recording, persists completed trips
through the domain layer, and relies on workers for buffered-trip retries and
cloud sync. `SyncOutboxWorker` has a network constraint; `TripBufferFlushWorker`
intentionally does not, because it only moves local buffered data into Room.

## Maintainer Notes

- Navigation: destination ownership lives under `navigation/*Graph.kt`.
  `MainAppShell` remains the shell/chrome coordinator and should stay focused
  on drawer, app bar, route selection, and top-level ViewModel wiring.
- Persistence: `MileageDatabase` is migration-controlled and exports schema
  snapshots. Entity changes must update the Room version, migration list, and
  schema files.
- Sync: local repositories and DAOs are the source of offline behavior.
  Cloud upload flows pass through sync metadata and outbox handling instead of
  direct UI-to-Firebase writes.
- Tracking: `DriveTrackingService`, `ActiveTripStore`, `TripBufferStore`,
  `RecoverInterruptedTripUseCase`, and the tracking workers form one durability
  boundary. Changes here should be verified with service, worker, and use-case
  tests together.
- Hilt: repository bindings are grouped by feature and store type. Prefer the
  existing module boundaries before adding new modules.
