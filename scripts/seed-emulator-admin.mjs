import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const projectId = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || "demo-no-project";
const emulatorHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const adminEmail = "admin@test.com";
const adminPassword = "password123";

if (!emulatorHost || !["127.0.0.1:9099", "localhost:9099"].includes(emulatorHost)) {
  throw new Error(
    "Refusing to seed an admin account: FIREBASE_AUTH_EMULATOR_HOST must point to the local Auth Emulator at 127.0.0.1:9099."
  );
}

const app = initializeApp({ projectId }, "youthverse-emulator-admin-seed");

try {
  const auth = getAuth(app);
  let user;

  try {
    user = await auth.getUserByEmail(adminEmail);
    user = await auth.updateUser(user.uid, {
      password: adminPassword,
      emailVerified: true,
    });
  } catch (error) {
    if (error?.code !== "auth/user-not-found") throw error;
    user = await auth.createUser({
      email: adminEmail,
      password: adminPassword,
      emailVerified: true,
    });
  }

  await auth.setCustomUserClaims(user.uid, { admin: true });
  console.log(`Seeded emulator-only admin claim for ${adminEmail} (${user.uid}).`);
} finally {
  await deleteApp(app);
}
