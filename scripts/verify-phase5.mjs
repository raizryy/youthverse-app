import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const auth = read("app/src/main/java/com/aistudio/youthverse/rvzkmq/ui/auth/AuthScreen.kt");
const users = read("app/src/main/java/com/aistudio/youthverse/rvzkmq/data/repository/UserRepository.kt");
const profile = read("app/src/main/java/com/aistudio/youthverse/rvzkmq/ui/profile/ProfileScreen.kt");
const rules = read("firestore.rules");

const checks = [
  ["Interactive sign-in handles coroutine cancellation", auth.includes("catch (e: CancellationException)") && auth.includes("throw e")],
  ["Credential flow does not blindly cast Context to Activity", auth.includes("context as? Activity")],
  ["Sign-in errors are mapped to safe user guidance", auth.includes("friendlyAuthError(e)")],
  ["Silent sign-in does not expose raw errors", auth.includes("Silent sign-in is optional")],
  ["Profile sync never changes authorization role", !users.includes('updates["role"]')],
  ["Profile sync never changes UID", !users.includes('updates["uid"]')],
  ["Profile sync never changes creation timestamp", !users.includes('updates["createdAt"]')],
  ["Language values are allowlisted", users.includes('lang !in setOf("my", "en")')],
  ["Sign-out button prevents repeat taps", profile.includes("enabled = !signingOut")],
  ["Firestore profile update preserves existing role", rules.includes("incoming().role == existing().role")],
];

for (const [label, passed] of checks) {
  console.log(`${passed ? "PASS" : "FAIL"} ${label}`);
}
const failed = checks.filter(([, passed]) => !passed);
console.log(`${checks.length - failed.length}/${checks.length} Phase 5 checks passed`);
if (failed.length) process.exitCode = 1;
