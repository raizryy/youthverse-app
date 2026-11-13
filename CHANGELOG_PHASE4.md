
# YouthVerse Phase 4 — Trusted Cloudinary Upload Authorization

## Implemented
- Added callable function `createCloudinaryUploadSignature`.
- Requires Firebase Auth, server-issued `admin: true` claim, and App Check.
- Generates public IDs server-side and signs an allowlisted folder, public ID, and timestamp.
- Reads Cloudinary credentials from Firebase Functions Secret Manager.
- Replaced Android unsigned-preset uploads with signed uploads.
- Initializes Debug App Check for debug builds and Play Integrity App Check for release builds.
- Added signature helper unit tests and setup/deploy documentation.

## Blockers / not verified
- Cloudinary API credentials have not been configured in Secret Manager.
- Firebase Functions not deployed; Blaze billing may be required.
- App Check Play Integrity registration and debug-token registration are user-side actions.
- No live signed upload was attempted.
- Android build and Firestore emulator tests were not run in this environment.
