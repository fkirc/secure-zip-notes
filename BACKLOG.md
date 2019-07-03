This file contains a list of potential tasks and features for the continous development of Secure Zip Notes.

- zip4j adaptions to make upstream merges easier, remove unnecessary modifications

- Translations for other languages, including the upper part of the README (which is the play store text).

- Tests: UI tests with Espresso, reaching a reasonable code coverage.

- Replace `createConfirmDeviceCredentialIntent` with the new `BiometricPrompt` API. This might provide a better user experience and is suited for future Android versions.

- Simple search functionality for open notes.

- Potential time stamp bug: Keep upstream time stamps after downloading from Dropbox/Google Drive.

- Option to use a pseudo keyboard for typing the master password, that is, a set of buttons that aims to protect users against keylogging malware.

- Password autofill with the autofill framework starting from Android 8.
