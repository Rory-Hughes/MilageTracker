"use strict";

const fs = require("node:fs");
const path = require("node:path");
const {assertFails, assertSucceeds, initializeTestEnvironment} =
  require("@firebase/rules-unit-testing");
const {doc, getDoc, setDoc} = require("firebase/firestore");
const {getBytes, ref, uploadString} = require("firebase/storage");

describe("MileageTracker Firebase rules", () => {
  let env;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "mileagetracker-rules-test",
      firestore: {
        rules: fs.readFileSync(path.join(__dirname, "../../firestore.rules"), "utf8"),
      },
      storage: {
        rules: fs.readFileSync(path.join(__dirname, "../../storage.rules"), "utf8"),
      },
    });
  });

  after(async () => env.cleanup());
  afterEach(async () => env.clearFirestore());

  it("allows an owner to read sync records but denies cross-account reads", async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "users/alice/syncRecords/trip-1"), {
        entityType: "trip",
      });
    });
    await assertSucceeds(
      getDoc(doc(env.authenticatedContext("alice").firestore(), "users/alice/syncRecords/trip-1")),
    );
    await assertFails(
      getDoc(doc(env.authenticatedContext("bob").firestore(), "users/alice/syncRecords/trip-1")),
    );
  });

  it("denies direct client writes because sync writes must use applySyncBatch", async () => {
    await assertFails(
      setDoc(doc(env.authenticatedContext("alice").firestore(), "users/alice/syncRecords/trip-1"), {
        entityType: "trip",
      }),
    );
  });

  it("isolates evidence files by uid", async () => {
    const aliceFile = ref(env.authenticatedContext("alice").storage(), "users/alice/evidence/a.txt");
    await assertSucceeds(uploadString(aliceFile, "receipt"));
    await assertSucceeds(getBytes(aliceFile));
    await assertFails(
      getBytes(ref(env.authenticatedContext("bob").storage(), "users/alice/evidence/a.txt")),
    );
  });
});
