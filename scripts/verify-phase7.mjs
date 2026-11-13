import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();
const checks = [];
const add = (name, ok, detail) => checks.push({ name, ok, detail });

for (const file of ["gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"]) {
  const present = fs.existsSync(path.join(root, file));
  add(`Gradle wrapper file: ${file}`, present, present ? "present" : "missing");
}

const wrapperProps = path.join(root, "gradle/wrapper/gradle-wrapper.properties");
if (fs.existsSync(wrapperProps)) {
  const props = fs.readFileSync(wrapperProps, "utf8");
  const url = props.match(/^distributionUrl=(.+)$/m)?.[1] ?? "";
  add("Gradle distribution URL", /gradle-[0-9][^/]*-bin\.zip/.test(url), url || "distributionUrl not found");
}

const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
add("Android SDK environment", Boolean(sdk), sdk || "ANDROID_HOME / ANDROID_SDK_ROOT not set");
const gradle = spawnSync("gradle", ["--version"], { encoding: "utf8" });
add("Gradle executable", gradle.status === 0, "A system Gradle executable is required when the wrapper is absent");
add("Firestore rules test source", fs.existsSync(path.join(root, "firestore.test.js")), "firestore.test.js presence");
add("Firestore rules file", fs.existsSync(path.join(root, "firestore.rules")), "firestore.rules presence");

const functionsPackage = JSON.parse(fs.readFileSync(path.join(root, "functions/package.json"), "utf8"));
add("Cloud Functions runtime is Node.js 22", functionsPackage.engines?.node === "22", `engines.node=${functionsPackage.engines?.node ?? "unset"}`);

let blocked = 0;
for (const check of checks) {
  console.log(`${check.ok ? "PASS" : "BLOCKED"} ${check.name}: ${check.detail}`);
  if (!check.ok) blocked++;
}
console.log(`\nPhase 7 readiness: ${checks.length - blocked}/${checks.length} checks ready; ${blocked} blocker(s).`);
console.log("This verifier does not compile Android, run Firebase emulators, deploy Firebase, or install an APK.");
