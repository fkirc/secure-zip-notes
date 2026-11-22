# Secure Zip Notes v2 — Specification (draft for review)

Status: **proposal, nothing implemented yet.** Felix defines the spec; everything below is a proposal to review, accept, or strike. Open questions are collected in [section 10](#10-open-questions-for-felix) and marked inline as **(Q1)**, **(Q2)**, etc.

The v1 Android app lives in [`legacy/`](legacy/) and serves as the reference for the file format, features, and test cases.

---

## 1. Goals and non-goals

### Goals

1. **Offline web app.** v2 ships as a downloadable zip containing only static HTML/JS (and CSS). The user extracts it and opens `index.html` straight from disk (`file://`) in a desktop or mobile browser. No server, no installation.
2. **Zero network requests.** The app never makes a request, at runtime or at load time. This is enforced technically (CSP, section 6) and verified by tests (section 8).
3. **Same file format as v1.** Notes stay in a standard AES-256 encrypted Zip file (WinZip AE-2), which 7-Zip, WinZip, The Unarchiver, and similar tools can open. A vault written by v1 opens in v2, and a vault written by v2 opens in 7-Zip. This keeps the v1 promise that data can still be decrypted in 50 years.
4. **Minimal supply-chain attack surface.** No runtime dependencies at all. As few dev dependencies as possible, each one justified (section 4).
5. **TypeScript**, compiled to plain JavaScript; **pnpm** as package manager.
6. **Recreated test suites**: unit, integration, and end-to-end (section 8).

### Non-goals

- **No cloud sync.** Dropbox and Google Drive integration are removed. Syncing and backups are the user's job: they keep the `.zip` file in whatever folder they like (Dropbox, Syncthing, USB stick, ...).
- No mobile app and no app-store builds.
- No hosted version, no service worker, no PWA install.
- No accounts, telemetry, crash reporting, or ads (v1 had Crashlytics and banner ads; both are gone).
- No rich text. Notes are plain UTF-8 text, as in v1.

---

## 2. Features (carried over from v1, minus sync)

| Feature | v1 | v2 proposal |
|---|---|---|
| Create a vault with a new master password (min. 8 characters, confirm step) | yes | yes |
| Generate a random password during setup | yes | yes, via `crypto.getRandomValues` |
| List notes (entries of the zip, including names with `/` subpaths) | yes | yes |
| Open / view / edit a note | yes | yes |
| Add a note (default name "Note N", unique) | yes | yes |
| Rename a note (reject duplicates and empty names) | yes | yes |
| Delete a note (with confirmation) | yes | yes |
| Import an existing AES zip | yes | yes ("Open vault") |
| Export the zip | yes | yes ("Save vault"), see section 5 |
| Entries encrypted with different passwords in the same zip | yes | yes, ask for the password per entry when the cached one fails |
| Remember master password via hardware keystore / biometrics | yes | **no**: the password lives only in memory while the tab is open **(Q4)** |
| Dropbox / Google Drive sync | yes | **removed** |
| Search across open notes (v1 backlog) | backlog | optional **(Q7)** |

---

## 3. File format

Identical to v1 (Zip4j settings in `legacy/app/src/main/java/com/ditronic/securezipnotes/zip/CryptoZip.kt`):

- Container: standard Zip (PKWARE APPNOTE), no Zip64 needed for writing.
- Encryption: WinZip AES, **AE-2**, AES-256 (strength byte `0x03`), compression method 99 with AES extra field `0x9901`.
- Key derivation: PBKDF2-HMAC-SHA1, **1000 iterations** (fixed by the WinZip spec, not configurable), 16-byte random salt per entry, 2-byte password verifier.
- Cipher: AES-256-CTR with WinZip's **little-endian** counter starting at 1.
- Authentication: HMAC-SHA1 over the ciphertext, truncated to 10 bytes.
- Compression when writing: **STORE** (no compression), as in v1.
- Compression accepted when reading: STORE and DEFLATE. BZIP2, DEFLATE64, LZMA, and PPMD are rejected with a clear message (same as v1's import rules).
- Rejected on import, as in v1: non-encrypted entries, ZipCrypto entries, directory-only entries, empty file names, non-zip files (e.g. `.7z`).
- Text encoding: UTF-8.

**Security note for review.** The WinZip format caps PBKDF2 at 1000 iterations of SHA-1, which is weak by today's standards against offline brute force. That is the price of 7-Zip compatibility. The spec should state plainly that a long random password is the defence. A stronger, non-standard format would break goal 3, so this proposal keeps the standard format **(Q5)**.

### Implementation without crypto/zip libraries

Everything is built on browser primitives, with no npm packages:

| Need | Built-in API |
|---|---|
| PBKDF2-HMAC-SHA1 | `crypto.subtle.deriveBits` |
| HMAC-SHA1 | `crypto.subtle.sign` |
| AES-256 block cipher | `crypto.subtle` AES-CTR (see note) |
| Random salt / passwords | `crypto.getRandomValues` |
| DEFLATE (read only) | `DecompressionStream('deflate-raw')` |
| CRC-32 | ~20 lines of in-house TS |
| Zip read/write | in-house TS module, ~400–600 lines, scoped to what we need |

Note on AES-CTR: WebCrypto's AES-CTR increments the counter big-endian, while WinZip increments it little-endian. The two agree only within one block. The plan is to generate the keystream by calling AES-CTR on each 16-byte counter block (or on runs of blocks where the low counter byte doesn't wrap). Notes are small, so this cost is negligible. Unit tests check this against the v1 fixtures and against 7-Zip output.

---

## 4. Tech stack and dependencies

### Runtime dependencies: **none**

The shipped zip contains only code from this repository. No React, no UI library, no crypto library, no zip library.

### Framework: drop React (recommendation)

| Option | Packages | Pros | Cons |
|---|---|---|---|
| **A. Vanilla TS + tiny in-house view helper** (recommended) | 0 | Zero runtime supply chain; every shipped line is ours and reviewable; small bundle | We write a ~100-line `h()`/render helper; less ergonomic for big UIs |
| B. Preact | 1 (no transitive deps) | Declarative, React-like, ~4 KB | One third-party package in the shipped bundle |
| C. React + ReactDOM | 3+ (`react`, `react-dom`, `scheduler`) | Familiar | Larger bundle and surface, no benefit at this app size |

The UI is about four screens: setup, note list, editor, and password prompt. That's small enough that a declarative-style pattern (state → `render()` → DOM) works fine in plain TS without a library. This follows the v1 deprecation notice's preference for declarative UI without pulling in a dependency. **(Q1)**

### Dev dependencies (build and test only, never shipped)

| Package | Purpose | Transitive packages | Justification |
|---|---|---|---|
| `typescript` | Type checking (`tsc --noEmit`, strict mode) | 0 | Required by the TS requirement; Microsoft, zero deps |
| `esbuild` | Bundle TS into one classic (non-module) script, inlined into `index.html` | 1 platform binary (e.g. `@esbuild/linux-x64`) | Needed because `file://` pages can't load ES modules in Chromium (CORS). Zero JS deps, single Go binary |
| `@types/node` | Types for build scripts and `node:test` | 1 (`undici-types`, types only) | Types only, never executed |
| `@playwright/test` | E2E tests in real Chromium, Firefox, WebKit | 2 (`playwright`, `playwright-core`) | All Microsoft-owned, no third-party deps; the only practical way to test the real `file://` app across browsers |

That's **4 direct dev dependencies and about 8 installed packages in total**, versus several hundred for a typical Vite + React + Vitest + ESLint setup.

Deliberately **not** used:

- **Vitest / Jest**: replaced by Node's built-in `node:test` runner. Node ≥ 22.18 runs `.ts` files directly via type stripping, and Node ships WebCrypto and `DecompressionStream`, so the crypto and zip code can be unit-tested in Node with zero test dependencies.
- **Vite / Webpack / Rollup**: esbuild alone covers it.
- **ESLint / Prettier**: large dependency trees (ESLint) or optional (Prettier). We rely on `tsc --strict` plus extra checks (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `erasableSyntaxOnly`). Prettier can be added later as a single zero-dep package if wanted **(Q2)**.
- **Any zip/crypto library** (JSZip, zip.js, fflate, ...): see section 3.

Alternative with even fewer packages: replace `esbuild` with `tsc` plus a small in-house script that concatenates the output. That's possible but brittle. esbuild is worth its 2 packages **(Q3)**.

### pnpm and supply-chain hardening

- `pnpm-lock.yaml` committed; CI runs `pnpm install --frozen-lockfile`.
- Exact versions in `package.json` (no `^` or `~`); `save-exact=true` in `.npmrc`.
- `packageManager` field in `package.json` pins the pnpm version (with hash), used via Corepack.
- Lifecycle scripts blocked (the pnpm 10 default). `esbuild` is allow-listed only if its postinstall turns out to be needed; it normally works through its optional platform package without one.
- `minimumReleaseAge` in `pnpm-workspace.yaml` (e.g. 7 days), so a freshly published, possibly compromised version is never picked up.
- Dependency updates are manual and reviewed (no auto-merge bots). Each update PR shows the lockfile diff.
- Node version pinned via `.nvmrc` / `engines`.

---

## 5. Runtime architecture

### Where data lives

The **zip file on the user's disk is the single source of truth.** The app keeps decrypted notes only in memory.

- **Open:** `<input type="file">`, which works in every browser on `file://`.
- **Save:**
  - Baseline (all browsers): download the updated zip via a Blob URL, so the user saves or overwrites the file.
  - Enhancement (Chromium): File System Access API (`showOpenFilePicker` / `createWritable`), so the opened file is saved in place with a "Save" button. Feature-detected and never required.
- **Unsaved changes:** a visible "unsaved changes" indicator plus a `beforeunload` warning.
- **No persistent browser storage** of note content or passwords (no localStorage or IndexedDB). On `file://` this storage is unreliable anyway, and its origin is shared with other local files in some browsers **(Q6)**.

### Password handling

- The master password is kept in memory for the session to decrypt and encrypt entries. It is never written anywhere.
- Optional auto-lock after N minutes idle, which clears the password and decrypted notes from memory **(Q4)**.
- Wrong password is detected via the 2-byte verifier and confirmed via the HMAC. A failed HMAC is reported as "file corrupted or wrong password", never shown as garbage text.

### Saving entries

v1 rewrote an entry by removing and re-adding it, which could lose data. v2 always builds a complete new zip in memory and only then hands it to the save path, so an error can never leave a half-written vault.

### Module layout (proposed)

```
src/
  crypto/    pbkdf2, aes-ctr-le, hmac, winzip-aes (encrypt/decrypt entry)
  zip/       reader, writer, crc32, inflate (DecompressionStream wrapper)
  vault/     Vault model: list/add/rename/delete/update, multi-password handling, import validation
  ui/        h() helper, screens (setup, list, editor, password prompt), styles
  main.ts    wiring
scripts/
  build.ts   esbuild bundle → inline into dist/index.html → write release zip + SHA-256
tests/
  unit/        node:test, *.test.ts
  integration/ node:test, fixtures + 7-Zip interop
  e2e/         Playwright, *.spec.ts
  fixtures/    copied from legacy/app/src/androidTest/assets/
```

`crypto/`, `zip/`, and `vault/` are pure TS with no DOM access, so they run unchanged in Node for tests.

---

## 6. No-network guarantee

1. **CSP** in `index.html` via `<meta http-equiv="Content-Security-Policy">`:
   `default-src 'none'; script-src 'sha256-<hash of inlined bundle>'; style-src 'sha256-<hash>'; img-src data: blob:; connect-src 'none'; form-action 'none'; base-uri 'none'`.
   The build script computes the hashes. `connect-src 'none'` blocks `fetch`, XHR, WebSocket, and EventSource. `default-src 'none'` blocks external scripts, fonts, and images.
2. **Single self-contained `index.html`**: JS and CSS are inlined, so there's nothing to load, not even local files. This also avoids the `file://` module/CORS problems.
3. **No external URLs in the source**: a build check fails if the bundle contains `http://` or `https://` (except an allow-list such as the license text).
4. **E2E test**: Playwright records every request while running all user flows and asserts that none were made except the initial `file://` load.

---

## 7. Build and release

- `pnpm build` → `dist/index.html` (single file), plus `dist/secure-zip-notes-v2-<version>.zip` containing `index.html`, `LICENSE`, `README.txt`, and `SHA256SUMS`.
- Reproducible build: same commit gives byte-identical output (fixed timestamps in the zip, no build-time randomness). A CI job builds twice and compares.
- Released through GitHub Releases with the SHA-256 checksum and a GitHub artifact attestation, so users can verify the download.
- Minimum browsers: current Chrome/Edge, Firefox, Safari (desktop and mobile). The baseline needs WebCrypto, `DecompressionStream('deflate-raw')` (Chrome 103+, Firefox 113+, Safari 16.4+), and `<input type=file>`.

---

## 8. Test plan

All suites run in GitHub Actions on every push to `master` (the v1 workflow moved to `legacy/` and no longer runs).

### Unit tests (`node:test`, zero deps)

- CRC-32 against known vectors.
- PBKDF2-HMAC-SHA1 against RFC 6070 vectors.
- WinZip little-endian AES-CTR against vectors from the WinZip AE spec and the v1 fixtures.
- Entry encrypt → decrypt round trip; tampered ciphertext → HMAC failure; wrong password → verifier failure.
- Zip writer/reader round trip: names with `/`, UTF-8 names and content, empty note, large note (e.g. 1 MB).
- Vault logic: default name generation ("Note N" skipping taken names), duplicate and empty rename rejection, delete last note, multi-password lookup.
- Password generator: length and alphabet; minimum-length validation.

### Integration tests (`node:test` + fixtures + system `7z`)

- **v1 fixtures** (copied from `legacy/app/src/androidTest/assets/`): `singlenote.aeszip`, `4notes.aeszip`, `subdirs.aeszip`, `twopasswords.ZIP`, `4passwords_subdirs.aeszip` must open with the same names and contents that v1's tests expect.
- **Compression fixtures**: `100a_flat`, `100a_deflate`, `100a_deflate_fast`, `100a_deflate_ultra` decode to `100a.txt`; `100a_bzip2`, `100a_deflate64`, `100a_lzma`, `100a_ppmd` are rejected with the same error messages as v1.
- **Invalid fixtures**: `emptyzip.zip`, `unencrypted_note.zip`, `broken_zipcrypto.zip`, `emptynote.7z` are rejected with v1's messages.
- **7-Zip interop (both directions):** a vault written by v2 is extracted by `7z x -p<pw>` with matching content; a zip created by `7z a -tzip -mem=AES256 -p<pw>` opens in v2. `7z` (p7zip) is an apt package on the CI runner, not an npm dependency.

### End-to-end tests (Playwright, Chromium + Firefox + WebKit, loading `dist/index.html` via `file://`)

These port the v1 Espresso tests (`legacy/app/src/androidTest/.../tests/`):

- Setup: create a new password, password too short, password mismatch, generate a random password (`SetupTests.kt`).
- Notes: add multiple notes, add with naming conflict, rename from the list and from an open note, invalid renames, delete multiple, delete last note, export an empty note (`ITest.kt`).
- Import: every fixture case from `ImportTests.kt`.
- Multi-password: two and four passwords (`MultiPasswordTests.kt`).
- New for v2: save via download and re-open gives identical notes; the unsaved-changes warning; **no network requests during any test** (section 6); CSP present and enforced (a probe `fetch` must fail); auto-lock if accepted **(Q4)**.
- `SyncTests.kt` (Dropbox) is dropped.

---

## 9. Repository layout and workflow

- v2 lives at the repository root (`package.json`, `src/`, `tests/`, ...); v1 stays untouched in `legacy/`.
- Work is pushed directly to `master` for now (no feature branches), per Felix.
- CI: `pnpm install --frozen-lockfile` → `tsc --noEmit` → unit → build → integration → e2e → reproducibility check.

---

## 10. Open questions for Felix

1. **Q1 — Framework:** vanilla TS with a tiny in-house view helper (recommended), or Preact, or React?
2. **Q2 — Formatter/linter:** none (rely on strict `tsc`), or add Prettier (1 package, zero deps)?
3. **Q3 — Bundler:** accept `esbuild` (2 packages), or go to the extreme of `tsc`-only with an in-house concatenation script?
4. **Q4 — Password lifetime:** keep the password in memory until the tab closes, or auto-lock after an idle timeout (how many minutes)? Any wish for a "remember on this device" option (I'd argue against it)?
5. **Q5 — Crypto strength:** keep the standard WinZip AES format (1000× PBKDF2-SHA1, 7-Zip compatible), or offer an optional stronger mode that breaks third-party compatibility? Recommendation: standard only.
6. **Q6 — Save model:** is download-to-save as the baseline plus in-place save on Chromium acceptable? Should "recent file" handles be remembered on Chromium (that needs IndexedDB, which only stores a file handle, not content)?
7. **Q7 — Scope additions:** include note search in v2.0, or later?
8. **Q8 — Single vs multi-password vaults:** v1 can *read* zips with entries under different passwords. Should v2 also let users *create* entries with a different password, or always use the master password for new entries (as v1 did)?
9. **Q9 — Target devices:** desktop only, or must it also work well on mobile browsers (Android/iOS file handling from a local `index.html` is clumsy, especially on iOS)?
10. **Q10 — Distribution:** GitHub Releases only, or also a hosted copy (e.g. GitHub Pages) for people who don't want to download? A hosted copy weakens the "never online" story, so the proposal says no.
