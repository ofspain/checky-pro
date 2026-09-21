package com.themistra.crypto.watch;

import com.themistra.crypto.token.TokenAllowlist;

/**
 * T25 Phase 3/4 Finding #2: a genuine, deliberate L15 violation - a class inside the {@code watch}
 * feature module holding a field of type {@code token.TokenAllowlist}, a real {@code @Entity} in a
 * different real feature module. Deliberately placed <b>inside</b> {@code com.themistra.crypto.watch}
 * (unlike {@code attest.archtestfixtures.RogueAttestReferencer}'s standalone top-level package) so
 * {@code featureModuleOf} correctly resolves it to {@code watch} - the whole point is to exercise the
 * real {@code entityModule != dependingModule} violation path, not the fail-fast null path a fixture
 * outside every known module would instead trip. Never referenced by any production code; the field is
 * declared, never read, which is itself the point (dependency-based checking, not access-based, is
 * required to catch exactly this shape - see {@code CrossModuleEntityArchitectureTest}'s own Javadoc).
 */
public class RogueWatchEntityReferencer {

    private final TokenAllowlist rogueCrossModuleEntityReference = null;
}
