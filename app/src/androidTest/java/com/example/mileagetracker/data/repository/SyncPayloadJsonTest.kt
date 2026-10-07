package com.example.mileagetracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.model.DetectionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncPayloadJsonTest {

    @Test
    fun drivePayload_roundTripsAllPersistedFields() {
        val drives = listOf(
            DriveEntity(
                id = 7,
                startedAt = 100L,
                endedAt = 200L,
                dateString = "2026-06-03",
                timeString = "09:00",
                timestamp = 100L,
                startLocation = "Start",
                endLocation = "End",
                distanceKm = 12.5f,
                vehicleId = 4,
                driverUid = "u1",
                tripCategory = TripCategory.BUSINESS,
                purposeCategory = "Business",
                classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
                purposeText = "Client visit",
                classificationConfidence = 0.82f,
                routePolyline = "abc123",
                appliedRateSnapshot = 0.7f,
                appliedRate = 0.65f,
                reimbursementAmountSnapshot = 8.75f,
                notes = "note",
                trackingSource = TrackingSource.AUTO_TRACKED,
                createdAt = 11L,
                updatedAt = 22L,
                deletedAt = null,
                trackingMetadata = TrackingMetadataEmbedded(
                    detectionSource = DetectionSource.AUTO,
                    confidenceScore = 0.91f,
                    batteryLevelAtStart = 80,
                    batteryLevelAtEnd = 72,
                    wasBatterySaverActive = true,
                ),
            ),
        )

        val decoded = drives.toDrivesPayloadJson().toDriveEntities()

        assertEquals(drives, decoded)
    }

    @Test
    fun vehicleAndRulePayloads_roundTrip() {
        val vehicles = listOf(
            VehicleEntity(
                id = 3,
                ownerUid = "u1",
                displayName = "Car",
                make = "Honda",
                model = "Civic",
                year = 2024,
                ownershipType = VehicleOwnershipType.PERSONAL,
                vehicleType = "Sedan",
                isActive = true,
                isDefault = true,
                fiscalYear = 2026,
                openingOdometerKm = 1000f,
                closingOdometerKm = 1500f,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )
        val rules = listOf(
            UserClassificationRuleEntity(
                id = 5,
                ownerUid = "u1",
                name = "Client",
                keyword = "client",
                category = TripCategory.BUSINESS.name,
                confidence = 0.8f,
                priority = 10,
                isEnabled = true,
                createdAt = 3L,
            ),
        )

        assertEquals(vehicles, vehicles.toVehiclesPayloadJson().toVehicleEntities())
        assertEquals(rules, rules.toUserClassificationRulesPayloadJson().toUserClassificationRuleEntities())
    }

    @Test
    fun attachmentExpenseOdometerRouteAndAuditPayloads_roundTrip() {
        assertRoundTripPayloads(
            routePoints = testRoutePoints(),
            audits = testTripAudits(),
            attachments = testTripAttachments(),
            expenses = testVehicleExpenses(),
            odometer = testOdometerCaptures(),
        )
    }

    private fun assertRoundTripPayloads(
        routePoints: List<RoutePointEntity>,
        audits: List<TripAuditEntity>,
        attachments: List<TripAttachmentSyncItem>,
        expenses: List<VehicleExpenseSyncItem>,
        odometer: List<OdometerCaptureSyncItem>,
    ) {
        assertEquals(routePoints, routePoints.toRoutePointsPayloadJson().toRoutePointEntities())
        assertEquals(audits, audits.toTripAuditPayloadJson().toTripAuditEntities())
        assertEquals(attachments, attachments.toTripAttachmentsPayloadJson().toTripAttachmentSyncItems())
        assertEquals(expenses, expenses.toVehicleExpenseSyncPayloadJson().toVehicleExpenseSyncItems())
        assertEquals(odometer, odometer.toOdometerCaptureSyncPayloadJson().toOdometerCaptureSyncItems())
    }

    private fun testRoutePoints(): List<RoutePointEntity> =
        listOf(RoutePointEntity(1, 9L, 45.0, -66.0, 4.5, 3.2f, 22f, 99L))

    private fun testTripAudits(): List<TripAuditEntity> =
        listOf(
            TripAuditEntity(
                id = 2,
                tripId = 9,
                actorUid = "u1",
                eventType = AuditEventType.CATEGORY_CHANGED,
                fieldName = "trip_category",
                oldValue = "UNCLASSIFIED",
                newValue = "BUSINESS",
                changedAt = 44L,
            ),
        )

    private fun testTripAttachments(): List<TripAttachmentSyncItem> =
        listOf(
            TripAttachmentSyncItem(
                entity = TripAttachmentEntity(
                    id = 4,
                    tripId = 9,
                    uri = "file:///tmp/a.jpg",
                    mimeType = "image/jpeg",
                    fileName = "a.jpg",
                    createdAt = 12L,
                ),
                remotePath = "users/u1/evidence/trip-attachments/9/a.jpg",
            ),
        )

    private fun testVehicleExpenses(): List<VehicleExpenseSyncItem> =
        listOf(
            VehicleExpenseSyncItem(
                entity = VehicleExpenseEntity(
                    id = 8,
                    vehicleId = 3,
                    categoryKey = "fuel",
                    amountCad = 44.2,
                    date = 55L,
                    vendorName = "Shell",
                    notes = "gas",
                    receiptAttachmentUri = "file:///tmp/r.jpg",
                    gstHstAmount = 2.1,
                    isBusinessUse = true,
                    createdAt = 66L,
                ),
                remotePath = "users/u1/evidence/vehicle-expenses/3/8_receipt.jpg",
            ),
        )

    private fun testOdometerCaptures(): List<OdometerCaptureSyncItem> =
        listOf(
            OdometerCaptureSyncItem(
                entity = OdometerCaptureEntity(
                    id = 6,
                    vehicleId = 3,
                    captureDate = 88L,
                    readingKm = 12345,
                    photoUri = "file:///tmp/o.jpg",
                    captureType = "MANUAL",
                    notes = "year end",
                    createdAt = 77L,
                ),
                remotePath = "users/u1/evidence/odometer/3/6.jpg",
            ),
        )
}
