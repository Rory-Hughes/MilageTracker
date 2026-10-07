# MileageTracker Room Inventory

> Repository snapshot: June 2026

## Database

- Class: `MileageDatabase`
- Path: `app/src/main/java/com/example/mileagetracker/data/local/MileageDatabase.kt`
- Annotation: `@Database(version = 15, exportSchema = true)`
- Registered migrations: `Migration1To2` through `Migration14To15`
- Seed callback: `MileageDatabase.SEEDING_CALLBACK`

The current schema includes:

- drives
- vehicles
- trip audit
- route points
- buffered trips
- report history
- vehicle expenses
- approvals and policy templates
- durable active-trip sessions and active route points
- sync metadata, outbox, and conflicts

## DriveEntity Highlights

The `drives` table is no longer the small pre-beta shape. Important current
columns include:

| Column | Type | Notes |
|---|---|---|
| `id` | `Int` | auto-generated PK |
| `started_at` | `Long` | canonical ordering/filtering field |
| `ended_at` | `Long?` | nullable end timestamp |
| `start_location` | `String` | address text or coordinate fallback |
| `end_location` | `String` | address text or coordinate fallback |
| `distance_km` | `Float` | core reporting/reimbursement value |
| `vehicle_id` | `Int?` | nullable unassigned vehicle FK reference |
| `driver_uid` | `String?` | user ownership and sync scope |
| `trip_category` | enum | `UNCLASSIFIED`, `BUSINESS`, `PERSONAL`, etc. |
| `classification_status` | enum | review workflow state |
| `applied_rate_snapshot` | `Float` | historical rate snapshot |
| `deleted_at` | `Long?` | soft-delete tombstone |

Legacy compatibility columns such as `timestamp`, `date_string`, and
`time_string` still exist, but current query contracts should prefer
`started_at`.

## Current DriveDao Surface

| Method | Purpose |
|---|---|
| `insertDrive` | returns new row ID |
| `updateDrive` | persists full-row changes |
| `softDeleteDrive` | marks a trip deleted without removing the row |
| `getAllDrives(driverUid)` | active rows only, reactive |
| `getAllActiveDrivesOnce(driverUid)` | active rows only, one-shot |
| `getAllDrivesIncludingDeletedForSync(driverUid)` | sync snapshot including tombstones |
| `getUnclassifiedDrives(driverUid)` | unclassified review list |
| `getBusinessDrivesByDateRange(...)` | business-only date range |
| `getDrivesForAnnualSummary(...)` | active annual export/report rows |
| `getDrivesInTimeWindow(...)` | duplicate detection candidates |

## Current Write Paths

Tracked-trip persistence should flow through the repository/use-case layer, not
direct DAO calls from the service. The tracked-drive path is responsible for:

1. Inserting the drive row.
2. Inserting route points.
3. Inserting audit rows.
4. Enqueueing sync metadata/outbox rows for the affected entity types.

Those writes should be grouped transactionally so partial trip persists do not
leave orphaned local state.

## Known Architectural Constraints

- `MainAppShell` is still a large navigation surface and remains a maintainability hotspot.
- Drive sync still uses an aggregate `"all"` payload for `drives`; method naming must make deleted-row inclusion explicit.
- The repo keeps legacy compatibility fields/columns that should not be reused for new query logic without review.
