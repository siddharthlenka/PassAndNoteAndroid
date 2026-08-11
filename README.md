<p align="center">
  <img src="app/src/main/res/drawable-nodpi/passnote_icon.png" width="128" alt="Pass&amp;Note Vault app icon">
</p>

# Pass&Note Vault for Android

Pass&Note Vault is a privacy-focused, local-first Android vault for passwords and secure notes. It works without an account or app-level internet access and stores vault records in an encrypted SQLCipher database on the device.

Website: [passandnote.com](https://passandnote.com)

> This repository is under active development and has not undergone an independent security audit.

## Features

- Password records with account name, optional username, secure password generation, copy, selection, deletion, and encrypted import/export
- Secure notes with autosave, generated or manually renamed titles, deletion, encrypted import/export, and optional plain-text export
- Full encrypted vault backup, restore, and merge-import
- Master Password unlock and optional Class 3/strong biometric quick unlock
- Configurable auto-lock and clipboard clearing
- Recent-apps preview protection and an in-app privacy shield
- Local-only operation with no required account, automatic cloud sync, analytics, advertising, or tracking SDK

## Security architecture

- Every new or restored vault receives a random 32-byte database key generated with `SecureRandom`.
- Passwords and notes are stored in a SQLCipher 4.17 encrypted database at the app-private `noBackupFilesDir/PrivateVault/vault.sqlite` path.
- The Master Password is processed using PBKDF2-HMAC-SHA256 with a random 32-byte salt and 600,000 iterations.
- The derived key wraps the random database key using AES-256-GCM; the Master Password is not stored by the app.
- The resulting key envelope is additionally encrypted with an AES-256-GCM key held in Android Keystore.
- Biometric Quick Unlock protects a separate copy of the vault key with an authentication-per-use Android Keystore key. It accepts only strong/Class 3 biometrics and is invalidated when biometric enrollment changes.
- Android application backup is disabled, and the database is kept in Android's no-backup app storage.
- The app does not request the Android internet permission.

SQLCipher encrypts the database as a whole. Individual database fields are not separately encrypted.

### Why this project does not use GRDB

GRDB is a Swift database library for Apple platforms. This Android implementation uses SQLCipher directly from Kotlin—without Room—to keep control of the SQL schema and remain aligned with the Pass&Note Vault iOS data model and encrypted export contract.

## Backup formats

| Extension | Contents | Protection |
|---|---|---|
| `.pnpass` | One or more password records | AES-256-GCM encrypted |
| `.pnnote` | One or more secure notes | AES-256-GCM encrypted |
| `.pnvault` | Complete password and note backup | AES-256-GCM encrypted |
| `.txt` | One secure note | Unencrypted plain text |

Encrypted exports use PBKDF2-HMAC-SHA256 with a random salt and 600,000 iterations. They require the password that protected the export when it was created. The format is designed to match the Pass&Note Vault iOS export contract; cryptographic compatibility vectors are covered by unit tests, but a complete cross-platform round-trip suite is still recommended before production release.

A restored vault receives a new local database key and may use a new local Master Password. Importing into an existing vault adds records and may create duplicates. Encrypted imports are limited to 25 MiB.

> Treat exported `.txt` notes as sensitive files. They are not protected by Pass&Note Vault encryption.

## Requirements

- Android Studio with Android SDK 37
- Android 7.0 or newer (API 24+)
- JDK 25 for the configured Gradle daemon toolchain
- A physical Android device or emulator for installation and connected tests

The Gradle wrapper and Foojay resolver configuration can provision the configured daemon JVM where supported. Android Studio's bundled runtime can also be selected as `JAVA_HOME`.

## Build

Open the repository in Android Studio and let Gradle sync, or build from a terminal:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Run on a physical device

Enable USB or wireless debugging, then list connected devices:

```bash
adb devices -l
```

Install the APK on one specific device and launch it:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.passandnote.vault/.MainActivity
```

You can also select the physical device in Android Studio and click **Run**.

## Tests and checks

Run the same checks used during development:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Run connected Android tests when a test device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The JVM suite includes CryptoKit-compatible PBKDF2/AES-GCM vectors and biometric-operation serialization tests. The current connected test performs a basic application-context/package check.

## Project structure

```text
app/src/main/java/com/passandnote/vault/
├── MainActivity.kt
├── VaultAppState.kt
├── backup/          # Encrypted import/export contract
├── data/            # SQLCipher database, models, and local metadata
├── security/        # Key derivation, key envelopes, and Keystore integration
└── ui/              # Jetpack Compose screens, components, and theme
```

Core technologies:

- Kotlin and Jetpack Compose
- SQLCipher for Android 4.17
- AndroidX Biometric
- Android Keystore
- Gradle 9.5, Android Gradle Plugin 9.3.1, and Kotlin 2.2.10

## Privacy

The current manifest requests only biometric permission and does not request internet access. There is no account system, automatic cloud sync, analytics SDK, or advertising SDK in the current build.

Imports and exports use Android's system document picker. You control the selected destination, which may include a third-party or cloud-backed document provider installed on the device.

## Important limitations

- Forgotten Master Passwords cannot be recovered.
- Biometric Quick Unlock is a convenience feature, not a recovery method.
- There is no automatic cloud sync, account recovery, browser extension, or autofill service.
- Rooted or otherwise compromised devices can weaken application security.
- Debug APKs are debuggable and must not be distributed as production builds.
- Release signing and Google Play publishing configuration are not included.
- Release optimization/minification is currently disabled.
- Security-sensitive changes should receive review and broader physical-device and interoperability testing before production release.

## Application identity

- Application ID: `com.passandnote.vault`
- Version name: `1.0`
- Version code: `1`
- Minimum Android version: Android 7.0 / API 24

## License

No open-source license is currently included. Until a license is added, copyright law reserves reuse and redistribution rights to the repository owner.
