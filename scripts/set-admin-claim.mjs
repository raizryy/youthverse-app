import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const projectId = process.env.FIREBASE_PROJECT_ID || "youthverse-yadanarlynn";
const email = process.argv[2] || "lwin09528@gmail.com";

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw new Error("Set GOOGLE_APPLICATION_CREDENTIALS to a Firebase service-account JSON file path. Never commit that file.");
}

initializeApp({ credential: applicationDefault(), projectId });
const auth = getAuth();
const user = await auth.getUserByEmail(email);
const existingClaims = user.customClaims || {};
await auth.setCustomUserClaims(user.uid, { ...existingClaims, admin: true });
console.log(`Admin custom claim enabled for ${email} (${user.uid}). Sign out and sign in again on YouthVerse to refresh the ID token.`);
