# Secure Zip Notes v2 — Specification (draft for review)

Status: **implemented in v2.0.0** (see [README.md](README.md)); where the implementation refined a detail, this spec was updated to match. Felix defines the spec. Felix answered the first round of open questions on 2026-11-22; the decisions are recorded in [section 11](#11-decisions-log) and already applied in the sections below.

The v1 Android app lives in [`legacy/`](legacy/) and serves as the reference for the file format, features, and test cases.

---

## 1. Goals and non-goals

### Goals

1. **Offline web app, two ways to distribute, one build:**
   - **Zip download:** a zip containing only static HTML/JS/CSS. The user extracts it and opens `index.html` straight from disk (`file://`). No server, no installation. Mainly for desktop.
   - **Progressive Web App (PWA):** the same build is hosted on GitHub Pages. Users install it with "Add to Home Screen" (Android and iOS) or "Install app" (desktop Chromium). After the first visit it runs fully offline through a service worker. This is the mobile path, with no App Store or Google Play involved (section 7).
2. **Zero network requests at runtime.** The zip variant never makes a request. The PWA makes only the browser's own service-worker update check against its origin; the app code itself never makes a request. This is enforced technically (CSP, section 6) and verified by tests (section 9).
3. **Same file format as v1.** Notes stay in a standard AES-256 encrypted Zip file (WinZip AE-2), which 7-Zip, WinZip, The Unarchiver, and similar tools can open. A vault written by v1 opens in v2, and a vault written by v2 opens in 7-Zip. This keeps the v1 promise that data can still be decrypted in 50 years.
4. **Minimal supply-chain attack surface.** No runtime dependencies at all. As few dev dependencies as possible, each one justified (section 4).
5. **TypeScript**, compiled to plain JavaScript; **pnpm** as package manager.
6. **Recreated test suites**: unit, integration, and end-to-end (section 9).
7. **Reproducible, transparent builds.** Every release is built by a public GitHub Actions pipeline. The build is fully deterministic: anyone who checks out the same tag and builds it gets byte-identical output and the same SHA-256 hashes. The hashes of every release are published (section 8).

### Non-goals

- **No cloud sync.** Dropbox and Google Drive integration are removed. Syncing and backups are the user's job: they keep the `.zip` file in whatever folder they like (Dropbox, Syncthing, USB stick, ...).
- No native mobile app and no App Store / Google Play builds. The PWA replaces them.
- No server-side code. GitHub Pages only serves the static files.
- No accounts, telemetry, crash reporting, or ads (v1 had Crashlytics and banner ads; both are gone).
- No rich text. Notes are plain UTF-8 text, as in v1.

---

## 2. Features (carried over from v1, minus sync)

| Feature | v1 | v2 |
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
| Single master password per vault | yes | yes. New and edited entries always use the master password, exactly like v1. No UI for creating entries with a different password |
| Reading imported zips whose entries use different passwords | yes | yes, same as v1: if the master password fails for an entry, ask for that entry's password (keeps v1's `MultiPasswordTests`) |
| Remember master password via hardware keystore / biometrics | yes | **no**: the password lives only in memory, with an idle auto-lock (default 60 minutes, user-configurable) |
| Install on the phone home screen | yes (Play Store) | yes, as a PWA, without any app store |
| Dropbox / Google Drive sync | yes | **removed** |
| Search | no (backlog) | **no**, dropped to keep v2 bare-bones |
| Automatic updates | Play Store | **never**: a user only gets a new version by installing it manually (section 7) |

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
- Password encoding: written as UTF-8 (what 7-Zip and WinZip use). When decrypting, v2 also tries v1's encoding (Zip4j took the low byte of each UTF-16 character). The two are identical for ASCII passwords, so this only matters for v1 vaults with non-ASCII passwords.
- Renaming a note only changes its Zip header. The file name isn't part of the encrypted data, so no re-encryption is needed.

**Security note.** The WinZip format caps PBKDF2 at 1000 iterations of SHA-1, which is weak by today's standards against offline brute force. That is the price of 7-Zip compatibility. The README states plainly that a long random password is the defence. **Decided:** all crypto stays exactly as in v1. There is no alternative or stronger mode.

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

### Framework: vanilla TypeScript (decided)

| Option | Packages | Pros | Cons |
|---|---|---|---|
| **A. Vanilla TS + tiny in-house view helper** (chosen) | 0 | Zero runtime supply chain; every shipped line is ours and reviewable; small bundle | We write a ~100-line `h()`/render helper; less ergonomic for big UIs |
| B. Preact (rejected) | 1 (no transitive deps) | Declarative, React-like, ~4 KB | One third-party package in the shipped bundle |
| C. React + ReactDOM (rejected) | 3+ (`react`, `react-dom`, `scheduler`) | Familiar | Larger bundle and surface, no benefit at this app size |

The UI is about four screens: setup, note list, editor, and password prompt. That's small enough that a declarative-style pattern (state → `render()` → DOM) works fine in plain TS without a library. This follows the v1 deprecation notice's preference for declarative UI without pulling in a dependency. **Decided:** vanilla TS only, no Preact, and nothing that isn't strictly needed.

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
- **ESLint / Prettier / any formatter or linter**: dropped (decided). We rely on `tsc --strict` plus extra checks (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `erasableSyntaxOnly`).
- **Any zip/crypto library** (JSZip, zip.js, fflate, ...): see section 3.

**Why esbuild is strictly needed (accepted):** Chromium refuses to load ES modules from `file://`, and the CSP hash scheme needs a single inlined script, so the TS modules must be bundled into one classic script. `tsc` can't do that for ES modules, and an in-house bundler would be more code to trust than esbuild's single, dependency-free binary. It's a build-time tool only; nothing from it ships except the bundled output of our own code, and the reproducibility matrix checks that output.

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

The **encrypted zip is the single source of truth.** The app keeps decrypted notes only in memory, never on disk or in browser storage.

**Zip variant (`file://`):** the zip file on the user's disk is the vault.

- **Open:** `<input type="file">`, which works in every browser on `file://`.
- **Save:**
  - Baseline (all browsers): download the updated zip via a Blob URL, so the user saves or overwrites the file.
  - Enhancement (Chromium): File System Access API (`showOpenFilePicker` / `createWritable`), so the opened file is saved in place with a "Save" button. Feature-detected and never required.
- **Unsaved changes:** a visible "unsaved changes" indicator plus a `beforeunload` warning.
- **No recent-file list.** The app doesn't remember file handles between sessions. On each launch the user opens the file again; this keeps the feature set minimal and avoids browser storage on `file://`.
- **No vault data in browser storage** in the zip variant. On `file://` this storage is unreliable anyway, and its origin is shared with other local files in some browsers. The only thing stored there, best effort, is the non-secret idle-lock setting (see below).

**PWA variant (`https://`):** picking a file on every launch is painful on a phone, so the PWA additionally keeps a copy of the **encrypted** zip bytes (ciphertext only, exactly what's in the file) in IndexedDB (available in every target browser, including iOS home-screen apps). It's read on launch, so the user only types the password.

- Never stored: the password, derived keys, decrypted notes.
- Browser storage is not a backup. iOS can evict it, and uninstalling the PWA deletes it. The app says so, and nags with "last exported N days ago" when the vault has changed since the last export. **Decided:** keep the encrypted vault in browser storage.
- "Export vault" (download or share sheet via `navigator.share` with a file, where supported) gets the zip into Files, Dropbox, etc. Backups remain the user's job.
- `navigator.storage.persist()` is requested to make eviction less likely.

### Password handling

- The master password is kept in memory for the session to decrypt and encrypt entries. It is never written anywhere.
- **Idle auto-lock (decided):** after **60 minutes** without user input (default), the app clears the password, derived keys, and decrypted notes from memory and shows the password prompt again. Unsaved edits are never discarded. Before locking, they're encrypted into an updated vault: the PWA writes it to browser storage, and the zip variant keeps the encrypted zip in memory (still marked unsaved) until the user unlocks and saves it.
- The timeout is configurable in Settings (e.g. 1–1440 minutes). The setting isn't secret; the PWA stores it in browser storage, and the zip variant stores it in `localStorage` best effort and falls back to 60 minutes.
- Closing the tab or app always drops everything from memory.
- Wrong password is detected via the 2-byte verifier and confirmed via the HMAC. A failed HMAC is reported as "file corrupted or wrong password", never shown as garbage text.

### Saving entries

v1 rewrote an entry by removing and re-adding it, which could lose data. v2 always builds a complete new zip in memory and only then hands it to the save path, so an error can never leave a half-written vault.

### Module layout (proposed)

```
src/
  crypto/    pbkdf2, aes-ctr-le, hmac, winzip-aes (encrypt/decrypt entry)
  zip/       reader, writer, crc32, inflate (DecompressionStream wrapper)
  vault/     Vault model: list/add/rename/delete/update, per-entry password fallback on read, import validation
  ui/        h() helper, screens (setup, list, editor, password prompt), styles
  pwa/       vault persistence (IndexedDB), service-worker registration
  sw.ts      service worker (precache + cache-first, no runtime fetches)
  main.ts    wiring; enables pwa/ only when served over https
public/
  manifest.webmanifest, icons (PNG, committed, not generated at build time)
scripts/
  build.ts   esbuild bundle → inline into index.html → sw.js with precache manifest → release zip → SHA256SUMS
  serve.ts   tiny node:http static server for PWA e2e tests (no dependency)
  verify-pwa.sh  compares the deployed GitHub Pages files with a release's SHA256SUMS
tests/
  unit/        node:test, *.test.ts
  integration/ node:test, fixtures + 7-Zip interop
  e2e/         Playwright, *.spec.ts
  fixtures/    copied from legacy/app/src/androidTest/assets/
```

`crypto/`, `zip/`, and `vault/` are pure TS with no DOM access, so they run unchanged in Node for tests.

---

## 6. No-network guarantee

1. **CSP** in `index.html` via `<meta http-equiv="Content-Security-Policy">`, identical for both variants:
   `default-src 'none'; script-src 'sha256-<hash of inlined bundle>'; style-src 'sha256-<hash>'; img-src 'self' data: blob:; manifest-src 'self'; worker-src 'self'; connect-src 'none'; form-action 'none'; base-uri 'none'`.
   The build script computes the hashes. `connect-src 'none'` blocks `fetch`, XHR, WebSocket, and EventSource from the page. `default-src 'none'` blocks external scripts, fonts, and images. `'self'` only ever means the app's own origin (GitHub Pages) and is inert on `file://`.
2. **Single self-contained `index.html`**: JS and CSS are inlined, so there's nothing to load, not even local files. This also avoids the `file://` module/CORS problems. The PWA serves the **same byte-identical `index.html`**, plus `sw.js`, the manifest, and icons.
3. **Service worker (PWA only):** precaches the fixed file list at install and serves everything cache-first. It has no runtime `fetch` passthrough for anything outside the precache list, so it can't be used to load new code. The only network traffic is the browser's own periodic check of `sw.js` for updates.
4. **No external URLs in the source**: a build check fails if the bundle contains `http://` or `https://` (except an allow-list such as the license text).
5. **E2E test**: Playwright records every request while running all user flows. It asserts that none were made except the initial `file://` load (zip variant) and, for the PWA, none at all after installation with the network switched off.

---

## 7. Distribution

One build produces two outputs from the same source and the same `index.html`:

```
dist/
  pwa/                                   deployed as-is to GitHub Pages under /v<version>/
    index.html                           identical bytes to the one in the zip
    sw.js
    manifest.webmanifest
    icons/icon-192.png, icon-512.png, apple-touch-icon.png
  secure-zip-notes-v<version>.zip        secure-zip-notes-v<version>/{index.html, LICENSE, README.txt}
  secure-zip-notes-v<version>-pwa.zip    the pwa/ folder, attached to the release so Pages can be rebuilt from releases
  SHA256SUMS                             hash of every file above, including both zips
```

Minimum browsers: current Chrome/Edge, Firefox, and Safari (desktop and mobile). The baseline needs WebCrypto, `DecompressionStream('deflate-raw')` (Chrome 103+, Firefox 113+, Safari 16.4+), and `<input type=file>`. The PWA additionally needs service workers.

### Method 1: zip download (desktop, fully offline)

Download the zip from GitHub Releases, check it against `SHA256SUMS`, extract it, and open `index.html`. It never touches the network. The service worker and manifest code stays inactive on `file://`.

### Method 2: PWA (mobile and desktop, no app store)

- Hosted on GitHub Pages at `https://fkirc.github.io/secure-zip-notes/` (decided, no custom domain). Free, static, no server code, deployed only by the release pipeline.
- **Android (Chrome):** open the URL, then "Install app" / "Add to Home Screen". It runs full-screen with its own icon.
- **iOS / iPadOS (Safari):** Share → "Add to Home Screen". Home-screen web apps run standalone and have their own storage.
- **Desktop Chromium:** "Install" in the address bar. Firefox desktop has no install, but the page still works offline after the first visit.
- No App Store, Google Play, developer accounts, contracts, signing keys, or review process. The only infrastructure is the GitHub repo.
- Manifest: `start_url: "./"`, `scope: "./"`, `display: "standalone"`. Relative paths, so each version works under its own `/secure-zip-notes/v<version>/` path.

**What a PWA can't do compared with v1** (to be stated in the README): no hardware-keystore or biometric unlock, iOS may evict storage (hence export reminders), and there are no automatic updates.

### Updates: never automatic (decided)

The app never updates itself. A user gets a new version only by installing it manually.

A normal PWA can't guarantee that: browsers re-check `sw.js` and activate a new version once all tabs are closed. So v2 makes every release **immutable and version-pinned**:

- Each release is deployed to its own path, e.g. `https://fkirc.github.io/secure-zip-notes/v2.0.0/`, and the files at that path never change again. The service worker's scope is that path only.
- An installed PWA points at its version path, so the browser's update check always finds the same `sw.js` bytes and there is nothing to update to.
- The service worker never calls `skipWaiting()` or `clients.claim()`, never fetches anything outside its precache list, and the app shows no "update available" prompt. It makes no request of its own to look for new versions.
- `https://fkirc.github.io/secure-zip-notes/` itself is a static landing page with no service worker and no app code. It lists every version with its release digest and a link to its GitHub Release.
- Settings → About shows the running version and the SHA-256 of the inline app script (the same hash pinned in the CSP and printed in the release notes), plus the releases URL as plain text. The release digest itself can't be embedded, because it covers `index.html`.

**Manually updating the PWA:** in the old version, choose "Export vault". Open the new version's URL, install it, and choose "Open vault" with the exported file. Then uninstall the old version. All versions share the same origin, so the new version may already see the stored vault, but export/import is the documented path because iOS isolates the storage of each home-screen app. **Manually updating the zip variant:** download and extract the new zip.

Old versions stay online, so an installed PWA keeps working even if a later release is bad.

Honest limitation: whoever controls the GitHub Pages deployment (Felix's GitHub account, or GitHub itself) could replace files at an existing version path. Browsers would then pick up the changed `sw.js`. The defences are that the pipeline only ever adds version directories (and checks that the old ones are byte-identical to their releases), the published hashes, build attestations, and the verification script in section 8, which can run on a schedule. Users who want zero trust in hosting use the zip.

GitHub Pages can't set HTTP headers, so the CSP is delivered via `<meta>` only. `frame-ancestors` (clickjacking protection) isn't available through `<meta>`, so the app adds a small frame-busting check instead.

---

## 8. Reproducible builds, release pipeline, and verification

### Determinism requirements

Building the same tag on any machine must produce byte-identical files. The build pins or neutralizes every input:

| Input | How it's fixed |
|---|---|
| Node.js | Exact version in `.nvmrc` and `engines`. CI uses exactly that version, and the build script refuses to run on any other version |
| pnpm | `packageManager: "pnpm@<exact>+sha512.<hash>"`, enforced through Corepack |
| Dependencies | `pnpm-lock.yaml` with integrity hashes, `--frozen-lockfile`, exact versions, no install scripts |
| esbuild | Exact version. Its output doesn't depend on the platform binary; the CI OS matrix proves that for every commit |
| Timestamps | None in the output. Zip entries use the fixed DOS date 1980-01-01 00:00, and no `Date.now()` in the build |
| File order | Zip entries, precache list, and `SHA256SUMS` are sorted by byte-wise path |
| Zip format | The release zip is written by **our own** zip writer (the same module the app uses, with STORE and no compression), not by an OS `zip` tool or zlib. Fixed permissions in the external attributes and no extra fields |
| Line endings | `.gitattributes` with `* text eol=lf` and `*.png binary`, so a Windows checkout gives the same bytes |
| Environment | No absolute paths, usernames, hostnames, env vars, locale, or `TZ` read by the build. Source maps are off in release output |
| Version string | Read from `package.json` only, with no git commit hash or build number injected |
| Icons | Committed PNGs, not rendered at build time |

`pnpm build` prints the release digest at the end, which is the SHA-256 of `SHA256SUMS`. It identifies the entire release in one hash.

### Release pipeline (GitHub Actions, public)

`.github/workflows/release.yml`, triggered by pushing a tag `v*`:

1. **Build matrix** (ubuntu, macos, windows): checkout → setup Node (pinned) → Corepack pnpm → `pnpm install --frozen-lockfile` → type check → all tests → `pnpm build`.
2. **Compare:** all three `SHA256SUMS` must be identical, or the release fails.
3. **Attest:** `actions/attest-build-provenance` creates a signed SLSA provenance attestation for the zip and every PWA file. It ties the hashes to this repo, workflow, and commit, and anyone can check it with `gh attestation verify`.
4. **Publish release:** a GitHub Release with the zip, `SHA256SUMS`, and the digest in the release notes.
5. **Deploy PWA:** a GitHub Pages deployment replaces the whole site, so the job assembles it from scratch: it downloads the `-pwa.zip` of **every** published v2 release, checks each against that release's `SHA256SUMS` (and fails if any differ), unpacks each into `v<version>/`, adds the new `dist/pwa/` as `v<new version>/`, and generates the landing page. Then `actions/upload-pages-artifact` + `actions/deploy-pages`. Pages is deployed only from this workflow, never from a branch. The landing page is the only file that changes between deployments.

Workflow hardening:

- Only first-party `actions/*` actions, each pinned by full commit SHA.
- `permissions:` set to the minimum per job (`contents: write` only on the release job, `id-token: write` + `attestations: write` for attestation, `pages: write` for deploy).
- No secrets are needed. No caches are restored into release builds, so there's no cache-poisoning path.

### How anyone can verify a release

**Rebuild from source and compare:**

```sh
git clone https://github.com/fkirc/secure-zip-notes && cd secure-zip-notes
git checkout v2.x.y                 # optionally verify a signed tag
corepack enable                     # uses the pinned pnpm
pnpm install --frozen-lockfile      # requires the Node version from .nvmrc
pnpm build
diff dist/SHA256SUMS <(curl -sL https://github.com/fkirc/secure-zip-notes/releases/download/v2.x.y/SHA256SUMS)
```

No output from `diff` means the published release is exactly what the source produces.

**Verify a downloaded zip:** `sha256sum -c SHA256SUMS --ignore-missing`, and optionally `gh attestation verify secure-zip-notes-v2.x.y.zip --repo fkirc/secure-zip-notes`.

**Verify the deployed PWA:** `scripts/verify-pwa.sh v2.x.y` downloads every file listed under `pwa/` in that release's `SHA256SUMS` from `https://fkirc.github.io/secure-zip-notes/v2.x.y/` and compares the hashes. With `--all` it checks every published version. It can be run by anyone at any time, including on a schedule from CI, which fails loudly if the live site ever differs from the release.

**Verify on the device:** Settings → About shows the version and the app script hash from the release notes. The page itself could be tampered with, so this is a convenience, not proof. The proof is the script above, or comparing the files the browser cached (DevTools → Application → Cache Storage) with `SHA256SUMS`.

---

## 9. Test plan

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
- Reading entries with other passwords: two and four passwords (`MultiPasswordTests.kt`); new entries always use the master password.
- New for v2: save via download and re-open gives identical notes; the unsaved-changes warning; **no network requests during any test** (section 6); CSP present and enforced (a probe `fetch` must fail); idle auto-lock after the default timeout and after a custom timeout (with a fake clock), with unsaved changes kept.
- PWA (Chromium, served by `scripts/serve.ts` on `http://localhost`, which counts as a secure context): service worker installs; with the network switched off the app still loads and works; the vault persists across reloads; deploying a different build at another version path doesn't change what the installed version serves, and no update prompt or activation ever happens; manifest is valid and installable.
- `SyncTests.kt` (Dropbox) is dropped.

### Reproducibility tests (CI)

- Build on Ubuntu, macOS, and Windows runners and compare `SHA256SUMS`; any difference fails CI.
- Build twice on the same runner in different directories, with different `TZ`, locale, and umask; outputs must match.

---

## 10. Repository layout and workflow

- v2 lives at the repository root (`package.json`, `src/`, `tests/`, ...); v1 stays untouched in `legacy/`.
- Work is pushed directly to `master` for now (no feature branches), per Felix.
- CI on every push: `pnpm install --frozen-lockfile` → `tsc --noEmit` → unit → build → integration → e2e → reproducibility matrix.
- Release on tag `v*`: see section 8.

---

## 11. Decisions log

Answered by Felix on 2026-11-22:

1. **Framework:** vanilla TypeScript with a tiny in-house view helper. No Preact, no React, nothing not strictly needed.
2. **Formatter/linter:** none.
3. **Bundler:** esbuild accepted, because it's strictly needed (section 4).
4. **Password lifetime:** in memory only, with an idle auto-lock. The default is 60 minutes and the user can change it.
5. **Crypto:** exactly the same as v1.
6. **Save model:** download-to-save everywhere, plus in-place save on Chromium. No recent-file list.
7. **Search:** dropped.
8. **Passwords:** single master password, as in v1. Reading imported entries with other passwords stays as v1 did it.
9. **Target devices:** desktop via the zip, mobile via the PWA.
10. **Distribution:** zip on GitHub Releases, and the PWA on GitHub Pages.
11. **PWA storage:** the encrypted vault is kept in browser storage.
12. **Hosting:** GitHub Pages at `fkirc.github.io/secure-zip-notes/`, no custom domain.
13. **Updates:** never automatic. Users update only by installing a new version manually (section 7).

No open questions remain.
