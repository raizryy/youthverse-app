# YouthVerse — Phase 7.2 CI and Emulator-Test Hardening Report

Date: 2026-11-12  
Base: `YouthVerse_Phase7_1_GitHub_Build_Setup.zip` (from the audited Phase 6 source)

## Summary

Phase 7.2 addresses CI reliability and test-environment gaps discovered while reviewing the first GitHub Actions run and the Android emulator test sources. The user's screenshot confirms the workflow run failed after 26 seconds, but it does not expose the failed step or error text. The Node.js deprecation annotations are warnings, not proof of the root cause.

## Findings addressed

1. Updated GitHub Actions to Node 24-compatible major versions: checkout v7, setup-java v6, setup-node v6, setup-android v4, Gradle setup/wrapper validation v6, and upload-artifact v6.
2. Gradle Wrapper generation now happens in an empty temporary Gradle project using the installed official Gradle 9.3.1 distribution. This avoids evaluating the YouthVerse Android project merely to create the wrapper. The generated wrapper is checksum-validated and can be downloaded as an artifact.
3. CI now starts both Firestore and Auth emulators before running the JavaScript Firestore Rules tests and Android Robolectric repository tests.
4. The Android repository test previously attempted an “admin” write by signing in with an email address only. The Rules authorize admins only through the server-issued `admin: true` custom claim, so an ordinary Auth Emulator account would be denied. CI now seeds an isolated `admin@test.com` account with a custom claim through Firebase Admin SDK, and the test uses that emulator-only account.
5. Robolectric offline mode previously forced every test run to use `/tmp/robolectric-deps`, which is not populated on a fresh GitHub runner. Offline mode is now opt-in through `ROBOLECTRIC_OFFLINE=true`; CI can resolve the required SDK artifact normally.
6. Added a manual physical-device test plan for Login → Home → Reading → Library/History → Admin.

## Files changed

- `.github/workflows/android-build.yml`
- `scripts/seed-emulator-admin.mjs` (new)
- `package.json`
- `app/src/test/java/com/aistudio/youthverse/rvzkmq/repository/YouthVerseRepositoryRuleTest.kt`
- `app/build.gradle.kts`
- `PHASE7_E2E_TEST_PLAN.md` (new)

## Verification performed in this environment

- Phase 4 static checks: 7/7 PASS
- Phase 5 static checks: 10/10 PASS
- Phase 6 static checks: 12/12 PASS
- Android package / namespace / Firebase identity check: PASS
- JavaScript syntax checks (`node --check`) for all repository `.js`/`.mjs` files, including the new emulator seed script: PASS.
- Cloud Functions unit tests: 3/3 PASS.
- Emulator-suite command wiring: argument parsing PASS; actual execution BLOCKED because Firebase CLI is not installed (`firebase: not found`).
- ZIP integrity and workflow YAML parse: PASS
- Gradle Wrapper files: still absent from source archive by design; CI generates them from official Gradle 9.3.1.
- Local Android compilation: NOT RUN (no Gradle executable, Android SDK, `adb`, or `sdkmanager`; outbound network/DNS unavailable in this environment).
- Firebase Emulator Rules tests: NOT RUN locally (Firebase CLI/dependencies unavailable and no outbound network).
- APK/AAB generation: NOT RUN.
- Firebase deployment: NOT RUN.
- Physical-device E2E: NOT RUN.

## Remaining blockers

1. The first GitHub Actions run's actual failure cause is still unknown because the supplied screenshot shows only the summary and deprecation annotations, not the failed job step log.
2. The updated workflow must be committed/pushed and run on GitHub to verify the real cloud build, emulator tests, and APK artifact.
3. After CI passes, install the debug APK on a physical Android device and complete `PHASE7_E2E_TEST_PLAN.md` using a disposable Firebase test project.
4. No production Firebase Rules/Functions deployment was performed. Phase 6 deletion still leaves Cloudinary assets for a separate cleanup workflow.

## Safety

The emulator admin seed script refuses to run unless `FIREBASE_AUTH_EMULATOR_HOST` points to the local Auth Emulator. It does not grant privileges to a production account. The debug keystore remains temporary in CI and is not committed or uploaded.
