import { test } from "node:test";
import assert from "node:assert/strict";
import {
  generatePassword,
  GENERATED_PASSWORD_LENGTH,
  PASSWORD_ALPHABET,
  validateNewPassword,
} from "../../src/crypto/password.ts";

test("generated passwords have 20 characters from the v1 alphabet", () => {
  for (let i = 0; i < 50; i++) {
    const pw = generatePassword();
    assert.equal(pw.length, GENERATED_PASSWORD_LENGTH);
    for (const ch of pw) assert.ok(PASSWORD_ALPHABET.includes(ch));
  }
});

test("generated passwords are different", () => {
  const seen = new Set(Array.from({ length: 100 }, () => generatePassword()));
  assert.equal(seen.size, 100);
});

test("password validation: minimum length and confirmation", () => {
  assert.equal(validateNewPassword("short", "short"), "Minimum length: 8 characters");
  assert.equal(validateNewPassword("", ""), "Minimum length: 8 characters");
  assert.equal(validateNewPassword("12345678", "12345679"), "Passwords do not match");
  assert.equal(validateNewPassword("12345678", "12345678"), null);
});
