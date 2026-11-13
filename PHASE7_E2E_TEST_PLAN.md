# YouthVerse Phase 7 — Manual Android End-to-End Test Plan

This checklist is for a disposable Firebase test project and a debug APK. Do not use production content or a production admin account.

## Preconditions

- GitHub Actions has completed `:app:assembleDebug`, Firestore Rules tests, and Android repository emulator tests.
- Install the `youthverse-debug-apk` artifact on a physical Android device.
- Confirm the APK points to the intended Firebase test project and that its Firestore Rules / Functions are deployed there.
- Create one ordinary test account and one separate account with the server-issued `admin: true` custom claim. Never grant admin by editing a client profile document.
- Use test content and test Cloudinary folders only.

## Test sequence

1. **Login**
   - Sign in with the ordinary test account.
   - Verify success, profile creation/sync, and persistence after force-close/reopen.
   - Verify invalid credentials and cancelled Google sign-in show safe errors and do not crash.
2. **Home**
   - Verify published/visible content loads from Firestore.
   - Verify loading, empty, and network-error states; no bundled sample content should appear as live content.
   - Open a content detail screen and confirm metadata and chapter counts.
3. **Reading**
   - Open a published novel chapter and verify text, scroll/page mode, and reader settings.
   - Open a published manga/manhwa chapter and verify ordered images, image loading, and vertical scrolling.
   - Navigate between chapters and confirm reader progress is saved.
4. **Library and History**
   - Add a content item to the library; change status, favorite, and bookmark state.
   - Read a chapter and verify history/progress appears.
   - Force-close and reopen; confirm the library/history state persists for the same user.
   - Sign in as another user and verify the first user's private library/history is not visible.
5. **Admin authorization and content management**
   - Sign in with the server-claimed admin test account and verify Admin Dashboard access.
   - Sign in as an ordinary user and verify admin actions are denied by the backend, not merely hidden by UI.
   - Create/update a test content item and chapter using the signed upload workflow.
   - Delete a disposable content item through the callable cascade function; verify Firestore chapters and dependent references are removed.
   - Note: Phase 6 cascade deletion does not remove Cloudinary assets; verify and clean test assets separately.
6. **Security and stability**
   - Confirm no admin secrets, service-account JSON, release keystore, or Cloudinary API secret is present in the APK/repository.
   - Test logout, app relaunch, denied permissions, and interrupted network requests.
   - Record device model, Android version, APK version, screenshots, and any Logcat stack traces.

## Result recording

Mark each step PASS/FAIL and attach the exact error text for failures. GitHub Actions emulator tests are not a substitute for this physical-device end-to-end run.
