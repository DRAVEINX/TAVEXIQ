# TAVEXIQ

TAVEXIQ is a standalone Android wallet MVP, separate from other DRAVEINX projects.

## First milestone
- Add, view and remove loyalty cards.
- Generate QR codes and Code 128 barcodes for saved loyalty codes.
- Keep card records in app-private local preferences.
- GitHub Actions builds a debug APK, release APK and AAB.

## Important limitations
- This is an early MVP, not a signed or Play-ready release.
- Do not store bank card numbers, PINs, CVVs or other payment credentials.
- OV-chipkaart/OVpay are not cloned or emulated. Any transit integration requires official provider support and authorization.
- Contactless bank payments require an approved wallet/tokenization partner, issuer support and applicable security certification.
- Before public release, implement encrypted local storage, privacy/export/delete flows, accessibility testing, and a proper release-signing setup.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. GitHub Actions uses Gradle 8.9 to build APK/AAB artifacts. Release outputs are unsigned until signing secrets and a release signing configuration are added.
