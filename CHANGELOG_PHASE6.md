## Phase 6 — Project audit and data-integrity hardening

- Added the trusted `deleteContentCascade` callable. It requires Firebase Authentication, the server-issued `admin: true` custom claim, and App Check.
- Content deletion now removes top-level comments/reviews, user library/history references, and the content document with its nested chapter subcollection using the Admin SDK.
- Firestore Rules now block direct client deletion of content and user profile documents until safe server-side cleanup workflows are used.
- Android `ContentRepository.deleteContent` now calls the trusted backend function instead of deleting only the parent document.
- Admin Dashboard now prevents repeated delete taps and displays a failure message instead of silently closing the confirmation dialog.
- Added Phase 6 static verification and Firestore rule tests for blocked direct deletion.
- Added `firebase-admin` as an explicit Cloud Functions dependency.
- Updated the Cloud Functions runtime from Node.js 20 to Node.js 22 because Node.js 20 reached its Cloud Run functions decommission date on 2026-10-30.

### Phase 6 validation status

- Phase 6 static checks: 11/11 passed.
- Existing Phase 4 and Phase 5 static checks: passed.
- Cloudinary signature unit tests: 3/3 passed.
- JavaScript syntax checks: passed.
- Firebase Emulator rule tests, Cloud Functions deployment, Android Gradle build, device runtime tests, and live end-to-end deletion remain unverified in this environment.
- Cloudinary files are not deleted by the Firestore cascade; orphaned media cleanup remains a separate follow-up task.
