#!/usr/bin/env bash
# Checks that the PWA files served by GitHub Pages are byte-identical to a release.
#
#   scripts/verify-pwa.sh v2.0.0 [v2.0.1 ...]
#   scripts/verify-pwa.sh --all
set -euo pipefail

REPO="fkirc/secure-zip-notes"
SITE="https://fkirc.github.io/secure-zip-notes"

if command -v sha256sum >/dev/null 2>&1; then
  hash() { sha256sum | cut -d' ' -f1; }
else
  hash() { shasum -a 256 | cut -d' ' -f1; }
fi

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <tag>... | --all" >&2
  exit 2
fi

tags=("$@")
if [ "$1" = "--all" ]; then
  mapfile -t tags < <(curl -fsSL "https://api.github.com/repos/$REPO/releases?per_page=100" \
    | grep -o '"tag_name": *"v[0-9][^"]*"' | sed 's/.*"\(v[^"]*\)"/\1/')
fi

failures=0
for tag in "${tags[@]}"; do
  sums=$(curl -fsSL "https://github.com/$REPO/releases/download/$tag/SHA256SUMS") || { echo "$tag: no SHA256SUMS, skipped"; continue; }
  if ! grep -q '  pwa/' <<<"$sums"; then echo "$tag: no PWA in this release, skipped"; continue; fi
  echo "$tag: release digest $(printf '%s\n' "$sums" | hash)"
  while read -r expected path; do
    rel="${path#pwa/}"
    actual=$(curl -fsSL "$SITE/$tag/$rel" | hash) || actual="(download failed)"
    if [ "$actual" = "$expected" ]; then
      echo "  ok        $rel"
    else
      echo "  MISMATCH  $rel (expected $expected, got $actual)"
      failures=$((failures + 1))
    fi
  done < <(grep '  pwa/' <<<"$sums")
done

if [ "$failures" -gt 0 ]; then
  echo "FAILED: $failures file(s) differ from the published releases." >&2
  exit 1
fi
echo "All checked files match the published releases."
