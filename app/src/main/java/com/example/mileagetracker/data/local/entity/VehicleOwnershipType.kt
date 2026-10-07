package com.example.mileagetracker.data.local.entity

/**
 * Describes how a vehicle is owned for mileage tracking purposes.
 *
 * Stored as its [name] string via a Room @TypeConverter so that reordering
 * entries never causes silent data corruption.
 *
 * - [PERSONAL] – personally owned vehicle.
 * - [COMPANY]  – company-owned vehicle.
 * - [LEASED]   – leased vehicle.
 */
enum class VehicleOwnershipType {
    PERSONAL,
    COMPANY,
    LEASED,
}
