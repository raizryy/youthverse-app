import { readFileSync } from "node:fs";
const service = readFileSync(new URL("../app/src/main/java/com/aistudio/youthverse/rvzkmq/data/remote/CloudinaryService.kt", import.meta.url), "utf8");
const fn = readFileSync(new URL("../functions/index.mjs", import.meta.url), "utf8");
const main = readFileSync(new URL("../app/src/main/java/com/aistudio/youthverse/rvzkmq/MainActivity.kt", import.meta.url), "utf8");
const gradle = readFileSync(new URL("../app/build.gradle.kts", import.meta.url), "utf8");
const checks = [
  ["Android upload does not use unsigned preset", !service.includes("upload_preset") && !service.includes("youthverse_readsonyv")],
  ["Android upload does not contain Cloudinary API secret", !service.includes("CLOUDINARY_API_SECRET") && !service.includes("apiSecret")],
  ["Server function enforces Firebase admin claim", fn.includes("request.auth.token.admin !== true")],
  ["Server function enforces App Check", fn.includes("enforceAppCheck: true")],
  ["Server function uses Secret Manager", fn.includes('defineSecret("CLOUDINARY_API_SECRET")')],
  ["Android initializes App Check providers", main.includes("DebugAppCheckProviderFactory") && main.includes("PlayIntegrityAppCheckProviderFactory")],
  ["Android includes Firebase Functions and Play Integrity dependencies", gradle.includes("libs.firebase.functions") && gradle.includes("libs.firebase.appcheck.playintegrity")],
];
for (const [label, ok] of checks) console.log(`${ok ? "PASS" : "FAIL"} ${label}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
