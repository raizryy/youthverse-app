
import { randomUUID } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createCloudinarySignature, ALLOWED_FOLDERS } from "./cloudinary-signature.mjs";
initializeApp();
const adminDb = getFirestore();

const cloudinaryApiKey = defineSecret("CLOUDINARY_API_KEY");
const cloudinaryApiSecret = defineSecret("CLOUDINARY_API_SECRET");
const CLOUDINARY_CLOUD_NAME = "do95w9n91";
export const createCloudinaryUploadSignature = onCall({
  region: "us-central1", secrets: [cloudinaryApiKey, cloudinaryApiSecret],
  enforceAppCheck: true, maxInstances: 3,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  if (request.auth.token.admin !== true) throw new HttpsError("permission-denied", "Administrator permission is required.");
  const folder = request.data?.folder;
  if (typeof folder !== "string" || !ALLOWED_FOLDERS.has(folder)) {
    throw new HttpsError("invalid-argument", "Unsupported upload folder.");
  }
  const timestamp = Math.floor(Date.now() / 1000);
  const publicId = `yv_${randomUUID().replaceAll("-", "")}`;
  const apiKey = cloudinaryApiKey.value();
  const apiSecret = cloudinaryApiSecret.value();
  if (!apiKey || !apiSecret) throw new HttpsError("failed-precondition", "Cloudinary server credentials are not configured.");
  let signature;
  try { signature = createCloudinarySignature({ folder, publicId, timestamp, apiSecret }); }
  catch { throw new HttpsError("internal", "Could not create upload authorization."); }
  return { cloudName: CLOUDINARY_CLOUD_NAME, apiKey, timestamp, folder, publicId, signature };
});


/**
 * Deletes a content item and all Firestore records that reference it.
 * Client-side Firestore deletes are deliberately disabled by the rules; this
 * callable is the single authorized deletion path.
 */
export const deleteContentCascade = onCall({
  region: "us-central1",
  enforceAppCheck: true,
  maxInstances: 3,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Administrator permission is required.");
  }

  const contentId = request.data?.contentId;
  if (typeof contentId !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(contentId)) {
    throw new HttpsError("invalid-argument", "A valid content ID is required.");
  }

  const contentRef = adminDb.collection("content").doc(contentId);
  const contentSnapshot = await contentRef.get();
  if (!contentSnapshot.exists) throw new HttpsError("not-found", "Content was not found.");

  // Delete top-level social records and user-owned references in bounded batches.
  const relatedCollections = ["comments", "reviews"];
  for (const collectionName of relatedCollections) {
    await deleteQueryInBatches(adminDb.collection(collectionName).where("contentId", "==", contentId));
  }
  for (const collectionName of ["library", "history"]) {
    await deleteQueryInBatches(adminDb.collectionGroup(collectionName).where("contentId", "==", contentId));
  }

  // recursiveDelete removes the content document and its nested chapters.
  await adminDb.recursiveDelete(contentRef);
  return { deleted: true, contentId };
});

async function deleteQueryInBatches(query) {
  while (true) {
    const snapshot = await query.limit(400).get();
    if (snapshot.empty) return;
    const batch = adminDb.batch();
    for (const document of snapshot.docs) batch.delete(document.ref);
    await batch.commit();
    if (snapshot.size < 400) return;
  }
}
