# YouthVerse Phase 5 — Authentication & Account Lifecycle Hardening

## Implemented
- Hardened Google Credential Manager sign-in against non-Activity contexts and missing web client IDs.
- Silent sign-in now treats cancellation/no-authorized-account as a non-fatal outcome and propagates coroutine cancellation.
- Interactive sign-in verifies credential type and Firebase sign-in result; user-facing errors are mapped to safe, actionable messages rather than exposing raw exception text.
- Existing Firebase profile metadata (display name, email, photo URL) is synchronized from the authenticated Firebase user when changed; role/UID/createdAt are not changed by this sync.
- Preferred language writes now validate the supported values (`my`, `en`) before Firestore.
- Sign-out UI disables repeat taps while credential state is cleared and Firebase Auth signs out.

## Not implemented / blockers
- Email/password, phone, Apple, and account-linking providers are not configured; Google remains the only implemented provider.
- Account deletion is not exposed because a safe deletion flow needs a trusted backend to delete user-owned subcollections and dependent social records, plus recent-login reauthentication.
- Firebase Console OAuth configuration, SHA-1/SHA-256 fingerprints, consent screen, App Check and live Google sign-in have not been verified in this workspace.
- Android compile/UI tests were not run because Android SDK and Gradle wrapper scripts/JAR are unavailable here.
