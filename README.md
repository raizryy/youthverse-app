# YouthVerse Android

YouthVerse is an Android reading app built with Kotlin, Jetpack Compose, Firebase Authentication/Firestore, and Cloudinary media delivery. The Android application ID and supplied Firebase Android client are aligned to `com.aistudio.youthverse.rvzkmq`.

## Firebase project

- Firebase project: `youthverse-yadanarlynn`
- Firestore database configured by this project: `(default)`
- Android application ID: `com.aistudio.youthverse.rvzkmq`
- Cloudinary cloud: `do95w9n91`
- Cloudinary upload preset: `youthverse_readsonyv`

The committed `app/google-services.json` is the client configuration supplied for this Firebase project. Firebase client API keys are identifiers, not server credentials. Never put a Firebase service-account key, Cloudinary API secret, or other server credential in the Android app or Git repository.

## Security model

- Publishing and content/chapter mutations are restricted by Firestore Rules to Firebase Auth tokens carrying the server-issued `admin: true` custom claim. An email address or editable profile `role` is not authorization.
- Ordinary users can read only content where `published == true` and `visibility == true`; chapters additionally require their parent content to be published and visible.
- User library/history/profile records are scoped to the authenticated UID.
- The initial admin bootstrap account is `lwin09528@gmail.com`. It receives no server privilege until the custom claim is explicitly assigned below.
- The Android UI now reads the `admin` custom claim from the Firebase ID token; email matching is not used to show privileged controls.

### One-time Admin claim setup

1. In Firebase Console, confirm the user `lwin09528@gmail.com` exists in Authentication.
2. Create/download a Firebase service-account key for a trusted local machine. Store it outside this repository. Restrict its access and delete/rotate it after use where practical.
3. Install Node.js dependencies: `npm install`.
4. Set `GOOGLE_APPLICATION_CREDENTIALS` to the absolute path of that service-account JSON file.
5. Run: `node scripts/set-admin-claim.mjs lwin09528@gmail.com`.
6. Sign out and sign back in to YouthVerse so Firebase Auth refreshes the ID token.

The service-account JSON must never be uploaded to AI Studio, committed to GitHub, or packaged into the APK.

## Deploy Firestore Rules

Install Firebase CLI and authenticate with an account authorized for this Firebase project, then run `npm run deploy:rules`. Review the target project in Firebase CLI before deployment. The rules target the project's `(default)` Firestore database.

Run emulator rule tests with `npm run test:rules` after starting the Firestore emulator prerequisites.

## Cloudinary signed-upload security (Phase 4)

- Android no longer uses the unsigned upload preset. It calls `createCloudinaryUploadSignature` in Firebase Functions first.
- The callable function requires Firebase Authentication, the server-issued `admin: true` custom claim, and a valid App Check token.
- The function accepts only `youthverse/covers` and `youthverse/chapters`, generates the public ID, and signs the fixed folder/public ID/timestamp parameters.
- Cloudinary API key and API secret are read from Firebase Functions Secret Manager. The secret is never sent to Android or committed. The API key is returned only because Cloudinary requires it for the upload request.
- Android refreshes the ID token, validates image MIME type, caps files at 10 MB, and verifies the returned Cloudinary URL host.

### Required one-time setup (not performed by this source update)

1. Obtain the existing Cloudinary API key and API secret from Cloudinary Console. Never paste the secret into chat, Android source, GitHub, or `.env`.
2. From a trusted terminal, run `firebase login` and select `youthverse-yadanarlynn`.
3. Set secrets interactively: `firebase functions:secrets:set CLOUDINARY_API_KEY` and `firebase functions:secrets:set CLOUDINARY_API_SECRET`.
4. Register Android App Check and configure Play Integrity; add the Play signing SHA-256 fingerprint. Debug builds use the Debug provider; register its debug token in Firebase Console.
5. Review billing before enabling Blaze. Firebase Cloud Functions deployment requires Blaze billing; set budget alerts first.
6. Install root and function dependencies, run `npm run test:functions`, `npm run test:rules`, and deploy after review with `npm run deploy:functions`.
7. After confirming the new path works, disable unsigned uploads for `youthverse_readsonyv` in Cloudinary Console if no other workflow needs it.

The function never accepts client-supplied public IDs or signatures. Cloudinary account-level upload limits and usage monitoring remain necessary.

## Build

Open this directory in Android Studio or import it into the Android build environment used by Google AI Studio. The repository archive did not include `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`, and this workspace has no Android SDK/Gradle installation, so a successful APK build has not yet been verified here. Keep the existing AI Studio signing configuration intact.

## Phase 2 changes

- Admin UI affordances now resolve the server-issued `admin` custom claim asynchronously; the email address is only the bootstrap identity for the one-time trusted claim script.
- Library status updates preserve existing favorite/bookmark flags unless the caller explicitly changes them.
- Library/history observation and mutations reject mismatched user IDs; reading-progress values and library status values are validated before writes. Firestore Rules remain the final access boundary.
- Chapter create/update/delete operations now update the published chapter counter in a Firestore transaction, avoiding the prior unawaited counter writes and keeping chapter changes atomic.
- Comment and review inputs are validated client-side before submission as well as by Firestore Rules.
- Comments and reviews are readable/writable only for published and visible parent content (or by an admin), and their content reference cannot be changed after creation.
- Firestore emulator tests use ES module imports consistently with the repository's `package.json` module mode; tests cover social access to unpublished and published works.

## Current status

These updates harden selected authentication, data-integrity, and social-access flows. Static checks passed for JSON configuration, XML resources, Node script syntax, package/config alignment, admin-claim wiring, and the updated rules/repository invariants. The Firestore emulator tests have not been executed because dependencies/emulator are not installed in this workspace. The APK has not been built because Gradle, the Gradle wrapper JAR/scripts, and Android SDK are not available here. Remaining work includes full compile/test validation in Android Studio/AI Studio, review of all repository/UI flows, auth-provider configuration and SHA fingerprints, App Check enforcement, reliable signed media uploads, pagination/search strategy, notification delivery, and release signing/Play Console checks.


## Phase 3 — Android package identity alignment

- Aligned the Gradle `namespace` with the configured `applicationId` and supplied Firebase Android client package: `com.aistudio.youthverse.rvzkmq`.
- Migrated Kotlin package declarations, imports, and source/test directory paths from the legacy `com.example` namespace.
- Added `npm run verify:android-package` to detect drift between Gradle, Firebase client configuration, and Kotlin source packages.

### Phase 3 validation

Run `npm run verify:android-package` from the project root. This is a static configuration check only; it does not replace an Android Gradle build or Firebase emulator tests.

### Still blocking production release

- The Cloudinary upload preset is unsigned. Checking a Firebase custom claim in the Android client is a useful UI guard but is bypassable by a modified client. A trusted signed-upload endpoint (or equivalent Cloudinary upload restrictions) is still required before production.
- The archive does not contain Gradle wrapper scripts/JAR, and this environment has no Android SDK or Gradle executable. The APK/AAB has not been built here.
- Firestore emulator tests, Firebase Auth provider/SHA setup, App Check enforcement, and release signing remain unverified.


## Phase 4 — Trusted Cloudinary upload authorization

- Added callable function `createCloudinaryUploadSignature`, requiring Auth, admin custom claim, and App Check.
- Added Secret Manager bindings for Cloudinary API key/secret.
- Replaced Android unsigned upload with signed upload flow.
- Added Play Integrity App Check in release and Debug App Check in debug builds.
- Added local unit tests for signature canonicalization and input allowlisting.

### Validation status

Local helper tests and syntax checks are intended to run with `npm run test:functions`. Function deployment, App Check enforcement, Cloudinary credentials, billing activation, live uploads, Firestore emulator tests, and Android build have not been performed in this workspace.

## Phase 5 — Authentication and account lifecycle hardening

- Google Credential Manager flow now handles absent Activity contexts, missing Web client IDs, unsupported credentials, user cancellation, coroutine cancellation, and Firebase failures explicitly.
- Silent sign-in failures do not block the user from using the interactive sign-in screen.
- Auth errors shown to readers are mapped to safe guidance; raw Firebase exception messages are not exposed in the UI.
- Existing profile display name/email/photo URL are synchronized from the signed-in Firebase identity when available. The sync never writes `role`, `uid`, or `createdAt`.
- Preferred language accepts only `my` or `en`.
- Sign-out is protected against repeated taps while the credential state and Firebase session are cleared.

### Phase 5 limitations
- Google is the only implemented authentication provider. Other providers are not enabled by this source change.
- Account deletion is intentionally not offered yet. A production deletion workflow must use a trusted backend to delete nested library/history and user-owned social records, and must require recent-login reauthentication.
- Verify OAuth provider enablement, web client ID, package/SHA fingerprints, and App Check in Firebase Console before testing real sign-in.

## Phase 6 — Safe content deletion

Cloud Functions runtime is Node.js 22. Do not revert it to Node.js 20: Node.js 20 was decommissioned for Cloud Run functions on 2026-10-30.

Content and user profile documents cannot be deleted directly by Android clients. Content deletion must use the `deleteContentCascade` callable in `us-central1`. The callable requires Firebase Authentication, the server-issued `admin: true` custom claim, and App Check. It removes comments, reviews, matching user library/history references, and the content document with nested chapters. Deploy Functions and Firestore Rules before distributing the Android build that calls this function:

```bash
npm --prefix functions install
npm --prefix functions test
firebase deploy --project youthverse-yadanarlynn --only functions
firebase deploy --project youthverse-yadanarlynn --only firestore:rules
```

The callable does not delete the corresponding Cloudinary assets yet. Account deletion is also blocked at the Firestore client layer until a trusted account-cleanup callable is implemented. Review billing, App Check configuration, IAM, and the target Firebase project before deployment.

Run Phase 6 static checks with `npm run verify:phase6`.

## Phase 8 source corrections

The current archive fixes the malformed Kotlin declaration in `ProfileScreen.kt`, removes accidentally duplicated legacy `com.example` source trees, updates KSP to 2.3.10 for AGP 9 compatibility, redacts account details from Firestore error logs, and moves Cloudinary URI reads off the UI thread. See `PHASE8_FIX_REPORT.md` for the verification record and remaining build blockers.

### Local debug signing key

The debug build type uses a temporary `debug.keystore` in the project root. GitHub Actions creates one automatically and does not upload it as source. For a local build, generate a temporary key from the project root with:

```bash
keytool -genkeypair -v \
  -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

Do not commit this file. It is only a debug signing key and must not be reused as the Play Store upload or app-signing key.
