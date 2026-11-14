import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import { test, before, after, beforeEach } from "node:test";
import fs from "node:fs";

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read users or library", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("library").get());
});

test("Authenticated user: cannot read another user's library", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("library").doc("book1").set({
      contentId: "book1",
      userId: BOB_UID,
      status: "READING",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("library").doc("book1").get());
});

test("Authenticated user: can read and write their own library entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library").doc("book1").set({
      contentId: "book1",
      userId: ALICE_UID,
      status: "READING",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("library").doc("book1").get());
});

test("Ordinary user: cannot create content (admin only)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  await assertFails(
    aliceDb.collection("content").doc("novel1").set({
      title: "Heroic Tale",
      author: "Renowned Author",
      contentType: "NOVEL",
      published: true,
      createdAt: new Date(),
    })
  );
});

test("Admin with server-issued custom claim can create content", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();

  await assertSucceeds(
    adminDb.collection("content").doc("novel1").set({
      title: "Heroic Tale",
      author: "Renowned Author",
      contentType: "NOVEL",
      published: true,
      createdAt: new Date(),
    })
  );
});

test("Public user: can read published content, but cannot read unpublished content if non-admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("published_novel").set({
      title: "Published Book",
      author: "Author A",
      contentType: "NOVEL",
      published: true,
      createdAt: new Date(),
    });
    await context.firestore().collection("content").doc("draft_novel").set({
      title: "Draft Book",
      author: "Author B",
      contentType: "NOVEL",
      published: false,
      visibility: true,
      createdAt: new Date(),
    });
    await context.firestore().collection("content").doc("hidden_novel").set({
      title: "Hidden Book",
      author: "Author C",
      contentType: "NOVEL",
      published: true,
      visibility: false,
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("published_novel").update({ visibility: true });
  });
  await assertSucceeds(aliceDb.collection("content").doc("published_novel").get());
  await assertFails(aliceDb.collection("content").doc("draft_novel").get());
  await assertFails(aliceDb.collection("content").doc("hidden_novel").get());
});


test("Email alone does not grant administrator privileges", async () => {
  const emailOnlyDb = testEnv.authenticatedContext("email_only_admin", {
    email: "lwin09528@gmail.com", email_verified: true,
  }).firestore();
  await assertFails(emailOnlyDb.collection("content").doc("forbidden").set({
    title: "Forbidden", author: "Author", contentType: "NOVEL",
    published: true, visibility: true, createdAt: new Date(),
  }));
});

test("Admin custom claim can read unpublished content", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("draft").set({
      title: "Draft", author: "Author", contentType: "NOVEL",
      published: false, visibility: true, createdAt: new Date(),
    });
  });
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();
  await assertSucceeds(adminDb.collection("content").doc("draft").get());
});


test("Ordinary user cannot read comments or reviews attached to unpublished content", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("content").doc("draft_social").set({
      title: "Draft", author: "Author", contentType: "NOVEL",
      published: false, visibility: true, createdAt: new Date(),
    });
    await db.collection("comments").doc("draft_comment").set({
      contentId: "draft_social", userId: ALICE_UID, userName: "Alice",
      text: "Private draft comment", createdAt: new Date(),
    });
    await db.collection("reviews").doc("draft_review").set({
      contentId: "draft_social", userId: ALICE_UID, userName: "Alice",
      rating: 5, createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("comments").doc("draft_comment").get());
  await assertFails(aliceDb.collection("reviews").doc("draft_review").get());
});

test("Ordinary user cannot add a comment to unpublished content", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("draft_social").set({
      title: "Draft", author: "Author", contentType: "NOVEL",
      published: false, visibility: true, createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("comments").add({
    contentId: "draft_social", userId: ALICE_UID, userName: "Alice",
    text: "Should be blocked", createdAt: new Date(),
  }));
});


test("Ordinary user can comment on published and visible content", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("published_social").set({
      title: "Published", author: "Author", contentType: "NOVEL",
      published: true, visibility: true, createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("comments").add({
    contentId: "published_social", userId: ALICE_UID, userName: "Alice",
    text: "A permitted comment", createdAt: new Date(),
  }));
});


test("Even an admin client cannot directly delete content documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("content").doc("cascade_target").set({
      title: "Cascade target", author: "Author", contentType: "NOVEL",
      published: false, visibility: false, createdAt: new Date(),
    });
  });
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();
  await assertFails(adminDb.collection("content").doc("cascade_target").delete());
});


test("A user cannot directly delete their profile and orphan nested data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: "alice@example.com", role: "USER", createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("User can create their own profile with role USER and valid provider, but cannot self-assign ADMIN", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      photoUrl: "",
      provider: "password",
      role: "USER",
      preferredLanguage: "en",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: "bob@example.com",
      provider: "password",
      role: "ADMIN",
      createdAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: "bob@example.com",
      provider: "invalid_provider",
      role: "USER",
      createdAt: new Date(),
    })
  );
});
