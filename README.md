# MileageTracker

A Canada-first mileage and vehicle-expense tracking app for Android. MileageTracker
records trips automatically in the background, keeps a CRA-compliant logbook, and
reports reimbursable deductions per province or territory.

> **Portfolio project.** This is a sanitized public copy. All API keys, Firebase
> credentials, signing keys, and personal identifiers have been removed or replaced
> with placeholders, and the package ID was renamed to `com.example.mileagetracker`.
> See [Security notes](#security-notes).

## Features

- **Automatic trip detection** — a foreground GPS service plus Google Activity
  Recognition transitions start and stop trips without user interaction. Manual entry
  remains available when background tracking is not opted in.
- **CRA-compliant logbook** — a versioned per-province/territory rate catalog with
  deduction and reimbursement calculations, ready to export.
- **CSV and PDF export** — produce a logbook your accountant can work with directly.
- **Multi-vehicle management** — track several vehicles with separate odometer
  baselines, ownership types, and expense histories.
- **Receipt capture** — on-device OCR (ML Kit) pulls totals and dates out of receipt
  photos; odometer photos attach to trip evidence.
- **Canadian address formatting** — postal code validation and structured formatting
  for Canadian addresses, including reverse-geocoded trip endpoints.
- **Offline-first sync** — Room is the source of truth on device. A UUID-keyed outbox,
  conflict records, and WorkManager jobs sync to Firestore when you opt in.
- **English and French** — full string resources in both locales.
- **Account deletion** — in-app deletion with reauthentication, plus a hosted
  deletion-request page.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3, Java 11 target |
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe routes) |
| Architecture | Layered DDD — domain / data / UI, with use cases and repository interfaces |
| DI | Hilt |
| Local persistence | Room 2.8 (16 migrations), DataStore preferences |
| Background work | WorkManager, foreground service for GPS capture |
| Backend | Firebase Auth, Firestore, Storage, Cloud Functions, App Check, Crashlytics |
| Maps & places | Google Maps SDK, Places SDK, geocode.maps.co reverse geocoding |
| OCR | ML Kit text recognition (on-device) |
| Quality | Detekt, JUnit, MockK, Robolectric, Compose UI tests, GitHub Actions CI |

## Architecture

Ten layers with a strict dependency direction: the domain layer has no Android
dependencies, and data repositories implement domain interfaces so the UI never
touches Room or Firestore directly.

```
Compose screen -> ViewModel (StateFlow) -> Domain use case -> Repository interface
                                                          -> Repository impl
                                                               -> Room | Firestore | DataStore
```

Read `docs/ARCHITECTURE.md` for the full map and `docs/ONBOARDING.md` for a guided
tour of the codebase.

## Project layout

```
app/                    Android application module (Kotlin/Compose)
  schemas/              Room schema snapshots (versions 11-16)
  src/main/java/        Production sources
  src/test/             JVM unit tests
  src/androidTest/      Instrumented tests (Room migrations, DAO + Compose UI)
config/detekt/          Detekt config and baseline
functions/              Firebase Cloud Functions (callable handlers)
firebase-tests/         Firestore/Storage security rule tests
web/                    Hosted static pages (account deletion request)
docs/                   Architecture, onboarding, and product docs
```

## Getting started

### Requirements

- JDK 21
- Android SDK with platform 37 and build-tools 37.0.0
- Node.js 20 (only for the Firebase backend)

### Configuration

Secrets are **never** committed. Create `local.properties` in the repo root:

```properties
sdk.dir=/path/to/Android/sdk

# From Google Cloud Console -> Credentials -> Maps SDK for Android
MAPS_API_KEY=your-maps-sdk-key

# From geocode.maps.co account
GEOCODE_MAPS_CO_API_KEY=your-geocode-key

# Optional: only needed to sign a release build
RELEASE_STORE_FILE=keystore/upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

`local.defaults.properties` is committed and holds `REPLACE_WITH_*` sentinels, so the
build resolves every key it needs even on a fresh clone. When release signing keys are
absent the `release` build type simply produces an unsigned APK instead of failing.

The app also needs `app/google-services.json` from your own Firebase project. It is
gitignored; without it the Firebase SDK initialisation fails at runtime, though
compilation and local unit tests still succeed.

### Build and test

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:detekt                 # static analysis
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device/emulator)
```

CI runs Detekt, unit tests, lint, and a debug assemble on every push via
`.github/workflows/android-ci.yml`.

### Backend

```bash
cd functions && npm test                    # handler unit tests
npm run test:emulator                      # rules + functions against emulators
npm run deploy                             # functions, rules, indexes, hosting
```

Set `MAPS_ROUTES_API_KEY` and `MAPS_GEOCODING_API_KEY` in Firebase Secret Manager
before deploying — the callable functions read them via `defineSecret`.

## Security notes

This repository is a sanitized fork of a private development repo:

- No API keys, Firebase credentials, service account keys, or signing keystores.
- The application ID is `com.example.mileagetracker`.
- Commit history starts fresh, so no prior credentials or personal identifiers exist
  in any reachable commit.
- Privacy and deletion contact addresses are `privacy@example.com` placeholders.

Real deployment keys were rotated when this copy was created.

## Privacy and data safety

Room is the authoritative offline store. Cloud records are scoped to the signed-in
Firebase UID, and Android cloud backup is disabled so route data stays off Google's
backup servers. Cloud upload requires versioned, explicit opt-in. Analytics and
advertising identifiers are not collected, and release builds strip `android.util.Log`
calls so location data cannot leak into device logs.

See `docs/PRIVACY_POLICY_DRAFT.md` and `docs/DATA_SAFETY_DRAFT.md`.

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements

CRA rate data reflects publicly published federal and provincial/territorial
deduction and mileage rates. Rates are versioned and stored in
`app/src/main/java/com/example/mileagetracker/data/seeder/`.
