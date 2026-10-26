import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';

/**
 * Does this ID token belong to the authorization request this tab made? (R7, L1)
 *
 * `state` ties the callback URL back to this tab - tasks 17 and 18 built that. The `nonce` ties the ID TOKEN back
 * to it, which is a different hop: a token obtained legitimately for some other login, then substituted into this
 * callback, carries a perfectly good `state` and is caught by nothing else. This check is the only thing that
 * catches it, which is why a comparison here that could pass vacuously would be worse than none at all - it would
 * read as protection.
 *
 * The payload is DECODED, not verified. R7 says "validate `nonce`" and L1 says "`state` and `nonce` validated";
 * neither mentions signatures, no JWKS handling appears anywhere in the spec package, and no JWT library is in the
 * confirmed stack (L13). That is also the OIDC position: a client that takes the token straight from the token
 * endpoint over TLS may rely on that channel instead of checking the signature itself (OIDC Core 3.1.3.7), which
 * is exactly this case on a single origin (L9). So only segment two is ever touched here. The signature is never
 * read, and no claim but `nonce` is looked at - an `iss` or `exp` check would be a different task's, and `iss`
 * would need a configured issuer that the bundle may not carry (L10).
 *
 * This module writes nothing and deletes nothing. The three stored items survive a pass and a failure alike:
 * deleting them is task 23's, and task 18 left a test that fails by design the moment task 23 lands.
 */

/**
 * Why the check failed.
 *
 * Deliberately not a boolean. A boolean cannot tell "the token carried no `nonce` claim" from "nothing was
 * stored to compare against", so a regression that collapsed those two paths into one would be invisible to
 * every test. These values exist for the tests to tell the five modes apart and for nothing else: NONE of them
 * is ever rendered, logged or mapped to copy, because L4 forbids telling a visitor which check failed.
 */
export type NonceRejection =
  | 'malformed-token'
  | 'claim-missing'
  | 'claim-not-a-string'
  | 'nothing-stored'
  | 'mismatch';

export type NonceResult = { ok: true } | { ok: false; reason: NonceRejection };

/**
 * The ID token's payload, or null if it is not readable as one.
 *
 * Returns rather than throws, for every failure: a bad segment count, a segment that is not base64url, a payload
 * that is not JSON, and a payload that is JSON but not an object. There is no error boundary anywhere in
 * production source, so a throw escaping this would blank the screen at the very end of a sign-in - and an
 * unreadable token is a rejection on the merits anyway, not an exceptional condition.
 *
 * `null` is excluded explicitly because `typeof null === 'object'`, and a JSON payload of `null` would otherwise
 * be accepted here and then produce a confusing read of `.nonce` on nothing.
 */
function decodePayload(idToken: string): Record<string, unknown> | null {
  const segments = idToken.split('.');
  // Exactly three. A two-segment value is an unsecured JWT shape and a four-segment one is JWE, and neither is
  // what the token endpoint returned; accepting either would mean comparing against a payload nobody signed.
  if (segments.length !== 3) return null;

  try {
    // base64url to base64: the alphabet differs in two characters, and the padding a decoder wants is omitted.
    const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const binary = globalThis.atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
    // Through TextDecoder rather than straight off atob: atob yields one char per byte, so any claim outside
    // ASCII would be silently mangled. The nonce itself is base64url, but a mangled payload could also throw
    // inside JSON.parse, and a token is better rejected for what it is than for how it was read.
    const payload: unknown = JSON.parse(new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0))));
    if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) return null;
    return payload as Record<string, unknown>;
  } catch {
    return null;
  }
}

/**
 * The nonce stored when the request was built, or null if there is nothing usable.
 *
 * The read is guarded. `sessionStorage.getItem` can THROW rather than return null - blocked site data, some
 * webviews - and with no error boundary in production source that would blank the screen. A throw is treated as
 * "nothing stored", which is also the right security answer: with nothing to compare against, the only safe
 * outcome is to reject.
 *
 * An empty string is normalized to null here so the caller has one absence to handle instead of two. It cannot
 * arise from `authorizeRequest.ts`, which always writes a 43-character token, but it is what a partially cleared
 * or tampered store looks like, and `'' === ''` would otherwise match a token whose claim was also empty.
 */
function storedNonce(): string | null {
  let stored: string | null = null;
  try {
    stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce);
  } catch {
    stored = null;
  }
  return stored === null || stored === '' ? null : stored;
}

/**
 * The decision. Reads exactly one stored item and never throws.
 *
 * The comparison is `!==` on two strings: no trim, no case folding, no prefix or length comparison. A nonce that
 * differs by one character is a different nonce, and every softening of that is a way for a substituted token to
 * pass. The supplied side is not trimmed either - unlike the callback `state`, which task 17 trims because query
 * padding is a client-side artefact. A claim comes out of a JSON document, so whitespace in it was put there by
 * whoever minted the token, and that is exactly who this check does not trust.
 */
export function idTokenNonceMatchesStoredValue(idToken: string): NonceResult {
  const payload = decodePayload(idToken);
  if (payload === null) return { ok: false, reason: 'malformed-token' };

  // Absent is a reachable shape, not a hypothetical: measured against the running auth service, Spring
  // Authorization Server OMITS the claim entirely when the authorize request sent no nonce, rather than emitting
  // null or an empty string. This SPA always sends one, so an absent claim means this is not the token this flow
  // asked for.
  if (!('nonce' in payload)) return { ok: false, reason: 'claim-missing' };
  const claimed = payload.nonce;
  // Checked rather than coerced. `String(null)` is `'null'` and `String(42)` is `'42'`, so a coercing comparison
  // would let a crafted non-string claim match a stored value that happened to read the same way. Storage cannot
  // hold a non-string - setItem coerces to DOMString - but a claim parsed out of a token is under no such rule.
  if (typeof claimed !== 'string') return { ok: false, reason: 'claim-not-a-string' };

  const stored = storedNonce();
  if (stored === null) return { ok: false, reason: 'nothing-stored' };

  return stored === claimed ? { ok: true } : { ok: false, reason: 'mismatch' };
}
