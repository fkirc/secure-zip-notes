# Secure Zip Notes
Securely store notes within an encrypted Zip file.

You do not trust bloated password managers with undocumented file formats?
You want to retain 100% control over your data?
Then Secure Zip Notes is your solution.

## Features:
- View and edit encrypted text files on any platform, using password-protected Zip files.
- Sync with Google Drive or Dropbox.
- Uses hardware-protected storage to avoid retyping the master password every time.
- Open Source: Fetch this app from github if you do not trust us.

This app only supports text notes.
If you are seeking advanced features like auto-fill passwords, then we recommend an app like Keepass2Android.

## Technical details:
- Supported independent programs: 7-Zip, WinZip, The Unarchiver (macOS), Gnome Archive Manager
- Encryption: AES-256 Counter Mode + HMAC-SHA1
- Key derivation: PBKDF2

Not all PC operating systems support Zip files with AES encryption by default.
Therefore, you might need to install a PC software like 7-zip.
_____________________________________________________________________

## Simple File Sync for Android:
The [SimpleFileSync](simplefilesync/) library is maintained as a separate module within this repository.

## Contributions:
Pull requests are always welcome.
You may also open an issue to ask for new functionality.
The [backlog](BACKLOG.md) contains a list of potential tasks and features for future releases of Secure Zip Notes.

## Copyright:
You are allowed to download this code for personal usage.
You must not redistribute this app or any modified version of this app in the Google Play Store or any other App Store.
The `simplefilesync` subfolder is licenced separately under the MIT license.

Attributions:
This app uses a modified version of the Zip4j library (Apache License 2.0).
