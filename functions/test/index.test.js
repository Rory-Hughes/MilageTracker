"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {__test__} = require("../index.js");

function createRequest({uid = "user-1", data = {}} = {}) {
  return {
    auth: uid ? {uid} : null,
    data,
  };
}

function createApplySyncDb({remoteRecord = null} = {}) {
  const state = {
    docPath: null,
    setCalls: [],
  };

  return {
    db: {
      doc(path) {
        state.docPath = path;
        return {path};
      },
      async runTransaction(block) {
        const transaction = {
          async get() {
            return remoteRecord == null ? {
              exists: false,
              data: () => undefined,
            } : {
              exists: true,
              data: () => remoteRecord,
            };
          },
          set(ref, value) {
            state.setCalls.push({ref, value});
          },
        };
        return block(transaction);
      },
    },
    state,
  };
}

async function assertHttpsError(action, expectedCode, expectedMessage) {
  await assert.rejects(
      action,
      (error) => {
        assert.equal(error.code, expectedCode);
        assert.equal(error.message, expectedMessage);
        return true;
      },
  );
}

test("applySyncBatch declares App Check enforcement", () => {
  assert.equal(__test__.applySyncBatchOptions.enforceAppCheck, true);
  assert.equal(__test__.deleteAccountDataOptions.enforceAppCheck, true);
  assert.equal(__test__.getRouteEstimateOptions.enforceAppCheck, true);
  assert.equal(__test__.reverseGeocodeTripEndpointsOptions.enforceAppCheck, true);
  assert.equal(__test__.recordHealthEventOptions.enforceAppCheck, true);
});

test("applySyncBatch rejects unauthenticated callers", async () => {
  const {db} = createApplySyncDb();
  const handler = __test__.createApplySyncBatchHandler({db});

  await assertHttpsError(
      () => handler(createRequest({uid: null, data: {records: []}})),
      "unauthenticated",
      "Sign in is required.",
  );
});

test("requireUid falls back to emulator bearer token claims when callable auth omits uid", () => {
  const original = process.env.FUNCTIONS_EMULATOR;
  process.env.FUNCTIONS_EMULATOR = "true";
  const payload = Buffer.from(JSON.stringify({user_id: "emulator-user"})).toString("base64url");
  const token = `header.${payload}.signature`;

  try {
    const uid = __test__.requireUid({
      auth: {},
      rawRequest: {
        headers: {
          authorization: `Bearer ${token}`,
        },
      },
    });

    assert.equal(uid, "emulator-user");
  } finally {
    if (original === undefined) {
      delete process.env.FUNCTIONS_EMULATOR;
    } else {
      process.env.FUNCTIONS_EMULATOR = original;
    }
  }
});

test("applySyncBatch returns conflict for stale remote revisions", async () => {
  const remote = {
    revision: 4,
    payloadJson: "{\"server\":true}",
    deletedAt: null,
  };
  const {db, state} = createApplySyncDb({remoteRecord: remote});
  const handler = __test__.createApplySyncBatchHandler({db});

  const result = await handler(createRequest({
    data: {
      records: [{
        syncId: "11111111-1111-1111-1111-111111111111",
        entityType: "drives",
        entityId: "42",
        payloadJson: "{\"client\":true}",
        baseRevision: 2,
      }],
    },
  }));

  assert.deepEqual(result, {
    results: [{
      syncId: "11111111-1111-1111-1111-111111111111",
      status: "conflict",
      remote,
    }],
  });
  assert.equal(state.setCalls.length, 0);
});

test("applySyncBatch applies upserts and increments revision", async () => {
  const {db, state} = createApplySyncDb({
    remoteRecord: {
      revision: 2,
      deletedAt: null,
    },
  });
  const handler = __test__.createApplySyncBatchHandler({db});

  const result = await handler(createRequest({
    uid: "owner-123",
    data: {
      records: [{
        syncId: "22222222-2222-2222-2222-222222222222",
        entityType: "vehicles",
        entityId: "vehicle-8",
        payloadJson: "{\"name\":\"Car\"}",
        baseRevision: 2,
      }],
    },
  }));

  assert.deepEqual(result, {
    results: [{
      syncId: "22222222-2222-2222-2222-222222222222",
      status: "applied",
      revision: 3,
    }],
  });
  assert.equal(state.docPath, "users/owner-123/syncRecords/22222222-2222-2222-2222-222222222222");
  assert.equal(state.setCalls.length, 1);
  assert.equal(state.setCalls[0].value.ownerUid, "owner-123");
  assert.equal(state.setCalls[0].value.revision, 3);
  assert.equal(state.setCalls[0].value.deletedAt, null);
});

test("deleteAccountData removes firestore data, evidence files, and auth user", async () => {
  const calls = {
    deleteFiles: [],
    deletedUsers: [],
    recursiveDelete: [],
    docPaths: [],
  };
  const handler = __test__.createDeleteAccountDataHandler({
    db: {
      doc(path) {
        calls.docPaths.push(path);
        return {path};
      },
      async recursiveDelete(ref) {
        calls.recursiveDelete.push(ref.path);
      },
    },
    auth: {
      async deleteUser(uid) {
        calls.deletedUsers.push(uid);
      },
    },
    storage: {
      bucket() {
        return {
          async deleteFiles(options) {
            calls.deleteFiles.push(options);
          },
        };
      },
    },
  });

  const result = await handler(createRequest({uid: "owner-123"}));

  assert.deepEqual(result, {deleted: true});
  assert.deepEqual(calls.docPaths, ["users/owner-123"]);
  assert.deepEqual(calls.recursiveDelete, ["users/owner-123"]);
  assert.deepEqual(calls.deleteFiles, [{prefix: "users/owner-123/evidence/"}]);
  assert.deepEqual(calls.deletedUsers, ["owner-123"]);
});

test("recordHealthEvent rejects sensitive metadata keys", async () => {
  const handler = __test__.createRecordHealthEventHandler({
    db: {
      collection() {
        return {
          async add() {
            throw new Error("should not write");
          },
        };
      },
    },
  });

  await assertHttpsError(
      () => handler(createRequest({
        data: {
          type: "sync_failure",
          metadata: {
            filePath: "users/u1/evidence/bad.jpg",
          },
        },
      })),
      "invalid-argument",
      "Health metadata contains sensitive data.",
  );
});

test("recordHealthEvent stores allowed health events", async () => {
  const writes = [];
  const handler = __test__.createRecordHealthEventHandler({
    db: {
      collection(path) {
        return {
          async add(data) {
            writes.push({path, data});
          },
        };
      },
    },
  });

  const result = await handler(createRequest({
    uid: "owner-123",
    data: {
      type: "sync_retry",
      metadata: {
        retryCount: "2",
        worker: "SyncOutboxWorker",
      },
    },
  }));

  assert.deepEqual(result, {recorded: true});
  assert.equal(writes.length, 1);
  assert.equal(writes[0].path, "users/owner-123/healthEvents");
  assert.equal(writes[0].data.type, "sync_retry");
  assert.deepEqual(writes[0].data.metadata, {
    retryCount: "2",
    worker: "SyncOutboxWorker",
  });
});
