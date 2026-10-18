# TAVEXIQ

TAVEXIQ is a standalone Android wallet project by DRAVEINX. It is separate from other DRAVEINX projects.

## Current Android features

- Save and manage loyalty cards, including editing, search, favourites, duplicate prevention, and A–Z/newest-first sorting.
- Display saved loyalty codes as QR codes and Code 128 barcodes; hide codes by default and reveal them on demand.
- Store loyalty-card data locally with encryption backed by Android Keystore.
- Protect the identity-document metadata vault with device authentication (biometric or device credential where supported).
- Search and edit identity-document metadata, validate expiry dates, and show expiry status.
- Export and restore identity-document metadata using password-protected, encrypted backups. Backup format uses PBKDF2-HMAC-SHA256 key derivation and AES-256-GCM authenticated encryption.
- Confirm destructive actions, provide empty states, and expose TalkBack labels for key controls.
- GitHub Actions runs unit tests and builds debug APK, release APK, and Android App Bundle (AAB) artifacts.

## Important limitations

- A green CI build means automated checks and build tasks passed; it does **not** by itself prove real-device usability, security certification, Play policy compliance, or publication readiness.
- The current Gradle version is `0.1.0` (`versionCode 1`). No public GitHub Release is currently published.
- Release APK/AAB outputs are **unsigned** until a release-signing configuration and repository signing secrets are deliberately set up. Do not distribute an unsigned build as a production release.
- The identity vault stores **metadata only**. It does not store or issue official identity credentials, and it does not currently support ID scans/photos, BSN, or document-number storage.
- TAVEXIQ is not a government-approved digital identity, payment wallet, or universally accepted NFC credential.
- Do not store bank-card numbers, PINs, CVVs, or other payment credentials in the app.
- OV-chipkaart/OVpay are not cloned or emulated. Transit integration requires official provider support and authorization.
- Contactless bank payments require an approved wallet/tokenization partner, issuer support, and applicable security certification.

## Release readiness checklist

Before a public Google Play release:

1. Review and test the app on supported physical Android devices, including accessibility and device-authentication flows.
2. Complete privacy, data-retention, export, restore, and deletion reviews.
3. Configure secure release signing; keep keystore files and passwords out of Git and logs.
4. Set the intended version name/code and verify the release APK/AAB.
5. Complete Play Console declarations, store listing, privacy policy, and required testing tracks.
6. Create a GitHub Release only after the signed release artifact has been verified.

## Build locally

Open the project in Android Studio with JDK 17 and Android SDK 35. GitHub Actions uses Gradle 8.9 and runs:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

The CI workflow uploads debug APK, release APK, and AAB artifacts for 14 days. These CI artifacts are not automatically a signed production release.
