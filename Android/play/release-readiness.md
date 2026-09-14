# Android release readiness

## Complete

- Native Android feature port matching the released iOS app
- Package name: `com.versarepair.meloremote`
- Release version: `1.1` (`versionCode` 1)
- Target SDK 36 and compile SDK 37
- Unit tests, Android lint, debug APK, and release bundle build successfully
- Debug smoke tests on Pixel 9 emulator and Samsung SM-A205U
- Google Play app created under VERSAREPAIR LLC
- Store copy, 512 px icon, 1024 x 500 feature graphic, and five 9:16 phone screenshots prepared
- Default store listing copy and all seven assets uploaded; listing is ready to send for review
- AI declaration completed accurately: only the generated feature graphic is labelled
- Google Play price set to USD $0.99 with Google-generated local prices
- Ads declaration completed: app does not contain ads
- Government-app declaration completed: app is not a government app
- Financial-features declaration completed: app has no financial features
- Health declaration completed: app has no health features
- Store category set to Music & Audio
- Public Play Store contact set to `contact@versarepair.com` and
  `https://versarepair.com/app-development/support/` (phone left blank)
- Google Play initial setup progress: 8 of 13 tasks complete
- In-app Privacy Policy and Support links added to Settings
- Login now warns users when a server connection is not encrypted with HTTPS
- Compliance changes verified with unit tests, Android lint, and a debug APK build

## Required before uploading a production bundle

- Create and securely back up the Android upload keystore
- Configure the four `MELOREMOTE_UPLOAD_*` signing properties
- Run `verifyReleaseSigning` and build a signed release AAB
- Test the signed release against a real Music Assistant server

## Required in Google Play Console

- Publish the revised cross-platform privacy policy from `privacy-policy-update.md`
  at the existing privacy-policy URL before declaring that URL current
- Complete app access instructions for Play review
- Complete content rating, target audience, and data safety declarations
- Create an internal-test release, verify installation, then promote to production

Play Console declarations and pricing require the developer's confirmation because
they are public/legal representations. Reviewer access also requires a reachable
test server or other review instructions that Google can use.
