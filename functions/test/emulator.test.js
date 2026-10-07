"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const test = require("node:test");

process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "demo-mileagetracker";
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
process.env.FIREBASE_STORAGE_EMULATOR_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199";

const {initializeApp: initializeAdminApp} = require("firebase-admin/app");
const {getAuth: getAdminAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");

const projectId = process.env.GCLOUD_PROJECT;
const bucketName = `${projectId}.appspot.com`;
const adminApp = initializeAdminApp({projectId, storageBucket: bucketName});
const adminAuth = getAdminAuth(adminApp);
const adminDb = getFirestore(adminApp);
const adminStorage = getStorage(adminApp).bucket(bucketName);

function randomId(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

async function createSignedInClient() {
  const [{initializeApp}, {getAuth, connectAuthEmulator, createUserWithEmailAndPassword}] = await Promise.all([
    import("firebase/app"),
    import("firebase/auth"),
  ]);

  const clientApp = initializeApp({
    apiKey: "demo-api-key",
    appId: randomId("app"),
    authDomain: `${projectId}.firebaseapp.com`,
    projectId,
  }, randomId("client"));

  const auth = getAuth(clientApp);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", {disableWarnings: true});
  const email = `${randomId("user")}@example.com`;
  const credential = await createUserWithEmailAndPassword(auth, email, "Passw0rd!");
  await credential.user.getIdToken(true);
  if (typeof auth.authStateReady === "function") {
    await auth.authStateReady();
  }

  const idToken = await credential.user.getIdToken();

  return {
    auth,
    async callable(name, data) {
      const response = await fetch(
          `http://127.0.0.1:5001/${projectId}/northamerica-northeast1/${name}`,
          {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${idToken}`,
              "Content-Type": "application/json",
              "X-Firebase-AppCheck": "debug-app-check-token",
            },
            body: JSON.stringify({data}),
          },
      );
      const payload = await response.json();
      if (!response.ok || payload.error) {
        const error = new Error(payload.error?.message ?? response.statusText);
        error.code = payload.error?.status ?? response.status;
        throw error;
      }
      return payload.result;
    },
    uid: credential.user.uid,
  };
}

async function assertMissingUser(uid) {
  await assert.rejects(
      () => adminAuth.getUser(uid),
      (error) => error?.code === "auth/user-not-found",
  );
}

test("applySyncBatch writes records and reports conflicts through the emulators", async () => {
  const client = await createSignedInClient();
  const syncId = crypto.randomUUID();
  const first = await client.callable("applySyncBatch", {
    records: [{
      syncId,
      entityType: "drives",
      entityId: "drive-1",
      payloadJson: "{\"distanceKm\":12.5}",
      baseRevision: 0,
      deletedAt: null,
    }],
  });

  assert.deepEqual(first.results, [{
    syncId,
    status: "applied",
    revision: 1,
  }]);

  const stored = await adminDb.doc(`users/${client.uid}/syncRecords/${syncId}`).get();
  assert.equal(stored.exists, true);
  assert.equal(stored.get("entityType"), "drives");
  assert.equal(stored.get("revision"), 1);
  assert.equal(stored.get("payloadJson"), "{\"distanceKm\":12.5}");

  const second = await client.callable("applySyncBatch", {
    records: [{
      syncId,
      entityType: "drives",
      entityId: "drive-1",
      payloadJson: "{\"distanceKm\":99.9}",
      baseRevision: 0,
      deletedAt: null,
    }],
  });

  assert.equal(second.results[0].syncId, syncId);
  assert.equal(second.results[0].status, "conflict");
  assert.equal(second.results[0].remote.revision, 1);
});

test("deleteAccountData removes firestore data, evidence files, and the auth user", async () => {
  const client = await createSignedInClient();
  const evidencePath = `users/${client.uid}/evidence/trip-attachments/demo.txt`;

  await adminDb.doc(`users/${client.uid}`).set({createdAt: Date.now()});
  await adminDb.doc(`users/${client.uid}/syncRecords/demo`).set({
    entityType: "drives",
    payloadJson: "{}",
    revision: 1,
  });
  await adminStorage.file(evidencePath).save("evidence");

  const response = await client.callable("deleteAccountData", {});
  assert.deepEqual(response, {deleted: true});

  assert.equal((await adminDb.doc(`users/${client.uid}`).get()).exists, false);
  assert.equal((await adminDb.collection(`users/${client.uid}/syncRecords`).get()).empty, true);

  const [files] = await adminStorage.getFiles({prefix: `users/${client.uid}/evidence/`});
  assert.equal(files.length, 0);

  await assertMissingUser(client.uid);
});
