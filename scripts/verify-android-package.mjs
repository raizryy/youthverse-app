import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
const root = new URL("../", import.meta.url).pathname;
const expected = "com.aistudio.youthverse.rvzkmq";
const gradle = readFileSync(join(root, "app/build.gradle.kts"), "utf8");
const appId = gradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1];
const namespace = gradle.match(/namespace\s*=\s*"([^"]+)"/)?.[1];
if (appId !== expected) throw new Error(`applicationId mismatch: ${appId}`);
if (namespace !== expected) throw new Error(`namespace mismatch: ${namespace}`);

const googleServicesPath = join(root, "app/google-services.json");
if (existsSync(googleServicesPath)) {
  const google = JSON.parse(readFileSync(googleServicesPath, "utf8"));
  const firebasePackages = (google.client ?? []).map(c => c.client_info?.android_client_info?.package_name);
  if (!firebasePackages.includes(expected)) throw new Error("google-services.json has no matching Android package");
} else {
  console.warn("Notice: app/google-services.json is absent; skipping Firebase client package check (Gradle uses MissingGoogleServicesStrategy.WARN).");
}
function walk(dir) {
 for (const name of readdirSync(dir)) {
  const path=join(dir,name);
  if (statSync(path).isDirectory()) walk(path);
  else if (path.endsWith(".kt") && readFileSync(path,"utf8").includes("com.example")) throw new Error(`Legacy package reference in ${path}`);
 }
}
for (const sourceRoot of ["app/src/main/java","app/src/test/java","app/src/androidTest/java"]) walk(join(root,sourceRoot));
console.log("Android package, namespace, Firebase client, and Kotlin package references are aligned.");
