# YouthVerse Phase 7.1 — GitHub Cloud Build Setup

## What this update changes

- Adds a GitHub Actions workflow at `.github/workflows/android-build.yml`.
- Uses JDK 21, Android SDK 36 / 36.1, and the project's declared Gradle 9.3.1 distribution.
- Generates the official Gradle Wrapper from the official Gradle distribution during CI. No placeholder or hand-made wrapper JAR is included.
- Creates an ephemeral standard Android debug keystore in the CI runner. It is not committed or uploaded.
- Runs the existing Phase 7 static verifier and Android package/Firebase identity check.
- Attempts `:app:assembleDebug` and `:app:testDebugUnitTest`.
- On success, uploads the debug APK and generated wrapper as separate workflow artifacts.

## Run it from an Android phone

1. Upload/extract this project into the GitHub repository that will hold YouthVerse. Keep the `.github/workflows/android-build.yml` path exactly as supplied.
2. Commit and push the files to `main` (or `master`). Alternatively, open the repository's **Actions** tab and manually run **YouthVerse Android Build** using **Run workflow**.
3. Open the latest workflow run and inspect each step. A green workflow means the configured build and unit-test commands completed successfully; it does not prove Firebase production readiness or real-device behavior.
4. If successful, open **Artifacts** and download `youthverse-debug-apk` to install the debug APK on an Android test device.
5. Also download `youthverse-gradle-wrapper`. Extract its contents into the repository root, preserving paths (`gradlew`, `gradlew.bat`, and `gradle/wrapper/*`), then commit those official wrapper files. Do not commit `debug.keystore`.

## If the workflow fails

- Open the first failed step and copy the complete relevant error (including the task name and `Caused by` section).
- Do not change dependency versions or Kotlin source blindly. Fix the first reproducible error, rerun, then continue to the next error.
- If SDK package `platforms;android-36.1` is unavailable in the runner, inspect the SDK manager output first; do not silently lower `compileSdk` or change `minorApiLevel`.
- If Gradle Wrapper generation fails, retain the logs and do not add a guessed JAR.

## Security boundaries

- This workflow does not deploy Firebase, change Firestore Rules, upload content, or contact production Cloud Functions.
- It does not need Cloudinary API secrets, Firebase service-account credentials, or a release keystore.
- The generated debug key is temporary and only for a debug APK. It is not a Play Store signing key.
- The checked-in `google-services.json` contains Firebase client configuration, not an admin service-account credential. Review Firebase App Check, API restrictions, and Firestore Rules separately before any public release.

## Current validation status

This workflow file is setup work only. Until a GitHub Actions run completes, Android compilation, unit tests, APK creation, and device behavior remain **unverified**.
