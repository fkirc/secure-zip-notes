/** Password rules and generator, same as v1 (NewPasswordActivity.kt). */

export const MIN_PASSWORD_LENGTH = 8;
export const GENERATED_PASSWORD_LENGTH = 20;
export const PASSWORD_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

export function validateNewPassword(password: string, confirmation: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Minimum length: ${MIN_PASSWORD_LENGTH} characters`;
  if (password !== confirmation) return "Passwords do not match";
  return null;
}

/** Uniformly random password from crypto.getRandomValues (rejection sampling, no modulo bias). */
export function generatePassword(length: number = GENERATED_PASSWORD_LENGTH): string {
  const n = PASSWORD_ALPHABET.length;
  const limit = 256 - (256 % n);
  let out = "";
  const buf = new Uint8Array(64);
  while (out.length < length) {
    globalThis.crypto.getRandomValues(buf);
    for (const b of buf) {
      if (b < limit && out.length < length) out += PASSWORD_ALPHABET[b % n];
    }
  }
  return out;
}
