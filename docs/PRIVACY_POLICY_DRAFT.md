# MileageTracker Privacy Policy Draft

Updated: 2026-06-13

MileageTracker records mileage logbook information for signed-in users. This can include trip times, route points, vehicles, expenses, receipts, odometer evidence, classification rules, and audit history.

## Data Handling

- Room is the authoritative offline store on the device.
- Cloud records and evidence files are scoped to the signed-in Firebase UID.
- Route data is not included in Android cloud backup.
- Product analytics and advertising identifiers are not collected.
- Crash reports and operational health events must not include coordinates, addresses, email addresses, or filenames.

## Calendar Access

- With your permission (`READ_CALENDAR`), the app reads calendar event titles, locations, and times in a short window around a trip to suggest a trip purpose.
- Calendar data is read on the device only and is not collected or transmitted in bulk.
- If you select a suggested purpose, that event title is saved as your trip's purpose and then syncs like any other trip detail when cloud upload is enabled.
- The feature can be disabled in Settings, and the permission can be revoked at any time in system settings; manual purpose entry remains available.

## Third-Party Services

MileageTracker relies on the following external services to function. Their handling of data is governed by their own privacy policies.

- **Google Firebase** (Authentication, Firestore, Storage, Cloud Functions, Crashlytics, App Check) — account sign-in, UID-scoped cloud sync of your logbook and evidence, and crash diagnostics.
- **Google Maps Platform** (Maps SDK and Places SDK) — displays maps using location and processes address-autocomplete queries you type.
- **geocode.maps.co** — converts trip start/end coordinates into street addresses. Precise coordinates are sent to this service when address resolution runs.

## User Choices

- Cloud upload requires versioned consent and remains disabled until the current disclosure is accepted.
- Background location and activity recognition require a separate explicit opt-in.
- Manual mileage entry remains available without background-detection opt-in.
- Calendar-based purpose hints are optional and can be turned off in Settings.
- Users can delete their account in the app or use the hosted deletion-request page.

Replace this draft with counsel-reviewed policy text and a real privacy contact before publishing.
