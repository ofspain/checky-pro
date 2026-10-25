/**
 * PKCE for the SPA's OIDC client (R6, L1).
 *
 * The SPA is a public client - no client secret in the bundle (agents.md:19-20) - so the code challenge is the only
 * thing binding an authorization code to the client that asked for it. This module generates the verifier and derives
 * its challenge; task 15 assembles the authorize request. It stores nothing: L16 lets sessionStorage hold a
 * code_verifier, but the task that writes that set as a unit owns it, and here the rule is a prohibition.
 *
 * The backend requires a challenge (RegisteredClientSeeder sets requireProofKey(true)) but does NOT require S256 -
 * S256 appears nowhere in the auth service's main source, and Spring Authorization Server accepts `plain` with no
 * switch to forbid it. So the strength of the exchange is decided here and nowhere else, which is why the method is
 * published as a literal type rather than left for a caller to spell.
 */

/** RFC 7636 4.3: the only method this client will ever send. A literal type, so no caller can widen it to `plain`. */
export const PKCE_CODE_CHALLENGE_METHOD = 'S256' as const;

/** RFC 7636 4.1 bounds. */
export const PKCE_VERIFIER_MIN_LENGTH = 43;
export const PKCE_VERIFIER_MAX_LENGTH = 128;

/** RFC 7636 4.1: `unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"`. All 66 characters, not base64url's 64. */
const UNRESERVED_PATTERN = /^[A-Za-z0-9\-._~]+$/;

/** 32 bytes is 256 bits, RFC 7636 4.1's entropy recommendation, and base64url-encodes to exactly 43 characters. */
const VERIFIER_BYTE_LENGTH = 32;

export type PkcePair = {
  verifier: string;
  challenge: string;
  method: typeof PKCE_CODE_CHALLENGE_METHOD;
};

/**
 * base64url without padding, for both halves, so the verifier and the challenge cannot disagree about the encoding.
 *
 * btoa, not Buffer: Buffer is a Node global that Vite does not polyfill, so it would pass under Vitest and fail in
 * the browser. The intermediate binary string is built with a loop rather than a spread, which would put the whole
 * array on the argument stack - irrelevant at 32 bytes, and wrong the moment anyone reuses this for a larger input.
 */
function base64UrlFromBytes(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

/**
 * Whether `candidate` is a well-formed verifier per RFC 7636 4.1. Never throws.
 *
 * Deliberately wider than what generateCodeVerifier emits: it accepts the full 43-128 range and all 66 unreserved
 * characters, because it validates any verifier - including one from a future scheme - and not only our own output.
 */
export function isValidCodeVerifier(candidate: string): boolean {
  return (
    candidate.length >= PKCE_VERIFIER_MIN_LENGTH &&
    candidate.length <= PKCE_VERIFIER_MAX_LENGTH &&
    UNRESERVED_PATTERN.test(candidate)
  );
}

/**
 * A fresh code verifier: 43 characters of base64url over 32 CSPRNG bytes.
 *
 * base64url rather than a draw from all 66 unreserved characters, because 64 divides 256 and the encoding is
 * therefore bias-free by construction. Mapping bytes onto 66 characters with `% 66` is not: 256 % 66 = 58, so eight
 * of the characters are drawn a quarter less often. Rejection sampling fixes that and buys two characters and no
 * entropy.
 */
export function generateCodeVerifier(): string {
  const bytes = new Uint8Array(VERIFIER_BYTE_LENGTH);
  crypto.getRandomValues(bytes);
  return base64UrlFromBytes(bytes);
}

/**
 * The S256 challenge: base64url(SHA-256(ASCII(verifier))), unpadded (RFC 7636 4.2).
 *
 * An invalid verifier is rejected rather than hashed - a function that hashes whatever it is handed would produce a
 * challenge for a 42-character verifier the server will later refuse, turning a local defect into a failed login.
 * TextEncoder emits UTF-8 where the RFC says ASCII; the two agree for every unreserved character, which holds only
 * because the validator above rejects anything outside that set.
 */
export async function deriveCodeChallenge(verifier: string): Promise<string> {
  if (!isValidCodeVerifier(verifier)) {
    throw new TypeError(
      `Not an RFC 7636 code verifier: expected ${PKCE_VERIFIER_MIN_LENGTH}-${PKCE_VERIFIER_MAX_LENGTH} unreserved characters`,
    );
  }
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlFromBytes(new Uint8Array(digest));
}

/** A verifier with its challenge and the method name, for the authorize request task 15 assembles. */
export async function generatePkcePair(): Promise<PkcePair> {
  const verifier = generateCodeVerifier();
  return { verifier, challenge: await deriveCodeChallenge(verifier), method: PKCE_CODE_CHALLENGE_METHOD };
}
