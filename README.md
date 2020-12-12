# Secure Zip Notes
Securely store notes within an encrypted Zip file.

<a href="https://play.google.com/store/apps/details?id=com.ditronic.securezipnotes" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="40"/></a>

<a href="https://github.com/fkirc/secure-zip-notes/actions?query=branch%3Amaster"><img alt="CI status" src="https://github.com/fkirc/secure-zip-notes/workflows/Tests/badge.svg/?branch=master"></a>

You do not trust bloated password managers with undocumented file formats?
You want to retain 100% control over your data?
Then Secure Zip Notes is your solution.

## Features
- View and edit encrypted text files on any platform, using password-protected Zip files.
- Sync with Google Drive or Dropbox.
- Uses hardware-protected storage to avoid retyping the master password every time.
- Open Source: Fetch this app from github if you do not trust us.

Our top priority is not only security and privacy, but also long-term stability.
We take the responsiblity to retain your data seriously.
Secure Zip Notes guarantees that you can easily decrypt your data in 50 years even if DiTronic Apps ceases to exist.

This app only supports text notes.
If you are seeking advanced features like auto-fill passwords, then we recommend an app like Keepass2Android.

## Technical details
- Supported independent programs: 7-Zip, WinZip, The Unarchiver (macOS), Gnome Archive Manager
- Encryption: AES-256 Counter Mode + HMAC-SHA1
- Key derivation: PBKDF2

Not all PC operating systems support Zip files with AES encryption by default.
Therefore, you might need to install a PC software like 7-Zip.
_____________________________________________________________________

## Simple File Sync for Android
The [SimpleFileSync](simplefilesync/) library is maintained as a separate module within this repository.

## Tech-Stack Switch

Due to unhappiness with native development tools, I shifted my focus from native apps towards cross-platform apps with Capacitor/React/TypeScript.
Nevertheless, this project remains in maintenance.

## Attributions
This app uses a modified version of the Zip4j library (Apache License 2.0).

