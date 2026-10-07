# Google Play Data Safety Draft

Updated: 2026-06-13

Validate this matrix against the final release artifact before submission.
Play's form asks two separate questions per data type — **Collected** (leaves the
device to you/your backend) and **Shared** (transferred to a third party). The
"Shared off-device" column below maps to that second question; see the
[Third-party transfers](#third-party-transfers) notes for the classification
calls that counsel/you must finalize.

| Data category | Collected | Shared off-device | Purpose | Handling |
| --- | --- | --- | --- | --- |
| Precise location | Yes, when tracking is enabled | Yes — see notes (1)(2) | Mileage logbook route recording; endpoint address resolution; map display | UID-scoped cloud sync; excluded from Android backup |
| Personal info: email | Yes | No | Firebase account authentication | Firebase Auth |
| Financial info: expense & reimbursement amounts | Yes — **confirm** (3) | No | aehicle expense and CRA reimbursement tracking | UID-scoped cloud sync; excluded from Android backup |
| Photos/files | Optional | No | Receipts and odometer evidence | UID-scoped Storage path; OCR runs on-device |
| Calendar events | No — accessed on-device only (4) | No | Suggesting trip-purpose hints | Read transiently in a ±30 min window; a user-selected event title may be saved as trip-purpose text and then synced |
| App activity / app info & performance (diagnostics) | Yes | Yes — Firebase Crashlytics (5) | Crash and coarse health monitoring | No custom keys logged; must keep location, addresses, email, and filenames out of exception messages |

No advertising IDs or product analytics are intentionally collected.

## Third-party transfers

These are the flows where data leaves the device to a party other than the
user's own UID-scoped Firebase storage. Each needs a deliberate
collected-vs-shared-vs-service-provider classification before submission.

1. **geocode.maps.co (reverse geocoding).** Precise lat/lng for trip start/end
   points is sent client-direct to `https://geocode.maps.co/reverse` (no
   Firebase Function in between) to obtain a street address. This is an external,
   non-Google service. Classify as **location shared with a third party** unless
   a service-provider/processor agreement is in place that supports treating it
   otherwise. Source: `MapsCoTripEndpointAddressRepository`.
2. **Google Maps Platform.** The Maps SDK renders map tiles using location, and
   the Places SDK sends typed autocomplete queries (which can contain addresses)
   to Google. Disclose per Google Maps Platform's data-handling guidance.
3. **Financial info — confirm category.** The app records vehicle expense and
   CRA reimbursement dollar amounts. Play's "Financial info" category centers on
   payment/purchase data, so this is a borderline fit. Either declare under
   "Financial info → other financial info" or document the rationale for
   treating expense figures as app activity/user content instead.
4. **Calendar.** `READ_CALENDAR` reads event title, location, and times within a
   ±30 min window around a trip to suggest purpose hints. The raw calendar is
   not collected or transmitted in bulk; data is processed transiently
   on-device. The one persistence path: if the user taps a suggested hint, that
   single event title is saved as their trip purpose and then syncs as ordinary
   user content. A separate **Play Console sensitive-permission declaration** for
   `READ_CALENDAR` is still required regardless of the Data Safety answer.
5. **Firebase Crashlytics (Google).** Acts as a diagnostics processor. aerified:
   no `setCustomKey`/`recordException`/manual-log calls exist, so only automatic
   crash data is sent. Classify per Firebase's processor relationship.
