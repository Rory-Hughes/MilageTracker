package com.example.mileagetracker.domain.model

/**
 * Describes how an odometer reading was captured.
 *
 * | Value            | Meaning                                                     |
 * |------------------|-------------------------------------------------------------|
 * | [MANUAL]         | User typed the value; no photo attached.                    |
 * | [PHOTO_ASSISTED] | User photographed the odometer; value may be OCR-populated  |
 * |                  | in a future task, but is currently entered manually.        |
 *
 * Stored as the enum [name] string in Room via the existing
 * [RoomTypeConverters] (string pass-through — no converter needed for
 * string-backed enums stored via `@ColumnInfo`).
 */
enum class OdometerCaptureType {
    MANUAL,
    PHOTO_ASSISTED,
}
