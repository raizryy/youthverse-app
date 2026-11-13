# YouthVerse Phase 7.2 — Apply the CI Hardening Patch

This patch updates only CI/test configuration and adds an emulator-only test helper. It does not change app screens, content models, Firestore production data, or live Firebase settings.

## Replace/add these exact repository paths

Replace:
- `.github/workflows/android-build.yml`
- `package.json`
- `app/build.gradle.kts`
- `app/src/test/java/com/aistudio/youthverse/rvzkmq/repository/YouthVerseRepositoryRuleTest.kt`

Add:
- `scripts/seed-emulator-admin.mjs`
- `PHASE7_E2E_TEST_PLAN.md`
- `PHASE7_2_CI_HARDENING_REPORT.md`

Keep all paths and folder names exactly. Do not upload the ZIP file itself into the repository root as a substitute for extracting its contents.

## Commit and run

1. Commit the changed files to `main`.
2. Open **Actions → YouthVerse Android Build**.
3. Open the newest run. The workflow now shows each verification/build/test action as a separate step.
4. If a step fails, open that specific red step and copy its full log, including the first `FAILURE:` section and `Caused by` details.
5. If all jobs pass, download `youthverse-debug-apk` and `youthverse-gradle-wrapper` from Artifacts. Commit the generated official wrapper files to the repository, but never commit `debug.keystore`.
6. Install the debug APK on a test device and follow `PHASE7_E2E_TEST_PLAN.md`.

The patch has not itself been run on GitHub. It does not claim that a build, emulator test, or APK generation has succeeded.
