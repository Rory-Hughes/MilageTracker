"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldValue, Timestamp} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const {defineSecret} = require("firebase-functions/params");
const {setGlobalOptions} = require("firebase-functions/v2");
const {onCall, HttpsError} = require("firebase-functions/v2/https");

initializeApp();
setGlobalOptions({region: "northamerica-northeast1", maxInstances: 20});

const mapsRoutesApiKey = defineSecret("MAPS_ROUTES_API_KEY");
const mapsGeocodingApiKey = defineSecret("MAPS_GEOCODING_API_KEY");
const db = getFirestore();
const applySyncBatchOptions = {enforceAppCheck: true};
const deleteAccountDataOptions = {enforceAppCheck: true};
const recordHealthEventOptions = {enforceAppCheck: true};
const getRouteEstimateOptions = {enforceAppCheck: true, secrets: [mapsRoutesApiKey]};
const reverseGeocodeTripEndpointsOptions = {
  enforceAppCheck: true,
  secrets: [mapsGeocodingApiKey],
};

function requireUid(request) {
  const uid = request.auth?.uid ??
    request.auth?.token?.uid ??
    request.auth?.token?.user_id ??
    decodeUidFromVerifiedEmulatorToken(request);
  if (!uid) throw new HttpsError("unauthenticated", "Sign in is required.");
  return uid;
}

function decodeUidFromVerifiedEmulatorToken(request) {
  if (process.env.FUNCTIONS_EMULATOR !== "true") {
    return null;
  }
  const authorization = request.rawRequest?.headers?.authorization;
  if (typeof authorization !== "string" || !authorization.startsWith("Bearer ")) {
    return null;
  }
  const token = authorization.slice("Bearer ".length);
  const segments = token.split(".");
  if (segments.length < 2) {
    return null;
  }
  try {
    const claims = JSON.parse(Buffer.from(segments[1], "base64url").toString("utf8"));
    return claims.user_id ?? claims.uid ?? claims.sub ?? null;
  } catch {
    return null;
  }
}

function requireObject(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", `${label} must be an object.`);
  }
  return value;
}

function requireCoordinate(value, label) {
  const point = requireObject(value, label);
  if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude)) {
    throw new HttpsError("invalid-argument", `${label} must contain numeric coordinates.`);
  }
  if (Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) {
    throw new HttpsError("invalid-argument", `${label} is outside coordinate bounds.`);
  }
  return {latitude: point.latitude, longitude: point.longitude};
}

function createApplySyncBatchHandler({db}) {
  return async (request) => {
    const uid = requireUid(request);
    const records = request.data?.records;
    if (!Array.isArray(records) || records.length === 0 || records.length > 100) {
      throw new HttpsError("invalid-argument", "records must contain 1 to 100 items.");
    }

    const results = [];
    for (const raw of records) {
      const record = requireObject(raw, "record");
      if (typeof record.syncId !== "string" || !/^[0-9a-f-]{36}$/i.test(record.syncId)) {
        throw new HttpsError("invalid-argument", "syncId must be a UUID.");
      }
      if (typeof record.entityType !== "string" || record.entityType.length > 64) {
        throw new HttpsError("invalid-argument", "entityType is invalid.");
      }
      if (typeof record.entityId !== "string" || record.entityId.length > 128) {
        throw new HttpsError("invalid-argument", "entityId is invalid.");
      }
      if (typeof record.payloadJson !== "string" || record.payloadJson.length > 500000) {
        throw new HttpsError("invalid-argument", "payloadJson is invalid.");
      }
      if (!Number.isInteger(record.baseRevision) || record.baseRevision < 0) {
        throw new HttpsError("invalid-argument", "baseRevision must be a non-negative integer.");
      }

      const ref = db.doc(`users/${uid}/syncRecords/${record.syncId}`);
      const result = await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(ref);
        const remote = snapshot.exists ? snapshot.data() : null;
        const remoteRevision = remote?.revision ?? 0;
        const remoteDeleted = remote?.deletedAt != null;
        const incomingDeleted = record.deletedAt != null;
        const stale = snapshot.exists && remoteRevision !== record.baseRevision;

        if ((stale && !incomingDeleted) || (remoteDeleted && !incomingDeleted)) {
          return {syncId: record.syncId, status: "conflict", remote};
        }
        if (remoteDeleted && incomingDeleted) {
          return {syncId: record.syncId, status: "discarded"};
        }

        const next = {
          ownerUid: uid,
          syncId: record.syncId,
          entityType: record.entityType,
          entityId: record.entityId,
          payloadJson: record.payloadJson,
          revision: remoteRevision + 1,
          updatedAt: FieldValue.serverTimestamp(),
          deletedAt: incomingDeleted ? Timestamp.fromMillis(record.deletedAt) : null,
        };
        transaction.set(ref, next);
        return {syncId: record.syncId, status: "applied", revision: next.revision};
      });
      results.push(result);
    }
    return {results};
  };
}

function createDeleteAccountDataHandler({db, auth, storage}) {
  return async (request) => {
    const uid = requireUid(request);
    await db.recursiveDelete(db.doc(`users/${uid}`));
    await storage.bucket().deleteFiles({prefix: `users/${uid}/evidence/`});
    await auth.deleteUser(uid);
    return {deleted: true};
  };
}

function createRouteEstimateHandler({fetchImpl, apiKeySecret}) {
  return async (request) => {
      requireUid(request);
      const origin = requireCoordinate(request.data?.origin, "origin");
      const destination = requireCoordinate(request.data?.destination, "destination");
      const response = await fetchImpl("https://routes.googleapis.com/directions/v2:computeRoutes", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Goog-Api-Key": apiKeySecret.value(),
          "X-Goog-FieldMask": "routes.distanceMeters,routes.duration",
        },
        body: JSON.stringify({
          origin: {location: {latLng: origin}},
          destination: {location: {latLng: destination}},
          travelMode: "DRIVE",
        }),
      });
      if (!response.ok) {
        throw new HttpsError("unavailable", "Route estimate is temporarily unavailable.");
      }
      const route = (await response.json()).routes?.[0];
      return route ? {
        distanceMeters: route.distanceMeters,
        duration: route.duration,
      } : {distanceMeters: null, duration: null};
  };
}

function createReverseGeocodeTripEndpointsHandler({fetchImpl, apiKeySecret}) {
  return async (request) => {
      requireUid(request);
      const start = requireCoordinate(request.data?.start, "start");
      const end = requireCoordinate(request.data?.end, "end");

      async function resolve(point) {
        const params = new URLSearchParams({
          latlng: `${point.latitude},${point.longitude}`,
          key: apiKeySecret.value(),
        });
        const response = await fetchImpl(`https://maps.googleapis.com/maps/api/geocode/json?${params}`);
        if (!response.ok) return null;
        return (await response.json()).results?.[0]?.formatted_address ?? null;
      }

      return {
        startAddress: await resolve(start),
        endAddress: await resolve(end),
      };
  };
}

function createRecordHealthEventHandler({db}) {
  return async (request) => {
    const uid = requireUid(request);
    const event = requireObject(request.data, "event");
    const allowedTypes = new Set([
      "sync_retry",
      "sync_failure",
      "tracking_recovery",
      "activity_registration_failure",
    ]);
    if (!allowedTypes.has(event.type)) {
      throw new HttpsError("invalid-argument", "Unsupported health event.");
    }
    const metadata = requireObject(event.metadata ?? {}, "metadata");
    const forbidden = /coordinate|latitude|longitude|address|email|filename|path/i;
    for (const [key, value] of Object.entries(metadata)) {
      if (forbidden.test(key) || typeof value === "object" || String(value).length > 200) {
        throw new HttpsError("invalid-argument", "Health metadata contains sensitive data.");
      }
    }
    await db.collection(`users/${uid}/healthEvents`).add({
      type: event.type,
      metadata,
      createdAt: FieldValue.serverTimestamp(),
    });
    return {recorded: true};
  };
}

const applySyncBatchHandler = createApplySyncBatchHandler({db});
const deleteAccountDataHandler = createDeleteAccountDataHandler({
  db,
  auth: getAuth(),
  storage: getStorage(),
});
const routeEstimateHandler = createRouteEstimateHandler({
  fetchImpl: fetch,
  apiKeySecret: mapsRoutesApiKey,
});
const reverseGeocodeTripEndpointsHandler = createReverseGeocodeTripEndpointsHandler({
  fetchImpl: fetch,
  apiKeySecret: mapsGeocodingApiKey,
});
const recordHealthEventHandler = createRecordHealthEventHandler({db});

exports.applySyncBatch = onCall(applySyncBatchOptions, applySyncBatchHandler);
exports.deleteAccountData = onCall(deleteAccountDataOptions, deleteAccountDataHandler);
exports.getRouteEstimate = onCall(getRouteEstimateOptions, routeEstimateHandler);
exports.reverseGeocodeTripEndpoints = onCall(
    reverseGeocodeTripEndpointsOptions,
    reverseGeocodeTripEndpointsHandler,
);
exports.recordHealthEvent = onCall(recordHealthEventOptions, recordHealthEventHandler);
exports.__test__ = {
  applySyncBatchOptions,
  createApplySyncBatchHandler,
  createDeleteAccountDataHandler,
  createRecordHealthEventHandler,
  createReverseGeocodeTripEndpointsHandler,
  createRouteEstimateHandler,
  deleteAccountDataOptions,
  getRouteEstimateOptions,
  recordHealthEventOptions,
  requireCoordinate,
  requireObject,
  requireUid,
  reverseGeocodeTripEndpointsOptions,
};
