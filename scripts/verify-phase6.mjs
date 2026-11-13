import fs from "node:fs";

const rules = fs.readFileSync("firestore.rules", "utf8");
const functions = fs.readFileSync("functions/index.mjs", "utf8");
const repository = fs.readFileSync("app/src/main/java/com/aistudio/youthverse/rvzkmq/data/repository/ContentRepository.kt", "utf8");
const dashboard = fs.readFileSync("app/src/main/java/com/aistudio/youthverse/rvzkmq/ui/admin/AdminDashboardScreen.kt", "utf8");
const functionPackage = JSON.parse(fs.readFileSync("functions/package.json", "utf8"));

const checks = [
  ["Firestore blocks direct client content deletion", rules.includes("allow delete: if false;")],
  ["Firestore blocks unsafe direct account deletion", rules.includes("Account deletion must go through a trusted cleanup workflow")],
  ["Callable deletion requires Firebase Auth", functions.includes('if (!request.auth) throw new HttpsError("unauthenticated"')],
  ["Callable deletion requires admin custom claim", functions.includes("request.auth.token.admin !== true")],
  ["Callable deletion enforces App Check", functions.includes("deleteContentCascade = onCall({") && functions.includes("enforceAppCheck: true")],
  ["Callable deletes chapter subcollections recursively", functions.includes("recursiveDelete(contentRef)")],
  ["Callable cleans comments and reviews", functions.includes('["comments", "reviews"]')],
  ["Callable cleans user library and history references", functions.includes('["library", "history"]')],
  ["Android repository invokes trusted deletion callable", repository.includes('getHttpsCallable("deleteContentCascade")')],
  ["Admin UI reports failed deletion and guards repeat taps", dashboard.includes("deleteError") && dashboard.includes("enabled = !isDeleting")],
  ["Functions package explicitly includes Firebase Admin SDK", Boolean(functionPackage.dependencies?.["firebase-admin"])],
  ["Functions runtime targets supported Node.js 22", functionPackage.engines?.node === "22"],
];
let failed = 0;
for (const [name, passed] of checks) {
  console.log(`${passed ? "PASS" : "FAIL"} ${name}`);
  if (!passed) failed++;
}
if (failed) process.exitCode = 1;
else console.log(`${checks.length}/${checks.length} Phase 6 checks passed`);
