# Secure Zip Notes

Encrypted notes in a standard AES-256 Zip file, as an offline web app.

- **Your vault is a normal Zip file** (WinZip AES-256). 7-Zip, WinZip, The Unarchiver and similar tools can open it with your password, today and in 50 years. Vaults from the v1 Android app open in v2, and vaults written by v2 open in 7-Zip.
- **Offline only.** The app never makes a network request and never updates itself. A strict Content-Security-Policy enforces this, and tests check it.
- **No accounts, no sync, no tracking.** Backups and syncing are up to you: keep the file wherever you like.
- **Zero runtime dependencies.** All crypto uses the browser's built-in WebCrypto. The whole app is one self-contained `index.html`.

The v1 Android app is in [`legacy/`](legacy/). The full specification is in [SPEC.md](SPEC.md).

## Two ways to use it

1. **Zip download (desktop).** Download `secure-zip-notes-vX.Y.Z.zip` from [Releases](https://github.com/fkirc/secure-zip-notes/releases), extract it, and open `index.html` in your browser. Use "Save vault" to write your vault file.
2. **Installable web app (mobile and desktop).** Open `https://fkirc.github.io/secure-zip-notes/vX.Y.Z/` and choose "Add to Home Screen" (iOS Safari, Android Chrome) or "Install" (desktop Chrome/Edge). After the first visit it runs fully offline. The encrypted vault is kept in browser storage. Browser storage is not a backup, so use "Export vault" regularly.

Each version lives at its own path, and the files there never change. An installed app therefore never updates by itself. To update, export your vault, open and install the new version, then open the vault there.

## Security notes

- Encryption is WinZip AE-2: AES-256-CTR, HMAC-SHA1, and PBKDF2-HMAC-SHA1 with **1000 iterations**, which the format fixes. That makes brute force cheaper than with modern formats, so **use a long random password**. The app can generate one for you.
- The password and decrypted notes exist only in memory. The app locks after 60 idle minutes by default; you can change this in Settings.

## Development

Requirements: Node.js (exact version in [`.nvmrc`](.nvmrc)), pnpm via Corepack, and 7-Zip (`7z`) for the interoperability tests.

```sh
corepack enable
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test:unit            # crypto, zip, vault (node:test, no extra dependencies)
pnpm build                # dist/: pwa/, release zips, SHA256SUMS, RELEASE_DIGEST
pnpm test:integration     # v1 fixtures, 7-Zip both directions, build output
pnpm test:e2e             # Playwright, file:// and PWA (add --project=chromium to run one browser)
pnpm check:reproducible   # builds twice with different TZ/locale and compares every byte
node scripts/serve.ts     # preview the PWA at http://localhost:4173/secure-zip-notes/vX.Y.Z/
```

Dev dependencies are exactly `typescript`, `esbuild`, `@types/node` and `@playwright/test`, which is 8 installed packages in total. Install scripts are disabled, and new package versions must be at least 7 days old (see [`pnpm-workspace.yaml`](pnpm-workspace.yaml)).

## Releasing

1. Bump `version` in `package.json` and commit.
2. Push a tag `vX.Y.Z`.

The [release workflow](.github/workflows/release.yml) runs all tests and builds on Linux, macOS and Windows. It requires byte-identical output, then publishes the release with `SHA256SUMS` and provenance attestations. Finally it deploys every version to GitHub Pages after checking each one against its release hashes.

One-time setup: in the repository settings, set **Pages → Build and deployment → Source** to **GitHub Actions**.

## Verifying a release

```sh
git checkout vX.Y.Z && corepack enable && pnpm install --frozen-lockfile && pnpm build
diff dist/SHA256SUMS <(curl -sL https://github.com/fkirc/secure-zip-notes/releases/download/vX.Y.Z/SHA256SUMS)
scripts/verify-pwa.sh vX.Y.Z    # the hosted PWA matches the release
```

## License

MIT
