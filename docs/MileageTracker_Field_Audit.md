# MileageTracker Field Audit

> Purpose: keep a current map of the drive fields and DAO contracts that are
> still safety-critical for UI rendering, CRA summaries, sync, and tracked-trip
> persistence.

## DriveEntity

Defined in `app/src/main/java/com/example/mileagetracker/data/local/entity/DriveEntity.kt`.

| Field | Type | Used By | Contract Risk |
|---|---|---|---|
| `id` | `Int` | trip detail, audit, route-point FKs, list keys | Primary key and route/audit join target |
| `startedAt` | `Long` | `DriveDao` ordering and date-range queries | Current canonical sort and filter column |
| `endedAt` | `Long?` | duplicate detection, trip display | Overlap logic depends on real end timestamps |
| `startLocation` | `String` | unclassified trips, reports, exports | Can be address text or coordinate fallback |
| `endLocation` | `String` | unclassified trips, reports, exports | Same as `startLocation` |
| `distanceKm` | `Float` | summaries, reimbursement, export totals | Core financial/reporting input |
| `driverUid` | `String?` | ownership, sync, account deletion | Must remain populated for user-owned data |
| `vehicleId` | `Int?` | vehicle summaries, exports, report history | Nullable is valid and means unassigned |
| `tripCategory` | `TripCategory` | review lists, CRA/export filters, summaries | Serialized enum names are used in SQL |
| `classificationStatus` | `ClassificationStatus` | review queues and duplicate handling | `PENDING`/`NEEDS_REVIEW` state drives UX |
| `appliedRateSnapshot` | `Float` | reimbursement/export history | Historical rate snapshot, not a live lookup |
| `trackingMetadata` | `TrackingMetadataEmbedded?` | CRA/audit provenance | Kept for tracked-trip confidence/provenance |
| `deletedAt` | `Long?` | soft delete and sync tombstones | Active queries must exclude it; sync snapshots may include it |

## Current DriveDao Contracts

Defined in `app/src/main/java/com/example/mileagetracker/data/local/dao/DriveDao.kt`.

| Method | Current Contract |
|---|---|
| `getAllDrives(driverUid)` | Active rows only, `deleted_at IS NULL`, `ORDER BY started_at DESC` |
| `getAllActiveDrivesOnce(driverUid)` | Same active-only semantics for one-shot callers |
| `getAllDrivesIncludingDeletedForSync(driverUid)` | Includes soft-deleted rows so sync can publish tombstones |
| `getUnclassifiedDrives(driverUid)` | `trip_category = 'UNCLASSIFIED'`, active only, newest first |
| `getBusinessDrivesByDateRange(...)` | `trip_category = 'BUSINESS'`, active only |
| `getYearToDateBusinessDistance(...)` | `SUM(distance_km)` for active business trips |
| `getDrivesForAnnualSummary(...)` | All active categories within the `started_at` window, ascending |

## Tracked-Trip Persistence Contract

Tracked trips are created from `DriveTrackingService` and recovered from
`buffered_trips`. The tracked-drive path must persist these pieces as one
logical unit:

1. The `drives` row.
2. The `route_points` rows for the new trip.
3. The initial `trip_audit` row (`DRIVE_CREATED`) plus any extra audit events.
4. The sync outbox and metadata rows for drives, route points, and audit.

If any of those writes fail, the local DB should roll back the tracked-trip
persist rather than leaving a half-saved trip behind.

## Serialized Enum Values That Must Stay Stable

These values are depended on directly by Room queries and export logic:

| Enum | Serialized Value | Why it matters |
|---|---|---|
| `TripCategory.UNCLASSIFIED` | `UNCLASSIFIED` | Unclassified trip list and review UX |
| `TripCategory.BUSINESS` | `BUSINESS` | CRA summaries and reimbursement/export totals |
| `TripCategory.PERSONAL` | `PERSONAL` | Category-filtered export/report behavior |
| `ClassificationStatus.PENDING` | `PENDING` | New tracked trips and review queues |
| `ClassificationStatus.NEEDS_REVIEW` | `NEEDS_REVIEW` | Flagged duplicate and exception handling |

## Legacy Compatibility Notes

- `timestamp`, `dateString`, and `timeString` still exist on `DriveEntity`,
  but new DAO/query logic should key off `startedAt`.
- `purposeCategory` remains as a legacy column for older data/contracts, but
  active list/report logic should use `tripCategory`.
